package com.ireddragonicy.konabessnext.core.scanner

import com.ireddragonicy.konabessnext.model.ChipDefinition
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

data class DtsScanResult(
    val isValid: Boolean,
    val dtbIndex: Int,
    val recommendedStrategy: String, // "MULTI_BIN" or "SINGLE_BIN"
    val voltageTablePattern: String?,
    val maxLevels: Int,
    val levelCount: Int = 0, // Estimated
    val confidence: String = "Low" // Low, Medium, High
)

object DtsScanner {

    fun scan(file: File, index: Int): DtsScanResult {
        if (!file.exists()) return DtsScanResult(false, index, "", null, 0)

        var hasMultiBin = false
        var hasSingleBin = false
        val voltagePatterns = mutableSetOf<String>()
        var levelCount = 0
        var maxLevels = 0

        // Regex patterns
        val pwrLevelsMultiInfo = "qcom,gpu-pwrlevels-0"
        val pwrLevelsSingleInfo = "qcom,gpu-pwrlevels" // without suffix
        
        // Use a simple counter for levels which usually appear as "level-X {" 
        // or just count lines inside power levels?
        // Actually the prompt suggests counting '@' subnodes under frequency table.
        // But usually levels are like "id-0 { ... }" inside qcom,gpu-pwrlevels.
        // Let's look for "qcom,gpu-freq" and count entries there? 
        // Or look for "freq-table-0" ?
        
        // Let's follow the heuristic analysis from requirements:
        // - Keyword Search: Scan for qcom,gpu-pwrlevels, gpu-opp-table, and qcom,gpu-freq.
        // - Strategy Detection: 
        //     - If it finds qcom,gpu-pwrlevels-0 { -> MULTI_BIN.
        //     - If it finds qcom,gpu-pwrlevels { (without index) -> SINGLE_BIN.
        // - Voltage Table Detection: Look for nodes containing opp-hz and opp-microvolt. Extract the parent node name.
        // - Level Counting: Count how many @ subnodes exist under the frequency table to suggest the maxTableLevels.
        
        var currentScope = ""
        var insideOppTable = false
        var currentOppTableName = ""
        var oppTableLevels = 0

        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    
                    // Strategy Detection
                    if (trimmed.contains("$pwrLevelsMultiInfo {")) {
                        hasMultiBin = true
                    } else if (trimmed.contains("$pwrLevelsSingleInfo {")) {
                        hasSingleBin = true
                    }
                    
                    // Voltage Table Detection logic
                    // Detect start of a node
                    if (trimmed.endsWith("{")) {
                        val nodeName = trimmed.substringBefore("{").trim()
                        if (nodeName.contains("opp-table")) { // Heuristic: usually contains "opp-table"
                            insideOppTable = true
                            currentOppTableName = nodeName
                            oppTableLevels = 0
                        }
                    } else if (trimmed == "};") {
                        if (insideOppTable) {
                            insideOppTable = false
                            if (oppTableLevels > 0) {
                                voltagePatterns.add(currentOppTableName)
                                if (oppTableLevels > maxLevels) {
                                    maxLevels = oppTableLevels
                                }
                            }
                        }
                    }
                    
                    // Count levels inside an opp-table
                    if (insideOppTable) {
                        // Check for opp items. Usually "opp-123456 {" or "opp-hz = <...>"
                        // Requirement: "Count how many @ subnodes exist" - this is for DTS style like "opp@1234"
                        // But also check for "opp-hz" lines?
                        // Let's look for "opp-hz" presence if we aren't sure about node names.
                        if (trimmed.contains("opp-hz")) {
                            oppTableLevels++
                        }
                    }

                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return DtsScanResult(false, index, "Error", null, 0)
        }

        val strategy = if (hasMultiBin) "MULTI_BIN" else if (hasSingleBin) "SINGLE_BIN" else "UNKNOWN"
        val voltPattern = voltagePatterns.firstOrNull() // Pick the first one found for now.
        
        // If we found a strategy, it's a valid candidate
        val isValid = strategy != "UNKNOWN"
        
        // Refine maxLevels based on observed count. 
        // If 0, default to something standard like 11 or 16? 
        // Requirements say "suggest maxTableLevels".
        val finalMaxLevels = if (maxLevels > 0) maxLevels else 15 // Fallback
        
        return DtsScanResult(
            isValid = isValid,
            dtbIndex = index,
            recommendedStrategy = strategy,
            voltageTablePattern = voltPattern,
            maxLevels = finalMaxLevels,
            levelCount = maxLevels,
            confidence = if (isValid && voltPattern != null) "High" else if (isValid) "Medium" else "Low"
        )
    }

    fun toChipDefinition(result: DtsScanResult): ChipDefinition {
        return ChipDefinition(
            id = "custom_detected_${result.dtbIndex}",
            name = "Discovered Device (DTB ${result.dtbIndex})",
            maxTableLevels = result.maxLevels,
            ignoreVoltTable = result.voltageTablePattern == null,
            minLevelOffset = 1, // Default safe value
            voltTablePattern = result.voltageTablePattern,
            strategyType = result.recommendedStrategy,
            levelCount = if (result.maxLevels > 0) result.maxLevels else 416, 
            levels = mapOf(), // Empty map, will need to be populated or handled
            binDescriptions = if (result.recommendedStrategy == "MULTI_BIN") mapOf(0 to "Bin 0", 1 to "Bin 1") else null,
            models = listOf("Auto-Detected")
        )
    }
}
