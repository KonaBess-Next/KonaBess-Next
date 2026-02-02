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

    // Strategy instances
    private val multiBinStrategy = com.ireddragonicy.konabessnext.core.strategy.MultiBinStrategy()
    private val singleBinStrategy = com.ireddragonicy.konabessnext.core.strategy.SingleBinStrategy()

    init {
        loadDefinitions()
    }

    fun loadDefinitions() {
        val defs = ChipLoader.loadDefinitions(context)
        _definitions.value = defs
    }

    fun setCurrentChip(chip: ChipDefinition?) {
        _currentChip.value = chip
    }

    fun getChipById(id: String): ChipDefinition? {
        return _definitions.value.find { it.id == id }
    }

    fun getArchitecture(def: ChipDefinition?): com.ireddragonicy.konabessnext.core.strategy.ChipArchitecture {
        return if (def?.strategyType == "SINGLE_BIN") singleBinStrategy else multiBinStrategy
    }

    fun getLevelsForCurrentChip(): IntArray {
        val c = _currentChip.value ?: return IntArray(0)
        val size = c.levelCount
        return IntArray(size) { it + 1 }
    }

    fun getLevelStringsForCurrentChip(): Array<String> {
        val c = _currentChip.value ?: return emptyArray()
        val size = c.levelCount
        return Array(size) { i ->
            // Map keys in JSON are indices (0-based)
            c.levels[i] ?: (i + 1).toString()
        }
    }
}
