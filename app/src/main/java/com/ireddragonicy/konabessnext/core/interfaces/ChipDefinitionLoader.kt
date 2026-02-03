package com.ireddragonicy.konabessnext.core.interfaces

import com.ireddragonicy.konabessnext.model.ChipDefinition

interface ChipDefinitionLoader {
    fun loadDefinitions(): List<ChipDefinition>
}
