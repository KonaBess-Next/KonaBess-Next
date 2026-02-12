package com.ireddragonicy.konabessnext.core.editor

import com.ireddragonicy.konabessnext.model.Bin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelOperationsTest {

    @Test
    fun offsetInitialLevel_preservesHexFormat() {
        val bins = arrayListOf(
            Bin(
                id = 0,
                header = arrayListOf("qcom,initial-pwrlevel = <0x4>;"),
                levels = arrayListOf()
            )
        )

        LevelOperations.offsetInitialLevel(bins, 0, 1)

        assertEquals("qcom,initial-pwrlevel = <0x5>;", bins[0].header[0])
    }

    @Test
    fun offsetInitialLevel_preservesDecimalFormat() {
        val bins = arrayListOf(
            Bin(
                id = 0,
                header = arrayListOf("qcom,initial-pwrlevel = <4>;"),
                levels = arrayListOf()
            )
        )

        LevelOperations.offsetInitialLevel(bins, 0, 1)

        assertEquals("qcom,initial-pwrlevel = <5>;", bins[0].header[0])
    }

    @Test
    fun offsetInitialLevel_doesNotGoBelowZero() {
        val bins = arrayListOf(
            Bin(
                id = 0,
                header = arrayListOf("qcom,initial-pwrlevel = <0>;"),
                levels = arrayListOf()
            )
        )

        LevelOperations.offsetInitialLevel(bins, 0, -1)

        assertEquals("qcom,initial-pwrlevel = <0>;", bins[0].header[0])
    }

    @Test
    fun offsetInitialLevel_isNoOpWhenPropertyMissing() {
        val original = "qcom,speed-bin = <1>;"
        val bins = arrayListOf(
            Bin(
                id = 0,
                header = arrayListOf(original),
                levels = arrayListOf()
            )
        )

        LevelOperations.offsetInitialLevel(bins, 0, 10)

        assertEquals(original, bins[0].header[0])
    }

    @Test
    fun patchThrottleLevel_setsValueToZero() {
        val bins = arrayListOf(
            Bin(
                id = 0,
                header = arrayListOf(
                    "qcom,throttle-pwrlevel = <3>;",
                    "qcom,initial-pwrlevel = <1>;"
                ),
                levels = arrayListOf()
            )
        )

        LevelOperations.patchThrottleLevel(bins)

        assertTrue(bins[0].header.any { it == "qcom,throttle-pwrlevel = <0>;" })
    }
}
