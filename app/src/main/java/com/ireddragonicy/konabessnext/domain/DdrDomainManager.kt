package com.ireddragonicy.konabessnext.domain

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.memory.MemoryFreqTable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain manager for DDR, LLCC, and DDR-QoS memory frequency tables.
 *
 * These tables are standalone nodes in Qualcomm DTS files with a single
 * `qcom,freq-tbl` property containing a hex array of frequencies in kHz.
 */
@Singleton
class DdrDomainManager @Inject constructor() {

    companion object {
        /** Node names that contain memory/cache frequency tables. */
        private val MEMORY_TABLE_NODE_NAMES = setOf(
            "ddr-freq-table",
            "llcc-freq-table",
            "ddrqos-freq-table"
        )

        private const val FREQ_TABLE_PROPERTY = "qcom,freq-tbl"
    }

    /**
     * Searches the AST for DDR/LLCC/DDR-QoS frequency table nodes
     * and returns parsed [MemoryFreqTable] entries.
     */
    fun findMemoryTables(root: DtsNode): List<MemoryFreqTable> {
        val tables = ArrayList<MemoryFreqTable>()
        recurseSearch(root, tables)
        return tables.distinctBy { it.nodeName }
    }

    private fun recurseSearch(node: DtsNode, results: MutableList<MemoryFreqTable>) {
        if (node.name in MEMORY_TABLE_NODE_NAMES) {
            val freqProp = node.getProperty(FREQ_TABLE_PROPERTY)
            if (freqProp != null) {
                val frequencies = parseHexArrayToLongs(freqProp.originalValue)
                if (frequencies.isNotEmpty()) {
                    results.add(MemoryFreqTable(node.name, frequencies))
                }
            }
        }
        for (child in node.children) {
            recurseSearch(child, results)
        }
    }

    /**
     * Finds the AST node for a specific memory table by name.
     * Used for mutation operations.
     */
    fun findMemoryTableNode(root: DtsNode, nodeName: String): DtsNode? {
        if (root.name == nodeName && root.getProperty(FREQ_TABLE_PROPERTY) != null) {
            return root
        }
        for (child in root.children) {
            findMemoryTableNode(child, nodeName)?.let { return it }
        }
        return null
    }

    /**
     * Updates the `qcom,freq-tbl` property of a memory table node
     * with the given list of frequencies (in kHz).
     *
     * Formats the values as a DTS hex array: `<0x858b8 0x14a780 ...>`
     *
     * @return true if update succeeded, false if the property was not found.
     */
    fun updateMemoryTable(node: DtsNode, newFrequencies: List<Long>): Boolean {
        val freqProp = node.getProperty(FREQ_TABLE_PROPERTY) ?: return false
        val hexString = buildHexArrayString(newFrequencies)
        freqProp.originalValue = hexString
        // Re-detect hex array status since we replaced the value
        freqProp.isHexArray = true
        return true
    }

    /**
     * Parses a DTS hex array string like `<0x858b8 0x14a780 ...>` into a list of Longs.
     */
    private fun parseHexArrayToLongs(rawValue: String): List<Long> {
        val trimmed = rawValue.trim()
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) return emptyList()

        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        val tokens = inner.split(Regex("\\s+"))
        return tokens.mapNotNull { token ->
            try {
                if (token.startsWith("0x", ignoreCase = true)) {
                    java.lang.Long.parseUnsignedLong(token.substring(2), 16)
                } else {
                    token.toLongOrNull()
                }
            } catch (_: NumberFormatException) {
                null
            }
        }
    }

    /**
     * Builds a DTS hex array string from a list of Longs.
     * Example output: `<0x858b8 0x14a780>`
     */
    private fun buildHexArrayString(frequencies: List<Long>): String {
        val sb = StringBuilder("<")
        frequencies.forEachIndexed { index, freq ->
            if (index > 0) sb.append(' ')
            sb.append("0x").append(freq.toString(16))
        }
        sb.append('>')
        return sb.toString()
    }
}
