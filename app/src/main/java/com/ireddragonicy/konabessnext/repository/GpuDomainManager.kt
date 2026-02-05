package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsHelper
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuDomainManager @Inject constructor(
    private val chipRepository: ChipRepositoryInterface
) {

    /**
     * Parses the full DTS file content (joined from lines) into Bins and Levels using AST.
     */
    fun parseBins(lines: List<String>): List<Bin> {
        if (lines.isEmpty()) return emptyList()

        // 1. Parse entire file to AST
        val fullText = lines.joinToString("\n")
        val root = DtsTreeHelper.parse(fullText)
        val bins = ArrayList<Bin>()

        // 2. Find Bin Nodes (qcom,gpu-pwrlevels or qcom,gpu-pwrlevels-X)
        val binNodes = findAllBinNodes(root)

        binNodes.forEachIndexed { index, node ->
            // Try to deduce ID from name suffix (e.g. pwrlevels-1 -> 1), otherwise use index
            val suffix = node.name.substringAfterLast("-", "")
            val binId = suffix.toIntOrNull() ?: index
            
            val bin = Bin(id = binId)

            // 3. Extract Header (Properties of the Bin Node)
            // The UI expects "lines" like "propertyName = value;" to display in the editor
            node.properties.forEach { prop ->
                // Reconstruct simple line format
                bin.addHeaderLine("${prop.name} = ${prop.originalValue};")
            }

            // 4. Extract Levels (Children of the Bin Node matching pattern)
            val levelNodes = node.children
                .filter { it.name.startsWith("qcom,gpu-pwrlevel@") }
                // Sort by level index from name
                .sortedBy { it.name.substringAfter("@").toIntOrNull() ?: 0 }

            levelNodes.forEach { lvlNode ->
                val level = Level()
                // Reconstruct lines for the Level object
                lvlNode.properties.forEach { prop ->
                    level.addLine("${prop.name} = ${prop.originalValue};")
                }
                bin.addLevel(level)
            }
            
            bins.add(bin)
        }
        
        return bins
    }

    private fun findAllBinNodes(root: DtsNode): List<DtsNode> {
        val results = ArrayList<DtsNode>()
        fun recurse(node: DtsNode) {
            val compatible = node.getProperty("compatible")?.originalValue
            
            // Standard check or legacy name check, BUT exclude "bins" container if it matches compatible
            val isCompatibleBin = compatible?.contains("qcom,gpu-pwrlevels") == true && compatible?.contains("bins") == false
            val isNameMatch = node.name.startsWith("qcom,gpu-pwrlevels")
            
            if (isCompatibleBin || isNameMatch) {
                results.add(node)
                // Don't recurse into a bin node looking for more bins
                return 
            }
            node.children.forEach { recurse(it) }
        }
        recurse(root)
        return results
    }

    fun parseOpps(lines: List<String>): List<Opp> {
        if (lines.isEmpty()) return emptyList()
        val pattern = chipRepository.currentChip.value?.voltTablePattern ?: return emptyList()
        
        // 1. Parse AST
        val fullText = lines.joinToString("\n")
        val root = DtsTreeHelper.parse(fullText)
        val opps = ArrayList<Opp>()
        
        // 2. Find Voltage Table Node
        val tableNode = findNodeByNameOrCompatible(root, pattern) ?: return emptyList()
        
        // 3. Iterate Children (opp nodes)
        tableNode.children.forEach { child ->
             // Usually named opp-12345 or similar, but structure defines content
             val freq = child.getLongValue("opp-hz")
             val volt = child.getLongValue("opp-microvolt")
             
             if (freq != null && volt != null) {
                 opps.add(Opp(freq, volt))
             }
        }
        
        return opps
    }
    
    // Helper to find specific node deep in tree
    private fun findNodeByNameOrCompatible(root: DtsNode, pattern: String): DtsNode? {
        if (root.name == pattern || root.getProperty("compatible")?.originalValue?.contains(pattern) == true) {
            return root
        }
        for (child in root.children) {
            val found = findNodeByNameOrCompatible(child, pattern)
            if (found != null) return found
        }
        return null
    }

    /**
     * Legacy helper used by GpuRepository to find line ranges for text replacement.
     * We kept GpuRepository text-based injection for safety, so we still need this logic mostly intact due to index reliance in updateParameterInBin.
     * However, ideally GpuRepository would ask DomainManager "where is this parameter?".
     * 
     * Since we are operating on 'lines', we revert to a simpler line scanning here or we could usage AST source tracking if we had it.
     * DtsParser/Lexer doesn't track line numbers perfectly yet for every property.
     * 
     * For now, I will modify this to be robust enough without full AST source map, 
     * likely keeping a simplified line scan similar to before BUT without the complex Strategy dependency.
     */
    fun findLevelLineRange(lines: List<String>, binIndex: Int, levelIndex: Int): Pair<Int, Int>? {
        // Simple state machine to find the Nth bin and Mth level
        var currentBinCount = -1
        var currentLevelCount = -1
        var insideTargetBin = false
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            
            // Detect Bin Start
            // compatible = "qcom,gpu-pwrlevels" OR node name qcom,gpu-pwrlevels...
            // A bit heuristic on line-based scan:
            if ((line.contains("qcom,gpu-pwrlevels") && line.endsWith("{")) || 
                (line.contains("compatible") && line.contains("qcom,gpu-pwrlevels"))) {
                
                // If it's a "compatible" line, we might be inside the node already started previous line?
                // Actually standard DTS: 
                // qcom,gpu-pwrlevels { 
                //    compatible = "qcom,gpu-pwrlevels";
                // }
                // Just counting the node start is safer:
            }
            
            // Regex match for node start
            if (line.matches(Regex(".*qcom,gpu-pwrlevels.*\\{.*"))) {
                currentBinCount++
                if (currentBinCount == binIndex) {
                    insideTargetBin = true
                    currentLevelCount = -1
                } else {
                    insideTargetBin = false
                }
            }
            
            if (insideTargetBin) {
                if (line.contains("qcom,gpu-pwrlevel@") && line.endsWith("{")) {
                    currentLevelCount++
                    if (currentLevelCount == levelIndex) {
                        // Found start line i
                        // Scan forward for matching brace
                        var braces = 1
                        for (j in i + 1 until lines.size) {
                            if (lines[j].contains("{")) braces++
                            if (lines[j].contains("}")) braces--
                            if (braces == 0) return Pair(i, j)
                        }
                    }
                }
                
                // If we exit the bin
                if (line == "};" && insideTargetBin && currentLevelCount == -1) { 
                    // This logic is tricky with nested braces. 
                    // Assuming indentation or just strict brace counting would be better if we tracked it from bin start.
                    // But for this specific function, we just need to find the level if it exists.
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

    /**
     * Generates DTS text representation of the given bins for export.
     * Replaces the deprecated ChipArchitecture.generateTable() method.
     */
    fun generateTableDts(bins: List<Bin>): List<String> {
        val lines = ArrayList<String>()
        
        bins.forEachIndexed { binIndex, bin ->
            // Bin Header
            lines.add("qcom,gpu-pwrlevels-$binIndex {")
            
            // Bin Properties (from header lines)
            bin.header.forEach { headerLine ->
                lines.add("\t$headerLine")
            }
            
            // Levels
            bin.levels.forEachIndexed { levelIndex, level ->
                lines.add("\tqcom,gpu-pwrlevel@$levelIndex {")
                level.lines.forEach { levelLine ->
                    lines.add("\t\t$levelLine")
                }
                lines.add("\t};")
            }
            
            lines.add("};")
        }
        
        return lines
    }
}
