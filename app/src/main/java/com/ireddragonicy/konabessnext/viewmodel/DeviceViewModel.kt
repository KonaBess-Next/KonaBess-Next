package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.repository.DeviceRepository
import com.ireddragonicy.konabessnext.core.scanner.DtsScanner
import com.ireddragonicy.konabessnext.core.scanner.DtsScanResult
import com.ireddragonicy.konabessnext.model.UiText
import com.ireddragonicy.konabessnext.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val chipRepository: com.ireddragonicy.konabessnext.repository.ChipRepository
) : ViewModel() {

    private val _detectionState = MutableStateFlow<UiState<List<Dtb>>?>(null)
    val detectionState: StateFlow<UiState<List<Dtb>>?> = _detectionState.asStateFlow()

    private val _isFilesExtracted = MutableStateFlow(false)
    val isFilesExtracted: StateFlow<Boolean> = _isFilesExtracted.asStateFlow()

    private val _selectedChipset = MutableStateFlow<Dtb?>(null)
    val selectedChipset: StateFlow<Dtb?> = _selectedChipset.asStateFlow()

    private val _isPrepared = MutableStateFlow(false)
    val isPrepared: StateFlow<Boolean> = _isPrepared.asStateFlow()
    
    // New flow for active DTB
    private val _activeDtbId = MutableStateFlow(-1)
    val activeDtbId: StateFlow<Int> = _activeDtbId.asStateFlow()

    fun detectChipset() {
        _detectionState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repository.setupEnv()
                repository.getBootImage()
                repository.bootImage2dts()
                repository.checkDevice()

                val dtbs = repository.dtbs
                
                // Update active DTB from repository
                _activeDtbId.value = repository.activeDtbId
                
                // If there are literally no DTBs found (rare), then it's a real error.
                if (dtbs.isEmpty()) {
                    _detectionState.value = UiState.Error(UiText.StringResource(R.string.gpu_prep_failed))
                    return@launch
                }

                _detectionState.value = UiState.Success(dtbs)
                _isFilesExtracted.value = true

                // Priority for Auto-Select:
                // 1. Active DTB (if supported)
                // 2. First supported DTB
                // 3. Fallback
                
                val activeDtb = if (repository.activeDtbId != -1) dtbs.find { it.id == repository.activeDtbId } else null
                val firstSupported = dtbs.firstOrNull { it.type.strategyType.isNotEmpty() }
                
                if (activeDtb != null && activeDtb.type.strategyType.isNotEmpty()) {
                    selectChipset(activeDtb)
                } else if (firstSupported != null) {
                    selectChipset(firstSupported)
                } else {
                    // All are unsupported placeholders. We point fallback to the first one.
                    repository.chooseFallbackTarget(dtbs[0].id)
                    _isPrepared.value = false
                }
                
            } catch (e: Exception) {
                Log.e("KonaBessDet", "Detection failed", e)
                _detectionState.value = UiState.Error(UiText.DynamicString(e.message ?: "Unknown error"), e)
                _isFilesExtracted.value = false
                _isPrepared.value = false
            }
        }
    }

    fun selectChipset(dtb: Dtb) {
        repository.chooseTarget(dtb)
        chipRepository.setCurrentChip(dtb.type)
        _selectedChipset.value = dtb
        _isPrepared.value = dtb.type.strategyType.isNotEmpty()
        _isFilesExtracted.value = true
    }

    fun performManualScan(dtbIndex: Int): DtsScanResult {
        val file = repository.getDtsFile(dtbIndex)
        return DtsScanner.scan(file, dtbIndex)
    }

    fun saveManualDefinition(def: ChipDefinition, dtbIndex: Int) {
        repository.setCustomChip(def, dtbIndex)
        chipRepository.setCurrentChip(def)
        val dtb = Dtb(dtbIndex, def)
        _selectedChipset.value = dtb
        _isPrepared.value = true
        _isFilesExtracted.value = true
        
        // Update the list state so the UI reflects the change
        val currentList = repository.dtbs.toMutableList()
        val existingIdx = currentList.indexOfFirst { it.id == dtbIndex }
        if (existingIdx != -1) currentList[existingIdx] = dtb else currentList.add(dtb)
        repository.dtbs.clear()
        repository.dtbs.addAll(currentList)
        
        _detectionState.value = UiState.Success(ArrayList(repository.dtbs))
    }

    fun tryRestoreLastChipset() {
        viewModelScope.launch {
            if (repository.tryRestoreLastChipset()) {
                repository.currentDtb?.let {
                    _selectedChipset.value = it
                    _isPrepared.value = it.type.strategyType.isNotEmpty()
                    _isFilesExtracted.value = true
                    chipRepository.setCurrentChip(it.type)
                    // We don't know the active one until we scan, so we might need a quick scan here
                    // or just leave it -1 until full detect
                }
            }
        }
    }

    private val _repackState = MutableStateFlow<UiState<UiText>?>(null)
    val repackState: StateFlow<UiState<UiText>?> = _repackState.asStateFlow()

    fun packAndFlash(context: Context) {
        _repackState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repository.dts2bootImage()
                repository.writeBootImage()
                _repackState.value = UiState.Success(UiText.StringResource(R.string.repack_flash_success))
            } catch (e: Exception) {
                _repackState.value = UiState.Error(UiText.DynamicString(e.message ?: "Unknown error"), e)
            }
        }
    }

    fun reboot() {
        viewModelScope.launch { try { repository.reboot() } catch (e: Exception) {} }
    }
}