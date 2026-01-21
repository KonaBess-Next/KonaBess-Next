package com.ireddragonicy.konabessnext.core

import android.app.Activity
import android.content.Context
import android.util.SparseArray
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.core.strategy.ChipArchitecture
import com.ireddragonicy.konabessnext.core.strategy.MultiBinStrategy
import com.ireddragonicy.konabessnext.core.strategy.SingleBinStrategy

object ChipInfo {

    // Stateless strategy instances
    private val MULTI_BIN: ChipArchitecture = MultiBinStrategy()
    private val SINGLE_BIN: ChipArchitecture = SingleBinStrategy()

    @JvmStatic
    var definitions: List<ChipDefinition> = emptyList()
        private set

    @JvmStatic
    var current: ChipDefinition? = null
        set(value) {
            field = value
            recomputeLevels()
        }

    // Cached values for the current chip
    private var cachedLevels: IntArray = IntArray(0)
    private var cachedLevelStrings: Array<String> = emptyArray()

    @JvmStatic
    fun loadDefinitions(context: Context) {
        definitions = ChipLoader.loadDefinitions(context)
    }
    
    @JvmStatic
    fun getArchitecture(def: ChipDefinition?): ChipArchitecture {
        return if (def?.strategyType == "SINGLE_BIN") SINGLE_BIN else MULTI_BIN
    }
    
    @JvmStatic
    fun getById(id: String): ChipDefinition? {
        return definitions.find { it.id == id }
    }

    private fun recomputeLevels() {
        val c = current ?: return
        val size = c.levelCount
        cachedLevels = IntArray(size) { it + 1 }
        cachedLevelStrings = Array(size) { i ->
            // Map keys in JSON are indices (0-based)
            c.levels[i] ?: (i + 1).toString()
        }
    }

    object rpmh_levels {
        @JvmStatic
        fun levels(): IntArray {
            return cachedLevels
        }

        @JvmStatic
        fun level_str(): Array<String> {
            return cachedLevelStrings
        }
    }
}

