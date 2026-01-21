/* --- src/main/java/com/ireddragonicy/konabessnext/core/editor/LevelOperations.kt --- */
package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import com.ireddragonicy.konabessnext.ui.adapters.GpuFreqAdapter
import com.ireddragonicy.konabessnext.utils.DtsHelper
import java.util.regex.Pattern

/**
 * Handles level and bin manipulation operations.
 * Optimized with Regex for robust DTS parsing.
 */
object LevelOperations {

    // Pre-compiled regex for performance
    private val PWR_LEVEL_REGEX = Pattern.compile("(qcom,(?:initial|ca-target)-pwrlevel\\s*=\\s*<)(0x[0-9a-fA-F]+|\\d+)(>;)")
    private val GPU_FREQ_REGEX = Pattern.compile("qcom,gpu-freq\\s*=\\s*<(0x[0-9a-fA-F]+|\\d+)>;")

    // ===== Cloning Operations =====

    @JvmStatic
    fun cloneBinsList(source: List<Bin>?): ArrayList<Bin> {
        if (source == null) return ArrayList()
        // Improve cloning using map-to-ArrayList for conciseness
        return source.mapTo(ArrayList()) { cloneBin(it) }
    }

    @JvmStatic
    fun cloneBin(original: Bin?): Bin {
        if (original == null) return Bin(0) // Safe fallback
        val copy = Bin(original.id)
        copy.header = ArrayList(original.header)
        copy.levels = ArrayList(original.levels.map { cloneLevel(it) })
        return copy
    }

    @JvmStatic
    fun cloneLevel(original: Level?): Level {
        if (original == null) return Level()
        return Level(ArrayList(original.lines))
    }

    // ===== Level Offset Operations (Fixed & Robust) =====

    /**
     * Safely offsets a power level property in the bin header using Regex.
     * Prevents crashes on malformed lines.
     */
    private fun safeOffsetHeaderValue(headerLines: ArrayList<String>, key: String, offset: Int) {
        for (i in headerLines.indices) {
            val line = headerLines[i]
            if (line.contains(key)) {
                val matcher = PWR_LEVEL_REGEX.matcher(line)
                if (matcher.find()) {
                    try {
                        val prefix = matcher.group(1)
                        val valueStr = matcher.group(2)
                        val suffix = matcher.group(3)

                        // Parse hex or decimal
                        val currentValue = if (valueStr?.startsWith("0x") == true) {
                            valueStr?.substring(2)?.toLong(16) ?: 0L
                        } else {
                            valueStr?.toLong() ?: 0L
                        }

                        // Apply offset but keep it non-negative
                        val newValue = (currentValue + offset).coerceAtLeast(0)
                        
                        // Reconstruct string
                        headerLines[i] = "$prefix$newValue$suffix"
                        return // Done
                    } catch (e: Exception) {
                        e.printStackTrace() // Log but don't crash
                    }
                }
            }
        }
    }

    @JvmStatic
    fun offsetInitialLevel(bins: ArrayList<Bin>?, binId: Int, offset: Int) {
        if (bins.isNullOrEmpty() || binId !in bins.indices) return
        safeOffsetHeaderValue(bins[binId].header, "qcom,initial-pwrlevel", offset)
    }

    @JvmStatic
    fun offsetCaTargetLevel(bins: ArrayList<Bin>?, binId: Int, offset: Int) {
        if (bins.isNullOrEmpty() || binId !in bins.indices) return
        safeOffsetHeaderValue(bins[binId].header, "qcom,ca-target-pwrlevel", offset)
    }

    @JvmStatic
    fun patchThrottleLevel(bins: ArrayList<Bin>?) {
        if (bins == null) return
        // Simple replacement is safer and faster than parsing
        for (bin in bins) {
            for (i in bin.header.indices) {
                if (bin.header[i].contains("qcom,throttle-pwrlevel")) {
                    // Normalize to standard format 0
                    val parts = bin.header[i].split("=")
                    if (parts.isNotEmpty()) {
                        bin.header[i] = "${parts[0].trim()} = <0>;"
                    }
                    break
                }
            }
        }
    }

    // ===== Frequency Operations =====

    @JvmStatic
    fun getFrequencyFromLevel(lvl: Level): Long {
        for (line in lvl.lines) {
            if (line.contains("qcom,gpu-freq")) {
                val matcher = GPU_FREQ_REGEX.matcher(line)
                if (matcher.find()) {
                    val valStr = matcher.group(1)
                    return runCatching {
                        if (valStr?.startsWith("0x") == true) valStr?.substring(2)?.toLong(16) ?: 0L
                        else valStr?.toLong() ?: 0L
                    }.getOrDefault(0L)
                }
            }
        }
        return 0L
    }

    @JvmStatic
    fun canAddNewLevel(bins: ArrayList<Bin>?, binID: Int): Boolean = true

    // ===== Adapter Synchronization =====

    @JvmStatic
    fun updateBinsFromAdapter(
        bins: ArrayList<Bin>?, binId: Int,
        items: List<GpuFreqAdapter.FreqItem>
    ): Boolean {
        if (bins == null || binId !in bins.indices) return false

        val currentLevels = bins[binId].levels
        val newLevels = ArrayList<Level>()
        
        // Map frequency to available levels (Snapshot to handle duplicates correctly)
        val availableLevels = ArrayList(currentLevels)
        
        for (item in items) {
            if (item.hasFrequencyValue()) {
                // Find matching level in available pool
                val iterator = availableLevels.iterator()
                while (iterator.hasNext()) {
                    val lvl = iterator.next()
                    if (getFrequencyFromLevel(lvl) == item.frequencyHz) {
                        newLevels.add(lvl)
                        iterator.remove() // Consume it so duplicates work
                        break
                    }
                }
            }
        }

        if (newLevels.size == currentLevels.size && newLevels != currentLevels) {
            bins[binId].levels = newLevels
            return true
        }
        return false
    }

    // ===== CRUD Operations =====

    @JvmStatic
    fun addLevelAtTop(bins: ArrayList<Bin>?, binId: Int) {
        if (bins == null || binId !in bins.indices) return
        val bin = bins[binId]
        if (bin.levels.isEmpty()) return

        val template = bin.levels[0]
        bin.levels.add(0, cloneLevel(template))
        
        offsetInitialLevel(bins, binId, 1)
        if (isLitoOrLagoon()) offsetCaTargetLevel(bins, binId, 1)
    }

    @JvmStatic
    fun addLevelAtBottom(bins: ArrayList<Bin>?, binId: Int) {
        if (bins == null || binId !in bins.indices) return
        val bin = bins[binId]
        if (bin.levels.isEmpty()) return

        // Always append to the end as requested by user
        val insertIndex = bin.levels.size
        
        // Use the level AT insertIndex as template if available (copying "down" logic), 
        // OR the last available level if we are at the end.
        // This ensures if we insert before retention levels (offset > 0), we copy the retention level (which the user sees as bottom).
        val templateIndex = if (insertIndex < bin.levels.size) insertIndex else bin.levels.lastIndex
        val template = bin.levels[templateIndex.coerceAtLeast(0)]
        
        bin.levels.add(insertIndex, cloneLevel(template))
        
        // Only offset if we inserted at 0 (effectively addAtTop behavior)
        if (insertIndex == 0) {
            offsetInitialLevel(bins, binId, 1)
            if (isLitoOrLagoon()) offsetCaTargetLevel(bins, binId, 1)
        }
    }

    @JvmStatic
    fun duplicateLevel(bins: ArrayList<Bin>?, binId: Int, levelIndex: Int) {
        if (bins == null || binId !in bins.indices) return
        val bin = bins[binId]
        if (levelIndex !in bin.levels.indices) return

        val template = bin.levels[levelIndex]
        bin.levels.add(levelIndex + 1, cloneLevel(template))

        offsetInitialLevel(bins, binId, 1)
        if (isLitoOrLagoon()) offsetCaTargetLevel(bins, binId, 1)
    }

    @JvmStatic
    fun removeLevel(bins: ArrayList<Bin>?, binId: Int, levelIndex: Int) {
        if (bins == null || binId !in bins.indices) return
        val bin = bins[binId]
        if (bin.levels.size <= 1 || levelIndex !in bin.levels.indices) return

        bin.levels.removeAt(levelIndex)
        
        offsetInitialLevel(bins, binId, -1)
        if (isLitoOrLagoon()) offsetCaTargetLevel(bins, binId, -1)
    }

    private fun isLitoOrLagoon(): Boolean {
        val type = ChipInfo.which ?: return false
        return type == ChipInfo.Type.lito_v1 || type == ChipInfo.Type.lito_v2 || type == ChipInfo.Type.lagoon
    }

    @JvmStatic
    fun getFrequencyLabel(level: Level, activity: Activity): String {
        return try {
            val freq = getFrequencyFromLevel(level)
            SettingsActivity.formatFrequency(freq, activity)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    @JvmStatic
    fun levelint2int(value: Long): Int {
        val levels = com.ireddragonicy.konabessnext.core.ChipInfo.rpmh_levels.levels()
        for (i in levels.indices) {
            if (levels[i].toLong() == value) return i
        }
        return 0
    }

    @JvmStatic
    fun levelint2str(value: Long): String {
        val levels = com.ireddragonicy.konabessnext.core.ChipInfo.rpmh_levels.levels()
        val strings = com.ireddragonicy.konabessnext.core.ChipInfo.rpmh_levels.level_str()
        for (i in levels.indices) {
            if (levels[i].toLong() == value) {
                return if (i < strings.size) strings[i] else value.toString()
            }
        }
        return value.toString()
    }
}
