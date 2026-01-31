package com.ireddragonicy.konabessnext.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.EditorState
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.model.LevelUiModel
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.model.UiText
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import com.ireddragonicy.konabessnext.core.ChipInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class SharedGpuViewModel @Inject constructor(
    private val application: Application,
    private val repository: GpuRepository,
    private val chipRepository: com.ireddragonicy.konabessnext.repository.ChipRepository,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    enum class ViewMode { MAIN_EDITOR, TEXT_ADVANCED, VISUAL_TREE }

    private val _viewMode = MutableStateFlow(ViewMode.MAIN_EDITOR)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    val dtsContent: StateFlow<String> = repository.dtsContent
    val bins: StateFlow<List<Bin>> = repository.bins
    val opps: StateFlow<List<Opp>> = repository.opps

    private val _binUiModels = MutableStateFlow<Map<Int, List<LevelUiModel>>>(emptyMap())
    val binUiModels: StateFlow<Map<Int, List<LevelUiModel>>> = _binUiModels.asStateFlow()

    val isDirty: StateFlow<Boolean> = repository.isDirty
    val canUndo: StateFlow<Boolean> = repository.canUndo
    val canRedo: StateFlow<Boolean> = repository.canRedo
    val history: StateFlow<List<String>> = repository.history

    sealed class WorkbenchState {
        object Loading : WorkbenchState()
        object Ready : WorkbenchState()
        data class Error(val message: String) : WorkbenchState()
    }

    private val _workbenchState = MutableStateFlow<WorkbenchState>(WorkbenchState.Loading)
    val workbenchState: StateFlow<WorkbenchState> = _workbenchState.asStateFlow()

    init {
        // Observe changes to Bins to update the UI models reactively
        viewModelScope.launch {
            repository.bins.collect { newBins ->
                precalculateUiModels(newBins)
            }
        }
        
        // Reload data if chipset changes
        viewModelScope.launch {
            chipRepository.currentChip.collect { 
                if (_workbenchState.value is WorkbenchState.Ready) {
                    loadData() 
                }
            }
        }
    }

    private fun precalculateUiModels(bins: List<Bin>) {
        if (ChipInfo.current == null || bins.isEmpty()) {
            _binUiModels.value = emptyMap()
            return
        }
        
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val context = application.applicationContext
                val newMap = bins.mapIndexed { binIndex, bin ->
                    val uiList = bin.levels.mapIndexedNotNull { lvlIndex, level ->
                        parseLevelToUiModel(lvlIndex, level, context)
                    }
                    binIndex to uiList
                }.toMap()
                _binUiModels.value = newMap
            } catch (e: Exception) { 
                e.printStackTrace() 
            }
        }
    }

    private fun parseLevelToUiModel(index: Int, level: Level, context: android.content.Context): LevelUiModel? {
        var freqHz = -1L
        var busMax = ""
        var busMin = ""
        var busFreq = ""
        var voltLabel: UiText = UiText.DynamicString("")

        for (line in level.lines) {
            val trimmed = line.trim()
            if (trimmed.contains("qcom,gpu-freq")) freqHz = fastExtractLong(trimmed)
            else if (trimmed.contains("qcom,bus-max")) { val v = fastExtractLong(trimmed); if (v != -1L) busMax = v.toString() }
            else if (trimmed.contains("qcom,bus-min")) { val v = fastExtractLong(trimmed); if (v != -1L) busMin = v.toString() }
            else if (trimmed.contains("qcom,bus-freq")) { val v = fastExtractLong(trimmed); if (v != -1L) busFreq = v.toString() }
            else if (trimmed.contains("qcom,level") || trimmed.contains("qcom,cx-level")) {
                val v = try { fastExtractLong(trimmed) } catch(e: Exception) { 0L }
                val rawLabel = com.ireddragonicy.konabessnext.core.editor.LevelOperations.levelint2str(v)
                if (rawLabel.isNotEmpty()) voltLabel = UiText.StringResource(R.string.level_format, listOf(rawLabel))
            }
        }
        if (freqHz <= 0) return null
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val unit = prefs.getInt(SettingsActivity.KEY_FREQ_UNIT, SettingsActivity.FREQ_UNIT_MHZ)
        val freqUiText = when (unit) {
            SettingsActivity.FREQ_UNIT_HZ -> UiText.StringResource(R.string.format_hz, listOf(freqHz))
            SettingsActivity.FREQ_UNIT_MHZ -> UiText.StringResource(R.string.format_mhz, listOf(freqHz / 1000000L))
            SettingsActivity.FREQ_UNIT_GHZ -> UiText.StringResource(R.string.format_ghz, listOf(freqHz / 1000000000.0))
            else -> UiText.StringResource(R.string.format_mhz, listOf(freqHz / 1000000L))
        }
        return LevelUiModel(index, freqUiText, busMin, busMax, busFreq, voltLabel, true)
    }

    private fun fastExtractLong(line: String): Long {
        val start = line.indexOf('<')
        val end = line.indexOf('>')
        if (start != -1 && end != -1 && end > start) {
            val valStr = line.substring(start + 1, end).trim()
            val spaceIndex = valStr.lastIndexOf(' ')
            val finalStr = if (spaceIndex != -1) valStr.substring(spaceIndex + 1).trim() else valStr
            return try {
                if (finalStr.startsWith("0x")) finalStr.substring(2).toLong(16) else finalStr.toLong()
            } catch (e: Exception) { -1L }
        }
        return -1L
    }

    private val _toastEvent = MutableSharedFlow<UiText>()
    val toastEvent: SharedFlow<UiText> = _toastEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<UiText>()
    val errorEvent: SharedFlow<UiText> = _errorEvent.asSharedFlow()

    data class SearchState(val query: String = "", val results: List<SearchResult> = emptyList(), val currentIndex: Int = -1)
    data class SearchResult(val startIndex: Int, val length: Int)
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _workbenchState.value = WorkbenchState.Loading
            try {
                repository.loadTable()
                // Fix: Mark as ready so Raw Text/Tree can open even if GUI parsing has issues
                _workbenchState.value = WorkbenchState.Ready
            } catch (e: Exception) {
                if (isActive) _workbenchState.value = WorkbenchState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateFromText(content: String, description: String) {
        repository.updateDtsContent(content, description)
    }

    fun undo() = repository.undo()
    fun redo() = repository.redo()
    
    fun save(showToast: Boolean = true) {
        viewModelScope.launch {
            try {
                repository.syncDts()
                repository.saveTable()
                if (showToast) _toastEvent.emit(UiText.StringResource(R.string.saved_successfully))
            } catch (e: Exception) {
                _errorEvent.emit(UiText.StringResource(R.string.save_failed_format, listOf(e.message ?: "")))
            }
        }
    }

    fun search(query: String) {
        if (query.isEmpty()) { _searchState.value = SearchState(); return }
        val content = dtsContent.value
        val results = mutableListOf<SearchResult>()
        var index = 0
        while (content.indexOf(query, index).also { index = it } != -1) {
            results.add(SearchResult(index, query.length))
            index += query.length
        }
        _searchState.value = SearchState(query, results, if (results.isNotEmpty()) 0 else -1)
    }

    fun nextSearchResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        _searchState.value = state.copy(currentIndex = (state.currentIndex + 1) % state.results.size)
    }

    fun previousSearchResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        _searchState.value = state.copy(currentIndex = (state.currentIndex - 1 + state.results.size) % state.results.size)
    }

    fun clearSearch() { _searchState.value = SearchState() }
    fun switchViewMode(mode: ViewMode) { _viewMode.value = mode }

    fun addFrequencyWrapper(binIndex: Int, toTop: Boolean = true) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        val sourceLevel = if (toTop) bin.levels.firstOrNull() else bin.levels.lastOrNull()
        if (sourceLevel != null) {
            bin.levels.add(if (toTop) 0 else bin.levels.size, Level(ArrayList(sourceLevel.lines)))
            repository.updateBins(newBins, "Add Frequency")
        }
    }

    fun duplicateFrequency(binIndex: Int, index: Int) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (index in bin.levels.indices) {
            bin.levels.add(index + 1, Level(ArrayList(bin.levels[index].lines)))
            repository.updateBins(newBins, "Duplicate Frequency")
        }
    }

    fun removeFrequency(binIndex: Int, index: Int) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (index in bin.levels.indices) {
            bin.levels.removeAt(index)
            repository.updateBins(newBins, "Remove Frequency")
        }
    }

    fun reorderFrequency(binIndex: Int, from: Int, to: Int) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (from in bin.levels.indices && to in bin.levels.indices) {
            java.util.Collections.swap(bin.levels, from, to)
            repository.updateBins(newBins, "Reorder Frequency")
        }
    }

    fun updateParameter(binIndex: Int, levelIndex: Int, lineIndex: Int, encodedLine: String, historyMsg: String) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (levelIndex in bin.levels.indices) {
           val level = bin.levels[levelIndex]
           if (lineIndex in level.lines.indices) {
               level.lines[lineIndex] = "\t\t$encodedLine"
               repository.updateBins(newBins, historyMsg)
           }
        }
    }
}