package com.ireddragonicy.konabessnext.model.dts

data class DeepMatch(
    val node: DtsNode,
    val property: DtsProperty?,
    val propertyIndex: Int,
    val flatId: String
)
