package com.ireddragonicy.konabessnext.core.strategy

import com.ireddragonicy.konabessnext.model.Bin
import java.util.ArrayList

class SingleBinStrategy : BaseChipArchitecture() {

    override fun isStartLine(line: String): Boolean {
        return line == "qcom,gpu-pwrlevels {"
    }

    override fun generateTable(bins: List<Bin>): List<String> {
        val lines = ArrayList<String>()
        if (bins.isNotEmpty()) {
            lines.add("qcom,gpu-pwrlevels {") // Single bin header is constant
            generateBinContent(bins[0], lines)
        }
        return lines
    }
}
