package com.ireddragonicy.konabessnext.core.scanner

import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DtsScanResult(
    val isValid: Boolean,
    val dtbIndex: Int,
    val recommendedStrategy: String, // "MULTI_BIN" or "SINGLE_BIN"
    val voltageTablePattern: String?,
    val maxLevels: Int,
    val levelCount: Int = 0,
    val confidence: String = "Low", // Low, Medium, High
    val detectedModel: String? = null
)

object DtsScanner {

    suspend fun scan(file: File, index: Int): DtsScanResult = withContext(Dispatchers.Default) {
        if (!file.exists()) return@withContext DtsScanResult(false, index, "UNKNOWN", null, 0)

        val content = try {
            file.readText()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext DtsScanResult(false, index, "UNKNOWN", null, 0)
        }

        val rootNode = DtsTreeHelper.parse(content)

        // 1. Find Model
        // Look for property "model" in the root node (or the first child if root is a dummy container)
        var modelProp = rootNode.getProperty("model")
        if (modelProp == null && rootNode.children.isNotEmpty()) {
             modelProp = rootNode.children.firstOrNull()?.getProperty("model")
        }
        val detectedModel = modelProp?.originalValue?.replace("\"", "")?.replace(";", "")?.trim()

        // 2. Strategy & Bin Detection
        val binNodes = mutableListOf<DtsNode>()
        findPwrLevels(rootNode, binNodes)

        var strategy = "UNKNOWN"
        if (binNodes.size > 1) {
            strategy = "MULTI_BIN"
        } else if (binNodes.size == 1) {
            strategy = "SINGLE_BIN"
        }

        // 3. Level Counting
        // Take the first bin found, count children starting with "qcom,gpu-pwrlevel@"
        // We use regex to extract the level index because the name is usually "qcom,gpu-pwrlevel@0"
        var maxObservedLevel = 0
        var levelCount = 0

        val firstBin = binNodes.firstOrNull()
        if (firstBin != null) {
            val levelNodes = firstBin.children.filter { it.name.startsWith("qcom,gpu-pwrlevel@") }
            levelNodes.forEach { node ->
                val levelIndex = node.name.substringAfterLast("@").toIntOrNull() ?: 0
                if (levelIndex > maxObservedLevel) {
                    maxObservedLevel = levelIndex
                }
            }
            if (levelNodes.isNotEmpty()) {
                levelCount = maxObservedLevel + 1
            }
        }

        // 4. Voltage Table Pattern Detection
        // Search specifically for "qcom,gpu-freq-table" or opp-table compatible properties/nodes
        // But scan result expects a String pattern name if it finds an opp-table.
        // The original scanner found table names like "gpu-opp-table".
        // Here we look for nodes that look like opp tables.
        
        var bestVoltPattern: String? = null
        val voltagePatterns = mutableSetOf<String>()
        
        fun findOppTables(node: DtsNode) {
            // Check if node name ends with "opp-table"
           if (node.name.contains("opp-table") || node.getProperty("compatible")?.originalValue?.contains("opp-table") == true) {
               voltagePatterns.add(node.name)
           }
           if (node.getProperty("qcom,gpu-freq-table") != null) {
               // This is slightly different, older style, but maybe we just mark found.
               // The original DtsScanner logic returned the *name* of the table.
           }
           node.children.forEach { findOppTables(it) }
        }
        findOppTables(rootNode)
        
        // Pick the most likely voltage pattern
        bestVoltPattern = voltagePatterns
            .filter { it.contains("gpu") || it.contains("cluster") }
            .maxByOrNull { it.length } 
            ?: voltagePatterns.firstOrNull()


        // Confidence calculation
        var confidenceScore = 0
        if (strategy != "UNKNOWN") confidenceScore += 2
        if (bestVoltPattern != null) confidenceScore += 1
        if (levelCount > 0) confidenceScore += 1
        
        val confidence = when {
            confidenceScore >= 3 -> "High"
            confidenceScore == 2 -> "Medium"
            else -> "Low"
        }

        val isValid = confidenceScore > 0

        DtsScanResult(
            isValid = isValid,
            dtbIndex = index,
            recommendedStrategy = if (strategy == "UNKNOWN") "MULTI_BIN" else strategy,
            voltageTablePattern = bestVoltPattern,
            maxLevels = if (levelCount > 0) levelCount else 15,
            levelCount = levelCount,
            confidence = confidence,
            detectedModel = detectedModel
        )
    }

    private fun findPwrLevels(node: DtsNode, result: MutableList<DtsNode>) {
        // compatible = "qcom,gpu-pwrlevels"
        // OR name starts with "qcom,gpu-pwrlevels" (legacy)
        val compatible = node.getProperty("compatible")?.originalValue
        
        // Exclude "bins" container
        val isCompatibleBin = compatible?.contains("qcom,gpu-pwrlevels") == true && compatible?.contains("bins") == false
        val isNameMatch = node.name.startsWith("qcom,gpu-pwrlevels")

        if (isCompatibleBin || isNameMatch) {
            result.add(node)
        }
        // Always recurse? No, strict recursion control.
        // If it's a bin, usually we don't look inside for other bins.
        // BUT DtsScanner logic originally just added everything it found.
        // If I change it to "if found add, else recurse", it aligns with GpuDomainManager.
        // The original logic was: add if match, AND recurse if children not empty.
        // Which meant it added BOTH container and child.
        // We want to avoid adding container.
        
        if (node.children.isNotEmpty()) {
             node.children.forEach { findPwrLevels(it, result) }
        }
    }

    fun toChipDefinition(result: DtsScanResult): ChipDefinition {
        return ChipDefinition(
            id = "custom_detected_${result.dtbIndex}_${System.currentTimeMillis()}",
            name = result.detectedModel ?: "Custom Device (DTB ${result.dtbIndex})",
            maxTableLevels = result.maxLevels,
            ignoreVoltTable = result.voltageTablePattern == null,
            minLevelOffset = 1,
            voltTablePattern = result.voltageTablePattern,
            strategyType = result.recommendedStrategy,
            levelCount = 480,
            levels = mapOf(), 
            binDescriptions = if (result.recommendedStrategy == "MULTI_BIN") mapOf(0 to "Default", 1 to "High Perf") else null,
            models = listOf(result.detectedModel ?: "Custom")
        )
    }
}