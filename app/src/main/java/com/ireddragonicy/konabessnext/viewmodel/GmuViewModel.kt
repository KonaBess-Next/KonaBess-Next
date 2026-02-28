package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.model.gmu.GmuFreqPair
import com.ireddragonicy.konabessnext.model.gmu.GmuFreqTable
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class GmuViewModel @Inject constructor(
    private val repository: GpuRepository
) : ViewModel() {

    val gmuTables: StateFlow<List<GmuFreqTable>> = repository.gmuTables

    fun editPair(nodeName: String, index: Int, newFreqKHz: Long, newVote: Long) {
        val currentTable = gmuTables.value.firstOrNull { it.nodeName == nodeName } ?: return
        if (index < 0 || index >= currentTable.pairs.size) return

        val updated = currentTable.pairs.toMutableList()
        updated[index] = GmuFreqPair(newFreqKHz * 1000L, newVote)
        updated.sortBy { it.freqHz } // Usually sorted
        
        repository.updateGmuTable(nodeName, updated, "Updated GMU Freq/Vote in $nodeName")
    }

    fun addPair(nodeName: String, freqKHz: Long, vote: Long) {
        val currentTable = gmuTables.value.firstOrNull { it.nodeName == nodeName } ?: return
        val updated = currentTable.pairs.toMutableList()
        updated.add(GmuFreqPair(freqKHz * 1000L, vote))
        updated.sortBy { it.freqHz }

        repository.updateGmuTable(nodeName, updated, "Added GMU Freq to $nodeName")
    }

    fun deletePair(nodeName: String, index: Int) {
        val currentTable = gmuTables.value.firstOrNull { it.nodeName == nodeName } ?: return
        if (index < 0 || index >= currentTable.pairs.size) return

        val updated = currentTable.pairs.toMutableList()
        updated.removeAt(index)
        if (updated.isEmpty()) return // Safety to avoid empty array

        repository.updateGmuTable(nodeName, updated, "Deleted GMU Freq from $nodeName")
    }
}
