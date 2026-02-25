package com.ireddragonicy.konabessnext.model.dts

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ItemType { NODE, PROPERTY }

@Stable
data class TreeItem(
    val id: String,
    val display: String,
    val depth: Int,
    val type: ItemType,
    val node: DtsNode? = null,
    val property: DtsProperty? = null,
    val isExpanded: Boolean = false,
    val childCount: Int = 0,          // Pre-computed count
    val indent: Dp = 16.dp           // Pre-computed indentation
)
