package com.ireddragonicy.konabessnext.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.EditorState
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.model.LevelUiModel
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val dtsLines: StateFlow<List<String>> = repository.dtsLines
    val parsedResult: StateFlow<GpuRepository.ParseResult> = repository.parsedResult
    val bins: StateFlow<List<Bin>> = repository.bins
    val opps: StateFlow<List<Opp>> = repository.opps

    private val _binUiModels = MutableStateFlow<Map<Int, List<LevelUiModel>>>(emptyMap())
    val binUiModels: StateFlow<Map<Int, List<LevelUiModel>>> = _binUiModels.asStateFlow()

    val definitions = chipRepository.definitions
    val currentChip = chipRepository.currentChip
    
    val isDirty: StateFlow<Boolean> = repository.isDirty
    val canUndo: StateFlow<Boolean> = repository.canUndo
    val canRedo: StateFlow<Boolean> = repository.canRedo
    val history: StateFlow<List<String>> = repository.history

    private val _selectedBinIndex = MutableStateFlow<Int?>(null)
    val selectedBinIndex: StateFlow<Int?> = _selectedBinIndex.asStateFlow()

    private val _selectedLevelIndex = MutableStateFlow<Int?>(null)
    val selectedLevelIndex: StateFlow<Int?> = _selectedLevelIndex.asStateFlow()

    sealed class WorkbenchState {
        object Loading : WorkbenchState()
        object Ready : WorkbenchState()
        data class Error(val message: String) : WorkbenchState()
    }

    private val _workbenchState = MutableStateFlow<WorkbenchState>(WorkbenchState.Loading)
    val workbenchState: StateFlow<WorkbenchState> = _workbenchState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.parsedResult.collect { result ->
                when (result) {
                    is GpuRepository.ParseResult.Loading -> {
                        _workbenchState.value = WorkbenchState.Loading
                    }
                    is GpuRepository.ParseResult.Success -> {
                        _workbenchState.value = WorkbenchState.Ready
                        precalculateUiModels(result.bins)
                    }
                    is GpuRepository.ParseResult.Error -> {
                        _workbenchState.value = WorkbenchState.Error(result.message)
                    }
                }
            }
        }
    }

    private fun precalculateUiModels(bins: List<Bin>) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                com.ireddragonicy.konabessnext.utils.DebugTimer.mark("ViewModel: Start Precalc")
                val context = application.applicationContext
                val newMap = bins.mapIndexed { binIndex, bin ->
                    val uiList = bin.levels.mapIndexedNotNull { lvlIndex, level ->
                        parseLevelToUiModel(lvlIndex, level, context)
                    }
                    binIndex to uiList
                }.toMap()
                _binUiModels.value = newMap
                com.ireddragonicy.konabessnext.utils.DebugTimer.mark("ViewModel: End Precalc (Items: ${newMap.size})")
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
        var voltLabel = ""

        for (line in level.lines) {
            val trimmed = line.trim()
            if (trimmed.contains("qcom,gpu-freq")) {
                freqHz = fastExtractLong(trimmed)
            } else if (trimmed.contains("qcom,bus-max")) {
                // Fix: Extract as Long then convert to String to remove 0x prefix if present
                val valLong = fastExtractLong(trimmed)
                if (valLong != -1L) busMax = valLong.toString()
            } else if (trimmed.contains("qcom,bus-min")) {
                val valLong = fastExtractLong(trimmed)
                if (valLong != -1L) busMin = valLong.toString()
            } else if (trimmed.contains("qcom,bus-freq")) {
                val valLong = fastExtractLong(trimmed)
                if (valLong != -1L) busFreq = valLong.toString()
            } else if (trimmed.contains("qcom,level") || trimmed.contains("qcom,cx-level")) {
                val voltVal = try { fastExtractLong(trimmed) } catch(e: Exception) { 0L }
                // Pre-format the label string to save UI composition time
                val rawLabel = com.ireddragonicy.konabessnext.core.editor.LevelOperations.levelint2str(voltVal)
                voltLabel = if (rawLabel.isNotEmpty()) "Level $rawLabel" else ""
            }
        }

        if (freqHz <= 0) return null

        val freqStr = SettingsActivity.formatFrequency(freqHz, context)
        
        return LevelUiModel(
            originalIndex = index,
            frequencyLabel = freqStr,
            busMin = busMin,
            busMax = busMax,
            busFreq = busFreq,
            voltageLabel = voltLabel,
            isVisible = true
        )
    }

    // High performance extraction helpers
    private fun fastExtractLong(line: String): Long {
        val start = line.indexOf('<')
        val end = line.indexOf('>')
        if (start != -1 && end != -1 && end > start) {
            val valStr = line.substring(start + 1, end).trim()
            val spaceIndex = valStr.lastIndexOf(' ')
            val finalStr = if (spaceIndex != -1) valStr.substring(spaceIndex + 1).trim() else valStr
            
            return try {
                // Handles 0x prefix automatically or decimal
                if (finalStr.startsWith("0x")) finalStr.substring(2).toLong(16)
                else finalStr.toLong()
            } catch (e: Exception) { -1L }
        }
        return -1L
    }

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    data class SearchState(
        val query: String = "",
        val results: List<SearchResult> = emptyList(),
        val currentIndex: Int = -1
    )
    data class SearchResult(val startIndex: Int, val length: Int)
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var isLoading = false

    fun loadData() {
        if (isLoading) return
        isLoading = true
        _workbenchState.value = WorkbenchState.Loading
        viewModelScope.launch {
            try {
                repository.loadTable()
            } catch (e: Exception) {
                _workbenchState.value = WorkbenchState.Error("Failed to load: ${e.message}")
                _errorEvent.emit("Failed to load table: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun onDtsContentChanged(content: String) {
        repository.updateDtsContent(content, "Text Edit")
    }

    fun syncDts() = repository.syncDts()

    fun switchViewMode(mode: ViewMode) { 
        if (_viewMode.value == ViewMode.MAIN_EDITOR && mode != ViewMode.MAIN_EDITOR) {
            syncDts()
        }
        _viewMode.value = mode 
    }
    fun selectBin(index: Int?) { _selectedBinIndex.value = index; _selectedLevelIndex.value = null }
    fun selectLevel(binIndex: Int, levelIndex: Int?) { _selectedBinIndex.value = binIndex; _selectedLevelIndex.value = levelIndex }

    fun addFrequencyWrapper(binIndex: Int, atTop: Boolean) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return
        val newBins = EditorState.deepCopyBins(currentBins)
        
        if (atTop) com.ireddragonicy.konabessnext.core.editor.LevelOperations.addLevelAtTop(newBins, binIndex)
        else com.ireddragonicy.konabessnext.core.editor.LevelOperations.addLevelAtBottom(newBins, binIndex)
        
        repository.updateBins(newBins, "Add frequency", regenerateDts = false)
    }

    fun duplicateFrequency(binIndex: Int, levelIndex: Int) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return
        val newBins = EditorState.deepCopyBins(currentBins)
        
        com.ireddragonicy.konabessnext.core.editor.LevelOperations.duplicateLevel(newBins, binIndex, levelIndex)
        repository.updateBins(newBins, "Duplicate frequency", regenerateDts = false)
    }

    fun removeFrequency(binIndex: Int, levelIndex: Int) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return
        val newBins = EditorState.deepCopyBins(currentBins)
        
        com.ireddragonicy.konabessnext.core.editor.LevelOperations.removeLevel(newBins, binIndex, levelIndex)
        repository.updateBins(newBins, "Remove frequency", regenerateDts = false)
    }

    fun reorderFrequency(binIndex: Int, fromPos: Int, toPos: Int) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        
        if (fromPos in bin.levels.indices && toPos in bin.levels.indices) {
            val level = bin.levels.removeAt(fromPos)
            bin.levels.add(toPos, level)
            repository.updateBins(newBins, "Reorder frequencies", regenerateDts = false)
        }
    }

    fun updateParameter(binIndex: Int, levelIndex: Int, lineIndex: Int, newLine: String, description: String) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return

        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (levelIndex !in bin.levels.indices) return

        val level = bin.levels[levelIndex]
        if (lineIndex in level.lines.indices) {
            level.lines[lineIndex] = newLine
        }

        repository.updateBins(newBins, description, regenerateDts = false)
    }
    
    fun updateFromText(content: String, description: String) = onDtsContentChanged(content)
    fun undo() = repository.undo()
    fun redo() = repository.redo()
    fun save(showToast: Boolean = true) {
        viewModelScope.launch {
            try {
                repository.syncDts()
                repository.saveTable()
                if (showToast) _toastEvent.emit("Saved successfully")
            } catch (e: Exception) {
                _errorEvent.emit("Save failed: ${e.message}")
            }
        }
    }

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
        _searchState.value = SearchState(query = query, results = results, currentIndex = if (results.isNotEmpty()) 0 else -1)
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

    fun clearSearch() { _searchState.value = SearchState() }
}