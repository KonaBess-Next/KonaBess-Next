package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.model.isp.CamIspFreqTable
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CamIspViewModel @Inject constructor(
    private val repository: GpuRepository
) : ViewModel() {

    val ispTables: StateFlow<List<CamIspFreqTable>> = repository.ispTables

    fun editFrequency(nodeName: String, index: Int, newFrequencyHz: Long) {
        val currentTable = ispTables.value.firstOrNull { it.nodeName == nodeName } ?: return
        if (index < 0 || index >= currentTable.freqHzList.size) return

        val updated = currentTable.freqHzList.toMutableList()
        updated[index] = newFrequencyHz
        
        // Let's format MHz for history
        val mhz = newFrequencyHz / 1_000_000.0
        val mhzStr = if (mhz == mhz.toLong().toDouble()) mhz.toLong().toString() else String.format(java.util.Locale.US, "%.1f", mhz)

        repository.updateIspTable(
            nodeName = nodeName,
            newFrequencies = updated,
            historyDesc = "Updated $nodeName freq[$index] to ${mhzStr} MHz"
        )
    }
}
