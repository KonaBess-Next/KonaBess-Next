package com.ireddragonicy.konabessnext.model.memory

/**
 * Represents a DDR, LLCC, or DDR-QoS frequency table found in the DTS.
 * The [frequenciesKHz] list is parsed from the `qcom,freq-tbl` hex array property.
 *
 * Example DTS node:
 * ```
 * ddr-freq-table {
 *     qcom,freq-tbl = <0x858b8 0x14a780 ...>;
 * };
 * ```
 */
data class MemoryFreqTable(
    val nodeName: String,
    val frequenciesKHz: List<Long>
)
