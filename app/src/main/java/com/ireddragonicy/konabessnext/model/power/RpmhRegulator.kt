package com.ireddragonicy.konabessnext.model.power

import androidx.compose.runtime.Stable

@Stable
data class RpmhRegulator(
    val parentNodeName: String,
    val subNodeName: String,
    val regulatorName: String,
    val minMicrovolt: Long,
    val maxMicrovolt: Long
)
