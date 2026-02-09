package com.ireddragonicy.konabessnext.core.scanner

import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Describes what kind of voltage/level mechanism was detected in the DTS.
 */
enum class VoltageType {
    /** Separate OPP table with opp-hz + opp-microvolt (e.g. SD860) */
    OPP_TABLE,
    /** Inline qcom,level property inside each gpu-pwrlevel node (e.g. Tuna/SM8750) */
    INLINE_LEVEL,
    /** No voltage mechanism detected */
    NONE
}

data class DtsScanResult(
    val isValid: Boolean,
    val dtbIndex: Int,
    val recommendedStrategy: String, // "MULTI_BIN" or "SINGLE_BIN"
    val voltageTablePattern: String?,
    val maxLevels: Int,
    val levelCount: Int = 0,
    val confidence: String = "Low", // Low, Medium, High
    val detectedModel: String? = null,
    val voltageType: VoltageType = VoltageType.NONE,
    val binCount: Int = 0,
    val detectedProperties: List<String> = emptyList(), // e.g. ["qcom,gpu-freq", "qcom,level", "qcom,bus-freq"]
    val gpuNodeName: String? = null, // e.g. "qcom,kgsl-3d0@3d00000"
    val gpuModel: String? = null, // e.g. "Adreno825" from qcom,gpu-model
    val chipId: String? = null // e.g. "0x44030000" from qcom,chipid
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
        
        scanContent(content, index)
    }

    /**
     * Scan DTS content string directly (no file needed).
     * Useful for scanning DTBs that have already been converted in-memory.
     */
    suspend fun scanContent(content: String, index: Int): DtsScanResult = withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext DtsScanResult(false, index, "UNKNOWN", null, 0)

        val rootNode = try {
            DtsTreeHelper.parse(content)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext DtsScanResult(false, index, "UNKNOWN", null, 0)
        }

        // 1. Find Model
        var modelProp = rootNode.getProperty("model")
        if (modelProp == null && rootNode.children.isNotEmpty()) {
            modelProp = rootNode.children.firstOrNull()?.getProperty("model")
        }
        val detectedModel = modelProp?.originalValue?.replace("\"", "")?.replace(";", "")?.trim()

        // 2. Find GPU node (qcom,kgsl-3d0@...) for GPU model/chipid
        var gpuNodeName: String? = null
        var gpuModel: String? = null
        var chipId: String? = null
        
        fun findGpuNode(node: DtsNode) {
            if (gpuNodeName != null) return
            val compat = node.getProperty("compatible")?.originalValue ?: ""
            if (node.name.contains("kgsl-3d0") || compat.contains("qcom,kgsl-3d0")) {
                gpuNodeName = node.name
                gpuModel = node.getProperty("qcom,gpu-model")?.originalValue
                    ?.replace("\"", "")?.replace(";", "")?.trim()
                chipId = node.getProperty("qcom,chipid")?.originalValue
                    ?.replace(";", "")?.trim()
                    ?.let { raw ->
                        // Extract hex value from <0x...>
                        val match = Regex("<([^>]+)>").find(raw)
                        match?.groupValues?.get(1)?.trim() ?: raw
                    }
            }
            node.children.forEach { findGpuNode(it) }
        }
        findGpuNode(rootNode)

        // 3. Strategy & Bin Detection
        val binNodes = mutableListOf<DtsNode>()
        findPwrLevels(rootNode, binNodes)

        var strategy = "UNKNOWN"
        if (binNodes.size > 1) {
            strategy = "MULTI_BIN"
        } else if (binNodes.size == 1) {
            strategy = "SINGLE_BIN"
        }

        val binCount = binNodes.size

        // 4. Level Counting & Property Detection
        var maxObservedLevel = 0
        var levelCount = 0
        val detectedProperties = mutableSetOf<String>()

        val firstBin = binNodes.firstOrNull()
        if (firstBin != null) {
            val levelNodes = firstBin.children.filter { it.name.startsWith("qcom,gpu-pwrlevel@") }
            levelNodes.forEach { node ->
                val levelIndex = node.name.substringAfterLast("@").toIntOrNull() ?: 0
                if (levelIndex > maxObservedLevel) {
                    maxObservedLevel = levelIndex
                }
                // Collect all property names from level nodes for smart detection
                node.properties.forEach { prop ->
                    detectedProperties.add(prop.name)
                }
            }
            if (levelNodes.isNotEmpty()) {
                levelCount = maxObservedLevel + 1
            }
        }

        // 5. Voltage Table Pattern Detection (OPP table style)
        var bestVoltPattern: String? = null
        val voltagePatterns = mutableSetOf<String>()

        fun findOppTables(node: DtsNode) {
            val compat = node.getProperty("compatible")?.originalValue ?: ""
            if (node.name.contains("opp-table") || compat.contains("operating-points") || compat.contains("opp-table")) {
                voltagePatterns.add(node.name)
            }
            node.children.forEach { findOppTables(it) }
        }
        findOppTables(rootNode)

        // Pick the most likely GPU voltage pattern
        bestVoltPattern = voltagePatterns
            .filter { it.contains("gpu") || it.contains("cluster") }
            .maxByOrNull { it.length }
            ?: voltagePatterns.firstOrNull()

        // 6. Determine voltage type
        val hasOppTable = bestVoltPattern != null
        val hasInlineLevel = detectedProperties.any { 
            it == "qcom,level" || it == "qcom,corner" || it == "qcom,bw-level" 
        }
        
        val voltageType = when {
            hasOppTable -> VoltageType.OPP_TABLE
            hasInlineLevel -> VoltageType.INLINE_LEVEL
            else -> VoltageType.NONE
        }

        // 7. Confidence calculation
        var confidenceScore = 0
        if (strategy != "UNKNOWN") confidenceScore += 2
        if (voltageType != VoltageType.NONE) confidenceScore += 1
        if (levelCount > 0) confidenceScore += 1
        if (gpuNodeName != null) confidenceScore += 1

        val confidence = when {
            confidenceScore >= 4 -> "High"
            confidenceScore >= 2 -> "Medium"
            else -> "Low"
        }

        val isValid = strategy != "UNKNOWN" && levelCount > 0

        DtsScanResult(
            isValid = isValid,
            dtbIndex = index,
            recommendedStrategy = if (strategy == "UNKNOWN") "MULTI_BIN" else strategy,
            voltageTablePattern = bestVoltPattern,
            maxLevels = if (levelCount > 0) levelCount else 15,
            levelCount = levelCount,
            confidence = confidence,
            detectedModel = detectedModel,
            voltageType = voltageType,
            binCount = binCount,
            detectedProperties = detectedProperties.toList().sorted(),
            gpuNodeName = gpuNodeName,
            gpuModel = gpuModel,
            chipId = chipId
        )
    }

    private fun findPwrLevels(node: DtsNode, result: MutableList<DtsNode>) {
        val compatible = node.getProperty("compatible")?.originalValue

        // Match patterns:
        // 1. Name is exactly "qcom,gpu-pwrlevels" (single bin, old style)
        // 2. Name matches "qcom,gpu-pwrlevels-N" (multi-bin style, SD860/Tuna)
        // 3. Compatible contains "qcom,gpu-pwrlevels" but NOT "bins" (to exclude container)
        val pwrLevelsPattern = Regex("""qcom,gpu-pwrlevels(-\d+)?$""")
        val isNameMatch = pwrLevelsPattern.matches(node.name)
        val isCompatibleBin = compatible?.contains("qcom,gpu-pwrlevels") == true 
            && compatible.contains("bins") == false

        if (isNameMatch || isCompatibleBin) {
            result.add(node)
        }

        // Always recurse into children to find nested pwrlevels
        node.children.forEach { findPwrLevels(it, result) }
    }

    fun toChipDefinition(result: DtsScanResult): ChipDefinition {
        // Build bin descriptions from detected bin count
        val binDescriptions = if (result.binCount > 1) {
            (0 until result.binCount).associate { i -> i to "Speed Bin $i" }
        } else null

        // Determine if voltage table should be ignored
        // OPP_TABLE → use it; INLINE_LEVEL → ignore separate volt table (levels are inline);
        // NONE → ignore
        val ignoreVoltTable = result.voltageType != VoltageType.OPP_TABLE

        return ChipDefinition(
            id = "custom_detected_${result.dtbIndex}_${System.currentTimeMillis()}",
            name = buildSmartName(result),
            maxTableLevels = result.maxLevels,
            ignoreVoltTable = ignoreVoltTable,
            minLevelOffset = 1,
            voltTablePattern = result.voltageTablePattern,
            strategyType = result.recommendedStrategy,
            levelCount = 480,
            levels = mapOf(),
            binDescriptions = binDescriptions,
            models = listOf(result.detectedModel ?: "Custom")
        )
    }

    /**
     * Build a display name from scan results using GPU model, chip model, or fallback.
     */
    private fun buildSmartName(result: DtsScanResult): String {
        val parts = mutableListOf<String>()
        
        // Use GPU model name if available (e.g. "Adreno825")
        if (!result.gpuModel.isNullOrBlank()) {
            parts.add(result.gpuModel)
        }
        
        // Use detected model (e.g. "Qualcomm Technologies, Inc. Tuna SoC")
        if (!result.detectedModel.isNullOrBlank()) {
            // Shorten "Qualcomm Technologies, Inc." prefix
            val shortModel = result.detectedModel
                .replace("Qualcomm Technologies, Inc. ", "")
                .replace("Qualcomm Technologies, Inc ", "")
            parts.add(shortModel)
        }
        
        return when {
            parts.isEmpty() -> "Custom Device (DTB ${result.dtbIndex})"
            parts.size == 1 -> parts[0]
            else -> "${parts[0]} - ${parts[1]}"
        }
    }
}