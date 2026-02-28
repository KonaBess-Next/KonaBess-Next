package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.model.memory.MemoryFreqTable
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel for the DDR / LLCC / DDR-QoS Memory Editor screen.
 * Delegates all data operations to [GpuRepository].
 */
@HiltViewModel
class DdrViewModel @Inject constructor(
    private val repository: GpuRepository
) : ViewModel() {

    val memoryTables: StateFlow<List<MemoryFreqTable>> = repository.memoryTables

    /**
     * Replaces a single frequency at [index] in the table identified by [nodeName].
     */
    fun editFrequency(nodeName: String, index: Int, newFrequencyKHz: Long) {
        val currentTable = memoryTables.value.firstOrNull { it.nodeName == nodeName } ?: return
        if (index < 0 || index >= currentTable.frequenciesKHz.size) return

        val updated = currentTable.frequenciesKHz.toMutableList()
        updated[index] = newFrequencyKHz
        repository.updateMemoryTable(
            nodeName = nodeName,
            newFrequencies = updated,
            historyDesc = "Updated $nodeName freq[$index] to ${newFrequencyKHz}kHz"
        )
    }

    /**
     * Adds a new frequency with the user-specified value.
     */
    fun addFrequency(nodeName: String, newFrequencyKHz: Long) {
        repository.addMemoryFrequency(
            nodeName = nodeName,
            newFrequencyKHz = newFrequencyKHz,
            historyDesc = "Added ${newFrequencyKHz}kHz to $nodeName"
        )
    }

    /**
     * Deletes the frequency at [index] from the table identified by [nodeName].
     */
    fun deleteFrequency(nodeName: String, index: Int) {
        repository.deleteMemoryFrequency(
            nodeName = nodeName,
            index = index,
            historyDesc = "Deleted freq[$index] from $nodeName"
        )
    }
}
