package com.ireddragonicy.konabessnext.model.ufs

data class UfsClockTarget(
    val name: String,
    val minFreqHz: Long,
    val maxFreqHz: Long,
    val minIndex: Int,
    val maxIndex: Int
)

/**
 * Represents a UFS frequency table extracted from a DTS file.
 *
 * @param nodeName The name of the UFS node (e.g., `ufshc@1d84000`)
 * @param clockNames A list of clock names ordered according to their min/max values.
 * @param frequenciesHz A list of frequencies in Hertz extracted from the `freq-table-hz` property.
 */
data class UfsFreqTable(
    val nodeName: String,
    val clockNames: List<String>,
    val frequenciesHz: List<Long>
) {
    val clockTargets: List<UfsClockTarget>
        get() {
            val targets = ArrayList<UfsClockTarget>()
            val pairsCount = frequenciesHz.size / 2
            
            for (i in 0 until pairsCount) {
                val minIndex = i * 2
                val maxIndex = i * 2 + 1
                val name = clockNames.getOrElse(i) { "Clock $i" }
                targets.add(
                    UfsClockTarget(
                        name = name,
                        minFreqHz = frequenciesHz.getOrElse(minIndex) { 0L },
                        maxFreqHz = frequenciesHz.getOrElse(maxIndex) { 0L },
                        minIndex = minIndex,
                        maxIndex = maxIndex
                    )
                )
            }
            return targets
        }
}
