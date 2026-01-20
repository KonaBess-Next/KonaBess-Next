package com.ireddragonicy.konabessnext.core.strategy

import com.ireddragonicy.konabessnext.model.Bin

interface ChipArchitecture {
    /**
     * Checks if the current line indicates the start of a GPU power level block.
     */
    fun isStartLine(line: String): Boolean

    /**
     * Decodes lines from the DTS file into Bin objects.
     *
     * @param dtsLines   The full list of lines in the DTS file.
     * @param bins       The list to populate with parsed Bin objects.
     * @param startIndex The line index where the block starts.
     */
    @Throws(Exception::class)
    fun decode(dtsLines: MutableList<String>, bins: MutableList<Bin>, startIndex: Int)

    /**
     * Generates DTS string lines from the Bin objects.
     */
    fun generateTable(bins: List<Bin>): List<String>
}
