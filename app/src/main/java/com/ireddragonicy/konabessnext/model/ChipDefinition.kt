package com.ireddragonicy.konabessnext.model

import kotlinx.serialization.Serializable

@Serializable
data class ChipDefinition(
    val id: String,
    val name: String,
    val maxTableLevels: Int,
    val ignoreVoltTable: Boolean,
    val minLevelOffset: Int,
    val voltTablePattern: String? = null,
    val strategyType: String, // "SINGLE_BIN" or "MULTI_BIN"
    val levelCount: Int, // e.g. 416, 464, 480
    val levels: Map<Int, String>, // Index to Label mapping
    val binDescriptions: Map<Int, String>? = null, // Bin index to Resource String mapping
    val needsCaTargetOffset: Boolean = false, // Whether to offset qcom,ca-target-pwrlevel
    val models: List<String> = emptyList() // Auto-generated mapping identifiers
) {
    init {
        require(id.isNotBlank()) { "Chip ID cannot be blank" }
        require(name.isNotBlank()) { "Chip Name cannot be blank" }
        require(maxTableLevels > 0) { "Max Table Levels must be > 0" }
        require(levelCount > 0) { "Level Count must be > 0" }
    }
}