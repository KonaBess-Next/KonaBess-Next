package com.ireddragonicy.konabessnext.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.model.LevelUiModel
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.model.UiText
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    // --- State Proxies from Repository (SSOT) ---
    
    // Content flows directly from Repo state
    val dtsContent = repository.dtsLines.map { it.joinToString("\n") }.stateIn(viewModelScope, SharingStarted.Lazily, "")
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

    // --- Derived UI Models ---
    
    val binUiModels: StateFlow<Map<Int, List<LevelUiModel>>> = combine(bins, _binOffsets) { list, offsets ->
        mapBinsToUiModels(list, offsets)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())


    // --- Persistent UI State (Scroll/Expansion) ---
    val textScrollIndex = MutableStateFlow(0)
    val textScrollOffset = MutableStateFlow(0)
    val treeScrollIndex = MutableStateFlow(0)
    val treeScrollOffset = MutableStateFlow(0)
    
    val parsedTree = repository.parsedTree // Auto-synced in Repo
    private val _expandedNodePaths = MutableStateFlow<Set<String>>(setOf("root"))
    
    // --- Actions ---

    fun loadData() {
        viewModelScope.launch {
            try { repository.loadTable() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Text Editor -> Repository
    fun updateFromText(content: String, description: String) {
        val lines = content.split("\n")
        repository.updateContent(lines)
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

    fun undo() = repository.undo()
    fun redo() = repository.redo()
    
    fun save(showToast: Boolean = true) {
        viewModelScope.launch {
            repository.saveTable()
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

    private fun mapBinsToUiModels(bins: List<Bin>, offsets: Map<Int, Float> = emptyMap()): Map<Int, List<LevelUiModel>> {
        val context = application.applicationContext
        return bins.mapIndexed { i, bin -> 
            val offsetMhz = offsets[bin.id] ?: 0f
            i to bin.levels.mapIndexedNotNull { j, lvl -> parseLevelToUi(j, lvl, context, offsetMhz) }
        }.toMap()
    }
    
    // Kept for compatibility if called without offsets, though internally we bind to the one with offsets.
    private fun mapBinsToUiModels(bins: List<Bin>): Map<Int, List<LevelUiModel>> {
        return mapBinsToUiModels(bins, emptyMap())
    }

    private fun parseLevelToUi(index: Int, level: Level, context: android.content.Context, offsetMhz: Float = 0f): LevelUiModel? {
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
        
        val vLevel = level.voltageLevel
        val chip = currentChip.value
        val levelName = if (chip != null && chip.levels.containsKey(vLevel)) {
             " - " + chip.levels[vLevel]
        } else {
             ""
        }
        
        return LevelUiModel(
            originalIndex = index,
            frequencyLabel = UiText.DynamicString(finalFreqStr),
            busMin = if (bMin > -1) "Min: $bMin" else "",
            busMax = if (bMax > -1) "Max: $bMax" else "",
            busFreq = if (bFreq > -1) "Freq: $bFreq" else "",
            voltageLabel = UiText.DynamicString("Volt: $vLevel"),
            voltageVal = "Volt: $vLevel$levelName",
            isVisible = true
        )
    }
    
    // --- Tree Logic ---
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
    
    // Stub implementation for complex operations that need full regen logic (add/remove level)
    // In a full implementation, these would manipulate _dtsLines directly or via Strategy.
    fun addFrequencyWrapper(binIndex: Int, toTop: Boolean) {}
    fun duplicateFrequency(binIndex: Int, index: Int) {}
    
    fun removeFrequency(binIndex: Int, index: Int) {
        repository.deleteLevel(binIndex, index)
    }
    
    fun reorderFrequency(binIndex: Int, from: Int, to: Int) {}
    
    val workbenchState = MutableStateFlow<WorkbenchState>(WorkbenchState.Ready) // Always ready with SSOT
    sealed class WorkbenchState { object Loading : WorkbenchState(); object Ready : WorkbenchState(); data class Error(val msg: String) : WorkbenchState() }
}