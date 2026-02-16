package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.model.AppError
import com.ireddragonicy.konabessnext.core.model.DomainResult
import com.ireddragonicy.konabessnext.model.display.DisplayPanel
import com.ireddragonicy.konabessnext.model.display.DisplayTiming
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsEditorDebug
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single Source of Truth for DTBO display overclock data.
 *
 * This repository is the display-overclock counterpart of [GpuRepository].
 * It owns the raw DTS lines for the DTBO image, derives display panel models,
 * and provides AST-based mutation methods for changing display timing parameters.
 *
 * Key design:
 * - **Text lines as SSOT**: `_dtsLines: StateFlow<List<String>>` holds the truth.
 * - **Derived panels flow**: Debounced parsing of display panels from lines.
 * - **AST mutations**: Property updates go through the parsed tree and round-trip back.
 * - **Independent from GPU**: No coupling to [GpuRepository] or GPU models.
 */
@Singleton
class DisplayRepository @Inject constructor(
    private val dtsFileRepository: DtsFileRepository,
    private val displayDomainManager: DisplayDomainManager,
    private val userMessageManager: com.ireddragonicy.konabessnext.utils.UserMessageManager
) : DtsDataProvider {
    // Own HistoryManager — not shared with GpuRepository
    private val historyManager = HistoryManager()

    // --- Selected Panel ---
    private val _selectedPanelIndex = MutableStateFlow(0)
    val selectedPanelIndex: StateFlow<Int> = _selectedPanelIndex.asStateFlow()

    fun selectPanel(index: Int) {
        _selectedPanelIndex.value = index
        // Reset timing selection when switching panels
        _selectedTimingIndex.value = 0
    }

    // --- Selected Timing ---
    private val _selectedTimingIndex = MutableStateFlow(0)
    val selectedTimingIndex: StateFlow<Int> = _selectedTimingIndex.asStateFlow()

    fun selectTiming(index: Int) {
        _selectedTimingIndex.value = index
    }

    // --- Single Source of Truth: The Text Lines ---
    private val _dtsLines = MutableStateFlow<List<String>>(emptyList())
    override val dtsLines: StateFlow<List<String>> = _dtsLines.asStateFlow()

    override val dtsContent: Flow<String> = _dtsLines.map { it.joinToString("\n") }.flowOn(Dispatchers.Default)

    // Manual trigger to force-refresh panels immediately (bypasses debounce).
    private val _structuralChange = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- Derived States ---

    @OptIn(FlowPreview::class)
    val panels: StateFlow<List<DisplayPanel>> = merge(
        _dtsLines.debounce(1000),
        _structuralChange.map { _dtsLines.value }
    )
        .distinctUntilChanged()
        .map { lines ->
            DtsEditorDebug.logFlowTriggered("display-panels", lines.size)
            displayDomainManager.parsePanelsFromLines(lines)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(repoScope, SharingStarted.Lazily, emptyList())

    private val _parsedTree = MutableStateFlow<DtsNode?>(null)
    override val parsedTree: StateFlow<DtsNode?> = _parsedTree.asStateFlow()

    override val canUndo: StateFlow<Boolean> = historyManager.canUndo
    override val canRedo: StateFlow<Boolean> = historyManager.canRedo
    val history = historyManager.history

    private val _isDirty = MutableStateFlow(false)
    override val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private var initialContentHash: Int = 0
    private var treeParseJob: Job? = null

    // --- Core Operations ---

    override suspend fun loadTable(): DomainResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val lines = dtsFileRepository.loadDtsLines()
            historyManager.clear()
            // Clear stale tree BEFORE emitting new lines.
            // Without this, getDisplaySnapshot() sees the old partition's tree
            // (e.g. vendor_boot GPU tree) and fails to find display panels.
            _parsedTree.value = null
            _dtsLines.value = lines

            _structuralChange.tryEmit(Unit)

            // Parse the AST
            val fullText = lines.joinToString("\n")
            try {
                _parsedTree.value = DtsTreeHelper.parse(fullText)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            initialContentHash = lines.hashCode()
            _isDirty.value = false
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            DomainResult.Failure(
                AppError.UnknownError(
                    e.localizedMessage ?: "Unknown error loading DTBO DTS", e
                )
            )
        }
    }

    override suspend fun saveTable(): DomainResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentLines = _dtsLines.value
            dtsFileRepository.saveDtsLines(currentLines)
            initialContentHash = currentLines.hashCode()
            _isDirty.value = false
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            DomainResult.Failure(
                AppError.IoError(
                    e.localizedMessage ?: "Unknown error saving DTBO DTS", e
                )
            )
        }
    }

    override fun updateContent(
        newLines: List<String>,
        description: String,
        addToHistory: Boolean,
        reparseTree: Boolean
    ) {
        if (newLines == _dtsLines.value) {
            return
        }

        if (addToHistory) {
            historyManager.snapshot(_dtsLines.value, newLines, description)
        }

        _dtsLines.value = newLines
        _isDirty.value = (newLines.hashCode() != initialContentHash)

        if (!reparseTree) {
            treeParseJob?.cancel()
            treeParseJob = null
            return
        }

        treeParseJob?.cancel()
        treeParseJob = repoScope.launch {
            try {
                delay(1500)
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
                _parsedTree.value = tree
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) { }
        }
    }

    // --- Display Timing Mutations ---

    /**
     * Updates a property on a specific timing node.
     *
     * @param panelNodeName The DTS node name of the panel (e.g. `qcom,mdss_dsi_o10u_36_02_0b_dsc_vid`)
     * @param timingIndex The timing index (typically 0)
     * @param propertyName The property to update (e.g. `qcom,mdss-dsi-panel-framerate`)
     * @param newValue The new value (decimal or hex string)
     * @param historyDesc Optional history description
     * @return true if update succeeded
     */
    fun updateTimingProperty(
        panelNodeName: String,
        timingIndex: Int,
        propertyName: String,
        newValue: String,
        fragmentIndex: Int = -1,
        historyDesc: String? = null
    ): Boolean {
        val root = ensureParsedTree() ?: return false
        val timingNode = displayDomainManager.findTimingNode(root, panelNodeName, timingIndex, fragmentIndex)
            ?: return false

        val success = displayDomainManager.updateTimingProperty(timingNode, propertyName, newValue)
        if (!success) return false

        commitTreeChanges(historyDesc ?: "Updated $propertyName to $newValue")
        return true
    }

    /**
     * Updates a panel-level property (not inside timing).
     */
    fun updatePanelProperty(
        panelNodeName: String,
        propertyName: String,
        newValue: String,
        fragmentIndex: Int = -1,
        historyDesc: String? = null
    ): Boolean {
        val root = ensureParsedTree() ?: return false
        val panelNode = displayDomainManager.findPanelNodeInTree(root, panelNodeName, fragmentIndex) ?: return false

        val success = displayDomainManager.updatePanelProperty(panelNode, propertyName, newValue)
        if (!success) return false

        commitTreeChanges(historyDesc ?: "Updated panel $propertyName")
        return true
    }

    /**
     * Convenience: update framerate on the selected panel's first timing.
     */
    fun updatePanelFramerate(newFps: Int): Boolean {
        if (newFps <= 0) return false
        val root = ensureParsedTree() ?: return false
        val panel = getSelectedPanel(root) ?: return false
        val timingIdx = _selectedTimingIndex.value.coerceIn(0, (panel.timings.size - 1).coerceAtLeast(0))
        val timingNode = displayDomainManager.findTimingNode(root, panel.nodeName, timingIdx, panel.fragmentIndex)
            ?: return false

        displayDomainManager.updateTimingProperty(
            timingNode, "qcom,mdss-dsi-panel-framerate", newFps.toString()
        )

        DtsEditorDebug.logDisplayUpdate("updatePanelFramerate", panel.nodeName, timingIdx, true, "newFps=$newFps")
        commitTreeChanges("Set framerate to ${newFps}Hz on fragment@${panel.fragmentIndex}")
        return true
    }

    /**
     * Updates the panel clockrate for the selected panel's first timing.
     */
    fun updatePanelClockRate(newClock: Long): Boolean {
        if (newClock < 0) return false
        val root = ensureParsedTree() ?: return false
        val panel = getSelectedPanel(root) ?: return false
        val timingIdx = _selectedTimingIndex.value.coerceIn(0, (panel.timings.size - 1).coerceAtLeast(0))
        val timingNode = displayDomainManager.findTimingNode(root, panel.nodeName, timingIdx, panel.fragmentIndex) ?: return false

        displayDomainManager.updateTimingProperty(
            timingNode, "qcom,mdss-dsi-panel-clockrate", newClock.toString()
        )

        DtsEditorDebug.logDisplayUpdate("updatePanelClockRate", panel.nodeName, timingIdx, true, "newClock=$newClock")
        commitTreeChanges("Set display clockrate to $newClock")
        return true
    }

    /**
     * Updates the DFPS list on the selected panel.
     * Automatically adds/removes the target FPS from the supported list.
     */
    fun updateDfpsList(fpsList: List<Int>): Boolean {
        val root = ensureParsedTree() ?: return false
        val panel = getSelectedPanel(root) ?: return false
        val panelNode = displayDomainManager.findPanelNodeInTree(root, panel.nodeName, panel.fragmentIndex) ?: return false

        displayDomainManager.updateDfpsList(panelNode, fpsList)
        DtsEditorDebug.logDisplayUpdate("updateDfpsList", panel.nodeName, -1, true, "list=$fpsList")
        commitTreeChanges("Updated DFPS list to ${fpsList.joinToString(", ")}Hz")
        return true
    }

    // --- Snapshot for UI ---

    /**
     * Returns a snapshot of the selected panel with timings for UI display.
     */
    fun getDisplaySnapshot(): DisplaySnapshot? {
        val root = _parsedTree.value ?: return getDisplaySnapshotFromLines()
        val panel = getSelectedPanel(root) ?: return null
        val timingIdx = _selectedTimingIndex.value.coerceIn(0, (panel.timings.size - 1).coerceAtLeast(0))
        val timing = panel.timings.getOrNull(timingIdx) ?: return null

        return DisplaySnapshot(
            panelNodeName = panel.nodeName,
            panelName = panel.panelName,
            panelType = panel.panelType,
            fragmentIndex = panel.fragmentIndex,
            dfpsList = panel.dfpsList,
            timing = timing,
            timingIndex = timingIdx,
            timingCount = panel.timings.size
        )
    }

    private fun getDisplaySnapshotFromLines(): DisplaySnapshot? {
        val panels = displayDomainManager.parsePanelsFromLines(_dtsLines.value)
        val panelsWithTimings = panels.filter { it.timings.isNotEmpty() }
        val idx = _selectedPanelIndex.value.coerceIn(0, (panelsWithTimings.size - 1).coerceAtLeast(0))
        val panel = panelsWithTimings.getOrNull(idx) ?: return null
        val timingIdx = _selectedTimingIndex.value.coerceIn(0, (panel.timings.size - 1).coerceAtLeast(0))
        val timing = panel.timings.getOrNull(timingIdx) ?: return null

        return DisplaySnapshot(
            panelNodeName = panel.nodeName,
            panelName = panel.panelName,
            panelType = panel.panelType,
            fragmentIndex = panel.fragmentIndex,
            dfpsList = panel.dfpsList,
            timing = timing,
            timingIndex = timingIdx,
            timingCount = panel.timings.size
        )
    }

    /**
     * Returns the selected panel (by index) from panels with timings.
     */
    private fun getSelectedPanel(root: DtsNode): DisplayPanel? {
        val allPanels = displayDomainManager.findAllPanels(root)
        val panelsWithTimings = allPanels.filter { it.timings.isNotEmpty() }
        val idx = _selectedPanelIndex.value.coerceIn(0, (panelsWithTimings.size - 1).coerceAtLeast(0))
        val result = panelsWithTimings.getOrNull(idx)
        return result
    }

    /**
     * Lightweight snapshot for the UI layer.
     */
    data class DisplaySnapshot(
        val panelNodeName: String,
        val panelName: String,
        val panelType: String,
        val fragmentIndex: Int,
        val dfpsList: List<Int>,
        val timing: DisplayTiming,
        val timingIndex: Int = 0,
        val timingCount: Int = 1
    )

    // --- Undo / Redo ---

    override fun undo() {
        val currentState = _dtsLines.value
        val revertedState = historyManager.undo(currentState)
        if (revertedState != null) {
            updateContent(revertedState, description = "Undo", addToHistory = false, reparseTree = true)
            _structuralChange.tryEmit(Unit)
        }
    }

    override fun redo() {
        val currentState = _dtsLines.value
        val revertedState = historyManager.redo(currentState)
        if (revertedState != null) {
            updateContent(revertedState, description = "Redo", addToHistory = false, reparseTree = true)
            _structuralChange.tryEmit(Unit)
        }
    }

    fun applySnapshot(content: String) {
        updateContent(content.split("\n"), "Applied external snapshot", addToHistory = true, reparseTree = true)
    }

    override fun currentDtsPath(): String? = dtsFileRepository.currentDtsPath()

    override fun syncTreeToText(description: String) {
        if (ensureParsedTree() == null) return
        commitTreeChanges(description)
    }

    // --- Internal ---

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
        val newText = DtsTreeHelper.generate(root)
        val newLines = newText.split("\n")
        val oldLines = _dtsLines.value
        val isSame = newLines == oldLines
        if (isSame) return

        updateContent(newLines, description, addToHistory = true, reparseTree = false)
        _structuralChange.tryEmit(Unit)
    }
}
