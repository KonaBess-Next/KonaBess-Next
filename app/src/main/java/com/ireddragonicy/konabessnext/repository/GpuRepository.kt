package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.utils.DtsHelper
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
import java.io.IOException
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

    // --- Derived States (Auto-Synced) ---
    
    // 1. Parsed Bins (GUI Model)
    val bins: StateFlow<List<Bin>> = _dtsLines
        .map { lines -> gpuDomainManager.parseBins(lines) }
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
        .map { lines -> gpuDomainManager.parseOpps(lines) }
        .flowOn(Dispatchers.Default)
        .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Lazily, emptyList())

    // --- History Management (Unified) ---
    // Delegate to HistoryManager
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
            
            // Reset State
            historyManager.clear()
            
            // Set Truth
            _dtsLines.value = lines
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

    /**
     * The Universal Update Method.
     * All Views (Text, GUI, Tree) call this to modify data.
     */
    fun updateContent(newLines: List<String>, description: String = "Edit", addToHistory: Boolean = true) {
        if (newLines == _dtsLines.value) return

        if (addToHistory) {
            historyManager.snapshot(_dtsLines.value, newLines, description)
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
        val range = gpuDomainManager.findLevelLineRange(currentLines, binIndex, levelIndex) ?: return
        
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
             val range = gpuDomainManager.findLevelLineRange(currentLines, update.binIndex, update.levelIndex)
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
        val newBlock = gpuDomainManager.generateOppTableBlock(newOpps)
        
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
            val newLines = newBlock.split("\n")
            validLines.addAll(startIdx, newLines)
            
            updateContent(validLines, "Updated OPP Table")
        }
    }

    fun deleteLevel(binIndex: Int, levelIndex: Int) {
        val currentLines = ArrayList(_dtsLines.value)
        val range = gpuDomainManager.findLevelLineRange(currentLines, binIndex, levelIndex) ?: return
        
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

    // --- History Logic ---

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
}
