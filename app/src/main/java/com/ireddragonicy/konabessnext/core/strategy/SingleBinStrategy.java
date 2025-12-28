package com.ireddragonicy.konabessnext.core.strategy;

import java.util.ArrayList;
import java.util.List;
import com.ireddragonicy.konabessnext.model.Bin;

public class SingleBinStrategy extends BaseChipArchitecture {

    @Override
    public boolean isStartLine(String line) {
        return line.equals("qcom,gpu-pwrlevels {");
    }

    @Override
    public List<String> generateTable(List<Bin> bins) {
        List<String> lines = new ArrayList<>();
        if (!bins.isEmpty()) {
            lines.add("qcom,gpu-pwrlevels {"); // Single bin header is constant
            generateBinContent(bins.get(0), lines);
        }
        return lines;
    }
}
