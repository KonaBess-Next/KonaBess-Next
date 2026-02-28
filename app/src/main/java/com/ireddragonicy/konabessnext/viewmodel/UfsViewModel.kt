package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.model.ufs.UfsFreqTable
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel for managing UFS frequency tables.
 */
@HiltViewModel
class UfsViewModel @Inject constructor(
    private val gpuRepository: GpuRepository
) : ViewModel() {

    /** Current list of UFS tables parsed from the DTS file. */
    val ufsTables: StateFlow<List<UfsFreqTable>> = gpuRepository.ufsTables

    /**
     * Edits a clock's min and max frequencies.
     * The inputs are provided in MHz and converted back to Hz.
     */
    fun editClockFrequencies(
        nodeName: String,
        clockName: String,
        minIndex: Int,
        newMinMHz: Long,
        maxIndex: Int,
        newMaxMHz: Long
    ) {
        val newMinHz = newMinMHz * 1_000_000L
        val newMaxHz = newMaxMHz * 1_000_000L
        val historyDesc = "Updated $clockName in UFS to $newMinMHz - $newMaxMHz MHz"
        gpuRepository.updateUfsClockFrequencies(nodeName, minIndex, newMinHz, maxIndex, newMaxHz, historyDesc)
    }
}
