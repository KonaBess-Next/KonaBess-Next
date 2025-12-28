package com.ireddragonicy.konabessnext.core.strategy;

import java.util.List;
import com.ireddragonicy.konabessnext.model.Bin;
import com.ireddragonicy.konabessnext.model.Level;

public abstract class BaseChipArchitecture implements ChipArchitecture {

    @Override
    public void decode(List<String> dtsLines, List<Bin> bins, int startIndex) throws Exception {
        int i = startIndex;
        int bracket = 0;

        while (i < dtsLines.size()) {
            String thisLine = dtsLines.get(i).trim();

            if (thisLine.contains("{")) {
                bracket++;
            }
            if (thisLine.contains("}")) {
                bracket--;
            }

            if (bracket == 0) {
                int end = i;
                decodeBin(dtsLines.subList(startIndex, end + 1), bins);
                dtsLines.subList(startIndex, end + 1).clear();
                return;
            }
            i++;
        }
        throw new Exception("Invalid bin range: non-terminating block starting at line " + startIndex);
    }

    /**
     * Decodes a single bin block.
     * 
     * @param lines The lines belonging to this bin block.
     * @param bins  The list to add the parsed Bin to.
     */
    protected void decodeBin(List<String> lines, List<Bin> bins) throws Exception {
        Bin bin = new Bin(bins.size());
        int i = 0;
        int bracket = 0;
        int start = 0;

        // Get bin ID from first line
        bin.setId(parseBinId(lines.get(0), bins.size()));

        while (++i < lines.size() && bracket >= 0) {
            String line = lines.get(i).trim();
            if (line.isEmpty())
                continue;

            if (line.contains("{")) {
                if (bracket != 0)
                    throw new Exception("Nested bracket error");
                start = i;
                bracket++;
                continue;
            }

            if (line.contains("}")) {
                if (--bracket < 0)
                    continue;
                int end = i;
                if (end >= start) {
                    bin.addLevel(decodeLevel(lines.subList(start, end + 1)));
                }
                continue;
            }

            if (bracket == 0) {
                bin.addHeaderLine(line);
            }
        }
        bins.add(bin);
    }

    /**
     * Helper to generate content for a single bin (headers + levels).
     */
    protected void generateBinContent(Bin bin, List<String> lines) {
        lines.addAll(bin.getHeader());
        for (int j = 0; j < bin.getLevelCount(); j++) {
            lines.add("qcom,gpu-pwrlevel@" + j + " {");
            lines.add("reg = <" + j + ">;");
            lines.addAll(bin.getLevel(j).getLines());
            lines.add("};");
        }
        lines.add("};");
    }

    /**
     * Decodes a level block.
     */
    protected Level decodeLevel(List<String> lines) {
        Level level = new Level();
        for (String line : lines) {
            line = line.trim();
            if (line.contains("{") || line.contains("}"))
                continue;
            if (line.contains("reg"))
                continue;
            level.addLine(line);
        }
        return level;
    }

    /**
     * Parses the bin ID from the definition line.
     */
    protected int parseBinId(String line, int defaultId) {
        line = line.trim().replace(" {", "").replace("-", "");
        try {
            for (int i = line.length() - 1; i >= 0; i--) {
                defaultId = Integer.parseInt(line.substring(i));
            }
        } catch (NumberFormatException ignored) {
        }
        return defaultId;
    }
}
