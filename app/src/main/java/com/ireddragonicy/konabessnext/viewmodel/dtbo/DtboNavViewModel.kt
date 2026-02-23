package com.ireddragonicy.konabessnext.viewmodel.dtbo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.repository.DtboRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DtboNavViewModel @Inject constructor(private val repository: DtboRepository) : ViewModel() {
    val currentStep = MutableStateFlow(0) // 0:Dash, 1:Display, 2:Touch, 3:Speaker
    val isDirty = repository.isDirty
    val canUndo = repository.canUndo
    val canRedo = repository.canRedo
    val history = repository.history
    fun loadData() { viewModelScope.launch { repository.loadTable() } }
    fun save() { viewModelScope.launch { repository.saveTable() } }
    fun undo() = repository.undo()
    fun redo() = repository.redo()
}
