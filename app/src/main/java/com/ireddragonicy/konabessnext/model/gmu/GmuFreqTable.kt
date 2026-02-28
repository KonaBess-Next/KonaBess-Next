package com.ireddragonicy.konabessnext.model.gmu

data class GmuFreqPair(
    val freqHz: Long,
    val vote: Long
)

data class GmuFreqTable(
    val nodeName: String,
    val pairs: List<GmuFreqPair>
)
