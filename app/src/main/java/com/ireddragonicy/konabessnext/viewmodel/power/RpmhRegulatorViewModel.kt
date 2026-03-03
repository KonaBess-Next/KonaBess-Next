package com.ireddragonicy.konabessnext.viewmodel.power

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.power.RpmhRegulator
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RpmhRegulatorViewModel @Inject constructor(
    private val gpuRepository: GpuRepository
) : ViewModel() {

    val rpmhRegulators: StateFlow<List<RpmhRegulator>> = gpuRepository.rpmhRegulators
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateBounds(parentNode: String, subNode: String, newMin: Long, newMax: Long) {
        val regulator = rpmhRegulators.value.firstOrNull {
            it.parentNodeName == parentNode && it.subNodeName == subNode
        } ?: return

        // Skip if nothing changed
        if (regulator.minMicrovolt == newMin && regulator.maxMicrovolt == newMax) return

        val displayName = regulator.regulatorName.ifEmpty { subNode }
        gpuRepository.updateRpmhRegulator(
            parentNodeName = parentNode,
            subNodeName = subNode,
            newMin = newMin,
            newMax = newMax,
            historyDesc = "Updated $displayName limits: min=0x${newMin.toString(16)}, max=0x${newMax.toString(16)}"
        )
    }
}
