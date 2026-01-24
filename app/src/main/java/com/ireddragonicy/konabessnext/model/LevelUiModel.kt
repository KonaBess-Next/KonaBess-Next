package com.ireddragonicy.konabessnext.model

import androidx.compose.runtime.Immutable

/**
 * Lightweight UI model for GPU Levels.
 * Marked @Immutable to allow Jetpack Compose to skip unnecessary recompositions.
 * All data formatting is pre-calculated in the ViewModel background scope.
 */
@Immutable
data class LevelUiModel(
    val originalIndex: Int,
    val frequencyLabel: String,
    val busMin: String,
    val busMax: String,
    val busFreq: String,
    val voltageLabel: String,
    val isVisible: Boolean // Optimized filtering flag
)