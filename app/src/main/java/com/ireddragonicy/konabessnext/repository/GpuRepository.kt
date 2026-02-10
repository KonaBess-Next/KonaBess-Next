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
                    _structuralChange.tryEmit(Unit)
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
        _structuralChange.tryEmit(Unit)
    }
    
    /**
     * Adds a new level at top or bottom of a bin by duplicating an existing template level.
     * Handles node naming (qcom,gpu-pwrlevel@X) and reg property renumbering.
     */
    fun addLevel(binIndex: Int, toTop: Boolean) {
        val lines = ArrayList(_dtsLines.value)
        val binStartIdx = findBinStartIndex(lines, binIndex)
        if (binStartIdx == -1) return

        // Find existing level count and the template level
        val levelIndices = findAllLevelRanges(lines, binStartIdx)
        if (levelIndices.isEmpty()) return

        val templateRange = if (toTop) levelIndices.first() else levelIndices.last()
        val templateLines = lines.subList(templateRange.first, templateRange.last + 1).toList()

        if (toTop) {
            // Insert before first level, shift all level numbers up by 1
            val insertIdx = levelIndices.first().first

            // First shift all existing level numbers +1
            shiftLevelNumbers(lines, binStartIdx, 0, 1)
            
            // Create new level with index 0
            val newLevelLines = templateLines.map { line ->
                renameLevelInLine(line, templateRange, 0)
            }
            lines.addAll(insertIdx, newLevelLines)

            // Update header pointers (+1)
            offsetHeaderPointers(lines, binStartIdx, 1)
        } else {
            // Insert after last level
            val insertIdx = levelIndices.last().last + 1
            val newIndex = levelIndices.size

            val newLevelLines = templateLines.map { line ->
                renameLevelInLine(line, templateRange, newIndex)
            }
            lines.addAll(insertIdx, newLevelLines)
        }

        updateContent(lines, "Added Level ${if (toTop) "at Top" else "at Bottom"} of Bin $binIndex")
        _structuralChange.tryEmit(Unit)
    }

    /**
     * Duplicates a level within a bin, inserting the copy right after the original.
     * Shifts subsequent level numbers up by 1.
     */
    fun duplicateLevelAt(binIndex: Int, levelIndex: Int) {
        val lines = ArrayList(_dtsLines.value)
        val binStartIdx = findBinStartIndex(lines, binIndex)
        if (binStartIdx == -1) return

        val levelStartIdx = findLevelStartIndex(lines, binStartIdx, levelIndex)
        if (levelStartIdx == -1) return

        // Find level end
        var levelEndIdx = -1
        var braceCount = 0
        for (i in levelStartIdx until lines.size) {
            if (lines[i].contains("{")) braceCount++
            if (lines[i].contains("}")) {
                braceCount--
                if (braceCount == 0) { levelEndIdx = i; break }
            }
        }
        if (levelEndIdx == -1) return

        val templateLines = lines.subList(levelStartIdx, levelEndIdx + 1).toList()
        val newIndex = levelIndex + 1

        // Shift levels after levelIndex up by 1
        shiftLevelNumbers(lines, binStartIdx, newIndex, 1)

        // Create duplicate with newIndex
        val newLevelLines = templateLines.map { line ->
            var result = line
            val nodeMatch = Regex("qcom,gpu-pwrlevel@(\\d+)").find(result)
            if (nodeMatch != null) {
                result = result.replace("qcom,gpu-pwrlevel@${nodeMatch.groupValues[1]}", "qcom,gpu-pwrlevel@$newIndex")
            }
            if (result.trim().startsWith("reg")) {
                val regMatch = Regex("reg\\s*=\\s*<([^>]+)>").find(result)
                if (regMatch != null) {
                    val rawVal = regMatch.groupValues[1].trim()
                    val isHex = rawVal.startsWith("0x")
                    val newValStr = if (isHex) "0x" + Integer.toHexString(newIndex) else newIndex.toString()
                    result = result.replace("<$rawVal>", "<$newValStr>")
                }
            }
            result
        }

        // Insert after original
        lines.addAll(levelEndIdx + 1, newLevelLines)

        // Update header pointers (+1)
        offsetHeaderPointers(lines, binStartIdx, 1)

        updateContent(lines, "Duplicated Level $levelIndex in Bin $binIndex")
        _structuralChange.tryEmit(Unit)
    }

    /**
     * Finds all level node ranges (start..end line indices) within a bin scope.
     */
    private fun findAllLevelRanges(lines: List<String>, binStartIdx: Int): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var binBraceCount = 0
        var i = binStartIdx
        while (i < lines.size) {
            if (lines[i].contains("{")) binBraceCount++
            if (lines[i].contains("}")) {
                binBraceCount--
                if (binBraceCount == 0) break // End of bin
            }
            if (lines[i].trim().startsWith("qcom,gpu-pwrlevel@") && lines[i].contains("{")) {
                val start = i
                var levelBraceCount = 0
                for (j in start until lines.size) {
                    if (lines[j].contains("{")) levelBraceCount++
                    if (lines[j].contains("}")) {
                        levelBraceCount--
                        if (levelBraceCount == 0) {
                            ranges.add(start..j)
                            i = j
                            break
                        }
                    }
                }
            }
            i++
        }
        return ranges
    }

    /**
     * Shifts level numbers >= fromIndex by the given offset within a bin scope.
     */
    private fun shiftLevelNumbers(lines: MutableList<String>, binStartIdx: Int, fromIndex: Int, offset: Int) {
        var binBraceCount = 0
        for (i in binStartIdx until lines.size) {
            if (lines[i].contains("{")) binBraceCount++
            if (lines[i].contains("}")) {
                binBraceCount--
                if (binBraceCount == 0) break
            }

            val nodeMatch = Regex("qcom,gpu-pwrlevel@(\\d+)").find(lines[i])
            if (nodeMatch != null) {
                val currentId = nodeMatch.groupValues[1].toInt()
                if (currentId >= fromIndex) {
                    val newId = currentId + offset
                    lines[i] = lines[i].replace("qcom,gpu-pwrlevel@$currentId", "qcom,gpu-pwrlevel@$newId")
                }
            }

            if (lines[i].trim().startsWith("reg")) {
                val regMatch = Regex("reg\\s*=\\s*<([^>]+)>").find(lines[i])
                if (regMatch != null) {
                    val rawVal = regMatch.groupValues[1].trim()
                    val intVal = try {
                        if (rawVal.startsWith("0x")) rawVal.substring(2).toInt(16) else rawVal.toInt()
                    } catch (e: Exception) { -1 }
                    if (intVal >= fromIndex) {
                        val newVal = intVal + offset
                        val newValStr = if (rawVal.startsWith("0x")) "0x" + Integer.toHexString(newVal) else newVal.toString()
                        lines[i] = lines[i].replace("<$rawVal>", "<$newValStr>")
                    }
                }
            }
        }
    }

    /**
     * Offsets initial-pwrlevel and ca-target-pwrlevel header pointers by the given amount.
     */
    private fun offsetHeaderPointers(lines: MutableList<String>, binStartIdx: Int, offset: Int) {
        var binBraceCount = 0
        for (i in binStartIdx until lines.size) {
            if (lines[i].contains("{")) binBraceCount++
            if (lines[i].contains("}")) {
                binBraceCount--
                if (binBraceCount == 0) break
            }
            if (lines[i].contains("qcom,initial-pwrlevel") || lines[i].contains("qcom,ca-target-pwrlevel")) {
                val valMatch = Regex("<([^>]+)>").find(lines[i])
                if (valMatch != null) {
                    val rawVal = valMatch.groupValues[1].trim()
                    val intVal = try {
                        if (rawVal.startsWith("0x")) rawVal.substring(2).toInt(16) else rawVal.toInt()
                    } catch (e: Exception) { -1 }
                    if (intVal != -1) {
                        val newVal = (intVal + offset).coerceAtLeast(0)
                        val newValStr = if (rawVal.startsWith("0x")) "0x" + Integer.toHexString(newVal) else newVal.toString()
                        lines[i] = lines[i].replace("<$rawVal>", "<$newValStr>")
                    }
                }
            }
        }
    }

    /**
     * Renames level node references in a single line to use the target index.
     */
    private fun renameLevelInLine(line: String, originalRange: IntRange, targetIndex: Int): String {
        var result = line
        val nodeMatch = Regex("qcom,gpu-pwrlevel@(\\d+)").find(result)
        if (nodeMatch != null) {
            result = result.replace("qcom,gpu-pwrlevel@${nodeMatch.groupValues[1]}", "qcom,gpu-pwrlevel@$targetIndex")
        }
        if (result.trim().startsWith("reg")) {
            val regMatch = Regex("reg\\s*=\\s*<([^>]+)>").find(result)
            if (regMatch != null) {
                val rawVal = regMatch.groupValues[1].trim()
                val isHex = rawVal.startsWith("0x")
                val newValStr = if (isHex) "0x" + Integer.toHexString(targetIndex) else targetIndex.toString()
                result = result.replace("<$rawVal>", "<$newValStr>")
            }
        }
        return result
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
        _structuralChange.tryEmit(Unit)
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
            _structuralChange.tryEmit(Unit)
        }
    }
    
    /**
     * Sync in-memory tree edits back to text representation.
     * Called after user finishes editing a property in the tree view.
     */
    fun syncTreeToText(description: String = "Tree Edit") {
        val root = _parsedTree.value ?: return
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
