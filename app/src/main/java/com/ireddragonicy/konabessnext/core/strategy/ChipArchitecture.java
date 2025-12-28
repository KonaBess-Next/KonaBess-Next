package com.ireddragonicy.konabessnext.core.strategy;

import java.util.List;
import com.ireddragonicy.konabessnext.model.Bin;

public interface ChipArchitecture {
    /**
     * Checks if the current line indicates the start of a GPU power level block.
     */
    boolean isStartLine(String line);

    /**
     * Decodes lines from the DTS file into Bin objects.
     * 
     * @param dtsLines   The full list of lines in the DTS file.
     * @param bins       The list to populate with parsed Bin objects.
     * @param startIndex The line index where the block starts.
     */
    void decode(List<String> dtsLines, List<Bin> bins, int startIndex) throws Exception;

    /**
     * Generates DTS string lines from the Bin objects.
     */
    List<String> generateTable(List<Bin> bins);
}
