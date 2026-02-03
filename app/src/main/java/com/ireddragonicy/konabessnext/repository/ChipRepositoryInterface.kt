package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.core.strategy.ChipArchitecture
import kotlinx.coroutines.flow.StateFlow

interface ChipRepositoryInterface {
    val definitions: StateFlow<List<ChipDefinition>>
    val currentChip: StateFlow<ChipDefinition?>
    
    fun loadDefinitions()
    fun setCurrentChip(chip: ChipDefinition?)
    fun getChipById(id: String): ChipDefinition?
    fun getArchitecture(def: ChipDefinition?): ChipArchitecture
    fun getLevelsForCurrentChip(): IntArray
    fun getLevelStringsForCurrentChip(): Array<String>
}
