package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.ChipDefinition
import kotlinx.coroutines.flow.StateFlow

interface ChipRepositoryInterface {
    val definitions: StateFlow<List<ChipDefinition>>
    val currentChip: StateFlow<ChipDefinition?>
    
    fun loadDefinitions()
    fun setCurrentChip(chip: ChipDefinition?)
    fun getChipById(id: String): ChipDefinition?
    fun getLevelsForCurrentChip(): IntArray
    fun getLevelStringsForCurrentChip(): Array<String>
}
