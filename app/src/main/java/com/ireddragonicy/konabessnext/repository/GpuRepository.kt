/* --- src/main/java/com/ireddragonicy/konabessnext/repository/GpuRepository.kt --- */
package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import com.ireddragonicy.konabessnext.utils.DtsEditorDebug

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

    // Manual trigger to force-refresh bins/opps immediately (bypasses debounce).
    // Used after structural changes (add/remove/duplicate level) so UI updates instantly.
    private val _structuralChange = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Shared CoroutineScope for all background work in this repository.
    // FIX: Previously, updateContent() created new CoroutineScope(Dispatchers.Default) per call = LEAKED.
    // bins/opps also used CoroutineScope(Dispatchers.Main) = LEAKED.
    // Now everything uses this single scope.
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- Derived States ---
    // FIX: Use shared repoScope instead of CoroutineScope(Dispatchers.Main) which was LEAKED
    // PERF: debounce(1000) prevents re-parsing bins on every keystroke
    // _structuralChange merge ensures instant refresh after add/remove/duplicate
    @OptIn(FlowPreview::class)
    val bins: StateFlow<List<Bin>> = merge(
        _dtsLines.debounce(1000),
        _structuralChange.map { _dtsLines.value }
    )
        .map { lines ->
            DtsEditorDebug.logFlowTriggered("bins", lines.size)
            gpuDomainManager.parseBins(lines)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(repoScope, SharingStarted.Lazily, emptyList())

    private val _parsedTree = MutableStateFlow<DtsNode?>(null)
    val parsedTree: StateFlow<DtsNode?> = _parsedTree.asStateFlow()

    // PERF: debounce(1000) prevents re-parsing opps on every keystroke
    // _structuralChange merge ensures instant refresh after structural edits
    @OptIn(FlowPreview::class)
    val opps: StateFlow<List<Opp>> = merge(
        _dtsLines.debounce(1000),
        _structuralChange.map { _dtsLines.value }
    )
        .map { lines ->
            DtsEditorDebug.logFlowTriggered("opps", lines.size)
            gpuDomainManager.parseOpps(lines)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(repoScope, SharingStarted.Lazily, emptyList())

    val canUndo = historyManager.canUndo
    val canRedo = historyManager.canRedo
    val history = historyManager.history

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()
    
    private var initialContentHash: Int = 0

    // FIX: Single Job for tree parsing — cancels previous before starting new one.
    // Previously used `CoroutineScope(Dispatchers.Default).launch` which LEAKED scopes.
    private var treeParseJob: Job? = null

    // --- Core Operations ---

    suspend fun loadTable() = withContext(Dispatchers.IO) {
        try {
            val lines = dtsFileRepository.loadDtsLines()
            historyManager.clear()
            _dtsLines.value = lines
            
            // Force immediate re-parse of bins/opps (bypasses debounce(1000))
            // Without this, switching DTS shows stale bins for 1 second
            _structuralChange.tryEmit(Unit)
            
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
        if (newLines == _dtsLines.value) {
            DtsEditorDebug.logUpdateContentSkipped("newLines == oldLines")
            return
        }

        DtsEditorDebug.logUpdateContent(newLines.size, description, addToHistory)

        if (addToHistory) {
            DtsEditorDebug.logHistorySnapshot(_dtsLines.value.size, newLines.size)
            historyManager.snapshot(_dtsLines.value, newLines, description)
        }
        
        _dtsLines.value = newLines
        _isDirty.value = (newLines.hashCode() != initialContentHash)
        
        // FIX: Cancel previous tree parse job before starting new one
        // Previously: CoroutineScope(Dispatchers.Default).launch { ... } ← LEAKED!
        treeParseJob?.let {
            it.cancel()
            DtsEditorDebug.logTreeParseJobCancelled()
        }
        DtsEditorDebug.logTreeParseJobStart()
        treeParseJob = repoScope.launch {
            try {
                delay(1500) // PERF: debounce tree parse — only runs after 1.5s of no edits
                val startTime = System.nanoTime()
                val fullText = newLines.joinToString("\n")
                val tree = DtsTreeHelper.parse(fullText)
                val durationMs = (System.nanoTime() - startTime) / 1_000_000
                _parsedTree.value = tree
                DtsEditorDebug.logTreeParseJobComplete(durationMs, countNodes(tree))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Don't swallow cancellation
            } catch (ignored: Exception) {
                DtsEditorDebug.logLeakWarning("GpuRepository", "treeParse failed: ${ignored.message}")
            }
        }
    }
    
    private fun countNodes(node: DtsNode?): Int {
        if (node == null) return 0
        return 1 + node.children.sumOf { countNodes(it) }
    }

    // --- GUI Mutators ---

    data class ParameterUpdate(val binIndex: Int, val levelIndex: Int, val paramKey: String, val newValue: String)

    /**
     * Updates a specific parameter using AST-only mutation.
     */
    fun updateParameterInBin(binIndex: Int, levelIndex: Int, paramKey: String, newValue: String, historyDesc: String? = null) {
        val root = ensureParsedTree() ?: return
        val binNode = gpuDomainManager.findBinNode(root, binIndex) ?: return
        val levelNode = gpuDomainManager.findLevelNode(binNode, levelIndex) ?: return

        levelNode.setProperty(paramKey, newValue)

        val desc = historyDesc ?: "Update $paramKey to $newValue (Bin $binIndex, Lvl $levelIndex)"
        commitTreeChanges(desc)
    }
    
    fun deleteLevel(binIndex: Int, levelIndex: Int) {
        val root = ensureParsedTree() ?: return
        val binNode = gpuDomainManager.findBinNode(root, binIndex) ?: return
        val levelNode = gpuDomainManager.findLevelNode(binNode, levelIndex) ?: return

        val removed = binNode.children.remove(levelNode)
        if (!removed) return
        levelNode.parent = null

        renumberLevelNodes(binNode)
        shiftHeaderPointersForDelete(binNode, levelIndex)

        commitTreeChanges("Deleted Level $levelIndex from Bin $binIndex")
    }
    
    fun addLevel(binIndex: Int, toTop: Boolean) {
        val root = ensureParsedTree() ?: return
        val binNode = gpuDomainManager.findBinNode(root, binIndex) ?: return
        val levelNodes = getLevelNodes(binNode)
        if (levelNodes.isEmpty()) return

        val templateNode = if (toTop) levelNodes.first() else levelNodes.last()
        val copiedNode = templateNode.deepCopy()

        val insertionChildIndex = if (toTop) {
            val firstLevelChildIndex = binNode.children.indexOf(levelNodes.first())
            if (firstLevelChildIndex == -1) 0 else firstLevelChildIndex
        } else {
            val lastLevelChildIndex = binNode.children.indexOf(levelNodes.last())
            if (lastLevelChildIndex == -1) binNode.children.size else lastLevelChildIndex + 1
        }

        binNode.children.add(insertionChildIndex, copiedNode)
        copiedNode.parent = binNode

        val insertedLevelIndex = if (toTop) 0 else levelNodes.size
        renumberLevelNodes(binNode)
        shiftHeaderPointersForInsert(binNode, insertedLevelIndex)

        commitTreeChanges("Added Level ${if (toTop) "at Top" else "at Bottom"} of Bin $binIndex")
    }

    fun duplicateLevelAt(binIndex: Int, levelIndex: Int) {
        val root = ensureParsedTree() ?: return
        val binNode = gpuDomainManager.findBinNode(root, binIndex) ?: return
        val levelNode = gpuDomainManager.findLevelNode(binNode, levelIndex) ?: return
        val insertionIndex = binNode.children.indexOf(levelNode)
        if (insertionIndex == -1) return

        val copiedNode = levelNode.deepCopy()
        binNode.children.add(insertionIndex + 1, copiedNode)
        copiedNode.parent = binNode

        renumberLevelNodes(binNode)
        shiftHeaderPointersForInsert(binNode, levelIndex + 1)

        commitTreeChanges("Duplicated Level $levelIndex in Bin $binIndex")
    }

    private fun ensureParsedTree(): DtsNode? {
        _parsedTree.value?.let { return it }
        val currentLines = _dtsLines.value
        if (currentLines.isEmpty()) return null

        val parsedRoot = DtsTreeHelper.parse(currentLines.joinToString("\n"))
        _parsedTree.value = parsedRoot
        return parsedRoot
    }

    private fun commitTreeChanges(description: String) {
        val root = _parsedTree.value ?: return
        // NOTE: DtsNode currently does not preserve comments; AST round-trip favors safety/validity.
        val newLines = DtsTreeHelper.generate(root).split("\n")
        updateContent(newLines, description)
        _structuralChange.tryEmit(Unit)
    }

    private fun getLevelNodes(binNode: DtsNode): List<DtsNode> {
        return binNode.children.filter { child -> child.name.startsWith(LEVEL_NODE_PREFIX) }
    }

    private fun renumberLevelNodes(binNode: DtsNode) {
        val levelNodes = getLevelNodes(binNode)
        levelNodes.forEachIndexed { index, levelNode ->
            levelNode.name = "$LEVEL_NODE_PREFIX$index"

            val regProp = levelNode.getProperty("reg")
            if (regProp == null) {
                levelNode.setProperty("reg", "<0x${index.toString(16)}>")
            } else if (regProp.isHexArray) {
                levelNode.setProperty("reg", index.toString())
            } else {
                levelNode.setProperty("reg", "<0x${index.toString(16)}>")
            }
        }
    }

    private fun shiftHeaderPointersForDelete(binNode: DtsNode, deletedIndex: Int) {
        val maxLevelIndex = (getLevelNodes(binNode).size - 1).coerceAtLeast(0)
        for (property in binNode.properties) {
            if (!isPowerLevelPointerProperty(property.name)) continue
            val currentIndex = parseSingleCellIndex(property.originalValue) ?: continue
            if (currentIndex < deletedIndex) continue

            val shiftedIndex = (currentIndex - 1).coerceAtLeast(0).coerceAtMost(maxLevelIndex)
            if (shiftedIndex != currentIndex) {
                binNode.setProperty(property.name, shiftedIndex.toString())
            }
        }
    }

    private fun shiftHeaderPointersForInsert(binNode: DtsNode, insertedIndex: Int) {
        val maxLevelIndex = (getLevelNodes(binNode).size - 1).coerceAtLeast(0)
        for (property in binNode.properties) {
            if (!isPowerLevelPointerProperty(property.name)) continue
            val currentIndex = parseSingleCellIndex(property.originalValue) ?: continue
            if (currentIndex < insertedIndex) continue

            val shiftedIndex = (currentIndex + 1).coerceAtMost(maxLevelIndex)
            if (shiftedIndex != currentIndex) {
                binNode.setProperty(property.name, shiftedIndex.toString())
            }
        }
    }

    private fun isPowerLevelPointerProperty(propertyName: String): Boolean {
        return propertyName.contains("pwrlevel") && !propertyName.contains("pwrlevels")
    }

    private fun parseSingleCellIndex(rawValue: String): Int? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null

        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) {
            return trimmed.toIntOrNull()
        }

        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty() || inner.contains(" ")) return null

        return if (inner.startsWith("0x", ignoreCase = true)) {
            inner.substring(2).toIntOrNull(16)
        } else {
            inner.toIntOrNull()
        }
    }

    private companion object {
        const val LEVEL_NODE_PREFIX = "qcom,gpu-pwrlevel@"
    }

    // --- OPP and Other Updates ---
    
    fun updateOppVoltage(frequency: Long, newVolt: Long, historyDesc: String? = null) {
        val root = ensureParsedTree() ?: return
        val success = gpuDomainManager.updateOppVoltage(root, frequency, newVolt)
        if (!success) return
        commitTreeChanges(historyDesc ?: "Updated OPP voltage")
    }

    fun batchUpdateParameters(updates: List<ParameterUpdate>, description: String = "Batch Update") {
        val root = ensureParsedTree() ?: return
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
            commitTreeChanges(description)
        }
    }
    
    /**
     * Sync in-memory tree edits back to text representation.
     * Called after user finishes editing a property in the tree view.
     */
    fun syncTreeToText(description: String = "Tree Edit") {
        val root = ensureParsedTree() ?: return
        val newText = DtsTreeHelper.generate(root)
        val newLines = newText.split("\n")
        if (newLines != _dtsLines.value) {
            updateContent(newLines, description)
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
        val validLines = ArrayList(currentLines)

        // Find ALL qcom,gpu-pwrlevels blocks (there can be multiple bins)
        val blocksToRemove = ArrayList<IntRange>()
        var i = 0
        while (i < validLines.size) {
            val trimmed = validLines[i].trim()
            if (trimmed.startsWith("qcom,gpu-pwrlevels") && trimmed.contains("{")) {
                val blockStart = i
                var braceCount = 0
                var blockEnd = -1
                for (j in i until validLines.size) {
                    if (validLines[j].contains("{")) braceCount++
                    if (validLines[j].contains("}")) braceCount--
                    if (braceCount == 0) {
                        blockEnd = j
                        break
                    }
                }
                if (blockEnd != -1) {
                    blocksToRemove.add(blockStart..blockEnd)
                    i = blockEnd + 1
                } else {
                    i++
                }
            } else {
                i++
            }
        }

        if (blocksToRemove.isNotEmpty()) {
            val insertIdx = blocksToRemove.first().first

            // Remove blocks from bottom to top to preserve indices
            for (range in blocksToRemove.reversed()) {
                for (k in range.last downTo range.first) {
                    validLines.removeAt(k)
                }
            }

            validLines.addAll(insertIdx, lines)
            updateContent(validLines, "Imported Frequency Table")
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
            _structuralChange.tryEmit(Unit)
        }
    }

    fun redo() {
        val currentState = _dtsLines.value
        val revertedState = historyManager.redo(currentState)
        if (revertedState != null) {
            updateContent(revertedState, addToHistory = false)
            _structuralChange.tryEmit(Unit)
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
