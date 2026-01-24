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
        get() {
            for (line in lines) {
                if (line.contains("qcom,gpu-freq")) {
                    return try {
                        line.replace(Regex("[^0-9]"), "").toLong()
                    } catch (e: NumberFormatException) {
                        -1
                    }
                }
            }
            return -1
        }

    val voltageLevel: Int
        get() {
            for (line in lines) {
                if (line.contains("qcom,level") && !line.contains("qcom,gpu-freq")) {
                    return try {
                        line.replace(Regex("[^0-9]"), "").toInt()
                    } catch (e: NumberFormatException) {
                        -1
                    }
                }
            }
            return -1
        }

        
    fun addLine(line: String) {
        lines.add(line)
    }
}
