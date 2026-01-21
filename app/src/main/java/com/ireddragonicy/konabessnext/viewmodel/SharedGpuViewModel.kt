package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.EditorState
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared ViewModel for the unified GPU Workbench.
 * 
 * Used by GpuFrequencyFragment, RawDtsFragment, and VisualTreeFragment.
 * Access via `by activityViewModels()` to share state across fragments.
 */
@HiltViewModel
class SharedGpuViewModel @Inject constructor(
    private val repository: GpuRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ===== View Mode Management =====
    enum class ViewMode { MAIN_EDITOR, TEXT_ADVANCED, VISUAL_TREE }

    private val _viewMode = MutableStateFlow(ViewMode.MAIN_EDITOR)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    // ===== Core State from Repository =====
    val dtsContent: StateFlow<String> = repository.dtsContent
    val parsedResult: StateFlow<GpuRepository.ParseResult> = repository.parsedResult
    val bins: StateFlow<List<Bin>> = repository.bins
    val opps: StateFlow<List<Opp>> = repository.opps
    
    val isDirty: StateFlow<Boolean> = repository.isDirty
    val canUndo: StateFlow<Boolean> = repository.canUndo
    val canRedo: StateFlow<Boolean> = repository.canRedo
    val history: StateFlow<List<String>> = repository.history

    // ===== Navigation State =====
    private val _selectedBinIndex = MutableStateFlow<Int?>(null)
    val selectedBinIndex: StateFlow<Int?> = _selectedBinIndex.asStateFlow()

    private val _selectedLevelIndex = MutableStateFlow<Int?>(null)
    val selectedLevelIndex: StateFlow<Int?> = _selectedLevelIndex.asStateFlow()

    // ===== Input Handling =====
    fun onDtsContentChanged(content: String) {
        // Forward directly to repository. Repository handles debouncing on the flow side
        repository.updateDtsContent(content, "Text Edit")
    }

    // ===== UI State =====
    sealed class WorkbenchState {
        object Loading : WorkbenchState()
        object Ready : WorkbenchState()
        data class Error(val message: String) : WorkbenchState()
    }

    private val _workbenchState = MutableStateFlow<WorkbenchState>(WorkbenchState.Loading)
    val workbenchState: StateFlow<WorkbenchState> = _workbenchState.asStateFlow()

    init {
        // Sync WorkbenchState with Repository
        viewModelScope.launch {
            repository.parsedResult.collect { result ->
                when (result) {
                    is GpuRepository.ParseResult.Loading -> {
                        // Only set loading if we haven't already marked it as ready or if explicit reload
                        // _workbenchState.value = WorkbenchState.Loading 
                        // Keep granular control or map 1:1? Mapping 1:1 is safer.
                    }
                    is GpuRepository.ParseResult.Success -> {
                        _workbenchState.value = WorkbenchState.Ready
                    }
                    is GpuRepository.ParseResult.Error -> {
                        _workbenchState.value = WorkbenchState.Error(result.message)
                    }
                }
            }
        }
    }

    // ===== Events =====
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    // ===== Search State (for Text Editor) =====
    data class SearchState(
        val query: String = "",
        val results: List<SearchResult> = emptyList(),
        val currentIndex: Int = -1
    )

    data class SearchResult(val startIndex: Int, val length: Int)

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var isLoading = false

    // ===== Initialization =====
    fun loadData() {
        if (isLoading) return
        isLoading = true

        _workbenchState.value = WorkbenchState.Loading
        viewModelScope.launch {
            try {
                repository.loadTable()
                _workbenchState.value = WorkbenchState.Ready
            } catch (e: Exception) {
                _workbenchState.value = WorkbenchState.Error("Failed to load: ${e.message}")
                _errorEvent.emit("Failed to load table: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // ===== View Mode Switching =====
    fun switchViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    // ===== Navigation =====
    fun selectBin(index: Int?) {
        _selectedBinIndex.value = index
        _selectedLevelIndex.value = null
    }

    fun selectLevel(binIndex: Int, levelIndex: Int?) {
        _selectedBinIndex.value = binIndex
        _selectedLevelIndex.value = levelIndex
    }

    fun navigateBack(): Boolean {
        return when {
            _selectedLevelIndex.value != null -> {
                _selectedLevelIndex.value = null
                true
            }
            _selectedBinIndex.value != null -> {
                _selectedBinIndex.value = null
                true
            }
            else -> false
        }
    }

    // ===== Text Editor Updates =====
    fun updateFromText(content: String, description: String = "Text edit") {
        onDtsContentChanged(content)
    }

    // ===== GUI Editor Updates =====
    fun updateBins(newBins: List<Bin>, description: String = "Modify frequency") {
        repository.updateBins(newBins, description)
    }

    fun updateParameter(binIndex: Int, levelIndex: Int, lineIndex: Int, newLine: String, description: String) {
        val currentBins = bins.value.toMutableList()
        if (binIndex !in currentBins.indices) return

        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (levelIndex !in bin.levels.indices) return

        val level = bin.levels[levelIndex]
        if (lineIndex in level.lines.indices) {
            level.lines[lineIndex] = newLine
        }

        repository.updateBins(newBins, description)
    }

    fun addFrequency(binIndex: Int, atTop: Boolean) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return

        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (bin.levels.isEmpty()) return

        val source = if (atTop) bin.levels.first() else bin.levels.last()
        val newLevel = source.copyLevel()

        if (atTop) bin.levels.add(0, newLevel) else bin.levels.add(newLevel)

        offsetInitialLevel(bin, 1)
        if (isLitoOrLagoon()) offsetCaTargetLevel(bin, 1)

        repository.updateBins(newBins, "Add frequency")
    }

    fun duplicateFrequency(binIndex: Int, levelIndex: Int) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return

        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (levelIndex !in bin.levels.indices) return

        val source = bin.levels[levelIndex]
        val newLevel = source.copyLevel()
        bin.levels.add(levelIndex + 1, newLevel)

        offsetInitialLevel(bin, 1)
        if (isLitoOrLagoon()) offsetCaTargetLevel(bin, 1)

        repository.updateBins(newBins, "Duplicate frequency")
    }

    fun removeFrequency(binIndex: Int, levelIndex: Int) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return

        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (levelIndex !in bin.levels.indices) return

        bin.levels.removeAt(levelIndex)

        offsetInitialLevel(bin, -1)
        if (isLitoOrLagoon()) offsetCaTargetLevel(bin, -1)

        repository.updateBins(newBins, "Remove frequency")
    }

    fun reorderFrequency(binIndex: Int, fromPos: Int, toPos: Int) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return

        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (fromPos !in bin.levels.indices || toPos !in bin.levels.indices) return

        val level = bin.levels.removeAt(fromPos)
        bin.levels.add(toPos, level)

        repository.updateBins(newBins, "Reorder frequencies")
    }

    // ===== History =====
    fun undo() = repository.undo()
    fun redo() = repository.redo()

    // ===== Save =====
    fun save(showToast: Boolean = true) {
        viewModelScope.launch {
            try {
                repository.saveTable()
                if (showToast) _toastEvent.emit("Saved successfully")
            } catch (e: Exception) {
                _errorEvent.emit("Save failed: ${e.message}")
            }
        }
    }

    // ===== Search (for Text Editor) =====
    fun search(query: String) {
        if (query.isEmpty()) {
            _searchState.value = SearchState()
            return
        }

        val content = dtsContent.value
        val results = mutableListOf<SearchResult>()
        var index = 0
        while (content.indexOf(query, index).also { index = it } != -1) {
            results.add(SearchResult(index, query.length))
            index += query.length
        }

        _searchState.value = SearchState(
            query = query,
            results = results,
            currentIndex = if (results.isNotEmpty()) 0 else -1
        )
    }

    fun nextSearchResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        val next = (state.currentIndex + 1) % state.results.size
        _searchState.value = state.copy(currentIndex = next)
    }

    fun previousSearchResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        val prev = (state.currentIndex - 1 + state.results.size) % state.results.size
        _searchState.value = state.copy(currentIndex = prev)
    }

    fun clearSearch() {
        _searchState.value = SearchState()
    }

    // ===== Helpers =====
    private fun offsetInitialLevel(bin: Bin, offset: Int) {
        for (i in bin.header.indices) {
            val line = bin.header[i]
            if (line.contains("qcom,initial-pwrlevel")) {
                try {
                    val decoded = com.ireddragonicy.konabessnext.utils.DtsHelper.decode_int_line(line)
                    val newValue = decoded.value + offset
                    bin.header[i] = com.ireddragonicy.konabessnext.utils.DtsHelper.encodeIntOrHexLine(
                        decoded.name ?: "", newValue.toString()
                    )
                    break
                } catch (e: Exception) { }
            }
        }
    }

    private fun offsetCaTargetLevel(bin: Bin, offset: Int) {
        for (i in bin.header.indices) {
            val line = bin.header[i]
            if (line.contains("qcom,ca-target-pwrlevel")) {
                try {
                    val decoded = com.ireddragonicy.konabessnext.utils.DtsHelper.decode_int_line(line)
                    val newValue = decoded.value + offset
                    bin.header[i] = com.ireddragonicy.konabessnext.utils.DtsHelper.encodeIntOrHexLine(
                        decoded.name ?: "", newValue.toString()
                    )
                    break
                } catch (e: Exception) { }
            }
        }
    }

    private fun isLitoOrLagoon(): Boolean {
        return try {
            val current = com.ireddragonicy.konabessnext.core.ChipInfo.current
            val id = current?.id
            id == "lito_v1" ||
            id == "lito_v2" ||
            id == "lagoon"
        } catch (e: Exception) { false }
    }

    fun getCurrentBins(): List<Bin> = bins.value
}
