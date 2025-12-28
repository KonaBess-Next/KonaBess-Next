package com.ireddragonicy.konabessnext.core.strategy;

import java.util.ArrayList;
import java.util.List;
import com.ireddragonicy.konabessnext.model.Bin;

public class MultiBinStrategy extends BaseChipArchitecture {

    @Override
    public boolean isStartLine(String line) {
        return line.contains("qcom,gpu-pwrlevels-")
                && !line.contains("compatible = ")
                && !line.contains("qcom,gpu-pwrlevel-bins");
    }

    @Override
    public List<String> generateTable(List<Bin> bins) {
        List<String> lines = new ArrayList<>();
        for (Bin bin : bins) {
            lines.add("qcom,gpu-pwrlevels-" + bin.getId() + " {");
            generateBinContent(bin, lines);
        }
        return lines;
    }
}
