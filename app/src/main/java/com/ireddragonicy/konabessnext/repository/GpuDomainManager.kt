package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.utils.DtsHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuDomainManager @Inject constructor(
    private val chipRepository: ChipRepositoryInterface
) {

    fun parseBins(lines: List<String>): List<Bin> {
        val currentChip = chipRepository.currentChip.value ?: return emptyList()
        val strategy = chipRepository.getArchitecture(currentChip)
        val bins = ArrayList<Bin>()
        val mutableLines = ArrayList(lines) 
        
        var i = -1
        while (++i < mutableLines.size) {
            val line = mutableLines[i].trim()
            if (strategy.isStartLine(line)) {
                try {
                    strategy.decode(mutableLines, bins, i)
                    i-- 
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        return bins
    }

    fun parseOpps(lines: List<String>): List<Opp> {
        val opps = ArrayList<Opp>()
        val pattern = chipRepository.currentChip.value?.voltTablePattern ?: return emptyList()
        
        var insideTable = false
        var insideNode = false
        var currentFreq = 0L
        var currentVolt = 0L
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains(pattern) && trimmed.endsWith("{")) insideTable = true
            if (!insideTable) continue
            
            if (trimmed.startsWith("opp-") && trimmed.endsWith("{")) {
                insideNode = true
                currentFreq = 0
                currentVolt = 0
            } else if (trimmed == "};" && insideNode) {
                if (currentFreq > 0) opps.add(Opp(currentFreq, currentVolt))
                insideNode = false
            } else if (insideNode) {
                if (trimmed.contains("opp-hz")) currentFreq = DtsHelper.extractLongValue(trimmed)
                if (trimmed.contains("opp-microvolt")) currentVolt = DtsHelper.extractLongValue(trimmed)
            } else if (trimmed == "};") {
                insideTable = false
            }
        }
        return opps
    }

    fun findLevelLineRange(lines: List<String>, binIndex: Int, levelIndex: Int): Pair<Int, Int>? {
        val chip = chipRepository.currentChip.value ?: return null
        val strategy = chipRepository.getArchitecture(chip)
        
        var currentBinIdx = -1
        var insideTargetBin = false
        var currentLevelIdx = -1
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            
            // Check Bin Start
            if (strategy.isStartLine(line)) {
                currentBinIdx++
                insideTargetBin = (currentBinIdx == binIndex)
                currentLevelIdx = -1 
                continue
            }
            
            if (!insideTargetBin) continue
            
            if (line.startsWith("qcom,gpu-pwrlevel@")) {
                currentLevelIdx++
                if (currentLevelIdx == levelIndex) {
                    var braceCount = 1
                    for (j in i + 1 until lines.size) {
                        if (lines[j].contains("{")) braceCount++
                        if (lines[j].contains("}")) braceCount--
                        if (braceCount == 0) return Pair(i, j)
                    }
                }
            }
        }
        return null
    }

    fun generateOppTableBlock(newOpps: List<Opp>): String {
        val patterns = chipRepository.currentChip.value?.voltTablePattern ?: return ""
        val newBlock = StringBuilder()
        newBlock.append("\t\t").append(patterns).append(" {\n")
        newOpps.forEach { opp ->
            newBlock.append("\t\t\topp-${opp.frequency} {\n")
            newBlock.append("\t\t\t\topp-hz = /bits/ 64 <${opp.frequency}>;\n")
            newBlock.append("\t\t\t\topp-microvolt = <${opp.volt}>;\n")
            newBlock.append("\t\t\t};\n")
        }
        newBlock.append("\t\t};")
        return newBlock.toString()
    }
}
