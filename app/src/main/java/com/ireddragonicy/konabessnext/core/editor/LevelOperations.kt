package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level


import com.ireddragonicy.konabessnext.utils.DtsHelper
import java.util.regex.Pattern

/**
 * Handles level and bin manipulation operations.
 * Optimized for performance using direct string parsing where possible.
 */
object LevelOperations {

    // Pre-compiled regex for complex replacements only
    private val PWR_LEVEL_REGEX = Pattern.compile("(qcom,(?:initial|ca-target)-pwrlevel\\s*=\\s*<)(0x[0-9a-fA-F]+|\\d+)(>;)")

    // ===== Cloning Operations =====

    @JvmStatic
    fun cloneBinsList(source: List<Bin>?): ArrayList<Bin> {
        if (source == null) return ArrayList()
        return source.mapTo(ArrayList()) { cloneBin(it) }
    }

    @JvmStatic
    fun cloneBin(original: Bin?): Bin {
        if (original == null) return Bin(0)
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

    // ===== Level Offset Operations =====

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

                        val currentValue = if (valueStr?.startsWith("0x") == true) {
                            valueStr.substring(2).toLong(16)
                        } else {
                            valueStr?.toLong() ?: 0L
                        }

                        val newValue = (currentValue + offset).coerceAtLeast(0)
                        headerLines[i] = "$prefix$newValue$suffix"
                        return
                    } catch (e: Exception) {
                        e.printStackTrace()
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
        for (bin in bins) {
            for (i in bin.header.indices) {
                if (bin.header[i].contains("qcom,throttle-pwrlevel")) {
                    val parts = bin.header[i].split("=")
                    if (parts.isNotEmpty()) {
                        bin.header[i] = "${parts[0].trim()} = <0>;"
                    }
                    break
                }
            }
        }
    }

    // ===== Frequency Operations (Optimized) =====

    @JvmStatic
    fun getFrequencyFromLevel(lvl: Level): Long {
        // Delegate to the model which delegates to the helper
        return lvl.frequency
    }

    @JvmStatic
    fun canAddNewLevel(bins: ArrayList<Bin>?, binID: Int): Boolean = true


    // ===== CRUD Operations =====

    @JvmStatic
    fun addLevelAtTop(bins: ArrayList<Bin>?, binId: Int, chipDef: com.ireddragonicy.konabessnext.model.ChipDefinition?) {
        if (bins == null || binId !in bins.indices) return
        val bin = bins[binId]
        if (bin.levels.isEmpty()) return

        val template = bin.levels[0]
        bin.levels.add(0, cloneLevel(template))

        offsetInitialLevel(bins, binId, 1)
        if (chipDef?.needsCaTargetOffset == true) offsetCaTargetLevel(bins, binId, 1)
    }

    @JvmStatic
    fun addLevelAtBottom(bins: ArrayList<Bin>?, binId: Int, chipDef: com.ireddragonicy.konabessnext.model.ChipDefinition?) {
        if (bins == null || binId !in bins.indices) return
        val bin = bins[binId]
        if (bin.levels.isEmpty()) return

        val insertIndex = bin.levels.size
        val templateIndex = if (insertIndex < bin.levels.size) insertIndex else bin.levels.lastIndex
        val template = bin.levels[templateIndex.coerceAtLeast(0)]

        bin.levels.add(insertIndex, cloneLevel(template))

        if (insertIndex == 0) {
            offsetInitialLevel(bins, binId, 1)
            if (chipDef?.needsCaTargetOffset == true) offsetCaTargetLevel(bins, binId, 1)
        }
    }

    @JvmStatic
    fun duplicateLevel(bins: ArrayList<Bin>?, binId: Int, levelIndex: Int, chipDef: com.ireddragonicy.konabessnext.model.ChipDefinition?) {
        if (bins == null || binId !in bins.indices) return
        val bin = bins[binId]
        if (levelIndex !in bin.levels.indices) return

        val template = bin.levels[levelIndex]
        bin.levels.add(levelIndex + 1, cloneLevel(template))

        offsetInitialLevel(bins, binId, 1)
        if (chipDef?.needsCaTargetOffset == true) offsetCaTargetLevel(bins, binId, 1)
    }

    @JvmStatic
    fun removeLevel(bins: ArrayList<Bin>?, binId: Int, levelIndex: Int, chipDef: com.ireddragonicy.konabessnext.model.ChipDefinition?) {
        if (bins == null || binId !in bins.indices) return
        val bin = bins[binId]
        if (bin.levels.size <= 1 || levelIndex !in bin.levels.indices) return

        bin.levels.removeAt(levelIndex)

        offsetInitialLevel(bins, binId, -1)
        if (chipDef?.needsCaTargetOffset == true) offsetCaTargetLevel(bins, binId, -1)
    }

    @JvmStatic
    fun getFrequencyLabel(level: Level, activity: Activity): String {
        return try {
            val freq = getFrequencyFromLevel(level)
            com.ireddragonicy.konabessnext.utils.FrequencyFormatter.format(activity, freq)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    @JvmStatic
    fun levelint2int(value: Long, levels: IntArray): Int {
        for (i in levels.indices) {
            if (levels[i].toLong() == value) return i
        }
        return 0
    }

    @JvmStatic
    fun levelint2str(value: Long, levels: IntArray, strings: Array<String>): String {
        for (i in levels.indices) {
            if (levels[i].toLong() == value) {
                return if (i < strings.size) strings[i] else value.toString()
            }
        }
        return value.toString()
    }
}