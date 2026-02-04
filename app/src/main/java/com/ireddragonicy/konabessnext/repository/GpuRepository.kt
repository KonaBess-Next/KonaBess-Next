package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.utils.DtsHelper
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import com.ireddragonicy.konabessnext.utils.FileUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.Deque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class GpuRepository @Inject constructor(
    private val deviceRepository: DeviceRepositoryInterface,
    private val chipRepository: ChipRepositoryInterface,
    private val fileDataSource: com.ireddragonicy.konabessnext.core.interfaces.FileDataSource
) {
    // --- Single Source of Truth: The Text Lines ---
    private val _dtsLines = MutableStateFlow<List<String>>(emptyList())
    val dtsLines: StateFlow<List<String>> = _dtsLines.asStateFlow()

    val dtsContent: Flow<String> = _dtsLines.map { it.joinToString("\n") }.flowOn(Dispatchers.Default)

    // --- Derived States (Auto-Synced) ---
    
    // 1. Parsed Bins (GUI Model)
    val bins: StateFlow<List<Bin>> = _dtsLines
        .map { lines -> parseBinsFromLines(lines) }
        .flowOn(Dispatchers.Default) // Compute on background
        .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Lazily, emptyList())

    // 2. Parsed Tree (Visual Tree Model)
    @OptIn(FlowPreview::class)
    val parsedTree = _dtsLines
        .debounce(300L) // Slight delay for tree parsing during heavy typing
        .map { lines -> 
            try { DtsTreeHelper.parse(lines.joinToString("\n")) } catch (e: Exception) { null }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Lazily, null)

    // 3. Parsed Opps (Voltage Table)
    val opps: StateFlow<List<Opp>> = _dtsLines
        .map { lines -> parseOppsFromLines(lines) }
        .flowOn(Dispatchers.Default)
        .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Lazily, emptyList())

    // --- History Management (Unified) ---
    private data class HistoryItem(val lines: List<String>, val description: String)
    
    private val undoStack: Deque<HistoryItem> = ArrayDeque()
    private val redoStack: Deque<HistoryItem> = ArrayDeque()
    private val MAX_HISTORY = 50

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()
    
    // Exposed History List (Reverse order for UI: Newest first)
    private val _historyList = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _historyList.asStateFlow()
    
    private fun refreshHistoryList() {
        _historyList.value = undoStack.map { it.description }.reversed()
    }

    private var initialContentHash: Int = 0

    // --- Core Operations ---

    suspend fun loadTable() = withContext(Dispatchers.IO) {
        val path = deviceRepository.dtsPath ?: 
                   (if (deviceRepository.tryRestoreLastChipset()) deviceRepository.dtsPath else null) ?: 
                   throw IOException("DTS Path not set")
        
        val lines = fileDataSource.readLines(path)
        
        // Reset State
        undoStack.clear()
        redoStack.clear()
        updateHistoryFlags()
        refreshHistoryList()
        
        // Set Truth
        _dtsLines.value = lines
        initialContentHash = lines.hashCode()
        _isDirty.value = false
    }

    suspend fun saveTable() = withContext(Dispatchers.IO) {
        val path = deviceRepository.dtsPath ?: throw IOException("DTS Path not set")
        val currentLines = _dtsLines.value
        fileDataSource.writeLines(path, currentLines)
        
        initialContentHash = currentLines.hashCode()
        _isDirty.value = false
    }

    /**
     * The Universal Update Method.
     * All Views (Text, GUI, Tree) call this to modify data.
     */
    fun updateContent(newLines: List<String>, description: String = "Edit", addToHistory: Boolean = true) {
        if (newLines == _dtsLines.value) return

        if (addToHistory) {
            snapshot(description)
        }
        
        _dtsLines.value = newLines
        _isDirty.value = (newLines.hashCode() != initialContentHash)
    }

    // --- GUI Mutators (Line Injection) ---

    data class ParameterUpdate(val binIndex: Int, val levelIndex: Int, val paramKey: String, val newValue: String)

    /**
     * Efficiently updates a specific parameter in the text without regenerating the whole file.
     * This keeps comments and formatting intact.
     */
    fun updateParameterInBin(binIndex: Int, levelIndex: Int, paramKey: String, newValue: String, historyDesc: String? = null) {
        val currentLines = ArrayList(_dtsLines.value)
        val range = findLevelLineRange(currentLines, binIndex, levelIndex) ?: return
        
        for (i in range.first..range.second) {
            val line = currentLines[i]
            if (line.contains(paramKey)) {
                // regex replace value inside <...>
                val updatedLine = line.replace(Regex("<[^>]+>"), "<$newValue>")
                currentLines[i] = updatedLine
                
                val desc = historyDesc ?: "Update $paramKey to $newValue (Bin $binIndex, Lvl $levelIndex)"
                updateContent(currentLines, desc)
                return
            }
        }
    }

    fun batchUpdateParameters(updates: List<ParameterUpdate>, description: String = "Batch Update") {
        val currentLines = ArrayList(_dtsLines.value)
        var dirty = false
        
        updates.forEach { update ->
             val range = findLevelLineRange(currentLines, update.binIndex, update.levelIndex)
             if (range != null) {
                 for (i in range.first..range.second) {
                     val line = currentLines[i]
                     if (line.contains(update.paramKey)) {
                         val updatedLine = line.replace(Regex("<[^>]+>"), "<${update.newValue}>")
                         if (currentLines[i] != updatedLine) {
                             currentLines[i] = updatedLine
                             dirty = true
                         }
                         break 
                     }
                 }
             }
        }
        
        if (dirty) {
            updateContent(currentLines, description)
        }
    }
    
    fun applySnapshot(content: String) {
        updateContent(content.split("\n"), "Applied external snapshot")
    }

    fun updateOpps(newOpps: List<Opp>) {
        val patterns = chipRepository.currentChip.value?.voltTablePattern ?: return
        // Generate new block
        val newBlock = StringBuilder()
        newBlock.append("\t\t").append(patterns).append(" {\n")
        newOpps.forEach { opp ->
            newBlock.append("\t\t\topp-${opp.frequency} {\n")
            newBlock.append("\t\t\t\topp-hz = /bits/ 64 <${opp.frequency}>;\n")
            newBlock.append("\t\t\t\topp-microvolt = <${opp.volt}>;\n")
            newBlock.append("\t\t\t};\n")
        }
        newBlock.append("\t\t};")
        
        // Replace in text
        val currentLines = _dtsLines.value
        val startIdx = currentLines.indexOfFirst { it.trim().contains(patterns) && it.trim().endsWith("{") }
        if (startIdx == -1) return
        
        // Find end
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
            // Remove old block
            val removalCount = endIdx - startIdx + 1
            for (k in 0 until removalCount) validLines.removeAt(startIdx)
            
            // Insert new block
            val newLines = newBlock.toString().split("\n")
            validLines.addAll(startIdx, newLines)
            
            updateContent(validLines, "Updated OPP Table")
        }
    }

    fun deleteLevel(binIndex: Int, levelIndex: Int) {
        val currentLines = ArrayList(_dtsLines.value)
        val range = findLevelLineRange(currentLines, binIndex, levelIndex) ?: return
        
        // Remove range. second is inclusive, so count = second - first + 1
        val removalCount = range.second - range.first + 1
        for (i in 0 until removalCount) {
             currentLines.removeAt(range.first)
        }
        updateContent(currentLines, "Deleted Level $levelIndex from Bin $binIndex")
    }
    
    fun importTable(lines: List<String>) {
        // Find qcom,gpu-pwrlevels
        val currentLines = _dtsLines.value
        val startIdx = currentLines.indexOfFirst { it.trim().startsWith("qcom,gpu-pwrlevels") && it.contains("{") }
        
        if (startIdx != -1) {
             // Find end
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

    // --- Helper Parsers ---

    private fun parseBinsFromLines(lines: List<String>): List<Bin> {
        val currentChip = chipRepository.currentChip.value ?: return emptyList()
        val strategy = chipRepository.getArchitecture(currentChip)
        val bins = ArrayList<Bin>()
        val mutableLines = ArrayList(lines) // Strategy consumes lines copy
        
        // Using existing strategy logic but adapted to not destructively consume
        // Since Architecture.decode consumes lines from the list to avoid reprocessing,
        // we pass a copy. 
        
        var i = -1
        while (++i < mutableLines.size) {
            val line = mutableLines[i].trim()
            if (strategy.isStartLine(line)) {
                try {
                    strategy.decode(mutableLines, bins, i)
                    i-- // Adjust for removed lines in strategy logic
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        return bins
    }

    private fun parseOppsFromLines(lines: List<String>): List<Opp> {
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

    /**
     * Locates the line range [start, end] for a specific Bin/Level in the text.
     * Essential for surgical edits.
     */
    private fun findLevelLineRange(lines: List<String>, binIndex: Int, levelIndex: Int): Pair<Int, Int>? {
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
                currentLevelIdx = -1 // Reset level count for new bin
                continue
            }
            
            if (!insideTargetBin) continue
            
            // Check Level Start
            // Standard pattern: qcom,gpu-pwrlevel@X {
            if (line.startsWith("qcom,gpu-pwrlevel@")) {
                currentLevelIdx++
                if (currentLevelIdx == levelIndex) {
                    // Found start. Now find matching brace end.
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

    // --- History Logic ---

    private fun snapshot(description: String) {
        val current = _dtsLines.value
        undoStack.push(HistoryItem(ArrayList(current), description))
        if (undoStack.size > MAX_HISTORY) undoStack.removeLast()
        redoStack.clear()
        updateHistoryFlags()
        refreshHistoryList()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _dtsLines.value
        val historyItem = undoStack.pop()
        
        // Push current text to Redo Stack
        // Description for redo? It's "Undo 'XYZ'" effectively.
        // We'll store the 'undo' action as a Redo item. 
        redoStack.push(HistoryItem(current, "Undo of '${historyItem.description}'"))
        
        updateContent(historyItem.lines, addToHistory = false)
        updateHistoryFlags()
        refreshHistoryList()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _dtsLines.value
        val historyItem = redoStack.pop()
        
        undoStack.push(HistoryItem(current, "Redo of '${historyItem.description}'"))
        
        updateContent(historyItem.lines, addToHistory = false)
        updateHistoryFlags()
        refreshHistoryList()
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}