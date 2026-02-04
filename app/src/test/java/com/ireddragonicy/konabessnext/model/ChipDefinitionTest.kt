package com.ireddragonicy.konabessnext.model

import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class ChipDefinitionTest {

    @Test(expected = IllegalArgumentException::class)
    fun testValidation_BlankId_Throws() {
        ChipDefinition(
            id = "",
            name = "Test",
            maxTableLevels = 10,
            ignoreVoltTable = false,
            minLevelOffset = 0,
            strategyType = "SINGLE_BIN",
            levelCount = 5,
            levels = mapOf(1 to "L1")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValidation_ZeroLevels_Throws() {
        ChipDefinition(
            id = "test",
            name = "Test",
            maxTableLevels = 10,
            ignoreVoltTable = false,
            minLevelOffset = 0,
            strategyType = "SINGLE_BIN",
            levelCount = 0, // Invalid
            levels = mapOf(1 to "L1")
        )
    }

    @Test
    fun testValidDefinition() {
        ChipDefinition(
            id = "test",
            name = "Test",
            maxTableLevels = 10,
            ignoreVoltTable = false,
            minLevelOffset = 0,
            strategyType = "SINGLE_BIN",
            levelCount = 5,
            levels = mapOf(1 to "L1")
        )
    }
}
