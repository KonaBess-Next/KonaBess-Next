package com.ireddragonicy.konabessnext.viewmodel.gpu

import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.model.gpu.GpuBandwidthTable
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel for the GPU Bus Bandwidth Tables (DDR & CNOC) Editor screen.
 * Delegates all data operations to [GpuRepository].
 */
@HiltViewModel
class GpuBandwidthViewModel @Inject constructor(
    private val repository: GpuRepository
) : ViewModel() {

    val bandwidthTables: StateFlow<List<GpuBandwidthTable>> = repository.gpuBandwidthTables

    /**
     * Replaces a single bandwidth at [index] in the table identified by [propertyName].
     */
    fun editBandwidth(propertyName: String, index: Int, newBandwidth: Long) {
        val currentTable = bandwidthTables.value.firstOrNull { it.propertyName == propertyName } ?: return
        if (index < 0 || index >= currentTable.bandwidths.size) return

        val updated = currentTable.bandwidths.toMutableList()
        updated[index] = newBandwidth
        repository.updateGpuBandwidthTable(
            propertyName = propertyName,
            newBandwidths = updated,
            historyDesc = "Updated $propertyName bandwidth[$index] to $newBandwidth"
        )
    }
}
