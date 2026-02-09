package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// ChipInfo import removed
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import android.widget.Toast
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val chipRepository: com.ireddragonicy.konabessnext.repository.ChipRepository,
    private val settingsRepository: com.ireddragonicy.konabessnext.repository.SettingsRepository
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
    
    // Trigger to signal UI to reload data (increments after import)
    private val _dataReloadTrigger = MutableStateFlow(0)
    val dataReloadTrigger: StateFlow<Int> = _dataReloadTrigger.asStateFlow()

    /** Whether the app is in root mode. Used by UI to show/hide root-only features. */
    val isRootMode: Boolean
        get() = settingsRepository.isRootMode()

    // Flash is only allowed in root mode with a physical DTB (ID >= 0).
    val canFlashOrRepack: StateFlow<Boolean> = _activeDtbId.map { id ->
        id >= 0 && settingsRepository.isRootMode()
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    companion object {
        private const val TAG = "KonaBessVM"
        // FDT (Flattened Device Tree) magic number
        private val DTB_MAGIC = byteArrayOf(0xD0.toByte(), 0x0D.toByte(), 0xFE.toByte(), 0xED.toByte())
        // Android boot image magic
        private const val BOOT_MAGIC = "ANDROID!"
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** Resolve display name from a content URI via ContentResolver. */
    private fun resolveDisplayName(context: Context, uri: Uri, fallback: String = "unknown"): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null } ?: uri.lastPathSegment ?: fallback
    }

    /** Finalize a successful DTB detection/import: select best chipset and update state. */
    private fun finalizeDetection(dtbs: List<Dtb>) {
        _activeDtbId.value = repository.activeDtbId
        _detectionState.value = UiState.Success(dtbs)
        _isFilesExtracted.value = true

        val firstSupported = dtbs.firstOrNull { it.type.strategyType.isNotEmpty() }
        if (firstSupported != null) {
            selectChipset(firstSupported)
        } else {
            repository.chooseFallbackTarget(dtbs[0].id)
            _isPrepared.value = false
        }
        _dataReloadTrigger.value++
    }

    // ── Import / Detection ───────────────────────────────────────────

    fun importExternalDts(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                _detectionState.value = UiState.Loading

                // Best-effort persistable permission (not all providers support it)
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { /* OK — one-shot grant still works */ }

                val displayName = resolveDisplayName(context, uri, "imported")
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: run {
                        _detectionState.value = null
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Cannot open file — permission denied", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                inputStream.use { stream ->
                    val dtb = repository.importExternalDts(stream, displayName)
                    // Repository already set: dtsPath, currentDtb, currentChip, prepared
                    _selectedChipset.value = dtb
                    _isPrepared.value = dtb.type.strategyType.isNotEmpty()
                    _isFilesExtracted.value = true
                    _detectionState.value = UiState.Success(ArrayList(repository.dtbs))
                    _dataReloadTrigger.value++

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import Successful: ${dtb.type.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _detectionState.value = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun exportBootImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // 1. Repack to valid image
                val bootImg = repository.dts2bootImage()
                
                // 2. Write to user URI
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        bootImg.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export Successful", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                 withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
                 }
            }
        }
    }

    fun detectChipset() {
        _detectionState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repository.setupEnv()
                if (isRootMode) repository.getBootImage()
                repository.bootImage2dts()
                repository.checkDevice()

                val dtbs = repository.dtbs
                if (dtbs.isEmpty()) {
                    _detectionState.value = UiState.Error(UiText.StringResource(R.string.gpu_prep_failed))
                    return@launch
                }

                // Prefer active DTB (matching running device) > first supported > fallback
                val activeDtb = if (repository.activeDtbId != -1) dtbs.find { it.id == repository.activeDtbId } else null
                if (activeDtb != null && activeDtb.type.strategyType.isNotEmpty()) {
                    _activeDtbId.value = repository.activeDtbId
                    _detectionState.value = UiState.Success(dtbs)
                    _isFilesExtracted.value = true
                    selectChipset(activeDtb)
                    _dataReloadTrigger.value++
                } else {
                    finalizeDetection(dtbs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Detection failed", e)
                _detectionState.value = UiState.Error(UiText.DynamicString(e.message ?: "Unknown error"), e)
                _isFilesExtracted.value = false
                _isPrepared.value = false
            }
        }
    }

    /**
     * Smart import: auto-detects file type (boot image vs DTS/DTB text) and routes
     * to the correct handler. Called from the unified non-root import button.
     */
    fun importFile(context: Context, uri: Uri) {
        _detectionState.value = UiState.Loading
        viewModelScope.launch {
            try {
                // Peek first bytes to auto-detect file type
                val magic = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val header = ByteArray(16)
                    val read = stream.read(header)
                    if (read >= 4) header.copyOfRange(0, read) else null
                } ?: throw IOException("Cannot read file — permission denied or file inaccessible")

                val isBootImage = magic.size >= 8 &&
                    String(magic, 0, 8, Charsets.US_ASCII).startsWith(BOOT_MAGIC)
                val isDtb = magic.size >= 4 &&
                    magic[0] == DTB_MAGIC[0] && magic[1] == DTB_MAGIC[1] &&
                    magic[2] == DTB_MAGIC[2] && magic[3] == DTB_MAGIC[3]

                when {
                    isBootImage && !isRootMode -> {
                        // Boot images need root mode for unpacking
                        _detectionState.value = null
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Boot images require Root Mode.\nPlease import a .dts or .dtb file.", Toast.LENGTH_LONG).show()
                        }
                    }
                    isBootImage -> importBootImage(context, uri)
                    else -> {
                        // Text DTS or binary DTB — both handled by importExternalDts
                        _detectionState.value = null
                        importExternalDts(context, uri)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Smart import failed", e)
                _detectionState.value = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Import a boot/vendor_boot image from user storage (non-root mode).
     * Copies the image to filesDir, unpacks it, detects DTBs, and sets up the editor.
     */
    private fun importBootImage(context: Context, uri: Uri) {
        _detectionState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val displayName = resolveDisplayName(context, uri, "boot.img")
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot read file — permission denied")

                inputStream.use { stream ->
                    repository.setupEnv()
                    repository.importBootImage(stream, displayName)
                }

                repository.bootImage2dts()
                repository.checkDevice()

                val dtbs = repository.dtbs
                if (dtbs.isEmpty()) {
                    _detectionState.value = UiState.Error(UiText.DynamicString("No DTBs found in imported image."))
                    return@launch
                }

                finalizeDetection(dtbs)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Boot image imported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Boot image import failed", e)
                _detectionState.value = UiState.Error(UiText.DynamicString("Import failed: ${e.localizedMessage}"), e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
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

    suspend fun performManualScan(dtbIndex: Int): DtsScanResult {
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

    /**
     * Load previously saved DTS files from disk (non-root mode startup).
     * If saved files exist, auto-selects the last one and transitions to the editor.
     */
    fun loadSavedDts() {
        viewModelScope.launch {
            try {
                repository.setupEnv()
                val savedDtbs = repository.loadSavedDts()
                if (savedDtbs.isNotEmpty()) {
                    _detectionState.value = UiState.Success(ArrayList(repository.dtbs))
                    selectChipset(savedDtbs.last())
                    _isFilesExtracted.value = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load saved DTS", e)
            }
        }
    }

    /**
     * Delete an imported/saved DTS by virtual ID. Updates UI state accordingly.
     */
    fun deleteDts(dtbId: Int) {
        if (!repository.deleteSavedDts(dtbId)) return
        val remaining = repository.dtbs
        if (remaining.isEmpty()) {
            _detectionState.value = null
            _isFilesExtracted.value = false
            _selectedChipset.value = null
            _isPrepared.value = false
        } else {
            _detectionState.value = UiState.Success(ArrayList(remaining))
            if (_selectedChipset.value?.id == dtbId) {
                selectChipset(remaining.last())
            }
        }
        _dataReloadTrigger.value++
    }

    fun tryRestoreLastChipset() {
        viewModelScope.launch {
            if (repository.tryRestoreLastChipset()) {
                repository.currentDtb?.let {
                    _selectedChipset.value = it
                    _isPrepared.value = it.type.strategyType.isNotEmpty()
                    _isFilesExtracted.value = true
                    chipRepository.setCurrentChip(it.type)
                }
            }
        }
    }

    private val _repackState = MutableStateFlow<UiState<UiText>?>(null)
    val repackState: StateFlow<UiState<UiText>?> = _repackState.asStateFlow()

    fun packAndFlash(context: Context) {
        if (!isRootMode) {
            _repackState.value = UiState.Error(UiText.DynamicString("Flash to device is not available in Non-Root mode. Use Export instead."))
            return
        }
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

    fun getDeviceModel(): String = repository.getCurrent("model")
    fun getDeviceBrand(): String = repository.getCurrent("brand")

    fun clearRepackState() {
        _repackState.value = null
    }
}