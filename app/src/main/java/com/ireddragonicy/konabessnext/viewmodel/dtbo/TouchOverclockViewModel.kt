package com.ireddragonicy.konabessnext.viewmodel.dtbo

import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.repository.DtboRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TouchOverclockViewModel @Inject constructor(private val repository: DtboRepository) : ViewModel() {
    val touchPanels = repository.touchPanels
    fun updateFrequency(nodeName: String, fragmentIndex: Int, newFrequency: Long) {
        repository.updateTouchSpiFrequency(nodeName, fragmentIndex, newFrequency)
    }
}
