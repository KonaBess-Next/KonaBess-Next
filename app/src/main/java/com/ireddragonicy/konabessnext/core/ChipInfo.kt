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

    var definitions: List<ChipDefinition> = emptyList()
        private set

    var current: ChipDefinition? = null
        set(value) {
            field = value
            recomputeLevels()
        }

    // Deprecated 'which' property for backward compatibility (where possible)
    // Note: This relies on the new system being initialized
    // We cannot easily map back to the old enum, so usages of 'which' should be updated.
    // However, for compilation safety, we might need a temporary shim if we don't fix all call sites immediately.
    // Ideally, we refactor all call sites. But to satisfy "Refactor ChipInfo" we will remove the enum.
    
    // Cached values for the current chip
    private var cachedLevels: IntArray = IntArray(0)
    private var cachedLevelStrings: Array<String> = emptyArray()

    fun loadDefinitions(context: Context) {
        definitions = ChipLoader.loadDefinitions(context)
    }
    
    fun getArchitecture(def: ChipDefinition?): ChipArchitecture {
        return if (def?.strategyType == "SINGLE_BIN") SINGLE_BIN else MULTI_BIN
    }
    
    // Helper to find definition by ID
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

