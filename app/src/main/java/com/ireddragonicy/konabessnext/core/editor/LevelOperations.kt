package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsHelper
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper

/**
 * Handles level and bin manipulation operations.
 * Refactored to use AST parsing for robust property modification.
 */
object LevelOperations {

    // ===== AST Helper =====

    private const val WRAPPER_NODE_NAME = "temp_wrapper"

    /**
     * Wraps raw property lines in a temporary dummy node so the DTS parser can parse them.
     *
     * `Bin.header` contains only the body *inside* a node (properties, no outer node wrapper).
     */
    private fun parsePropertiesToNode(lines: List<String>): DtsNode {
        val rawContent = lines.joinToString("\n")
        val wrappedContent = "$WRAPPER_NODE_NAME {\n$rawContent\n};"

        val root = DtsTreeHelper.parse(wrappedContent)
        return root.getChild(WRAPPER_NODE_NAME)
            ?: throw IllegalStateException("Failed to find wrapper node '$WRAPPER_NODE_NAME' after parse")
    }

    /**
     * Extract the property lines *inside* [WRAPPER_NODE_NAME] from a generated DTS string.
     *
     * DtsTreeHelper.generate always prepends `/dts-v1/;`, so we locate the wrapper node region
     * and return only its inner lines.
     */
    private fun unwrapGeneratedNodeToPropertyLines(generated: String): List<String> {
        val lines = generated.lines()

        val openIdx = lines.indexOfFirst { it.trim() == "$WRAPPER_NODE_NAME {" }
        if (openIdx == -1) return emptyList()

        val closeRel = lines.subList(openIdx + 1, lines.size).indexOfFirst { it.trim() == "};" }
        val closeIdx = if (closeRel == -1) -1 else (openIdx + 1 + closeRel)
        if (closeIdx == -1 || closeIdx <= openIdx) return emptyList()

        return lines
            .subList(openIdx + 1, closeIdx)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Round-trips [lines] through the AST, applies [modifier], regenerates, then unwraps back
     * into the `Bin.header` format.
     *
     * Safety: Any parse/generate failure results in a no-op to preserve data integrity.
     */
    private fun modifyHeaderProperties(lines: ArrayList<String>, modifier: (DtsNode) -> Boolean) {
        if (lines.isEmpty()) return

        val originalSnapshot = ArrayList(lines)

        try {
            val wrapperNode = parsePropertiesToNode(lines)
            val changed = modifier(wrapperNode)
            if (!changed) return

            val generated = DtsTreeHelper.generate(wrapperNode)
            val newLines = unwrapGeneratedNodeToPropertyLines(generated)

            // If we couldn't unwrap reliably, keep the original header as-is.
            if (newLines.isEmpty()) return

            lines.clear()
            lines.addAll(newLines)
        } catch (e: Exception) {
            e.printStackTrace()
            lines.clear()
            lines.addAll(originalSnapshot)
        }
    }

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
        modifyHeaderProperties(headerLines) { node ->
            val prop = node.getProperty(key) ?: return@modifyHeaderProperties false

            // Construct a valid statement for DtsHelper to parse.
            val currentVal = DtsHelper.extractLongValue("${prop.name} = ${prop.originalValue};")
            if (currentVal == -1L) return@modifyHeaderProperties false

            val newVal = (currentVal + offset).coerceAtLeast(0)

            // Preserve original formatting choice: hex if value started as <0x...>
            val isHex = prop.originalValue.trim().startsWith("<0x", ignoreCase = true)
            val newValueStr = if (isHex) {
                "<0x${java.lang.Long.toHexString(newVal)}>"
            } else {
                "<$newVal>"
            }

            if (prop.originalValue != newValueStr) {
                prop.originalValue = newValueStr
                true
            } else {
                false
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
            modifyHeaderProperties(bin.header) { node ->
                val prop = node.getProperty("qcom,throttle-pwrlevel") ?: return@modifyHeaderProperties false
                if (prop.originalValue != "<0>") {
                    prop.originalValue = "<0>"
                    true
                } else {
                    false
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