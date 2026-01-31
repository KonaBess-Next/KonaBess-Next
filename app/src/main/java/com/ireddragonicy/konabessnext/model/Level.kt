package com.ireddragonicy.konabessnext.model

data class Level(
    @JvmField var lines: ArrayList<String> = ArrayList()
) {
    // Deep copy constructor logic handled by data class copy if structures are immutable, 
    // but since ArrayList is mutable we might want a manual copy or use helper.
    // For simplicity in migration, we can add a method or specific constructor if needed.
    // But data classes are usually immutable. The original code used mutable ArrayLists.
    // We'll keep it mutable for now to match logic.

    constructor(other: Level) : this(ArrayList(other.lines))

    fun copyLevel(): Level {
        return Level(ArrayList(lines))
    }

    val frequency: Long
        get() = findValue("qcom,gpu-freq")

    val voltageLevel: Int
        get() {
            // Check both potential keys
            val val1 = findValue("qcom,level")
            if (val1 != -1L) return val1.toInt()
            val val2 = findValue("qcom,cx-level")
            return if (val2 != -1L) val2.toInt() else -1
        }

    // Common private helper that uses the DRY DtsHelper
    private fun findValue(key: String): Long {
        for (line in lines) {
            if (line.contains(key)) {
                return com.ireddragonicy.konabessnext.utils.DtsHelper.extractLongValue(line)
            }
        }
        return -1L
    }
        
    fun addLine(line: String) {
        lines.add(line)
    }
}
