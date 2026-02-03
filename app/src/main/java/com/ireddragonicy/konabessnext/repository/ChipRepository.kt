package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.interfaces.ChipDefinitionLoader
import com.ireddragonicy.konabessnext.model.ChipDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ChipRepository @Inject constructor(
    private val loader: ChipDefinitionLoader
) : ChipRepositoryInterface {
    private val _definitions = MutableStateFlow<List<ChipDefinition>>(emptyList())
    override val definitions: StateFlow<List<ChipDefinition>> = _definitions.asStateFlow()

    private val _currentChip = MutableStateFlow<ChipDefinition?>(null)
    override val currentChip: StateFlow<ChipDefinition?> = _currentChip.asStateFlow()

    // Strategy instances
    private val multiBinStrategy = com.ireddragonicy.konabessnext.core.strategy.MultiBinStrategy()
    private val singleBinStrategy = com.ireddragonicy.konabessnext.core.strategy.SingleBinStrategy()

    init {
        loadDefinitions()
    }

    override fun loadDefinitions() {
        val defs = loader.loadDefinitions()
        _definitions.value = defs
    }

    override fun setCurrentChip(chip: ChipDefinition?) {
        _currentChip.value = chip
    }

    override fun getChipById(id: String): ChipDefinition? {
        return _definitions.value.find { it.id == id }
    }

    override fun getArchitecture(def: ChipDefinition?): com.ireddragonicy.konabessnext.core.strategy.ChipArchitecture {
        return if (def?.strategyType == "SINGLE_BIN") singleBinStrategy else multiBinStrategy
    }

    override fun getLevelsForCurrentChip(): IntArray {
        val c = _currentChip.value ?: return IntArray(0)
        val size = c.levelCount
        return IntArray(size) { it + 1 }
    }

    override fun getLevelStringsForCurrentChip(): Array<String> {
        val c = _currentChip.value ?: return emptyArray()
        val size = c.levelCount
        return Array(size) { i ->
            // Map keys in JSON are indices (0-based)
            c.levels[i] ?: (i + 1).toString()
        }
    }
}
