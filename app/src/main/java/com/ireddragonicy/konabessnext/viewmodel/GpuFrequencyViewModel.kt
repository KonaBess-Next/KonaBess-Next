package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * VM specialized for the GUI Editor Fragment.
 * Now delegates everything to the Shared Repository (SSOT).
 */
@HiltViewModel
class GpuFrequencyViewModel @Inject constructor(
    private val repository: GpuRepository
) : ViewModel() {

    // View State
    val selectedBinIndex = MutableStateFlow(-1)
    val selectedLevelIndex = MutableStateFlow(-1)
    val navigationStep = MutableStateFlow(0)

    // Data Delegation
    val binsState = repository.bins.map { UiState.Success(it) }
    val binsLiveData = binsState.asLiveData()
    
    val isDirtyLiveData = repository.isDirty.asLiveData()
    val canUndoLiveData = repository.canUndo.asLiveData()
    val canRedoLiveData = repository.canRedo.asLiveData()
    
    val isDirty = repository.isDirty
    val canUndo = repository.canUndo
    val canRedo = repository.canRedo
    val history = repository.history

    // Actions
    fun save(showToast: Boolean) {
        // Call repository save
        // Toast logic handled in UI layer observing states
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.saveTable()
        }
    }

    fun undo() = repository.undo()
    fun redo() = repository.redo()

    fun batchUpdateFrequencies(updates: List<Triple<Int, Int, Long>>) {
        val paramUpdates = updates.map { (binIdx, levelIdx, freq) ->
            // Convert long freq to hex string "0x..."
            val hexVal = "0x" + java.lang.Long.toHexString(freq)
            // Param key is usually implicit in the caller context, but for frequency it is "qcom,gpu-freq"
            GpuRepository.ParameterUpdate(binIdx, levelIdx, "qcom,gpu-freq", hexVal)
        }
        repository.batchUpdateParameters(paramUpdates)
    }
}