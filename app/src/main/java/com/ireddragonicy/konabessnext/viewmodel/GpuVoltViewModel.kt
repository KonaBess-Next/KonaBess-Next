package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.EditorState
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.core.model.DomainResult
import com.ireddragonicy.konabessnext.utils.UserMessageManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GpuVoltViewModel @Inject constructor(
    private val repository: GpuRepository
) : ViewModel() {

    val opps: StateFlow<List<Opp>> = repository.opps
    val isDirty: StateFlow<Boolean> = repository.isDirty
    val canUndo: StateFlow<Boolean> = repository.canUndo
    val canRedo: StateFlow<Boolean> = repository.canRedo

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    fun updateOpp(index: Int, newFreq: Long, newVolt: Long) {
        val currentOpps = repository.opps.value
        if (index !in currentOpps.indices) return

        val newOpps = EditorState.deepCopyOpps(currentOpps)
        val opp = newOpps[index]
        opp.frequency = newFreq
        opp.volt = newVolt

        repository.updateOpps(newOpps)
    }

    fun addOpp(freq: Long, volt: Long) {
        val currentOpps = repository.opps.value
        val newOpps = EditorState.deepCopyOpps(currentOpps)
        newOpps.add(Opp(freq, volt))
        // Optional: Sort by frequency? 
        // Typically OPP tables are sorted.
        newOpps.sortBy { it.frequency }
        
        repository.updateOpps(newOpps)
    }

    fun removeOpp(index: Int) {
        val currentOpps = repository.opps.value
        if (index !in currentOpps.indices) return

        val newOpps = EditorState.deepCopyOpps(currentOpps)
        newOpps.removeAt(index)
        
        repository.updateOpps(newOpps)
    }

    fun undo() = repository.undo()
    fun redo() = repository.redo()

    fun save() {
        viewModelScope.launch {
            when (val result = repository.saveTable()) {
                is DomainResult.Success -> _toastEvent.emit("Saved successfully")
                is DomainResult.Failure -> _errorEvent.emit("Save failed: ${result.error.message}")
            }
        }
    }
}
