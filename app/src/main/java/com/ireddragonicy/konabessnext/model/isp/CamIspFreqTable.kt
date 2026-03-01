package com.ireddragonicy.konabessnext.model.isp

data class CamIspFreqTable(
    val nodeName: String,
    val levels: List<String>,
    val freqHzList: List<Long>
)
