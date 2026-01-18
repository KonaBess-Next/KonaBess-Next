package com.ireddragonicy.konabessnext.model

data class Bin(
    @JvmField var id: Int = 0,
    @JvmField var header: ArrayList<String> = ArrayList(),
    @JvmField var levels: ArrayList<Level> = ArrayList()
) {
    constructor(id: Int) : this(id, ArrayList(), ArrayList())

    constructor(other: Bin) : this(
        other.id,
        ArrayList(other.header),
        other.levels.mapTo(ArrayList()) { it.copyLevel() }
    )

    fun copyBin(): Bin {
        return Bin(this)
    }

    fun addLevel(level: Level) {
        levels.add(level)
    }

    fun addHeaderLine(line: String) {
        header.add(line)
    }

    val levelCount: Int
        get() = levels.size

    fun getLevel(index: Int): Level {
        return levels[index]
    }
}
