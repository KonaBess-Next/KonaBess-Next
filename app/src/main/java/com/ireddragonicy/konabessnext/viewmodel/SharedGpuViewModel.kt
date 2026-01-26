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
import com.ireddragonicy.konabessnext.model.UiText
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
        // Observe changes in bins repository to automatically refresh UI models
        viewModelScope.launch {
            repository.bins.collect { newBins ->
                if (newBins.isNotEmpty()) {
                    precalculateUiModels(newBins)
                }
            }
        }
    }

    private fun precalculateUiModels(bins: List<Bin>) {
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
            if (trimmed.contains("qcom,gpu-freq")) {
                freqHz = fastExtractLong(trimmed)
            } else if (trimmed.contains("qcom,bus-max")) {
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
                val rawLabel = com.ireddragonicy.konabessnext.core.editor.LevelOperations.levelint2str(voltVal)
                if (rawLabel.isNotEmpty()) {
                    voltLabel = UiText.StringResource(com.ireddragonicy.konabessnext.R.string.level_format, listOf(rawLabel))
                }
            }
        }

        if (freqHz <= 0) return null

        // Determine Unit from Prefs directly (simulating SettingsActivity.formatFrequency)
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val unit = prefs.getInt(SettingsActivity.KEY_FREQ_UNIT, SettingsActivity.FREQ_UNIT_MHZ)
        
        val freqUiText = when (unit) {
            SettingsActivity.FREQ_UNIT_HZ -> UiText.StringResource(com.ireddragonicy.konabessnext.R.string.format_hz, listOf(freqHz))
            SettingsActivity.FREQ_UNIT_MHZ -> UiText.StringResource(com.ireddragonicy.konabessnext.R.string.format_mhz, listOf(freqHz / 1000000L))
            SettingsActivity.FREQ_UNIT_GHZ -> UiText.StringResource(com.ireddragonicy.konabessnext.R.string.format_ghz, listOf(freqHz / 1000000000.0))
            else -> UiText.StringResource(com.ireddragonicy.konabessnext.R.string.format_mhz, listOf(freqHz / 1000000L))
        }
        
        return LevelUiModel(
            originalIndex = index,
            frequencyLabel = freqUiText,
            busMin = busMin,
            busMax = busMax,
            busFreq = busFreq,
            voltageLabel = voltLabel,
            isVisible = true
        )
    }

    private fun fastExtractLong(line: String): Long {
        val start = line.indexOf('<')
        val end = line.indexOf('>')
        if (start != -1 && end != -1 && end > start) {
            val valStr = line.substring(start + 1, end).trim()
            val spaceIndex = valStr.lastIndexOf(' ')
            val finalStr = if (spaceIndex != -1) valStr.substring(spaceIndex + 1).trim() else valStr
            
            return try {
                if (finalStr.startsWith("0x")) finalStr.substring(2).toLong(16)
                else finalStr.toLong()
            } catch (e: Exception) { -1L }
        }
        return -1L
    }

    private val _toastEvent = MutableSharedFlow<UiText>()
    val toastEvent: SharedFlow<UiText> = _toastEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<UiText>()
    val errorEvent: SharedFlow<UiText> = _errorEvent.asSharedFlow()

    // ... Search Code ...
    data class SearchState(
        val query: String = "",
        val results: List<SearchResult> = emptyList(),
        val currentIndex: Int = -1
    )
    data class SearchResult(val startIndex: Int, val length: Int)
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadData() {
        android.util.Log.d("KonaBessVM", "loadData called. Cancelling previous job: ${loadJob?.isActive}")
        loadJob?.cancel()
        
        loadJob = viewModelScope.launch {
            _workbenchState.value = WorkbenchState.Loading
            try {
                android.util.Log.d("KonaBessVM", "loadData: Invoking repository.loadTable()")
                repository.loadTable()
                android.util.Log.d("KonaBessVM", "loadData: repository.loadTable() completed successfully")
                
                // Explicitly valid success state
                _workbenchState.value = WorkbenchState.Ready
                
            } catch (e: Exception) {
                if (isActive) {
                    android.util.Log.e("KonaBessVM", "loadData: Error loading table", e)
                    val errorMsg = e.message ?: "Unknown error"
                    _workbenchState.value = WorkbenchState.Error(errorMsg) // UiState.Error needs updated if used here? No, WorkbenchState is local.
                    // WorkbenchState.Error(val message: String). I should update WorkbenchState too?
                    // For now, keep String in WorkbenchState or update it. 
                    // Let's stick to minimal changes for WorkbenchState unless necessary.
                    _errorEvent.emit(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.failed_to_load_format, listOf(errorMsg)))
                }
            }
        }
    }

    // ... (omitted methods) ...
    // ...
    
    fun updateFromText(content: String, description: String) = onDtsContentChanged(content)
    fun undo() = repository.undo()
    fun redo() = repository.redo()
    fun save(showToast: Boolean = true) {
        viewModelScope.launch {
            try {
                repository.syncDts()
                repository.saveTable()
                if (showToast) _toastEvent.emit(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.saved_successfully))
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                _errorEvent.emit(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.save_failed_format, listOf(errorMsg)))
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
        val results = state.results
        if (results.isEmpty()) return
        val next = (state.currentIndex + 1) % results.size
        _searchState.value = state.copy(currentIndex = next)
    }

    fun previousSearchResult() {
        val state = _searchState.value
        val results = state.results
        if (results.isEmpty()) return
        val prev = (state.currentIndex - 1 + results.size) % results.size
        _searchState.value = state.copy(currentIndex = prev)
    }

    fun onDtsContentChanged(content: String) {
        repository.updateDtsContent(content, "Text Edit")
    }

    fun addFrequencyWrapper(binIndex: Int, toTop: Boolean = true) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return
        
        // Deep copy needed for safety
        val newBins = com.ireddragonicy.konabessnext.model.EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        
        val sourceLevel = if (toTop) bin.levels.firstOrNull() else bin.levels.lastOrNull()
        if (sourceLevel != null) {
            // Primitive copy of lines
            val newLines = ArrayList(sourceLevel.lines)
            val newLevel = com.ireddragonicy.konabessnext.model.Level(newLines)
            
            if (toTop) bin.levels.add(0, newLevel)
            else bin.levels.add(newLevel)
            
            repository.updateBins(newBins, "Add Frequency")
        }
    }

    fun duplicateFrequency(binIndex: Int, index: Int) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return
        
        val newBins = com.ireddragonicy.konabessnext.model.EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        
        if (index in bin.levels.indices) {
            val sourceLevel = bin.levels[index]
            val newLevel = com.ireddragonicy.konabessnext.model.Level(ArrayList(sourceLevel.lines))
            bin.levels.add(index + 1, newLevel)
            repository.updateBins(newBins, "Duplicate Frequency")
        }
    }

    fun removeFrequency(binIndex: Int, index: Int) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return
        
        val newBins = com.ireddragonicy.konabessnext.model.EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        
        if (index in bin.levels.indices) {
            bin.levels.removeAt(index)
            repository.updateBins(newBins, "Remove Frequency")
        }
    }

    fun reorderFrequency(binIndex: Int, from: Int, to: Int) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return

        val newBins = com.ireddragonicy.konabessnext.model.EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        
        if (from in bin.levels.indices && to in bin.levels.indices) {
            if (from < to) {
                for (i in from until to) java.util.Collections.swap(bin.levels, i, i + 1)
            } else {
                for (i in from downTo to + 1) java.util.Collections.swap(bin.levels, i, i - 1)
            }
            repository.updateBins(newBins, "Reorder Frequency")
        }
    }

    fun updateParameter(binIndex: Int, levelIndex: Int, lineIndex: Int, encodedLine: String, historyMsg: String) {
        val currentBins = bins.value
        if (binIndex !in currentBins.indices) return

        val newBins = com.ireddragonicy.konabessnext.model.EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        
        if (levelIndex in bin.levels.indices) {
           val level = bin.levels[levelIndex]
           
           if (lineIndex in level.lines.indices) {
               // Use direct index access which is safer if provided
               level.lines[lineIndex] = "\t\t$encodedLine" // indent
               repository.updateBins(newBins, historyMsg)
           } else {
               // Fallback to tag search if lineIndex is invalid (e.g. -1)
               // Extract tag from encodedLine approx (e.g. "qcom,gpu-freq = <...>")
               val tagEnd = encodedLine.indexOf('=')
               if (tagEnd != -1) {
                   val tag = encodedLine.substring(0, tagEnd).trim()
                   val foundIdx = level.lines.indexOfFirst { it.trim().startsWith(tag) }
                   if (foundIdx != -1) {
                       level.lines[foundIdx] = "\t\t$encodedLine" 
                       repository.updateBins(newBins, historyMsg)
                   }
               }
           }
        }
    }
    
    fun switchViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun clearSearch() { _searchState.value = SearchState() }
}