package com.ireddragonicy.konabessnext.core.strategy

import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level

abstract class BaseChipArchitecture : ChipArchitecture {

    @Throws(Exception::class)
    override fun decode(dtsLines: MutableList<String>, bins: MutableList<Bin>, startIndex: Int) {
        var i = startIndex
        var bracket = 0

        while (i < dtsLines.size) {
            val thisLine = dtsLines[i].trim()

            if (thisLine.contains("{")) {
                bracket++
            }
            if (thisLine.contains("}")) {
                bracket--
            }

            if (bracket == 0) {
                val end = i
                decodeBin(dtsLines.subList(startIndex, end + 1), bins)
                dtsLines.subList(startIndex, end + 1).clear()
                return
            }
            i++
        }
        throw Exception("Invalid bin range: non-terminating block starting at line $startIndex")
    }

    /**
     * Decodes a single bin block.
     *
     * @param lines The lines belonging to this bin block.
     * @param bins  The list to add the parsed Bin to.
     */
    @Throws(Exception::class)
    protected fun decodeBin(lines: List<String>, bins: MutableList<Bin>) {
        val bin = Bin(bins.size)
        var i = 0
        var bracket = 0
        var start = 0

        // Get bin ID from first line
        bin.id = parseBinId(lines[0], bins.size)
        bin.header = ArrayList()

        // Pre-increment i to skip first line? In Java: while (++i < lines.size() ...)
        // Here we can start from index 1.
        i = 1
        while (i < lines.size && bracket >= 0) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }

            if (line.contains("{")) {
                if (bracket != 0)
                    throw Exception("Nested bracket error")
                start = i
                bracket++
                i++
                continue
            }

            if (line.contains("}")) {
                bracket--
                if (bracket < 0) {
                    i++
                    continue
                }
                val end = i
                if (end >= start) {
                    bin.addLevel(decodeLevel(lines.subList(start, end + 1)))
                }
                i++
                continue
            }

            if (bracket == 0) {
                bin.addHeaderLine(line)
            }
            i++
        }
        bins.add(bin)
    }

    /**
     * Helper to generate content for a single bin (headers + levels).
     */
    protected fun generateBinContent(bin: Bin, lines: MutableList<String>) {
        lines.addAll(bin.header)
        for (j in 0 until bin.levelCount) {
            lines.add("qcom,gpu-pwrlevel@$j {")
            lines.add("reg = <$j>;")
            lines.addAll(bin.getLevel(j).lines)
            lines.add("};")
        }
        lines.add("};")
    }

    /**
     * Decodes a level block.
     */
    protected fun decodeLevel(lines: List<String>): Level {
        val level = Level()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.contains("{") || line.contains("}"))
                continue
            if (line.contains("reg"))
                continue
            level.addLine(line)
        }
        return level
    }

    /**
     * Parses the bin ID from the definition line.
     */
    protected fun parseBinId(line: String, defaultId: Int): Int {
        var processedLine = line.trim().replace(" {", "").replace("-", "")
        var currentId = defaultId
        try {
            for (i in processedLine.length - 1 downTo 0) {
                currentId = processedLine.substring(i).toInt()
            }
        } catch (ignored: NumberFormatException) {
        }
        return currentId
    }
}
