package com.ireddragonicy.konabessnext.repository

import android.content.Context
import com.ireddragonicy.konabessnext.core.ChipLoader
import com.ireddragonicy.konabessnext.model.ChipDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChipRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _definitions = MutableStateFlow<List<ChipDefinition>>(emptyList())
    val definitions: StateFlow<List<ChipDefinition>> = _definitions.asStateFlow()

    private val _currentChip = MutableStateFlow<ChipDefinition?>(null)
    val currentChip: StateFlow<ChipDefinition?> = _currentChip.asStateFlow()

    init {
        loadDefinitions()
    }

    fun loadDefinitions() {
        _definitions.value = ChipLoader.loadDefinitions(context)
    }

    fun setCurrentChip(chip: ChipDefinition?) {
        _currentChip.value = chip
        // Update legacy ChipInfo for backward compatibility during transition
        com.ireddragonicy.konabessnext.core.ChipInfo.current = chip
    }

    fun getChipById(id: String): ChipDefinition? {
        return _definitions.value.find { it.id == id }
    }
}
