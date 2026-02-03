package com.ireddragonicy.konabessnext.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.model.LevelUiModel
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.model.UiText
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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
    
    val isDirty = repository.isDirty
    val canUndo = repository.canUndo
    val canRedo = repository.canRedo
    val history = repository.history

    val currentChip = chipRepository.currentChip

    // --- Derived UI Models ---
    
    val binUiModels: StateFlow<Map<Int, List<LevelUiModel>>> = bins
        .map { list -> mapBinsToUiModels(list) }
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

    private fun mapBinsToUiModels(bins: List<Bin>): Map<Int, List<LevelUiModel>> {
        val context = application.applicationContext
        return bins.mapIndexed { i, bin -> 
            i to bin.levels.mapIndexedNotNull { j, lvl -> parseLevelToUi(j, lvl, context) }
        }.toMap()
    }

    private fun parseLevelToUi(index: Int, level: Level, context: android.content.Context): LevelUiModel? {
        val freq = level.frequency
        if (freq <= 0) return null
        
        val unit = context.getSharedPreferences(SettingsActivity.PREFS_NAME, 0)
            .getInt(SettingsActivity.KEY_FREQ_UNIT, SettingsActivity.FREQ_UNIT_MHZ)
            
        val freqStr = com.ireddragonicy.konabessnext.utils.FrequencyFormatter.format(context, freq, unit)
        
        return LevelUiModel(
            originalIndex = index,
            frequencyLabel = UiText.DynamicString(freqStr),
            busMin = "", // Simplification for conciseness
            busMax = "",
            busFreq = "",
            voltageLabel = UiText.DynamicString("Volt: ${level.voltageLevel}"),
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
    fun removeFrequency(binIndex: Int, index: Int) {}
    fun reorderFrequency(binIndex: Int, from: Int, to: Int) {}
    
    val workbenchState = MutableStateFlow<WorkbenchState>(WorkbenchState.Ready) // Always ready with SSOT
    sealed class WorkbenchState { object Loading : WorkbenchState(); object Ready : WorkbenchState(); data class Error(val msg: String) : WorkbenchState() }
}