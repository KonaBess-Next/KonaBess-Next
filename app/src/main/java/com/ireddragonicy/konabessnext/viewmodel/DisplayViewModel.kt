package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.display.DisplayPanel
import com.ireddragonicy.konabessnext.model.display.DisplayTiming
import com.ireddragonicy.konabessnext.repository.DisplayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for display overclock via DTBO.
 *
 * This is the display counterpart of [SharedGpuViewModel].
 * It exposes display panels, timing snapshots, and mutation methods to the UI layer,
 * delegating all data/logic to [DisplayRepository].
 */
@HiltViewModel
class DisplayViewModel @Inject constructor(
    private val repository: DisplayRepository
) : ViewModel() {

    // --- State from Repository ---

    val panels: StateFlow<List<DisplayPanel>> = repository.panels

    /** Only panels that have timings (the ones we can edit). */
    val panelsWithTimings: StateFlow<List<DisplayPanel>> = repository.panels
        .map { all -> all.filter { it.timings.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPanelIndex: StateFlow<Int> = repository.selectedPanelIndex

    val selectedTimingIndex: StateFlow<Int> = repository.selectedTimingIndex

    val displaySnapshot: StateFlow<DisplayRepository.DisplaySnapshot?> =
        combine(
            repository.dtsLines,
            repository.parsedTree,
            repository.selectedPanelIndex,
            repository.selectedTimingIndex
        ) { _, _, _, _ ->
            repository.getDisplaySnapshot()
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isDirty: StateFlow<Boolean> = repository.isDirty
    val canUndo: StateFlow<Boolean> = repository.canUndo
    val canRedo: StateFlow<Boolean> = repository.canRedo
    val history: StateFlow<List<String>> = repository.history

    // --- Loading / State ---

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Actions ---

    fun loadData() {
        _isLoading.value = true
        _errorMessage.value = null
        repository.selectPanel(0)  // Reset selection on fresh load (also resets timing)
        viewModelScope.launch {
            val result = repository.loadTable()
            when (result) {
                is com.ireddragonicy.konabessnext.core.model.DomainResult.Failure -> {
                    _errorMessage.value = result.error.message
                }
                is com.ireddragonicy.konabessnext.core.model.DomainResult.Success -> {
                    _errorMessage.value = null
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Select a panel by its index in the panels-with-timings list.
     */
    fun selectPanel(index: Int) {
        repository.selectPanel(index)
    }

    /**
     * Select a timing by its index within the current panel's timings list.
     */
    fun selectTiming(index: Int) {
        repository.selectTiming(index)
    }

    /**
     * Quick framerate update for the first panel's first timing.
     */
    fun updatePanelFramerate(newFps: Int): Boolean {
        if (newFps <= 0) return false
        return repository.updatePanelFramerate(newFps)
    }

    /**
     * Update the panel clockrate for the first panel's first timing.
     */
    fun updatePanelClockRate(newClock: Long): Boolean {
        return repository.updatePanelClockRate(newClock)
    }

    /**
     * Update any timing property by name.
     */
    fun updateTimingProperty(
        propertyName: String,
        rawValue: String,
        panelNodeName: String? = null,
        timingIndex: Int? = null,
        fragmentIndex: Int? = null
    ): Boolean {
        val snapshot = displaySnapshot.value ?: return false
        val targetPanel = panelNodeName ?: snapshot.panelNodeName
        val targetTiming = timingIndex ?: snapshot.timingIndex
        val targetFragment = fragmentIndex ?: snapshot.fragmentIndex

        return repository.updateTimingProperty(
            panelNodeName = targetPanel,
            timingIndex = targetTiming,
            propertyName = propertyName,
            newValue = rawValue,
            fragmentIndex = targetFragment
        )
    }

    /**
     * Update a panel-level property (outside timing blocks).
     */
    fun updatePanelProperty(
        propertyName: String,
        rawValue: String,
        panelNodeName: String? = null,
        fragmentIndex: Int? = null
    ): Boolean {
        val snapshot = displaySnapshot.value ?: return false
        val targetPanel = panelNodeName ?: snapshot.panelNodeName
        val targetFragment = fragmentIndex ?: snapshot.fragmentIndex

        return repository.updatePanelProperty(
            panelNodeName = targetPanel,
            propertyName = propertyName,
            newValue = rawValue,
            fragmentIndex = targetFragment
        )
    }

    /**
     * Update the DFPS (Dynamic FPS) supported list.
     */
    fun updateDfpsList(fpsList: List<Int>): Boolean {
        return repository.updateDfpsList(fpsList)
    }

    fun undo() = repository.undo()
    fun redo() = repository.redo()

    fun save() {
        viewModelScope.launch {
            repository.saveTable()
        }
    }
}
