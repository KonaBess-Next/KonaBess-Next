package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import com.ireddragonicy.konabessnext.ui.adapters.GpuFreqAdapter
import com.ireddragonicy.konabessnext.utils.DtsHelper

/**
 * Handles level and bin manipulation operations.
 * Includes cloning, offsets, frequency extraction, and adapter synchronization.
 */
object LevelOperations {

    // ===== Cloning Operations =====

    /**
     * Deep clone a list of bins.
     */
    @JvmStatic
    fun cloneBinsList(source: List<Bin>?): ArrayList<Bin> {
        val clone = ArrayList<Bin>()
        if (source == null) return clone
        for (original in source) {
            cloneBin(original)?.let { clone.add(it) }
        }
        return clone
    }

    /**
     * Deep clone a single bin.
     */
    @JvmStatic
    fun cloneBin(original: Bin?): Bin? {
        if (original == null) return null
        val copy = Bin()
        copy.id = original.id
        copy.header = ArrayList(original.header)
        copy.levels = ArrayList()
        for (lvl in original.levels) {
            cloneLevel(lvl)?.let { copy.levels.add(it) }
        }
        return copy
    }

    /**
     * Deep clone a level.
     */
    @JvmStatic
    fun cloneLevel(original: Level?): Level? {
        if (original == null) return null
        val copy = Level()
        copy.lines = ArrayList(original.lines)
        return copy
    }

    /**
     * Shallow clone a level (shares same lines reference).
     * Used for adding new level entries.
     */
    @JvmStatic
    fun levelClone(from: Level): Level {
        val next = Level()
        next.lines = ArrayList(from.lines)
        return next
    }

    // ===== Level Offset Operations =====

    /**
     * Offset initial power level in bin header.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun offsetInitialLevel(bins: ArrayList<Bin>?, binId: Int, offset: Int) {
        if (bins.isNullOrEmpty()) return
        val targetBinId = if (binId in bins.indices) binId else 0
        val bin = bins[targetBinId]

        for (i in bin.header.indices) {
            val line = bin.header[i]
            if (line.contains("qcom,initial-pwrlevel")) {
                bin.header[i] = DtsHelper.encodeIntOrHexLine(
                    DtsHelper.decode_int_line(line).name!!,
                    (DtsHelper.decode_int_line(line).value + offset).toString()
                )
                break
            }
        }
    }

    /**
     * Offset CA target power level in bin header.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun offsetCaTargetLevel(bins: ArrayList<Bin>?, binId: Int, offset: Int) {
        if (bins == null || binId < 0 || binId >= bins.size) return

        for (i in bins[binId].header.indices) {
            val line = bins[binId].header[i]
            if (line.contains("qcom,ca-target-pwrlevel")) {
                bins[binId].header[i] = DtsHelper.encodeIntOrHexLine(
                    DtsHelper.decode_int_line(line).name!!,
                    (DtsHelper.decode_int_line(line).value + offset).toString()
                )
                break
            }
        }
    }

    /**
     * Patch throttle level to 0 for all bins.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun patchThrottleLevel(bins: ArrayList<Bin>?) {
        if (bins == null) return

        for (bin in bins) {
            for (i in bin.header.indices) {
                val line = bin.header[i]
                if (line.contains("qcom,throttle-pwrlevel")) {
                    bin.header[i] = DtsHelper.encodeIntOrHexLine(
                        DtsHelper.decode_int_line(line).name!!, "0"
                    )
                    break
                }
            }
        }
    }

    // ===== Frequency Operations =====

    /**
     * Extract GPU frequency from a level.
     * @return Frequency in Hz
     */
    @JvmStatic
    @Throws(Exception::class)
    fun getFrequencyFromLevel(lvl: Level): Long {
        for (line in lvl.lines) {
            if (line.contains("qcom,gpu-freq")) {
                return DtsHelper.decode_int_line(line).value
            }
        }
        throw Exception("No gpu-freq found in level")
    }

    /**
     * Check if adding a new level is allowed.
     */
    @JvmStatic
    fun canAddNewLevel(bins: ArrayList<Bin>?, binID: Int): Boolean {
        // Limit removed as per user request
        return true
    }

    // ===== Adapter Synchronization =====

    /**
     * Update bins order from adapter items after drag-and-drop.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun updateBinsFromAdapter(
        bins: ArrayList<Bin>?, binId: Int,
        items: List<GpuFreqAdapter.FreqItem>,
        changeCallback: EditorStateManager.EditorChange?
    ): Boolean {
        if (bins == null || binId < 0 || binId >= bins.size) return false

        val currentLevels = bins[binId].levels
        val newLevels = ArrayList<Level>()
        val used = BooleanArray(currentLevels.size)

        for (item in items) {
            if (!item.isHeader && !item.isFooter && item.hasFrequencyValue()) {
                for (index in currentLevels.indices) {
                    if (used[index]) continue
                    val levelRef = currentLevels[index]
                    val freq = getFrequencyFromLevel(levelRef)
                    if (freq == item.frequencyHz) {
                        newLevels.add(levelRef)
                        used[index] = true
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

    // ===== Level Addition =====

    /**
     * Add a new level at the top of the bin.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun addLevelAtTop(bins: ArrayList<Bin>?, binId: Int) {
        if (bins == null || binId < 0 || binId >= bins.size) return

        val template = bins[binId].levels[0]
        bins[binId].levels.add(0, levelClone(template))
        offsetInitialLevel(bins, binId, 1)

        if (ChipInfo.which == ChipInfo.Type.lito_v1
            || ChipInfo.which == ChipInfo.Type.lito_v2
            || ChipInfo.which == ChipInfo.Type.lagoon
        ) {
            offsetCaTargetLevel(bins, binId, 1)
        }
    }

    /**
     * Add a new level at the bottom of the bin (before min level offset).
     */
    @JvmStatic
    @Throws(Exception::class)
    fun addLevelAtBottom(bins: ArrayList<Bin>?, binId: Int) {
        if (bins == null || binId < 0 || binId >= bins.size) return

        val offset = ChipInfo.which!!.minLevelOffset
        val insertIndex = bins[binId].levels.size - offset
        val template = bins[binId].levels[insertIndex]
        bins[binId].levels.add(insertIndex, levelClone(template))
        offsetInitialLevel(bins, binId, 1)

        if (ChipInfo.which == ChipInfo.Type.lito_v1
            || ChipInfo.which == ChipInfo.Type.lito_v2
            || ChipInfo.which == ChipInfo.Type.lagoon
        ) {
            offsetCaTargetLevel(bins, binId, 1)
        }
    }

    /**
     * Remove a level from the bin.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun removeLevel(bins: ArrayList<Bin>?, binId: Int, levelIndex: Int) {
        if (bins == null || binId < 0 || binId >= bins.size) return
        if (bins[binId].levels.size <= 1) return

        bins[binId].levels.removeAt(levelIndex)
        offsetInitialLevel(bins, binId, -1)

        if (ChipInfo.which == ChipInfo.Type.lito_v1
            || ChipInfo.which == ChipInfo.Type.lito_v2
            || ChipInfo.which == ChipInfo.Type.lagoon
        ) {
            offsetCaTargetLevel(bins, binId, -1)
        }
    }

    // ===== Formatting Helpers =====

    /**
     * Get formatted frequency label for a level.
     */
    @JvmStatic
    fun getFrequencyLabel(level: Level, activity: Activity): String {
        return try {
            val freq = getFrequencyFromLevel(level)
            SettingsActivity.formatFrequency(freq, activity)
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
