package com.ireddragonicy.konabessnext.core.scanner

import com.ireddragonicy.konabessnext.model.ChipDefinition
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.regex.Pattern

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

    // Regex for robust matching
    private val REGEX_OPP_TABLE = Pattern.compile("(\\w+[-_]opp[-_]table)\\s*\\{")
    private val REGEX_GPU_FREQ_TABLE = Pattern.compile("qcom,gpu-freq-table")
    private val REGEX_PWR_LEVEL = Pattern.compile("qcom,gpu-pwrlevel@(\\d+)")
    private val REGEX_PWR_LEVELS_MULTI = Pattern.compile("qcom,gpu-pwrlevels-\\d+")
    private val REGEX_PWR_LEVELS_SINGLE = Pattern.compile("qcom,gpu-pwrlevels\\s*\\{")

    fun scan(file: File, index: Int): DtsScanResult {
        if (!file.exists()) return DtsScanResult(false, index, "UNKNOWN", null, 0)

        var hasMultiBin = false
        var hasSingleBin = false
        val voltagePatterns = mutableSetOf<String>()
        var maxObservedLevel = 0
        var detectedModel: String? = null

        // Heuristics state
        var insideOppTable = false
        var currentOppTableName = ""
        var currentOppCount = 0

        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()

                    // 1. Strategy Detection
                    if (REGEX_PWR_LEVELS_MULTI.matcher(trimmed).find()) {
                        hasMultiBin = true
                    } else if (REGEX_PWR_LEVELS_SINGLE.matcher(trimmed).find()) {
                        hasSingleBin = true
                    }

                    // 2. Level Counting (looking for qcom,gpu-pwrlevel@X)
                    val lvlMatcher = REGEX_PWR_LEVEL.matcher(trimmed)
                    if (lvlMatcher.find()) {
                        try {
                            val lvl = lvlMatcher.group(1)?.toInt() ?: 0
                            if (lvl > maxObservedLevel) maxObservedLevel = lvl
                        } catch (e: Exception) { }
                    }

                    // 3. Voltage Table Pattern Detection
                    // Detect "xyz-opp-table {"
                    val oppMatcher = REGEX_OPP_TABLE.matcher(trimmed)
                    if (oppMatcher.find()) {
                        currentOppTableName = oppMatcher.group(1) ?: ""
                        // Filter out common non-GPU tables if possible, but keep generic for now
                        if (currentOppTableName.contains("gpu") || currentOppTableName.contains("cluster")) {
                            insideOppTable = true
                            currentOppCount = 0
                        }
                    }

                    if (insideOppTable) {
                        if (trimmed.contains("opp-hz") || trimmed.startsWith("opp-")) {
                            currentOppCount++
                        }
                        if (trimmed == "};") {
                            insideOppTable = false
                            if (currentOppCount > 0) {
                                voltagePatterns.add(currentOppTableName)
                            }
                        }
                    }
                    
                    // 4. Model Detection hint
                    if (trimmed.startsWith("model =")) {
                        detectedModel = trimmed.substringAfter("=").replace(";", "").replace("\"", "").trim()
                    }

                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return DtsScanResult(false, index, "UNKNOWN", null, 0)
        }

        // Determine Strategy
        // Priority: Multi Bin > Single Bin > Unknown
        val strategy = when {
            hasMultiBin -> "MULTI_BIN"
            hasSingleBin -> "SINGLE_BIN"
            else -> "UNKNOWN"
        }

        // Pick the most likely voltage pattern (heuristic: usually contains "gpu" or is longest)
        val bestVoltPattern = voltagePatterns
            .filter { it.contains("gpu") }
            .maxByOrNull { it.length } 
            ?: voltagePatterns.firstOrNull()

        // If we found power levels, detect count (0-based index means count is max + 1)
        val levelCount = if (maxObservedLevel > 0) maxObservedLevel + 1 else 0
        
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

        // If we found nothing relevant, isValid is false
        val isValid = confidenceScore > 0

        return DtsScanResult(
            isValid = isValid,
            dtbIndex = index,
            recommendedStrategy = if (strategy == "UNKNOWN") "MULTI_BIN" else strategy,
            voltageTablePattern = bestVoltPattern,
            maxLevels = if (levelCount > 0) levelCount else 15, // Default recommendation
            levelCount = levelCount,
            confidence = confidence,
            detectedModel = detectedModel
        )
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
            levelCount = 480, // Safe default for modern chips (Snapdragon 8 Gen 1+)
            levels = mapOf(), 
            binDescriptions = if (result.recommendedStrategy == "MULTI_BIN") mapOf(0 to "Default", 1 to "High Perf") else null,
            models = listOf(result.detectedModel ?: "Custom")
        )
    }
}