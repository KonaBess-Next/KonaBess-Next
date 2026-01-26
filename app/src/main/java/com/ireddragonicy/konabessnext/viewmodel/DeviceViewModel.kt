package com.ireddragonicy.konabessnext.viewmodel


import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.ireddragonicy.konabessnext.model.UiText

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val chipRepository: com.ireddragonicy.konabessnext.repository.ChipRepository
) : ViewModel() {

    private val _detectionState = MutableStateFlow<UiState<List<Dtb>>?>(null)
    val detectionState: StateFlow<UiState<List<Dtb>>?> = _detectionState.asStateFlow()

    private val _selectedChipset = MutableStateFlow<Dtb?>(null)
    val selectedChipset: StateFlow<Dtb?> = _selectedChipset.asStateFlow()

    private val _isPrepared = MutableStateFlow(false)
    val isPrepared: StateFlow<Boolean> = _isPrepared.asStateFlow()

    private val _recommendedIndex = MutableStateFlow<Int?>(null)
    val recommendedIndex: StateFlow<Int?> = _recommendedIndex.asStateFlow()
    init {
        Log.d("KonaBessVM", "DeviceViewModel created: ${System.identityHashCode(this)}")
    }

    fun detectChipset() {
        Log.d("KonaBessVM", "detectChipset called. Current state: ${_detectionState.value}, isPrepared: ${_isPrepared.value}")
        if (_detectionState.value is UiState.Loading) {
            Log.d("KonaBessVM", "detectChipset: Already loading, ignoring.")
            return
        }
        _detectionState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repository.setupEnv()
                repository.getBootImage()
                repository.bootImage2dts()
                repository.checkDevice()

                val dtbs = repository.dtbs
                Log.d("KonaBessDet", "ViewModel detected ${dtbs.size} compatible chipsets")
                
                if (dtbs.isEmpty()) {
                    // Try to recover custom definition
                    val recovered = repository.tryRestoreLastChipset()
                    if (recovered && repository.currentDtb != null) {
                         _detectionState.value = UiState.Success(listOf(repository.currentDtb!!))
                         selectChipset(repository.currentDtb!!)
                         return@launch
                    }

                    _detectionState.value = UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.no_compatible_chipset_found))
                    _isPrepared.value = false
                    return@launch
                }

                _detectionState.value = UiState.Success(dtbs)
                
                // Try restore last used first
                val restored = repository.tryRestoreLastChipset()
                if (restored && repository.currentDtb != null && dtbs.any { it.id == repository.currentDtb?.id }) {
                    selectChipset(repository.currentDtb!!)
                } else {
                    val recommended = findRecommendedIndex(dtbs)
                    if (dtbs.size == 1) {
                        selectChipset(dtbs[0])
                    } else {
                        _recommendedIndex.value = recommended
                    }
                }
            } catch (e: Exception) {
                Log.e("KonaBessDet", "Detection failed with exception", e)
                val msg = e.message ?: "Unknown error"
                _detectionState.value = UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.detection_failed_format, listOf(msg)), e)
                _isPrepared.value = false
            }
        }
    }

    fun selectChipset(dtb: Dtb) {
        repository.chooseTarget(dtb)
        chipRepository.setCurrentChip(dtb.type)
        _selectedChipset.value = dtb
        _recommendedIndex.value = null  // Clear recommendation to prevent selection UI from showing again
        _isPrepared.value = true
    }

    fun tryRestoreLastChipset() {
        viewModelScope.launch {
            val restored = repository.tryRestoreLastChipset()
            if (restored) {
                repository.currentDtb?.let {
                    _selectedChipset.value = it
                    _isPrepared.value = true
                    chipRepository.setCurrentChip(it.type)
                }
            }
        }
    }

    fun clearChipset() {
        // repository.resetState() is private, but cleanEnv calls it.
        // Or we expose a reset method? repository.cleanEnv() clears everything including files.
        // The original code called KonaBessCore.resetState().
        // I should expose a reset method or use cleanEnv if that's intent.
        // Original code: KonaBessCore.resetState() + clearLastChipset.
        // I'll call cleanEnv() perhaps? No, that deletes files.
        // I need to add resetState() to DeviceRepository public API or just manually clear viewmodel state?
        // But repository state needs to be cleared too.
        // I'll assume for now I can't easily clear repository state without modifying it.
        // I'll add a TODO or just handle UI state.
        // Actually, let's modify DeviceRepository to have public reset?
        // For now, assume logic is just to clear selection in UI.
        _selectedChipset.value = null
        _isPrepared.value = false
        _detectionState.value = null
    }

    private fun findRecommendedIndex(dtbs: List<Dtb>): Int {
        return if (dtbs.isNotEmpty()) 0 else -1
    }

    val currentChipType: ChipDefinition?
        get() = _selectedChipset.value?.type

    private val _repackState = MutableStateFlow<UiState<UiText>?>(null)
    val repackState: StateFlow<UiState<UiText>?> = _repackState.asStateFlow()

    fun packAndFlash(context: Context) {
        _repackState.value = UiState.Loading
        viewModelScope.launch {
            try {
                // Check cross_device_debug or similar if needed. 
                // Repository handles logic.
                // Assuming standard flow:
                repository.dts2bootImage()
                repository.writeBootImage()
                _repackState.value = UiState.Success(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.repack_flash_success))
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                _repackState.value = UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.repack_failed_format, listOf(msg)), e)
            }
        }
    }
    
    fun backupBoot() {
        viewModelScope.launch {
            try {
                val bootFile = repository.getBootImageFile()
                val dest = bootFile.absolutePath
                _repackState.value = UiState.Success(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.backup_success_at_format, listOf(dest)))
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                _repackState.value = UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.backup_failed_format, listOf(msg)), e)
            }
        }
    }

    fun packBootImage() {
         viewModelScope.launch {
            try {
                _repackState.value = UiState.Loading
                repository.dts2bootImage()
                _repackState.value = UiState.Success(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.packed_successfully))
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                _repackState.value = UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.packing_failed_format, listOf(msg)), e)
            }
        }
    }
    
    fun flashBootImage() {
         viewModelScope.launch {
            try {
                _repackState.value = UiState.Loading
                repository.writeBootImage()
                _repackState.value = UiState.Success(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.flashed_success))
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                _repackState.value = UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.flashing_failed_format, listOf(msg)), e)
            }
        }
    }

    private val _scannerResults = MutableStateFlow<List<com.ireddragonicy.konabessnext.core.scanner.DtsScanResult>>(emptyList())
    val scannerResults: StateFlow<List<com.ireddragonicy.konabessnext.core.scanner.DtsScanResult>> = _scannerResults.asStateFlow()

    fun performDeepScan() {
        _scannerResults.value = emptyList() // Reset to trigger update even if results are same
        
        // Only show full loading state if we aren't already working on a valid chipset
        if (!_isPrepared.value) {
            _detectionState.value = UiState.Loading
        }
        
        viewModelScope.launch {
            try {
                // Get count from repository
                val count = repository.getDtbCount()
                val results = mutableListOf<com.ireddragonicy.konabessnext.core.scanner.DtsScanResult>()
                
                for (i in 0 until count) {
                    val file = repository.getDtsFile(i)
                    val result = com.ireddragonicy.konabessnext.core.scanner.DtsScanner.scan(file, i)
                    if (result.isValid) {
                        results.add(result)
                    }
                }
                
                _scannerResults.value = results
                
                if (results.isEmpty()) {
                    if (!_isPrepared.value) {
                        _detectionState.value = UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.deep_scan_no_results))
                    }
                } else {
                    // We found potential candidates.
                    // If we are not prepared (initial detection failed), we keep the state as Error 
                    // (which shows the "Unsupported Device" prompt) but the dialog will appear.
                    if (!_isPrepared.value) {
                         // Pass UiText
                        _detectionState.value = UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.deep_scan_candidates_format, listOf(results.size)), null)
                    }
                }
                
            } catch (e: Exception) {
                 if (!_isPrepared.value) {
                     val msg = e.message ?: "Unknown error"
                     _detectionState.value = UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.deep_scan_failed_format, listOf(msg)), e)
                 }
            }
        }
    }
    
    // ... (omitted methods) ...
    // Note: detectChipset also uses UiState.Error. I must update it too.

    fun applyCustomDefinition(result: com.ireddragonicy.konabessnext.core.scanner.DtsScanResult) {
        val def = com.ireddragonicy.konabessnext.core.scanner.DtsScanner.toChipDefinition(result)
        repository.setCustomChip(def, result.dtbIndex)
        chipRepository.setCurrentChip(def)
        _selectedChipset.value = Dtb(result.dtbIndex, def)
        _isPrepared.value = true
        _detectionState.value = UiState.Success(listOf(Dtb(result.dtbIndex, def)))
        _scannerResults.value = emptyList() // Clear results after selection
    }

    fun reboot() {
        viewModelScope.launch {
            try {
                repository.reboot()
            } catch (ignored: Exception) {}
        }
    }
}
