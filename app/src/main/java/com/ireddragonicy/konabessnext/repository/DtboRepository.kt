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

@Singleton
class DtboRepository @Inject constructor(
    private val dtsFileRepository: DtsFileRepository,
    private val displayDomainManager: DisplayDomainManager,
    private val touchManager: com.ireddragonicy.konabessnext.domain.TouchDomainManager,
    private val speakerManager: com.ireddragonicy.konabessnext.domain.SpeakerDomainManager,
    private val userMessageManager: com.ireddragonicy.konabessnext.utils.UserMessageManager
) : DtsDataProvider {
    private val historyManager = HistoryManager()

    private val _selectedPanelIndex = MutableStateFlow(0)
    val selectedPanelIndex: StateFlow<Int> = _selectedPanelIndex.asStateFlow()

    fun selectPanel(index: Int) {
        _selectedPanelIndex.value = index
        _selectedTimingIndex.value = 0
    }

    private val _selectedTimingIndex = MutableStateFlow(0)
    val selectedTimingIndex: StateFlow<Int> = _selectedTimingIndex.asStateFlow()

    fun selectTiming(index: Int) {
        _selectedTimingIndex.value = index
    }

    private val _dtsLines = MutableStateFlow<List<String>>(emptyList())
    override val dtsLines: StateFlow<List<String>> = _dtsLines.asStateFlow()
    override val dtsContent: Flow<String> = _dtsLines.map { it.joinToString("\n") }.flowOn(Dispatchers.Default)

    private val _structuralChange = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    private val historyDispatcher = Dispatchers.Default.limitedParallelism(1)

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

    val touchPanels = _parsedTree.map { if (it == null) emptyList() else touchManager.findTouchPanels(it) }
        .flowOn(Dispatchers.Default).stateIn(repoScope, SharingStarted.Lazily, emptyList())

    val speakerPanels = _parsedTree.map { if (it == null) emptyList() else speakerManager.findSpeakerPanels(it) }
        .flowOn(Dispatchers.Default).stateIn(repoScope, SharingStarted.Lazily, emptyList())

    override val canUndo: StateFlow<Boolean> = historyManager.canUndo
    override val canRedo: StateFlow<Boolean> = historyManager.canRedo
    val history = historyManager.history

    private val _isDirty = MutableStateFlow(false)
    override val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private var initialContentHash: Int = 0
    private var treeParseJob: Job? = null

    override suspend fun loadTable(): DomainResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val lines = dtsFileRepository.loadDtsLines()
            historyManager.clear()
            _parsedTree.value = null
            _dtsLines.value = lines
            _structuralChange.tryEmit(Unit)

            val fullText = lines.joinToString("\n")
            try { _parsedTree.value = DtsTreeHelper.parse(fullText) } catch (e: Exception) { e.printStackTrace() }

            initialContentHash = lines.hashCode()
            _isDirty.value = false
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            DomainResult.Failure(AppError.UnknownError(e.localizedMessage ?: "Unknown error", e))
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
            DomainResult.Failure(AppError.IoError(e.localizedMessage ?: "Unknown error", e))
        }
    }

    override fun updateContent(
        newLines: List<String>,
        description: String,
        addToHistory: Boolean,
        reparseTree: Boolean
    ) {
        val oldLines = _dtsLines.value
        if (newLines === oldLines) return
        
        if (addToHistory) {
            repoScope.launch(historyDispatcher) {
                historyManager.snapshot(oldLines, newLines, description)
            }
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
                    com.ireddragonicy.konabessnext.utils.DtsLexer.LexOptions(checkCancelled = checkCancelled)
                )
                val tree = com.ireddragonicy.konabessnext.utils.DtsParser(tokens).parse(
                    com.ireddragonicy.konabessnext.utils.DtsParser.ParseOptions(checkCancelled = checkCancelled)
                )
                _parsedTree.value = tree
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) { }
        }
    }

    private fun getTreeCopy(): DtsNode? {
        return ensureParsedTree()?.deepCopy()
    }

    fun updateTimingProperty(
        panelNodeName: String,
        timingIndex: Int,
        propertyName: String,
        newValue: String,
        fragmentIndex: Int = -1,
        historyDesc: String? = null
    ): Boolean {
        repoScope.launch {
            val root = getTreeCopy() ?: return@launch
            val timingNode = displayDomainManager.findTimingNode(root, panelNodeName, timingIndex, fragmentIndex)
                ?: return@launch
            
            if (displayDomainManager.updateTimingProperty(timingNode, propertyName, newValue)) {
                commitTreeChanges(historyDesc ?: "Updated $propertyName to $newValue", root)
            }
        }
        return true
    }

    fun updatePanelProperty(
        panelNodeName: String,
        propertyName: String,
        newValue: String,
        fragmentIndex: Int = -1,
        historyDesc: String? = null
    ): Boolean {
        repoScope.launch {
            val root = getTreeCopy() ?: return@launch
            val panelNode = displayDomainManager.findPanelNodeInTree(root, panelNodeName, fragmentIndex) ?: return@launch
            
            if (displayDomainManager.updatePanelProperty(panelNode, propertyName, newValue)) {
                commitTreeChanges(historyDesc ?: "Updated panel $propertyName", root)
            }
        }
        return true
    }

    fun updatePanelFramerate(newFps: Int): Boolean {
        if (newFps <= 0) return false
        repoScope.launch {
            val root = getTreeCopy() ?: return@launch
            val panel = getSelectedPanel(root) ?: return@launch
            val timingIdx = _selectedTimingIndex.value.coerceIn(0, (panel.timings.size - 1).coerceAtLeast(0))
            val timingNode = displayDomainManager.findTimingNode(root, panel.nodeName, timingIdx, panel.fragmentIndex)
                ?: return@launch

            displayDomainManager.updateTimingProperty(timingNode, "qcom,mdss-dsi-panel-framerate", newFps.toString())
            commitTreeChanges("Set framerate to ${newFps}Hz", root)
        }
        return true
    }

    fun updatePanelClockRate(newClock: Long): Boolean {
        if (newClock < 0) return false
        repoScope.launch {
            val root = getTreeCopy() ?: return@launch
            val panel = getSelectedPanel(root) ?: return@launch
            val timingIdx = _selectedTimingIndex.value.coerceIn(0, (panel.timings.size - 1).coerceAtLeast(0))
            val timingNode = displayDomainManager.findTimingNode(root, panel.nodeName, timingIdx, panel.fragmentIndex) ?: return@launch

            displayDomainManager.updateTimingProperty(timingNode, "qcom,mdss-dsi-panel-clockrate", newClock.toString())
            commitTreeChanges("Set display clockrate to $newClock", root)
        }
        return true
    }

    fun updateDfpsList(fpsList: List<Int>): Boolean {
        repoScope.launch {
            val root = getTreeCopy() ?: return@launch
            val panel = getSelectedPanel(root) ?: return@launch
            val panelNode = displayDomainManager.findPanelNodeInTree(root, panel.nodeName, panel.fragmentIndex) ?: return@launch

            displayDomainManager.updateDfpsList(panelNode, fpsList)
            commitTreeChanges("Updated DFPS list", root)
        }
        return true
    }

    fun updateTouchSpiFrequency(
        nodeName: String,
        fragmentIndex: Int,
        newFrequency: Long,
        historyDesc: String? = null
    ): Boolean {
        repoScope.launch {
            val root = getTreeCopy() ?: return@launch
            val touchNode = touchManager.findTouchNodeInTree(root, nodeName, fragmentIndex) ?: return@launch

            if (touchManager.updateTouchSpiFrequency(touchNode, newFrequency.toString())) {
                commitTreeChanges(historyDesc ?: "Updated touch panel SPI frequency", root)
            }
        }
        return true
    }

    fun updateSpeakerReBounds(
        nodeName: String,
        fragmentIndex: Int,
        newReMin: Long,
        newReMax: Long,
        historyDesc: String? = null
    ): Boolean {
        repoScope.launch {
            val root = getTreeCopy() ?: return@launch
            val speakerNode = speakerManager.findSpeakerNodeInTree(root, nodeName, fragmentIndex) ?: return@launch

            if (speakerManager.updateSpeakerReBounds(speakerNode, newReMin.toString(), newReMax.toString())) {
                commitTreeChanges(historyDesc ?: "Updated speaker bounds", root)
            }
        }
        return true
    }

    fun getDisplaySnapshot(): DisplaySnapshot? {
        val root = _parsedTree.value ?: return getDisplaySnapshotFromLines()
        val panel = getSelectedPanel(root) ?: return null
        val timingIdx = _selectedTimingIndex.value.coerceIn(0, (panel.timings.size - 1).coerceAtLeast(0))
        val timing = panel.timings.getOrNull(timingIdx) ?: return null

        return DisplaySnapshot(
            panelNodeName = panel.nodeName, panelName = panel.panelName, panelType = panel.panelType,
            fragmentIndex = panel.fragmentIndex, dfpsList = panel.dfpsList, timing = timing,
            timingIndex = timingIdx, timingCount = panel.timings.size
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
            panelNodeName = panel.nodeName, panelName = panel.panelName, panelType = panel.panelType,
            fragmentIndex = panel.fragmentIndex, dfpsList = panel.dfpsList, timing = timing,
            timingIndex = timingIdx, timingCount = panel.timings.size
        )
    }

    private fun getSelectedPanel(root: DtsNode): DisplayPanel? {
        val allPanels = displayDomainManager.findAllPanels(root)
        val panelsWithTimings = allPanels.filter { it.timings.isNotEmpty() }
        val idx = _selectedPanelIndex.value.coerceIn(0, (panelsWithTimings.size - 1).coerceAtLeast(0))
        return panelsWithTimings.getOrNull(idx)
    }

    data class DisplaySnapshot(
        val panelNodeName: String, val panelName: String, val panelType: String,
        val fragmentIndex: Int, val dfpsList: List<Int>, val timing: DisplayTiming,
        val timingIndex: Int = 0, val timingCount: Int = 1
    )

    override fun undo() {
        repoScope.launch(historyDispatcher) {
            val current = _dtsLines.value
            val reverted = historyManager.undo(current)
            if (reverted != null) {
                updateContent(reverted, description = "Undo", addToHistory = false, reparseTree = true)
            }
        }
    }

    override fun redo() {
        repoScope.launch(historyDispatcher) {
            val current = _dtsLines.value
            val reverted = historyManager.redo(current)
            if (reverted != null) {
                updateContent(reverted, description = "Redo", addToHistory = false, reparseTree = true)
            }
        }
    }

    fun applySnapshot(content: String) {
        updateContent(content.split("\n"), "Applied external snapshot", addToHistory = true, reparseTree = true)
    }

    override fun currentDtsPath(): String? = dtsFileRepository.currentDtsPath()

    override fun syncTreeToText(description: String) {
        repoScope.launch {
            if (ensureParsedTree() == null) return@launch
            commitTreeChanges(description, _parsedTree.value!!)
        }
    }

    private fun ensureParsedTree(): DtsNode? {
        _parsedTree.value?.let { return it }
        val currentLines = _dtsLines.value
        if (currentLines.isEmpty()) return null

        val parsedRoot = DtsTreeHelper.parse(currentLines.joinToString("\n"))
        _parsedTree.value = parsedRoot
        return parsedRoot
    }

    private fun commitTreeChanges(description: String, mutatedRoot: DtsNode) {
        val newText = DtsTreeHelper.generate(mutatedRoot)
        val newLines = newText.split("\n")
        
        if (newLines == _dtsLines.value) {
            _parsedTree.value = mutatedRoot
            _structuralChange.tryEmit(Unit)
            return
        }

        updateContent(newLines, description, addToHistory = true, reparseTree = false)
        _parsedTree.value = mutatedRoot
        _structuralChange.tryEmit(Unit)
    }
}
