package com.ireddragonicy.konabessnext.domain

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.ufs.UfsFreqTable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain manager for UFS (Storage) & Bus Overclocking.
 *
 * It parses and modifies the `freq-table-hz` property inside UFS nodes.
 */
@Singleton
class UfsDomainManager @Inject constructor() {

    companion object {
        private const val FREQ_TABLE_PROPERTY = "freq-table-hz"
        private const val UFS_PREFIX = "ufshc"
        private const val COMPATIBLE_UFS = "qcom,ufshc"
    }

    /**
     * Searches the AST for UFS frequency table nodes
     * and returns parsed [UfsFreqTable] entries.
     */
    fun findUfsTables(root: DtsNode): List<UfsFreqTable> {
        val tables = ArrayList<UfsFreqTable>()
        recurseSearch(root, tables)
        return tables.distinctBy { it.nodeName }
    }

    private fun isUfsNode(node: DtsNode): Boolean {
        if (node.name.startsWith(UFS_PREFIX)) return true
        val compatibleProp = node.getProperty("compatible")
        if (compatibleProp != null && compatibleProp.originalValue.contains(COMPATIBLE_UFS)) {
            return true
        }
        return false
    }

    private fun recurseSearch(node: DtsNode, results: MutableList<UfsFreqTable>) {
        if (isUfsNode(node)) {
            val freqProp = node.getProperty(FREQ_TABLE_PROPERTY)
            if (freqProp != null) {
                val frequencies = parseHexArrayToLongs(freqProp.originalValue)
                val clockNamesProp = node.getProperty("clock-names")
                val clockNames = clockNamesProp?.originalValue?.split(",")?.map { it.trim().removeSurrounding("\"") } ?: emptyList()
                if (frequencies.isNotEmpty()) {
                    results.add(UfsFreqTable(node.name, clockNames, frequencies))
                }
            }
        }
        for (child in node.children) {
            recurseSearch(child, results)
        }
    }

    /**
     * Finds the AST node for a specific UFS table by name.
     * Used for mutation operations.
     */
    fun findUfsTableNode(root: DtsNode, nodeName: String): DtsNode? {
        if (root.name == nodeName && root.getProperty(FREQ_TABLE_PROPERTY) != null) {
            return root
        }
        for (child in root.children) {
            findUfsTableNode(child, nodeName)?.let { return it }
        }
        return null
    }

    /**
     * Updates the `freq-table-hz` property of a UFS node
     * with the given list of frequencies (in Hz).
     *
     * Formats the values as a DTS hex array: `<0x5f5e100 0x18054ac0 0x0 ...>`
     *
     * @return true if update succeeded, false if the property was not found.
     */
    fun updateUfsTable(node: DtsNode, newFrequencies: List<Long>): Boolean {
        val freqProp = node.getProperty(FREQ_TABLE_PROPERTY) ?: return false
        val hexString = buildHexArrayString(newFrequencies)
        freqProp.originalValue = hexString
        // Re-detect hex array status since we replaced the value
        freqProp.isHexArray = true
        return true
    }

    /**
     * Parses a DTS hex array string like `<0x5f5e100 0x18054ac0 0x0 ...>` into a list of Longs.
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
     * Example output: `<0x5f5e100 0x18054ac0>`
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
