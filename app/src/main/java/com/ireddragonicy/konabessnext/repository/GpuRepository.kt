/* --- src/main/java/com/ireddragonicy/konabessnext/repository/GpuRepository.kt --- */
package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.utils.DtsHelper
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
import java.io.IOException
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class GpuRepository @Inject constructor(
    private val dtsFileRepository: DtsFileRepository,
    private val gpuDomainManager: GpuDomainManager,
    private val historyManager: HistoryManager,
    private val chipRepository: ChipRepositoryInterface,
    private val userMessageManager: com.ireddragonicy.konabessnext.utils.UserMessageManager
) {
    // --- Single Source of Truth: The Text Lines ---
    private val _dtsLines = MutableStateFlow<List<String>>(emptyList())
    val dtsLines: StateFlow<List<String>> = _dtsLines.asStateFlow()

    val dtsContent: Flow<String> = _dtsLines.map { it.joinToString("\n") }.flowOn(Dispatchers.Default)

    // --- Derived States ---
    val bins: StateFlow<List<Bin>> = _dtsLines
        .map { lines -> gpuDomainManager.parseBins(lines) }
        .flowOn(Dispatchers.Default)
        .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Lazily, emptyList())

    private val _parsedTree = MutableStateFlow<DtsNode?>(null)
    val parsedTree: StateFlow<DtsNode?> = _parsedTree.asStateFlow()

    val opps: StateFlow<List<Opp>> = _dtsLines
        .map { lines -> gpuDomainManager.parseOpps(lines) }
        .flowOn(Dispatchers.Default)
        .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Lazily, emptyList())

    val canUndo = historyManager.canUndo
    val canRedo = historyManager.canRedo
    val history = historyManager.history

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()
    
    private var initialContentHash: Int = 0

    // --- Core Operations ---

    suspend fun loadTable() = withContext(Dispatchers.IO) {
        try {
            val lines = dtsFileRepository.loadDtsLines()
            historyManager.clear()
            _dtsLines.value = lines
            
            // Populate Tree for View Mode
            val fullText = lines.joinToString("\n")
            try {
                _parsedTree.value = DtsTreeHelper.parse(fullText)
            } catch (e: Exception) { e.printStackTrace() }
            
            initialContentHash = lines.hashCode()
            _isDirty.value = false
        } catch (e: Exception) {
            userMessageManager.emitError("Load Failed", e.localizedMessage ?: "Unknown error loading DTS")
        }
    }

    suspend fun saveTable() = withContext(Dispatchers.IO) {
        try {
            val currentLines = _dtsLines.value
            dtsFileRepository.saveDtsLines(currentLines)
            initialContentHash = currentLines.hashCode()
            _isDirty.value = false
        } catch (e: Exception) {
            userMessageManager.emitError("Save Failed", e.localizedMessage ?: "Unknown error saving DTS")
        }
    }

    fun updateContent(newLines: List<String>, description: String = "Edit", addToHistory: Boolean = true) {
        if (newLines == _dtsLines.value) return

        if (addToHistory) {
            historyManager.snapshot(_dtsLines.value, newLines, description)
        }
        
        _dtsLines.value = newLines
        _isDirty.value = (newLines.hashCode() != initialContentHash)
        
        // Update tree lazily to prevent blocking
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val fullText = newLines.joinToString("\n")
                _parsedTree.value = DtsTreeHelper.parse(fullText)
            } catch (ignored: Exception) {}
        }
    }

    // --- GUI Mutators ---

    data class ParameterUpdate(val binIndex: Int, val levelIndex: Int, val paramKey: String, val newValue: String)

    /**
     * Updates a specific parameter directly in the text lines.
     * Uses robust text scanning to ensure we edit the correct Bin/Level.
     */
    fun updateParameterInBin(binIndex: Int, levelIndex: Int, paramKey: String, newValue: String, historyDesc: String? = null) {
        val currentLines = ArrayList(_dtsLines.value)
        val binStartIdx = findBinStartIndex(currentLines, binIndex)
        
        if (binStartIdx != -1) {
            val levelStartIdx = findLevelStartIndex(currentLines, binStartIdx, levelIndex)
            if (levelStartIdx != -1) {
                // Scan inside the level for the parameter
                var propFound = false
                var braceCount = 0
                
                // Find end of level to limit search
                for (i in levelStartIdx until currentLines.size) {
                    val line = currentLines[i]
                    if (line.contains("{")) braceCount++
                    if (line.contains("}")) {
                        braceCount--
                        if (braceCount == 0) break // End of level
                    }
                    
                    // Match property:  key = <value>;
                    // We look for key followed by =
                    if (line.trim().startsWith(paramKey) && line.contains("=")) {
                        // Preserve indentation
                        val indent = line.substring(0, line.indexOf(paramKey))
                        currentLines[i] = "$indent$paramKey = <$newValue>;"
                        propFound = true
                        break
                    }
                }
                
                // If property not found (e.g. adding a new one), we could insert it, 
                // but currently we usually update existing ones.
                
                if (propFound) {
                    val desc = historyDesc ?: "Update $paramKey to $newValue (Bin $binIndex, Lvl $levelIndex)"
                    updateContent(currentLines, desc)
                }
            }
        }
    }
    
    /**
     * Safely deletes a level using text manipulation and renumbers remaining levels.
     * This avoids AST regeneration issues which can break 'dtc' compilation.
     */
    fun deleteLevel(binIndex: Int, levelIndex: Int) {
        val lines = ArrayList(_dtsLines.value)
        val binStartIdx = findBinStartIndex(lines, binIndex)
        if (binStartIdx == -1) return

        val levelStartIdx = findLevelStartIndex(lines, binStartIdx, levelIndex)
        if (levelStartIdx == -1) return

        // 1. Calculate Level End Index
        var levelEndIdx = -1
        var braceCount = 0
        // Determine scope end
        for (i in levelStartIdx until lines.size) {
            if (lines[i].contains("{")) braceCount++
            if (lines[i].contains("}")) {
                braceCount--
                if (braceCount == 0) {
                    levelEndIdx = i
                    break
                }
            }
        }

        if (levelEndIdx == -1) return

        // 2. Determine Bin Scope (to limit renumbering)
        var binEndIdx = -1
        var binBraceCount = 0
        // Reset and find bin end starting from binStart
        for (i in binStartIdx until lines.size) {
            if (lines[i].contains("{")) binBraceCount++
            if (lines[i].contains("}")) {
                binBraceCount--
                if (binBraceCount == 0) {
                    binEndIdx = i
                    break
                }
            }
        }

        // 3. Delete Lines
        val linesToRemove = levelEndIdx - levelStartIdx + 1
        repeat(linesToRemove) { lines.removeAt(levelStartIdx) }
        
        // Adjust binEndIdx because we removed lines
        binEndIdx -= linesToRemove

        // 4. Renumber Subsequent Levels (Shift ID down by 1)
        // Only scan within the Bin's scope
        for (i in binStartIdx..binEndIdx) {
            val line = lines[i]
            
            // Regex match: qcom,gpu-pwrlevel@X
            val nodeMatch = Regex("qcom,gpu-pwrlevel@(\\d+)").find(line)
            if (nodeMatch != null) {
                val currentId = nodeMatch.groupValues[1].toInt()
                if (currentId > levelIndex) {
                    val newId = currentId - 1
                    lines[i] = line.replace("qcom,gpu-pwrlevel@$currentId", "qcom,gpu-pwrlevel@$newId")
                }
            }
            
            // Regex match: reg = <X> (Decimal) or <0xX> (Hex)
            // Safety: Only replace strict matches to avoid false positives
            if (line.trim().startsWith("reg")) {
                val regMatch = Regex("reg\\s*=\\s*<([^>]+)>").find(line)
                if (regMatch != null) {
                    val rawVal = regMatch.groupValues[1].trim()
                    val intVal = try {
                        if (rawVal.startsWith("0x")) rawVal.substring(2).toInt(16) else rawVal.toInt()
                    } catch (e: Exception) { -1 }
                    
                    if (intVal > levelIndex) {
                        val newVal = intVal - 1
                        val newValStr = if (rawVal.startsWith("0x")) "0x" + Integer.toHexString(newVal) else newVal.toString()
                        lines[i] = line.replace("<$rawVal>", "<$newValStr>")
                    }
                }
            }
        }

        // 5. Update Header Pointers (initial-pwrlevel, etc.)
        for (i in binStartIdx..binEndIdx) {
            val line = lines[i]
            if (line.contains("qcom,initial-pwrlevel") || line.contains("qcom,ca-target-pwrlevel")) {
                val valMatch = Regex("<([^>]+)>").find(line)
                if (valMatch != null) {
                    val rawVal = valMatch.groupValues[1].trim()
                    val intVal = try {
                        if (rawVal.startsWith("0x")) rawVal.substring(2).toInt(16) else rawVal.toInt()
                    } catch (e: Exception) { -1 }
                    
                    if (intVal != -1) {
                        var newVal = intVal
                        if (intVal == levelIndex) {
                            // Pointed to deleted item -> decrement or clamp to 0
                            newVal = if (intVal > 0) intVal - 1 else 0
                        } else if (intVal > levelIndex) {
                            // Pointed to item that shifted down -> decrement
                            newVal = intVal - 1
                        }
                        
                        if (newVal != intVal) {
                            val newValStr = if (rawVal.startsWith("0x")) "0x" + Integer.toHexString(newVal) else newVal.toString()
                            lines[i] = line.replace("<$rawVal>", "<$newValStr>")
                        }
                    }
                }
            }
        }
        
        updateContent(lines, "Deleted Level $levelIndex from Bin $binIndex")
    }
    
    // Helper to find bin start line
    private fun findBinStartIndex(lines: List<String>, binId: Int): Int {
        // Try precise name first: qcom,gpu-pwrlevels-X
        var idx = lines.indexOfFirst { it.trim().startsWith("qcom,gpu-pwrlevels-$binId") && it.contains("{") }
        if (idx != -1) return idx
        
        // Fallback: If binId is 0, maybe it's just "qcom,gpu-pwrlevels {" (legacy single bin)
        if (binId == 0) {
            idx = lines.indexOfFirst { it.trim().startsWith("qcom,gpu-pwrlevels") && !it.contains("-") && it.contains("{") }
            if (idx != -1) return idx
        }
        
        // Fallback 2: Iterate all bin nodes and check qcom,speed-bin property?
        // Too complex for regex line scan, usually naming convention holds.
        return -1
    }
    
    // Helper to find level start line
    private fun findLevelStartIndex(lines: List<String>, binStartIdx: Int, levelIndex: Int): Int {
        var braceCount = 0
        // Limit search to current bin
        for (i in binStartIdx until lines.size) {
            if (lines[i].contains("{")) braceCount++
            if (lines[i].contains("}")) {
                braceCount--
                if (braceCount == 0) return -1 // End of bin
            }
            
            // Match qcom,gpu-pwrlevel@X
            if (lines[i].trim().startsWith("qcom,gpu-pwrlevel@$levelIndex") && lines[i].contains("{")) {
                return i
            }
        }
        return -1
    }

    // --- OPP and Other Updates ---
    
    fun updateOppVoltage(frequency: Long, newVolt: Long, historyDesc: String? = null) {
        val root = _parsedTree.value ?: return
        val success = gpuDomainManager.updateOppVoltage(root, frequency, newVolt)
        if (!success) return
        val newText = DtsTreeHelper.generate(root)
        updateContent(newText.split("\n"), historyDesc ?: "Updated OPP voltage")
    }

    fun batchUpdateParameters(updates: List<ParameterUpdate>, description: String = "Batch Update") {
        // Fallback to AST for batch updates as they are complex to do via Regex
        // But ensure we re-parse if needed
        val root = _parsedTree.value ?: return
        var anyChanged = false
        
        updates.forEach { update ->
            val binNode = gpuDomainManager.findBinNode(root, update.binIndex)
            if (binNode != null) {
                val levelNode = gpuDomainManager.findLevelNode(binNode, update.levelIndex)
                if (levelNode != null) {
                    levelNode.setProperty(update.paramKey, update.newValue)
                    anyChanged = true
                }
            }
        }
        
        if (anyChanged) {
            val newText = DtsTreeHelper.generate(root)
            updateContent(newText.split("\n"), description)
        }
    }
    
    fun applySnapshot(content: String) {
        updateContent(content.split("\n"), "Applied external snapshot")
    }

    fun updateOpps(newOpps: List<Opp>) {
        val patterns = chipRepository.currentChip.value?.voltTablePattern ?: return
        val newBlock = gpuDomainManager.generateOppTableBlock(newOpps)
        
        val currentLines = _dtsLines.value
        val startIdx = currentLines.indexOfFirst { it.trim().contains(patterns) && it.trim().endsWith("{") }
        if (startIdx == -1) return
        
        var braceCount = 0
        var endIdx = -1
        for (i in startIdx until currentLines.size) {
             if (currentLines[i].contains("{")) braceCount++
             if (currentLines[i].contains("}")) braceCount--
             if (braceCount == 0) {
                 endIdx = i
                 break
             }
        }
        
        if (endIdx != -1) {
            val validLines = ArrayList(currentLines)
            val removalCount = endIdx - startIdx + 1
            for (k in 0 until removalCount) validLines.removeAt(startIdx)
            val newLines = newBlock.split("\n")
            validLines.addAll(startIdx, newLines)
            updateContent(validLines, "Updated OPP Table")
        }
    }
    
    fun importTable(lines: List<String>) {
        val currentLines = _dtsLines.value
        val startIdx = currentLines.indexOfFirst { it.trim().startsWith("qcom,gpu-pwrlevels") && it.contains("{") }
        
        if (startIdx != -1) {
            var braceCount = 0
            var endIdx = -1
            for (i in startIdx until currentLines.size) {
                 if (currentLines[i].contains("{")) braceCount++
                 if (currentLines[i].contains("}")) braceCount--
                 if (braceCount == 0) {
                     endIdx = i
                     break
                 }
            }
            if (endIdx != -1) {
                val validLines = ArrayList(currentLines)
                val removalCount = endIdx - startIdx + 1
                for (k in 0 until removalCount) validLines.removeAt(startIdx)
                validLines.addAll(startIdx, lines)
                updateContent(validLines, "Imported Frequency Table")
            }
        }
    }

    fun importVoltTable(lines: List<String>) {
        val patterns = chipRepository.currentChip.value?.voltTablePattern ?: return
        val currentLines = _dtsLines.value
        val startIdx = currentLines.indexOfFirst { it.trim().contains(patterns) && it.trim().endsWith("{") }
        if (startIdx != -1) {
            var braceCount = 0
            var endIdx = -1
            for (i in startIdx until currentLines.size) {
                 if (currentLines[i].contains("{")) braceCount++
                 if (currentLines[i].contains("}")) braceCount--
                 if (braceCount == 0) {
                     endIdx = i
                     break
                 }
            }
            if (endIdx != -1) {
                val validLines = ArrayList(currentLines)
                val removalCount = endIdx - startIdx + 1
                for (k in 0 until removalCount) validLines.removeAt(startIdx)
                validLines.addAll(startIdx, lines)
                updateContent(validLines, "Imported Voltage Table")
            }
        }
    }

    fun undo() {
        val currentState = _dtsLines.value
        val revertedState = historyManager.undo(currentState)
        if (revertedState != null) {
            updateContent(revertedState, addToHistory = false)
        }
    }

    fun redo() {
        val currentState = _dtsLines.value
        val revertedState = historyManager.redo(currentState)
        if (revertedState != null) {
            updateContent(revertedState, addToHistory = false)
        }
    }

    fun getGpuModelName(): String {
        val lines = _dtsLines.value
        val modelPattern = Pattern.compile("""qcom,gpu-model\s*=\s*"([^"]+)";""")
        for (line in lines) {
            val matcher = modelPattern.matcher(line.trim())
            if (matcher.find()) return matcher.group(1) ?: ""
        }
        val chipidPattern = Pattern.compile("""qcom,chipid\s*=\s*<(0x[0-9a-fA-F]+)>;""")
        for (line in lines) {
            val matcher = chipidPattern.matcher(line.trim())
            if (matcher.find()) {
                val chipidHex = matcher.group(1) ?: ""
                val chipid = chipidHex.removePrefix("0x").toLongOrNull(16) ?: 0L
                return mapChipIdToGpuName(chipid)
            }
        }
        return ""
    }
    
    private fun mapChipIdToGpuName(chipid: Long): String {
        val major = ((chipid shr 24) and 0xFF).toInt()
        val minor = ((chipid shr 16) and 0xFF).toInt()
        return when {
            major == 6 && minor == 4 -> "Adreno 640"
            major == 6 && minor == 5 -> "Adreno 650"
            major == 6 && minor == 6 -> "Adreno 660"
            major == 6 && minor == 8 -> "Adreno 680"
            major == 6 && minor == 9 -> "Adreno 690"
            major == 7 && minor == 3 -> "Adreno 730"
            major == 7 && minor == 4 -> "Adreno 740"
            major == 7 && minor == 5 -> "Adreno 750"
            else -> "Adreno ${major}${minor}0"
        }
    }

    fun updateGpuModelName(newName: String) {
        val currentLines = ArrayList(_dtsLines.value)
        val pattern = Pattern.compile("""(qcom,gpu-model\s*=\s*")([^"]+)(";.*)""")
        var found = false
        for (i in currentLines.indices) {
            val line = currentLines[i]
            val matcher = pattern.matcher(line)
            if (matcher.find()) {
                val newLine = line.replaceFirst(Regex(""""[^"]+""""), "\"$newName\"")
                currentLines[i] = newLine
                found = true
                break
            }
        }
        if (found) {
            updateContent(currentLines, "Renamed GPU to $newName")
        }
    }
}
