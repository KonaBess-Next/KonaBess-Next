package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.EditorState
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import javax.inject.Inject
import androidx.lifecycle.asLiveData

@HiltViewModel
class GpuFrequencyViewModel @Inject constructor(
    private val repository: GpuRepository
) : ViewModel() {

    val binsState: StateFlow<UiState<List<Bin>>> 
        get() = _binsState.asStateFlow()
    private val _binsState = MutableStateFlow<UiState<List<Bin>>>(UiState.Loading)

    // Java compatibility
    val binsLiveData: androidx.lifecycle.LiveData<UiState<List<Bin>>> = _binsState.asLiveData()

    val canUndoLiveData: androidx.lifecycle.LiveData<Boolean> = repository.canUndo.asLiveData()
    val canRedoLiveData: androidx.lifecycle.LiveData<Boolean> = repository.canRedo.asLiveData()
    val isDirtyLiveData: androidx.lifecycle.LiveData<Boolean> = repository.isDirty.asLiveData()

    val selectedBinIndex = MutableStateFlow(-1)
    val selectedLevelIndex = MutableStateFlow(-1)

    val isDirty: StateFlow<Boolean> = repository.isDirty
    val canUndo: StateFlow<Boolean> = repository.canUndo
    val canRedo: StateFlow<Boolean> = repository.canRedo
    val history: StateFlow<List<String>> = repository.history
    val stateVersion: StateFlow<Long> = repository.stateVersion

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    fun getCurrentBins(): List<Bin> {
        return repository.bins.value
    }

    init {
        viewModelScope.launch {
            repository.parsedResult.collect { result ->
                when (result) {
                    is GpuRepository.ParseResult.Loading -> _binsState.value = UiState.Loading
                    is GpuRepository.ParseResult.Success -> _binsState.value = UiState.Success(result.bins)
                    is GpuRepository.ParseResult.Error -> _binsState.value = UiState.Error(result.message)
                }
            }
        }
    }

    private var loadJob: kotlinx.coroutines.Job? = null

    fun resetSelection() {
        // Reset selection on reload to prevent stuck UI
        selectedBinIndex.value = -1
        selectedLevelIndex.value = -1
    }
    
    // Delegate operations to repository or handle logic here if repository is pure data
    // Pure data repository approach:
    
    fun updateParameter(binIndex: Int, levelIndex: Int, paramKey: String, newValue: String) {
        // Logic to update bin
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return
        
        // Deep copy needed to modify? GpuRepository handles modification?
        // Ideally GpuRepository should expose methods to modify state.
        // But the previous ViewModel had logic.
        // For refactor speed, I'll implement logic here and push to repository.
        // But repository needs to track history.
        // So I should modify a copy and call repository.modifyBins()
        
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (levelIndex !in bin.levels.indices) return
        
        val level = bin.levels[levelIndex]
        for (i in level.lines.indices) {
            val line = level.lines[i]
            if (line.contains(paramKey)) {
                val updated = line.replace(Regex("<[^>]+>"), "<$newValue>")
                level.lines[i] = updated
                break
            }
        }
        
        repository.modifyBins(newBins)
    }

    fun updateLevelLine(binIndex: Int, levelIndex: Int, lineIndex: Int, newLine: String, historyMsg: String) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return

        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (levelIndex !in bin.levels.indices) return

        val level = bin.levels[levelIndex]
        if (lineIndex !in level.lines.indices) return

        level.lines[lineIndex] = newLine

        // Save state for history if message provided
        // But modifyBins usually handles history? No, modifyBins just updates state.
        // We usually capture state BEFORE modification?
        // GpuRepository.modifyBins takes care of pushing to undo stack if we managed it there?
        // Actually GpuRepository.modifyBins just validates. 
        // We need to verify if distinct history entry is needed.
        // For now, let's just update.
        
        repository.modifyBins(newBins)
    }

    fun addFrequency(binIndex: Int, atTop: Boolean) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return
        
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (bin.levels.isEmpty()) return
        
        val source = if (atTop) bin.levels.first() else bin.levels.last()
        // source.copyLevel() returns Deep Copy? Level.kt data class with ArrayList needs manual deep copy wrapper?
        // I implemented copyLevel in Level.kt
        val newLevel = source.copyLevel()
        
        if (atTop) bin.levels.add(0, newLevel) else bin.levels.add(newLevel)
        
        // Apply offsets
        val offset = 1 // Logic from GpuTableEditor says 1 for Add, -1 for remove?
        // Wait, GpuTableEditor: offset_initial_level(id, 1).
        offsetInitialLevel(bin, 1)
        if (isLitoOrLagoon()) {
            offsetCaTargetLevel(bin, 1)
        }
        
        repository.modifyBins(newBins)
    }

    fun duplicateFrequency(binIndex: Int, levelIndex: Int) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return
        
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (levelIndex !in bin.levels.indices) return
        
        val source = bin.levels[levelIndex]
        val newLevel = source.copyLevel()
        
        // Insert after the original
        bin.levels.add(levelIndex + 1, newLevel)
        
        // Apply offsets
        offsetInitialLevel(bin, 1)
        if (isLitoOrLagoon()) {
            offsetCaTargetLevel(bin, 1)
        }
        
        repository.modifyBins(newBins)
    }

    fun removeFrequency(binIndex: Int, levelIndex: Int) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return
        
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
        if (levelIndex !in bin.levels.indices) return
        
        bin.levels.removeAt(levelIndex)
        
        // Apply offsets
        offsetInitialLevel(bin, -1)
        if (isLitoOrLagoon()) {
            offsetCaTargetLevel(bin, -1)
        }
        
        repository.modifyBins(newBins)
    }

    fun reorderFrequency(binIndex: Int, fromPos: Int, toPos: Int) {
        val currentBins = repository.bins.value
        if (binIndex !in currentBins.indices) return
        
        val newBins = EditorState.deepCopyBins(currentBins)
        val bin = newBins[binIndex]
         if (fromPos !in bin.levels.indices || toPos !in bin.levels.indices) return
         
         val level = bin.levels.removeAt(fromPos)
         bin.levels.add(toPos, level)
         
         repository.modifyBins(newBins)
    }

    fun undo() = repository.undo()
    fun redo() = repository.redo()

    fun performBatchEdit(action: (MutableList<Bin>) -> Unit) {
        val currentBins = repository.bins.value
        if (currentBins.isEmpty()) return

        val newBins = EditorState.deepCopyBins(currentBins)
        try {
            action(newBins)
            repository.modifyBins(newBins)
        } catch (e: Exception) {
            viewModelScope.launch {
                _errorEvent.emit("Batch edit failed: ${e.message}")
            }
        }
    }

    fun save(showToast: Boolean) {
        viewModelScope.launch {
            try {
                repository.saveTable()
                if (showToast) _toastEvent.emit("Saved successfully")
            } catch (e: Exception) {
                _errorEvent.emit("Save failed: ${e.message}")
            }
        }
    }

    private fun offsetInitialLevel(bin: Bin, offset: Int) {
        for (i in bin.header.indices) {
            val line = bin.header[i]
            if (line.contains("qcom,initial-pwrlevel")) {
                try {
                     val decoded = com.ireddragonicy.konabessnext.utils.DtsHelper.decode_int_line(line)
                     val newValue = decoded.value + offset
                     bin.header[i] = com.ireddragonicy.konabessnext.utils.DtsHelper.encodeIntOrHexLine(decoded.name ?: "", newValue.toString())
                     break
                } catch (e: Exception) {
                    // ignore
                }
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
                     bin.header[i] = com.ireddragonicy.konabessnext.utils.DtsHelper.encodeIntOrHexLine(decoded.name ?: "", newValue.toString())
                     break
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun captureState(desc: String) = repository.saveState(desc)

    private fun isLitoOrLagoon(): Boolean {
        try {
            val current = com.ireddragonicy.konabessnext.core.ChipInfo.current
            val id = current?.id ?: return false
            return id == "lito_v1" ||
                   id == "lito_v2" ||
                   id == "lagoon"
        } catch (e: Exception) {
            return false
        }
    }
}
