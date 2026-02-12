/* --- src/main/java/com/ireddragonicy/konabessnext/repository/GpuRepository.kt --- */
package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
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

    fun currentDtsPath(): String? = dtsFileRepository.currentDtsPath()

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
        .distinctUntilChanged()
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
        .distinctUntilChanged()
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

    fun updateContent(
        newLines: List<String>,
        description: String = "Edit",
        addToHistory: Boolean = true,
        reparseTree: Boolean = true
    ) {
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

        if (!reparseTree) {
            treeParseJob?.cancel()
            treeParseJob = null
            return
        }
        
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
                val checkCancelled = { ensureActive() }
                val tokens = com.ireddragonicy.konabessnext.utils.DtsLexer(fullText).tokenize(
                    com.ireddragonicy.konabessnext.utils.DtsLexer.LexOptions(
                        checkCancelled = checkCancelled
                    )
                )
                val tree = com.ireddragonicy.konabessnext.utils.DtsParser(tokens).parse(
                    com.ireddragonicy.konabessnext.utils.DtsParser.ParseOptions(
                        checkCancelled = checkCancelled
                    )
                )
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

        setPropertyPreservingFormat(levelNode, paramKey, newValue)

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
        if (newLines == _dtsLines.value) return
        updateContent(newLines, description, reparseTree = false)
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
                    setPropertyPreservingFormat(levelNode, update.paramKey, update.newValue)
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
        if (ensureParsedTree() == null) return
        commitTreeChanges(description)
    }

    fun applySnapshot(content: String) {
        updateContent(content.split("\n"), "Applied external snapshot")
    }

    fun updateOpps(newOpps: List<Opp>) {
        val root = ensureParsedTree() ?: return
        val pattern = chipRepository.currentChip.value?.voltTablePattern ?: return
        val tableNode = gpuDomainManager.findNodeByNameOrCompatible(root, pattern) ?: return
        val oppNodes = newOpps.map(::buildOppNode)
        replaceOppChildren(tableNode, oppNodes)
        commitTreeChanges("Updated OPP Table")
    }
    
    fun importTable(lines: List<String>) {
        val root = ensureParsedTree() ?: return
        val importedTree = parseExternalLinesToTree(lines) ?: return
        val importedBins = gpuDomainManager.findAllBinNodes(importedTree).map { it.deepCopy() }
        if (importedBins.isEmpty()) return

        val existingBins = collectGpuBinNodesWithParents(root)
        if (existingBins.isEmpty()) return

        val insertParent = existingBins.first().first
        val insertIndex = existingBins
            .filter { (parent, _) -> parent === insertParent }
            .mapNotNull { (parent, node) -> parent.children.indexOf(node).takeIf { it >= 0 } }
            .minOrNull() ?: insertParent.children.size

        existingBins.forEach { (parent, node) ->
            parent.children.remove(node)
            node.parent = null
        }

        importedBins.forEachIndexed { index, binNode ->
            insertParent.children.add(insertIndex + index, binNode)
            binNode.parent = insertParent
        }

        commitTreeChanges("Imported Frequency Table")
    }

    fun importVoltTable(lines: List<String>) {
        val root = ensureParsedTree() ?: return
        val pattern = chipRepository.currentChip.value?.voltTablePattern ?: return
        val tableNode = gpuDomainManager.findNodeByNameOrCompatible(root, pattern) ?: return
        val importedOppNodes = extractImportedOppNodes(lines, pattern)
        if (importedOppNodes.isEmpty()) return

        replaceOppChildren(tableNode, importedOppNodes)
        commitTreeChanges("Imported Voltage Table")
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
        val root = ensureParsedTree() ?: return ""
        val modelNode = gpuDomainManager.findNodeContainingProperty(root, "qcom,gpu-model")
        val model = modelNode?.getProperty("qcom,gpu-model")?.originalValue?.trim()
        if (!model.isNullOrEmpty()) {
            return model.removeSurrounding("\"")
        }

        val chipIdNode = gpuDomainManager.findNodeContainingProperty(root, "qcom,chipid")
        val chipIdRaw = chipIdNode?.getProperty("qcom,chipid")?.originalValue ?: return ""
        val chipId = parseSingleCellLong(chipIdRaw) ?: return ""
        return mapChipIdToGpuName(chipId)
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
        val normalized = normalizeGpuModelName(newName)
        if (normalized.isEmpty()) return

        val root = ensureParsedTree()
        if (root != null) {
            val modelNode = gpuDomainManager.findNodeContainingProperty(root, "qcom,gpu-model")
            if (modelNode != null) {
                modelNode.setProperty("qcom,gpu-model", "\"$normalized\"")
                commitTreeChanges("Renamed GPU to ${normalized.replace("\\\"", "\"")}")
                return
            }
        }

        // Fallback if the AST path cannot locate qcom,gpu-model.
        val currentLines = _dtsLines.value
        val patched = replaceGpuModelInRawLines(currentLines, normalized) ?: return
        updateContent(
            patched,
            description = "Renamed GPU to ${normalized.replace("\\\"", "\"")}",
            reparseTree = true
        )
        _structuralChange.tryEmit(Unit)
    }

    private fun normalizeGpuModelName(input: String): String {
        val collapsed = input
            .replace("\u0000", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        // Escape characters that can break DTS string literals.
        return collapsed
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun replaceGpuModelInRawLines(lines: List<String>, modelName: String): List<String>? {
        val regex = Regex("""^(\s*qcom,gpu-model\s*=\s*)(.*?)(\s*;.*)$""")
        val result = lines.toMutableList()
        var changed = false

        for (i in result.indices) {
            val match = regex.find(result[i]) ?: continue
            val updatedLine = "${match.groupValues[1]}\"$modelName\"${match.groupValues[3]}"
            if (updatedLine != result[i]) {
                result[i] = updatedLine
                changed = true
            }
            break
        }

        return if (changed) result else null
    }

    private fun buildOppNode(opp: Opp): DtsNode {
        val oppNode = DtsNode("opp-${opp.frequency}")
        oppNode.addProperty(DtsProperty("opp-hz", "/bits/ 64 <${opp.frequency}>"))
        oppNode.addProperty(DtsProperty("opp-microvolt", "<${opp.volt}>"))
        return oppNode
    }

    private fun replaceOppChildren(tableNode: DtsNode, newOppNodes: List<DtsNode>) {
        val preservedChildren = tableNode.children.filterNot(::isOppNode)
        tableNode.children.clear()
        preservedChildren.forEach(tableNode::addChild)
        newOppNodes.forEach(tableNode::addChild)
    }

    private fun isOppNode(node: DtsNode): Boolean {
        return node.name.startsWith("opp-") || node.getProperty("opp-hz") != null
    }

    private fun extractImportedOppNodes(lines: List<String>, tablePattern: String): List<DtsNode> {
        val importedTree = parseExternalLinesToTree(lines) ?: return emptyList()
        val sourceOppNodes = gpuDomainManager.findNodeByNameOrCompatible(importedTree, tablePattern)
            ?.children
            ?.filter(::isOppNode)
            .orEmpty()
            .ifEmpty { collectOppNodes(importedTree) }
        return sourceOppNodes.map { it.deepCopy() }
    }

    private fun collectOppNodes(root: DtsNode): List<DtsNode> {
        val results = ArrayList<DtsNode>()
        fun recurse(node: DtsNode) {
            if (isOppNode(node)) {
                results.add(node)
            }
            node.children.forEach(::recurse)
        }
        recurse(root)
        return results
    }

    private fun collectGpuBinNodesWithParents(root: DtsNode): List<Pair<DtsNode, DtsNode>> {
        val results = ArrayList<Pair<DtsNode, DtsNode>>()
        fun recurse(parent: DtsNode, node: DtsNode) {
            if (isGpuBinNode(node)) {
                results.add(parent to node)
                return
            }
            node.children.forEach { child -> recurse(node, child) }
        }
        root.children.forEach { child -> recurse(root, child) }
        return results
    }

    private fun isGpuBinNode(node: DtsNode): Boolean {
        val compatible = node.getProperty("compatible")?.originalValue
        val isCompatibleBin = compatible?.contains("qcom,gpu-pwrlevels") == true && !compatible.contains("bins")
        return isCompatibleBin || node.name.startsWith("qcom,gpu-pwrlevels")
    }

    private fun parseExternalLinesToTree(lines: List<String>): DtsNode? {
        val nonEmptyLines = lines.filter { it.isNotBlank() }
        if (nonEmptyLines.isEmpty()) return null

        val rawText = nonEmptyLines.joinToString("\n")
        val wrappedText = if (nonEmptyLines.any { it.contains("/dts-v1/") }) {
            rawText
        } else {
            buildString {
                append("/dts-v1/;\n\n/ {\n")
                nonEmptyLines.forEach { line ->
                    append('\t').append(line).append('\n')
                }
                append("};")
            }
        }

        return DtsTreeHelper.parse(wrappedText)
    }

    private fun parseSingleCellLong(rawValue: String): Long? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null

        val token = if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.isEmpty()) return null
            inner.split(Regex("\\s+")).firstOrNull() ?: return null
        } else {
            trimmed
        }

        return if (token.startsWith("0x", ignoreCase = true)) {
            token.substring(2).toLongOrNull(16)
        } else {
            token.toLongOrNull()
        }
    }

    private fun setPropertyPreservingFormat(node: DtsNode, propertyName: String, newValue: String) {
        val existingProp = node.getProperty(propertyName)
        if (existingProp == null) {
            node.setProperty(propertyName, newValue)
            return
        }

        if (existingProp.isHexArray) {
            existingProp.updateFromDisplayValue(newValue)
            return
        }

        val original = existingProp.originalValue.trim()
        if (original.startsWith("\"") && original.endsWith("\"")) {
            existingProp.originalValue = "\"${newValue.removeSurrounding("\"")}\""
            return
        }

        val open = original.indexOf('<')
        val close = original.indexOf('>', open + 1)
        if (open != -1 && close != -1) {
            val currentCellToken = original
                .substring(open + 1, close)
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
            val formattedCell = formatCellValueByCurrentStyle(newValue, currentCellToken)
            existingProp.originalValue = original.substring(0, open + 1) + formattedCell + original.substring(close)
            return
        }

        existingProp.originalValue = newValue
    }

    private fun formatCellValueByCurrentStyle(newValue: String, currentCellToken: String?): String {
        val normalized = newValue.trim()
        if (normalized.startsWith("0x", ignoreCase = true)) return normalized
        val numeric = normalized.toLongOrNull() ?: return normalized

        return if (currentCellToken?.startsWith("0x", ignoreCase = true) == true) {
            "0x${numeric.toString(16)}"
        } else {
            numeric.toString()
        }
    }
}
