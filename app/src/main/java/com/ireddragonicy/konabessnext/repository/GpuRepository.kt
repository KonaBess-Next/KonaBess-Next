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
    
    // 1. Parsed Bins (GUI Model) - Still derived from lines for now, or could be derived from tree?
    // User requested: "Keep _parsedTree as Mutable Model for editing"
    // Let's keep bins derived from lines for safety/legacy compatibility unless specific instruction to change everything.
    // Actually, if we update lines, this will trigger and re-parse bins from lines.
    // Ideally we should derive bins from the tree... but GpuDomainManager.parseBins logic takes lines. 
    // Let's stick to lines -> bins for now as it wasn't explicitly asked to refactor parseBins to take Node (it does internally parse AST though).
    // GpuDomainManager.parseBins calls DtsTreeHelper.parse(lines), so it parses AGAIN.
    // Optimization: parseBins could assume valid AST input. But let's leave that for later to avoid scope creep.
    val bins: StateFlow<List<Bin>> = _dtsLines
        .map { lines -> gpuDomainManager.parseBins(lines) }
        .flowOn(Dispatchers.Default) // Compute on background
        .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Lazily, emptyList())

    // 2. Parsed Tree (Visual Tree Model) - NOW MUTABLE SOURCE
    private val _parsedTree = MutableStateFlow<DtsNode?>(null)
    val parsedTree: StateFlow<DtsNode?> = _parsedTree.asStateFlow()

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
            
            // Populate Tree
            val fullText = lines.joinToString("\n")
            val root = DtsTreeHelper.parse(fullText)
            _parsedTree.value = root
            
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
     * With AST refactor: We usually generate new lines FROM the tree, then call this to update state flows.
     */
    fun updateContent(newLines: List<String>, description: String = "Edit", addToHistory: Boolean = true) {
        if (newLines == _dtsLines.value) return

        if (addToHistory) {
            historyManager.snapshot(_dtsLines.value, newLines, description)
        }
        
        _dtsLines.value = newLines
        _isDirty.value = (newLines.hashCode() != initialContentHash)
        
        // Also update the tree to match these new lines, in case the update came from raw text edit
        // NOTE: If the update came from AST manipulation, we are reparsing here. 
        // A better optimization would be to pass the tree if available, but for now strict consistency:
        // Text -> Tree.
        // However, if we just generated text FROM tree, we parse it again?
        // Let's accept this cost for correctness and simplicity unless we add overloads.
        val fullText = newLines.joinToString("\n")
        // We run this on Default dispatcher to avoid blocking if called from main?
        // But updateContent is usually "synchronous state update". 
        // Let's launch a coroutine to update the tree view if it's lagging?
        // No, DtsTreeEditor needs consistency.
        // We'll trust DtsTreeHelper.parse is reasonably fast or accepted overhead.
        try {
            _parsedTree.value = DtsTreeHelper.parse(fullText)
        } catch (e: Exception) {
            // Keep old tree or null?
        }
    }

    // --- GUI Mutators (AST Manipulation) ---

    data class ParameterUpdate(val binIndex: Int, val levelIndex: Int, val paramKey: String, val newValue: String)

    /**
     * Updates a specific parameter using AST manipulation and regenerates the file.
     */
    fun updateParameterInBin(binIndex: Int, levelIndex: Int, paramKey: String, newValue: String, historyDesc: String? = null) {
        val root = _parsedTree.value ?: return
        
        // 1. Find Nodes
        val binNode = gpuDomainManager.findBinNode(root, binIndex) ?: return
        val levelNode = gpuDomainManager.findLevelNode(binNode, levelIndex) ?: return
        
        // 2. Modify Property
        levelNode.setProperty(paramKey, newValue)
        
        // 3. Regenerate
        val newText = DtsTreeHelper.generate(root)
        val newLines = newText.split("\n")
        
        val desc = historyDesc ?: "Update $paramKey to $newValue (Bin $binIndex, Lvl $levelIndex)"
        
        // Update valid state (including history)
        // Note: updateContent will re-parse the tree from text. 
        // Ideally we would set _parsedTree directly to avoid re-parse, 
        // but verify consistency first.
        updateContent(newLines, desc)
    }

    fun batchUpdateParameters(updates: List<ParameterUpdate>, description: String = "Batch Update") {
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
        val root = _parsedTree.value ?: return
        val binNode = gpuDomainManager.findBinNode(root, binIndex) ?: return
        val levelNode = gpuDomainManager.findLevelNode(binNode, levelIndex) ?: return
        
        binNode.children.remove(levelNode)
        
        val newText = DtsTreeHelper.generate(root)
        updateContent(newText.split("\n"), "Deleted Level $levelIndex from Bin $binIndex")
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

    fun getGpuModelName(): String {
        val lines = _dtsLines.value
        
        // Try 1: Look for qcom,gpu-model property
        val modelPattern = java.util.regex.Pattern.compile("""qcom,gpu-model\s*=\s*"([^"]+)";""")
        for (line in lines) {
            val matcher = modelPattern.matcher(line.trim())
            if (matcher.find()) {
                return matcher.group(1) ?: ""
            }
        }
        
        // Try 2: Extract from qcom,chipid and map to Adreno GPU name
        val chipidPattern = java.util.regex.Pattern.compile("""qcom,chipid\s*=\s*<(0x[0-9a-fA-F]+)>;""")
        for (line in lines) {
            val matcher = chipidPattern.matcher(line.trim())
            if (matcher.find()) {
                val chipidHex = matcher.group(1) ?: ""
                val chipid = chipidHex.removePrefix("0x").toLongOrNull(16) ?: 0L
                return mapChipIdToGpuName(chipid)
            }
        }
        
        // Try 3: Extract from label property in kgsl node
        val labelPattern = java.util.regex.Pattern.compile("""label\s*=\s*"([^"]+)";""")
        for (line in lines) {
            val matcher = labelPattern.matcher(line.trim())
            if (matcher.find()) {
                val label = matcher.group(1) ?: ""
                if (label.contains("kgsl")) {
                    return label.uppercase()
                }
            }
        }
        
        return ""
    }
    
    // Map Qualcomm GPU chip IDs to human-readable Adreno names
    private fun mapChipIdToGpuName(chipid: Long): String {
        // Format: Major.Minor.Patch encoded in hex
        // e.g., 0x6040001 = Adreno 640, 0x6080001 = Adreno 680
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
            major == 5 && minor == 12 -> "Adreno 512"
            major == 5 && minor == 40 -> "Adreno 540"
            major == 6 && minor == 19 -> "Adreno 619"
            major == 6 && minor == 18 -> "Adreno 618"
            major == 6 && minor == 12 -> "Adreno 612"
            else -> "Adreno ${major}${minor}0"
        }
    }

    fun updateGpuModelName(newName: String) {
        val currentLines = ArrayList(_dtsLines.value)
        val pattern = java.util.regex.Pattern.compile("""(qcom,gpu-model\s*=\s*")([^"]+)(";.*)""")
        
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
