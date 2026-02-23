package com.ireddragonicy.konabessnext.model.display

/**
 * Represents a speaker amplifier configuration block found in the DTBO,
 * such as the 'aw882xx_smartpa' node.
 */
data class SpeakerPanel(
    val fragmentIndex: Int,
    val nodeName: String,
    val compatible: String,
    val awReMin: Long,
    val awReMax: Long
)
