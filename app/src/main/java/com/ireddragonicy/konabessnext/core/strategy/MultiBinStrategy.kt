package com.ireddragonicy.konabessnext.core.strategy

import com.ireddragonicy.konabessnext.model.Bin
import java.util.ArrayList

class MultiBinStrategy : BaseChipArchitecture() {

    override fun isStartLine(line: String): Boolean {
        return line.contains("qcom,gpu-pwrlevels-")
                && !line.contains("compatible = ")
                && !line.contains("qcom,gpu-pwrlevel-bins")
    }

    override fun generateTable(bins: List<Bin>): List<String> {
        val lines = ArrayList<String>()
        for (bin in bins) {
            lines.add("qcom,gpu-pwrlevels-" + bin.id + " {")
            generateBinContent(bin, lines)
        }
        return lines
    }
}
