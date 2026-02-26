package com.ireddragonicy.konabessnext.model.display

data class TouchPanel(
    val fragmentIndex: Int,
    val nodeName: String,
    val compatible: String,
    val spiMaxFrequency: Long
)
