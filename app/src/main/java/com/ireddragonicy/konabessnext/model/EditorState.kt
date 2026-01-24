package com.ireddragonicy.konabessnext.model

data class EditorState(
    @JvmField var linesInDts: ArrayList<String> = ArrayList(),
    @JvmField var binsSnapshot: ArrayList<Bin> = ArrayList(),
    @JvmField var binPosition: Int = -1,
    @JvmField var oppsSnapshot: ArrayList<Opp> = ArrayList(),
    @JvmField var oppPosition: Int = -1
) {
    companion object {
        fun deepCopyBins(source: List<Bin>?): ArrayList<Bin> {
            val copy = ArrayList<Bin>()
            source?.forEach { copy.add(it.copyBin()) }
            return copy
        }

        fun deepCopyOpps(source: List<Opp>?): ArrayList<Opp> {
            val copy = ArrayList<Opp>()
            source?.forEach { copy.add(it.copy()) }
            return copy
        }
    }
}
