package com.ireddragonicy.konabessnext.viewmodel.dtbo

import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.repository.DtboRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SpeakerOverclockViewModel @Inject constructor(private val repository: DtboRepository) : ViewModel() {
    val speakerPanels = repository.speakerPanels
    fun updateReBounds(nodeName: String, fragmentIndex: Int, newMin: Long, newMax: Long) {
        repository.updateSpeakerReBounds(nodeName, fragmentIndex, newMin, newMax)
    }
}
