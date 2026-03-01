package com.ireddragonicy.konabessnext.domain

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.isp.CamIspFreqTable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CamIspDomainManager @Inject constructor() {

    companion object {
        private val ISP_NODE_PREFIXES = listOf(
            "qcom,ife",
            "qcom,ipe",
            "qcom,cam-cpas",
            "qcom,csid",
            "qcom,jpeg"
        )
        private const val CLOCK_CNTL_LEVEL_PROP = "clock-cntl-level"
        private const val CLOCK_RATES_PROP = "clock-rates"
    }

    fun findIspTables(root: DtsNode): List<CamIspFreqTable> {
        val tables = ArrayList<CamIspFreqTable>()
        recurseSearch(root, tables)
        return tables.distinctBy { it.nodeName }
    }

    private fun recurseSearch(node: DtsNode, results: MutableList<CamIspFreqTable>) {
        if (ISP_NODE_PREFIXES.any { node.name.startsWith(it) }) {
            val levelsProp = node.getProperty(CLOCK_CNTL_LEVEL_PROP)
            val ratesProp = node.getProperty(CLOCK_RATES_PROP)

            if (levelsProp != null && ratesProp != null) {
                val levels = parseStringArray(levelsProp.originalValue)
                val rates = parseHexArrayToLongs(ratesProp.originalValue)
                
                if (levels.isNotEmpty() && rates.isNotEmpty()) {
                    // Try to match up levels to rates by focusing on the maximums or specific offsets per block if matrix.
                    // To keep it safe and user-editable, if they don't exactly match in size, we'll extract all non-zero 
                    // frequencies and map them linearly as best as possible, or keep all frequencies if it's 1-to-1.
                    val freqList = if (rates.size > levels.size) {
                        // Extract highest frequency per "block" if it's a matrix, or just grab the non-zero values
                        // Many times ISP tables have columns for different clocks. Let's just grab the max of each block
                        // by assuming rates are grouped evenly based on the number of levels.
                        val itemsPerLevel = rates.size / levels.size
                        val maxRates = mutableListOf<Long>()
                        for (i in levels.indices) {
                            val startIdx = i * itemsPerLevel
                            val endIdx = minOf(startIdx + itemsPerLevel, rates.size)
                            val blockRates = rates.subList(startIdx, endIdx)
                            maxRates.add(blockRates.maxOrNull() ?: 0L)
                        }
                        if (maxRates.size == levels.size) maxRates else rates
                    } else {
                        rates
                    }

                    results.add(CamIspFreqTable(node.name, levels, freqList))
                }
            }
        }
        for (child in node.children) {
            recurseSearch(child, results)
        }
    }

    fun findIspTableNode(root: DtsNode, nodeName: String): DtsNode? {
        if (root.name == nodeName && root.getProperty(CLOCK_RATES_PROP) != null) {
            return root
        }
        for (child in root.children) {
            findIspTableNode(child, nodeName)?.let { return it }
        }
        return null
    }

    fun updateIspClockRates(node: DtsNode, newFrequencies: List<Long>): Boolean {
        val ratesProp = node.getProperty(CLOCK_RATES_PROP) ?: return false
        val levelsProp = node.getProperty(CLOCK_CNTL_LEVEL_PROP) ?: return false
        
        val originalRates = parseHexArrayToLongs(ratesProp.originalValue)
        val levels = parseStringArray(levelsProp.originalValue)
        
        // If it was a matrix (sizes didn't match), we only tracked the max frequencies. 
        // We must reconstruct the matrix by replacing the max values with the new frequencies 
        // and scaling or keeping the other values (or simply zeroing them out if that is standard, but keeping is safer)
        val newRatesReconstructed = if (originalRates.size > levels.size && newFrequencies.size == levels.size) {
            val itemsPerLevel = originalRates.size / levels.size
            val reconstructed = originalRates.toMutableList()
            for (i in levels.indices) {
                val startIdx = i * itemsPerLevel
                val endIdx = minOf(startIdx + itemsPerLevel, originalRates.size)
                
                // Find the index of the max value in this block and replace it
                var maxVal = -1L
                var maxIdx = -1
                for (j in startIdx until endIdx) {
                    if (reconstructed[j] > maxVal) {
                        maxVal = reconstructed[j]
                        maxIdx = j
                    }
                }
                
                if (maxIdx != -1) {
                    reconstructed[maxIdx] = newFrequencies[i]
                }
            }
            reconstructed
        } else {
            newFrequencies
        }

        val hexString = buildHexArrayString(newRatesReconstructed)
        ratesProp.originalValue = hexString
        ratesProp.isHexArray = true
        return true
    }

    private fun parseStringArray(rawValue: String): List<String> {
        return rawValue.split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }

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

    private fun buildHexArrayString(rates: List<Long>): String {
        val sb = StringBuilder("<")
        rates.forEachIndexed { index, rate ->
            if (index > 0) sb.append(' ')
            sb.append("0x").append(rate.toString(16))
        }
        sb.append('>')
        return sb.toString()
    }
}
