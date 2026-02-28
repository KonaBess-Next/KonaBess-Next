package com.ireddragonicy.konabessnext.domain

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.gmu.GmuFreqPair
import com.ireddragonicy.konabessnext.model.gmu.GmuFreqTable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GmuDomainManager @Inject constructor() {

    companion object {
        private const val FREQ_TABLE_PROPERTY = "qcom,gmu-freq-table"
    }

    fun findGmuTables(root: DtsNode): List<GmuFreqTable> {
        val tables = ArrayList<GmuFreqTable>()
        recurseSearch(root, tables)
        return tables.distinctBy { it.nodeName }
    }

    private fun recurseSearch(node: DtsNode, results: MutableList<GmuFreqTable>) {
        val freqProp = node.getProperty(FREQ_TABLE_PROPERTY)
        if (freqProp != null) {
            val pairs = parseHexArrayToPairs(freqProp.originalValue)
            if (pairs.isNotEmpty()) {
                results.add(GmuFreqTable(node.name, pairs))
            }
        }
        for (child in node.children) {
            recurseSearch(child, results)
        }
    }

    fun findGmuTableNode(root: DtsNode, nodeName: String): DtsNode? {
        if (root.name == nodeName && root.getProperty(FREQ_TABLE_PROPERTY) != null) {
            return root
        }
        for (child in root.children) {
            findGmuTableNode(child, nodeName)?.let { return it }
        }
        return null
    }

    fun updateGmuTable(node: DtsNode, newPairs: List<GmuFreqPair>): Boolean {
        val freqProp = node.getProperty(FREQ_TABLE_PROPERTY) ?: return false
        val hexString = buildHexArrayString(newPairs)
        freqProp.originalValue = hexString
        freqProp.isHexArray = true
        return true
    }

    private fun parseHexArrayToPairs(rawValue: String): List<GmuFreqPair> {
        val trimmed = rawValue.trim()
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) return emptyList()

        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        val tokens = inner.split(Regex("\\s+")).mapNotNull { token ->
            try {
                if (token.startsWith("0x", ignoreCase = true)) {
                    java.lang.Long.parseUnsignedLong(token.substring(2), 16)
                } else {
                    token.toLongOrNull()
                }
            } catch (_: Exception) { null }
        }

        val pairs = mutableListOf<GmuFreqPair>()
        for (i in 0 until tokens.size - 1 step 2) {
            pairs.add(GmuFreqPair(tokens[i], tokens[i + 1]))
        }
        return pairs
    }

    private fun buildHexArrayString(pairs: List<GmuFreqPair>): String {
        val sb = StringBuilder("<")
        pairs.forEachIndexed { index, pair ->
            if (index > 0) sb.append(' ')
            sb.append("0x").append(pair.freqHz.toString(16))
            sb.append(' ')
            sb.append("0x").append(pair.vote.toString(16))
        }
        sb.append('>')
        return sb.toString()
    }
}
