package com.ireddragonicy.konabessnext.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.model.LevelUiModel
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.model.UiText
import com.ireddragonicy.konabessnext.model.dts.DtsError
import com.ireddragonicy.konabessnext.model.dts.Severity
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.utils.DtsFormatter
import com.ireddragonicy.konabessnext.utils.DtsLexer
import com.ireddragonicy.konabessnext.utils.DtsParser
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.content.Context
import android.widget.Toast
import javax.inject.Inject
import com.ireddragonicy.konabessnext.utils.DtsEditorDebug

@HiltViewModel
class SharedGpuViewModel @Inject constructor(
    private val application: Application,
    private val repository: GpuRepository,
    private val chipRepository: com.ireddragonicy.konabessnext.repository.ChipRepository
) : AndroidViewModel(application) {

    enum class ViewMode { MAIN_EDITOR, TEXT_ADVANCED, VISUAL_TREE }

    private val _viewMode = MutableStateFlow(ViewMode.MAIN_EDITOR)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()


    // --- Search State ---
    data class SearchState(val query: String = "", val results: List<SearchResult> = emptyList(), val currentIndex: Int = -1)
    data class SearchResult(val lineIndex: Int)
    
    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

    // --- Lint State ---
    // SnapshotStateMap for granular per-line Compose invalidation.
    // Only lines whose error list actually changes will recompose.
    val lintErrorsByLine: SnapshotStateMap<Int, List<DtsError>> = mutableStateMapOf()
    private val _lintErrorCount = MutableStateFlow(0)
    val lintErrorCount: StateFlow<Int> = _lintErrorCount.asStateFlow()
    private var lintJob: Job? = null

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val gpuModelName: StateFlow<String> = repository.dtsLines
        .debounce(2000) // PERF: GPU model name rarely changes during typing
        .map { repository.getGpuModelName() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    fun updateGpuModelName(newName: String) {
        repository.updateGpuModelName(newName)
        // No manual update needed, flow will trigger from repository update
    }

    // --- State Proxies from Repository (SSOT) ---
    
    // PERF: flowOn(Default) prevents 600KB string construction from blocking main thread
    val dtsContent = repository.dtsLines.map { it.joinToString("\n") }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, "")
    val bins: StateFlow<List<Bin>> = repository.bins
    val opps: StateFlow<List<Opp>> = repository.opps

    // Preview / Transient State
    // Stores offset (MHz) for each binId.
    private val _binOffsets = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val binOffsets = _binOffsets.asStateFlow()

    val isDirty = repository.isDirty
    val canUndo = repository.canUndo
    val canRedo = repository.canRedo
    val history = repository.history

    val currentChip = chipRepository.currentChip

    // --- UI State (SSOT Refactor) ---
    private val _isLoading = MutableStateFlow(true)

    // Combined Atomic State for Bins List
    val binListState: StateFlow<UiState<List<Bin>>> = combine(repository.bins, _isLoading) { bins, loading ->
        if (loading) {
            UiState.Loading
        } else if (bins.isNotEmpty()) {
            UiState.Success(bins)
        } else {
            UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.no_gpu_tables_found))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    // --- Derived UI Models ---
    
    val binUiModels: StateFlow<Map<Int, List<LevelUiModel>>> = combine(bins, _binOffsets, currentChip, opps) { list, offsets, _, oppList ->
        mapBinsToUiModels(list, offsets, oppList)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startHighlightSession() {
        viewModelScope.launch {
            // PERF: debounce(300) prevents re-highlighting all 24K lines on every keystroke
            // EditorSession.visibleRange ensures only visible lines are prioritized
            combine(repository.dtsLines, _searchState) { lines, search ->
                lines to search.query
            }.debounce(300).collect { (lines, query) ->
                DtsEditorDebug.logEditorSessionUpdate(lines.size, query)
                com.ireddragonicy.konabessnext.editor.EditorSession.update(lines, query, viewModelScope)
            }
        }
    }

    init {
        startHighlightSession()
    }
    val textScrollIndex = MutableStateFlow(0)
    val textScrollOffset = MutableStateFlow(0)
    val treeScrollIndex = MutableStateFlow(0)
    val treeScrollOffset = MutableStateFlow(0)
    
    val parsedTree = repository.parsedTree // Auto-synced in Repo
    private val _expandedNodePaths = MutableStateFlow<Set<String>>(setOf("root"))
    
    /**
     * Resets all transient editor state when switching to a new device/DTS.
     * Call this when importing a new file or switching chipsets.
     */
    fun resetEditorState() {
        DtsEditorDebug.dumpCounters()
        DtsEditorDebug.resetCounters()
        textScrollIndex.value = 0
        textScrollOffset.value = 0
        treeScrollIndex.value = 0
        treeScrollOffset.value = 0
        _expandedNodePaths.value = setOf("root")
        _binOffsets.value = emptyMap()
        _searchState.value = SearchState()
        lintErrorsByLine.clear()
        _lintErrorCount.value = 0
        lintJob?.cancel()
        _viewMode.value = ViewMode.MAIN_EDITOR
    }

    // --- Actions ---

    fun loadData() {
        // Reset transient UI state first (scroll positions, expanded nodes, etc.)
        resetEditorState()
        
        // Lock UI to Loading immediately
        _isLoading.value = true
        _workbenchState.value = WorkbenchState.Loading
        
        viewModelScope.launch {
            try { 
                repository.loadTable()
                
                // DATA SYNCHRONIZATION BARRIER
                // loadTable() emits _structuralChange which triggers immediate bins re-parse.
                // We must wait for the NEW bins (not stale ones from previous DTS).
                // drop(1) skips the current (possibly stale) value and waits for the
                // next emission from the _structuralChange-triggered re-parse.
                val lines = repository.dtsLines.value
                if (lines.isNotEmpty()) {
                    withTimeoutOrNull(3000) {
                        repository.bins.drop(1).first()
                    }
                }
                
                // Done loading
                _isLoading.value = false
                _workbenchState.value = WorkbenchState.Ready
            } catch (e: Exception) { 
                e.printStackTrace()
                _isLoading.value = false // Errors should be handled by repository bins being empty or error state
                _workbenchState.value = WorkbenchState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    // Text Editor -> Repository
    fun updateFromText(content: String, description: String) {
        val lines = content.split("\n")
        DtsEditorDebug.logUpdateFromText(content.length, lines.size)
        repository.updateContent(lines, description)

        // Debounced lint — cancel previous and schedule new after 600ms idle
        lintJob?.let {
            it.cancel()
            DtsEditorDebug.logLintJobCancel()
        }
        DtsEditorDebug.logLintJobStart()
        lintJob = viewModelScope.launch {
            delay(600)
            val startTime = System.nanoTime()
            val grouped = withContext(Dispatchers.Default) {
                try {
                    val lexer = DtsLexer(content)
                    val tokens = lexer.tokenize()
                    val parser = DtsParser(tokens)
                    parser.parse()
                    parser.getLintResult().errors.groupBy { it.line }
                } catch (_: Exception) {
                    emptyMap()
                }
            }
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            // Diff-update the SnapshotStateMap so only changed lines recompose
            val oldKeys = lintErrorsByLine.keys.toSet()
            val newKeys = grouped.keys
            // Remove lines that no longer have errors
            for (key in oldKeys - newKeys) {
                lintErrorsByLine.remove(key)
            }
            // Add/update lines with errors
            for ((key, value) in grouped) {
                val existing = lintErrorsByLine[key]
                if (existing != value) {
                    lintErrorsByLine[key] = value
                }
            }
            _lintErrorCount.value = grouped.values.sumOf { it.size }
            DtsEditorDebug.logLintJobComplete(grouped.values.sumOf { it.size }, durationMs)
        }
    }

    /**
     * Reformats the current DTS text using the auto-formatter.
     * Parses current text -> AST -> regenerates with consistent style.
     */
    fun reformatCode() {
        viewModelScope.launch {
            val current = dtsContent.value
            if (current.isBlank()) return@launch

            val startTime = System.nanoTime()
            val formatted = withContext(Dispatchers.Default) {
                DtsFormatter.format(current)
            }
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            DtsEditorDebug.logReformat(current.length, formatted.length, durationMs)
            if (formatted != current) {
                val lines = formatted.split("\n")
                repository.updateContent(lines, "Reformat code")
            }
        }
    }

    // GUI -> Repository
    fun updateParameter(binIndex: Int, levelIndex: Int, lineIndex: Int, encodedLine: String, historyMsg: String) {
        // Advanced: encodedLine is usually "prop = <val>;". Extract key/value.
        val parts = encodedLine.split("=")
        if (parts.size >= 2) {
            val key = parts[0].trim()
            val valueWithBrackets = parts[1].trim().trim(';')
            val value = valueWithBrackets.replace("<", "").replace(">", "").trim()
            
            repository.updateParameterInBin(binIndex, levelIndex, key, value)
        }
    }
    
    /**
     * Updates OPP table voltage for devices where voltage is stored separately (e.g., SD860).
     */
    fun updateOppVoltage(frequency: Long, newVolt: Long) {
        repository.updateOppVoltage(frequency, newVolt)
    }

    fun undo() = repository.undo()
    fun redo() = repository.redo()
    
    fun save(showToast: Boolean = true) {
        viewModelScope.launch {
            repository.saveTable()
        }
    }

    fun exportRawDts(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = dtsContent.value
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray())
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "DTS Saved", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Atomically updates frequency and voltage for a specific level.
     * Ensures only one history entry is created for dragging a point on the curve.
     */
    fun updateBinLevel(binIndex: Int, levelIndex: Int, freqMhz: Int, volt: Int) {
        val updates = mutableListOf<GpuRepository.ParameterUpdate>()
        val newFreqHz = freqMhz * 1_000_000L

        // Frequency Updates
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,gpu-freq", newFreqHz.toString()))
        // opp-hz usually has /bits/ 64 prefix, but our DtsHelper regex just replaces the content inside <>
        // so we pass just the number.
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "opp-hz", newFreqHz.toString()))

        // Voltage Updates - Add all candidates, repository will only update what exists
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,level", volt.toString()))
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,cx-level", volt.toString()))
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,vdd-level", volt.toString())) // Just in case
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "opp-microvolt", volt.toString()))

        repository.batchUpdateParameters(updates, "Set Bin $binIndex Level $levelIndex: ${freqMhz}MHz / $volt")
    }

    @Deprecated("Use updateBinLevel for atomic updates")
    fun updateFrequency(binIndex: Int, levelIndex: Int, newFreqMhz: Int) {
        val newFreq = newFreqMhz * 1_000_000L
        repository.updateParameterInBin(binIndex, levelIndex, "qcom,gpu-freq", newFreq.toString(), "Set Bin $binIndex Level $levelIndex Freq to ${newFreqMhz}MHz")
        // Use bare number for opp-hz as regex inside repo targets <...> content
        repository.updateParameterInBin(binIndex, levelIndex, "opp-hz", newFreq.toString())
    }

    @Deprecated("Use updateBinLevel for atomic updates")
    fun updateVoltage(binIndex: Int, levelIndex: Int, newVolt: Int) {
         // Try updating primary keys
         val updates = listOf(
             GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,level", newVolt.toString()),
             GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,cx-level", newVolt.toString()),
             GpuRepository.ParameterUpdate(binIndex, levelIndex, "opp-microvolt", newVolt.toString())
         )
         repository.batchUpdateParameters(updates, "Set Bin $binIndex Level $levelIndex Volt to ${newVolt}")
    }

    // Set preview offset for a bin. Does NOT commit to repo history yet.
    fun setBinOffset(binId: Int, offsetMhz: Float) {
        val current = _binOffsets.value.toMutableMap()
        current[binId] = offsetMhz
        _binOffsets.value = current
    }

    fun applyGlobalOffset(binId: Int, offsetMhz: Int) {
        if (offsetMhz == 0) return
        val currentBins = bins.value
        val binIndex = currentBins.indexOfFirst { it.id == binId }
        val bin = currentBins.getOrNull(binIndex) ?: return
        
        val updates = mutableListOf<GpuRepository.ParameterUpdate>()
        
        // When applying, we use the original frequency from Repo, add the offset, and write it back.
        // We should also clear the preview offset since it's now "baked in".
        
        bin.levels.forEachIndexed { i, level ->
            val currentFreq = level.frequency
            if (currentFreq > 0) {
                val newFreq = currentFreq + (offsetMhz * 1_000_000L)
                if (newFreq > 0) {
                    updates.add(GpuRepository.ParameterUpdate(binIndex, i, "qcom,gpu-freq", newFreq.toString()))
                    // Also try to update opp-hz if present
                    updates.add(GpuRepository.ParameterUpdate(binIndex, i, "opp-hz", newFreq.toString()))
                }
            }
        }
        
        if (updates.isNotEmpty()) {
            repository.batchUpdateParameters(updates, "Applied Global Offset ${offsetMhz}MHz to Bin $binId")
            
            // Reset preview for this bin
            val currentOffsets = _binOffsets.value.toMutableMap()
            currentOffsets.remove(binId)
            _binOffsets.value = currentOffsets
        }
    }

    fun switchViewMode(mode: ViewMode) { _viewMode.value = mode }
    
    // --- Search Actions ---
    fun search(query: String) {
        if (query.isEmpty()) { _searchState.value = SearchState(); return }
        
        val lines = repository.dtsLines.value
        val results = mutableListOf<SearchResult>()
        lines.forEachIndexed { i, line ->
            if (line.contains(query, true)) results.add(SearchResult(i))
        }
        _searchState.value = SearchState(query, results, if (results.isNotEmpty()) 0 else -1)
    }

    fun nextSearchResult() {
        val current = _searchState.value
        if (current.results.isEmpty()) return
        val nextIdx = (current.currentIndex + 1) % current.results.size
        _searchState.value = current.copy(currentIndex = nextIdx)
    }

    fun previousSearchResult() {
        val current = _searchState.value
        if (current.results.isEmpty()) return
        val prevIdx = if (current.currentIndex - 1 < 0) current.results.size - 1 else current.currentIndex - 1
        _searchState.value = current.copy(currentIndex = prevIdx)
    }

    fun clearSearch() { _searchState.value = SearchState() }

    // --- Logic Helpers ---

    private fun mapBinsToUiModels(bins: List<Bin>, offsets: Map<Int, Float> = emptyMap(), oppList: List<Opp> = emptyList()): Map<Int, List<LevelUiModel>> {
        val context = application.applicationContext
        return bins.mapIndexed { i, bin -> 
            val offsetMhz = offsets[bin.id] ?: 0f
            i to bin.levels.mapIndexedNotNull { j, lvl -> parseLevelToUi(j, lvl, context, offsetMhz, oppList) }
        }.toMap()
    }
    
    // Kept for compatibility if called without offsets, though internally we bind to the one with offsets.
    private fun mapBinsToUiModels(bins: List<Bin>): Map<Int, List<LevelUiModel>> {
        return mapBinsToUiModels(bins, emptyMap(), emptyList())
    }

    private fun parseLevelToUi(index: Int, level: Level, context: android.content.Context, offsetMhz: Float = 0f, oppList: List<Opp> = emptyList()): LevelUiModel? {
        val freqRaw = level.frequency
        if (freqRaw <= 0) return null
        
        // Apply Preview Offset
        // Offset is in MHz. Freq is in Hz.
        val freqWithOffset = if (offsetMhz != 0f) {
             freqRaw + (offsetMhz * 1_000_000L).toLong()
        } else {
             freqRaw
        }
        
        val unit = context.getSharedPreferences(com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel.PREFS_NAME, 0)
            .getInt(com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel.KEY_FREQ_UNIT, com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel.FREQ_UNIT_MHZ)
            
        val freqStr = com.ireddragonicy.konabessnext.utils.FrequencyFormatter.format(context, freqWithOffset, unit)
        
        // Add indicator if offset is active
        val finalFreqStr = if (offsetMhz != 0f) "$freqStr*" else freqStr

        val bMin = level.busMin
        val bMax = level.busMax
        val bFreq = level.busFreq
        
        // Get voltage from Level (qcom,level or qcom,cx-level)
        var vLevel = level.voltageLevel
        var voltageDisplayStr = ""
        
        // If Level doesn't have voltage, try to find it from OPP table by matching frequency
        if (vLevel == -1 && oppList.isNotEmpty()) {
            // Find OPP entry with matching (or closest) frequency
            val matchingOpp = oppList.find { it.frequency == freqRaw }
                ?: oppList.minByOrNull { kotlin.math.abs(it.frequency - freqRaw) }
            
            if (matchingOpp != null) {
                // OPP voltage is in opp-microvolt format (e.g., 0x181 = 385 = RPMH level)
                val oppVolt = matchingOpp.volt
                voltageDisplayStr = "Volt: ${oppVolt}"
            }
        } else if (vLevel != -1) {
            // Use Level's voltage level
            val chip = currentChip.value
            val levelName = if (chip != null) {
                 val raw = chip.levels[vLevel - 1] ?: chip.levels[vLevel] ?: ""
                 if (raw.contains(" - ")) raw.substringAfter(" - ") else raw
            } else {
                 ""
            }
            val finalLevelName = if (levelName.isNotEmpty()) " - $levelName" else ""
            voltageDisplayStr = "Volt: $vLevel$finalLevelName"
        }
        
        return LevelUiModel(
            originalIndex = index,
            frequencyLabel = UiText.DynamicString(finalFreqStr),
            busMin = if (bMin > -1) "Min: $bMin" else "",
            busMax = if (bMax > -1) "Max: $bMax" else "",
            busFreq = if (bFreq > -1) "Freq: $bFreq" else "",
            voltageLabel = UiText.DynamicString(voltageDisplayStr),
            voltageVal = voltageDisplayStr,
            isVisible = true
        )
    }
    
    // --- Tree Logic ---
    fun syncTreeToText() {
        repository.syncTreeToText("Property Edit")
    }

    fun toggleNodeExpansion(path: String, expanded: Boolean) {
        val set = _expandedNodePaths.value.toMutableSet()
        if (expanded) set.add(path) else set.remove(path)
        _expandedNodePaths.value = set
        
        // Update the transient DtsNode object if it exists to reflect UI state immediately
        val root = parsedTree.value
        if (root != null) {
            findNode(root, path)?.isExpanded = expanded
        }
    }
    
    private fun findNode(node: com.ireddragonicy.konabessnext.model.dts.DtsNode, path: String): com.ireddragonicy.konabessnext.model.dts.DtsNode? {
        if (node.getFullPath() == path) return node
        for (child in node.children) {
            val found = findNode(child, path)
            if (found != null) return found
        }
        return null
    }
    
    fun getLevelStrings() = chipRepository.getLevelStringsForCurrentChip()
    fun getLevelValues() = chipRepository.getLevelsForCurrentChip()
    
    // --- Level Manipulation (add/remove/duplicate) ---
    // These delegate to GpuRepository's text-level operations which manipulate DTS lines directly.
    
    fun addFrequencyWrapper(binIndex: Int, toTop: Boolean) {
        repository.addLevel(binIndex, toTop)
    }
    
    fun duplicateFrequency(binIndex: Int, index: Int) {
        repository.duplicateLevelAt(binIndex, index)
    }
    
    fun removeFrequency(binIndex: Int, index: Int) {
        repository.deleteLevel(binIndex, index)
    }
    
    fun reorderFrequency(binIndex: Int, from: Int, to: Int) {}
    
    private val _workbenchState = MutableStateFlow<WorkbenchState>(WorkbenchState.Loading)
    val workbenchState = _workbenchState.asStateFlow()
    sealed class WorkbenchState { object Loading : WorkbenchState(); object Ready : WorkbenchState(); data class Error(val msg: String) : WorkbenchState() }
}