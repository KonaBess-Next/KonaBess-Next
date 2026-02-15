package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// ChipInfo import removed
import com.ireddragonicy.konabessnext.core.model.AppError
import com.ireddragonicy.konabessnext.core.model.DomainResult
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.model.TargetPartition
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

    private val _availablePartitions = MutableStateFlow(repository.availablePartitions)
    val availablePartitions: StateFlow<List<TargetPartition>> = _availablePartitions.asStateFlow()

    private val _selectedPartition = MutableStateFlow(repository.selectedPartition)
    val selectedPartition: StateFlow<TargetPartition> = _selectedPartition.asStateFlow()

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
        private const val DTBO_TABLE_MAGIC = 0xD7B7AB1E.toInt()
        // Android boot image magic
        private const val BOOT_MAGIC = "ANDROID!"
    }

    private fun syncPartitionState() {
        _availablePartitions.value = repository.availablePartitions
        _selectedPartition.value = repository.selectedPartition
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

    private fun mapAppErrorToMessage(error: AppError): String {
        return when (error) {
            is AppError.IoError ->
                "Storage/file error. Check file permissions and available disk space.\n${error.message}"
            is AppError.ShellError ->
                "Shell command failed. Check Magisk/root shell availability and retry.\n${error.message}"
            is AppError.ParsingError ->
                "Parsing failed. Verify the DTS/DTB or boot image format.\n${error.message}"
            is AppError.BootImageError ->
                "Boot image processing failed. Verify image compatibility and repack inputs.\n${error.message}"
            is AppError.RootAccessError ->
                "Root access required for this action. Open Magisk and grant root permission."
            is AppError.UnknownError ->
                "Unexpected error. ${error.message}"
        }
    }

    private fun mapAppErrorToUiText(error: AppError): UiText {
        return UiText.DynamicString(mapAppErrorToMessage(error))
    }

    private fun applyDetectionFailure(error: AppError) {
        _detectionState.value = UiState.Error(mapAppErrorToUiText(error), error.cause)
        _isFilesExtracted.value = false
        _isPrepared.value = false
    }

    private fun applyRepackFailure(error: AppError) {
        _repackState.value = UiState.Error(mapAppErrorToUiText(error), error.cause)
    }

    /** Finalize a successful DTB detection/import: select best chipset and update state. */
    private fun finalizeDetection(dtbs: List<Dtb>) {
        syncPartitionState()
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
            _detectionState.value = UiState.Loading

            // Best-effort persistable permission (not all providers support it)
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* One-shot permission still works. */ }

            val displayName = resolveDisplayName(context, uri, "imported")
            val inputStream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                ?: run {
                    _detectionState.value = null
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.cannot_open_file_permission_denied), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

            inputStream.use { stream ->
                when (val result = repository.importExternalDts(stream, displayName)) {
                    is DomainResult.Success -> {
                        syncPartitionState()
                        val dtb = result.data
                        // Repository already set: dtsPath, currentDtb, currentChip, prepared
                        _selectedChipset.value = dtb
                        _isPrepared.value = dtb.type.strategyType.isNotEmpty()
                        _isFilesExtracted.value = true
                        _detectionState.value = UiState.Success(ArrayList(repository.dtbs))
                        _dataReloadTrigger.value++

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.import_successful_format, dtb.type.name), Toast.LENGTH_SHORT).show()
                        }
                    }

                    is DomainResult.Failure -> {
                        Log.e(TAG, "Import failed: ${result.error.message}", result.error.cause)
                        applyDetectionFailure(result.error)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, mapAppErrorToMessage(result.error), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    fun exportBootImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            when (val repackResult = repository.dts2bootImage()) {
                is DomainResult.Failure -> {
                    applyRepackFailure(repackResult.error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.export_failed_format, mapAppErrorToMessage(repackResult.error)), Toast.LENGTH_LONG).show()
                    }
                }

                is DomainResult.Success -> {
                    // 2. Write to user URI
                    val writeResult = withContext(Dispatchers.IO) {
                        runCatching {
                            val out = context.contentResolver.openOutputStream(uri)
                                ?: throw IOException("Cannot open destination URI for writing.")
                            out.use { output ->
                                repackResult.data.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }

                    if (writeResult.isSuccess) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.export_successful), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errMsg = writeResult.exceptionOrNull()?.localizedMessage ?: "Unknown export error"
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.export_failed_error_format, errMsg), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    suspend fun tryExportBootImageToDefault(context: Context): Boolean {
        val defaultUriStr = settingsRepository.getDefaultExportUri() ?: return false
        val defaultUri = Uri.parse(defaultUriStr)
        
        return try {
            withContext(Dispatchers.IO) {
                val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, defaultUri)
                if (dir == null || !dir.canWrite()) return@withContext false

                when (val repackResult = repository.dts2bootImage()) {
                    is DomainResult.Failure -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.export_failed_format, mapAppErrorToMessage(repackResult.error)), Toast.LENGTH_LONG).show()
                        }
                        false
                    }
                    is DomainResult.Success -> {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                        val name = "boot_repack_$timestamp.img"
                        val newFile = dir.createFile("application/octet-stream", name) 
                            ?: return@withContext false
                        
                        val writeResult = runCatching {
                            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                                repackResult.data.inputStream().use { input -> input.copyTo(output) }
                            }
                        }

                        if (writeResult.isSuccess) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Saved to ${settingsRepository.getExportPathDisplay()}/$name", Toast.LENGTH_SHORT).show()
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun detectChipset() {
        _detectionState.value = UiState.Loading
        viewModelScope.launch {
            when (val setupResult = repository.setupEnv()) {
                is DomainResult.Failure -> {
                    Log.e(TAG, "Environment setup failed: ${setupResult.error.message}", setupResult.error.cause)
                    applyDetectionFailure(setupResult.error)
                    return@launch
                }
                is DomainResult.Success -> Unit
            }

            if (isRootMode) {
                when (val bootPullResult = repository.getBootImage()) {
                    is DomainResult.Failure -> {
                        Log.e(TAG, "Boot image pull failed: ${bootPullResult.error.message}", bootPullResult.error.cause)
                        applyDetectionFailure(bootPullResult.error)
                        return@launch
                    }
                    is DomainResult.Success -> Unit
                }
            }

            when (val splitResult = repository.bootImage2dts()) {
                is DomainResult.Failure -> {
                    Log.e(TAG, "Boot unpack failed: ${splitResult.error.message}", splitResult.error.cause)
                    applyDetectionFailure(splitResult.error)
                    return@launch
                }
                is DomainResult.Success -> Unit
            }

            when (val checkResult = repository.checkDevice()) {
                is DomainResult.Failure -> {
                    Log.e(TAG, "Device check failed: ${checkResult.error.message}", checkResult.error.cause)
                    applyDetectionFailure(checkResult.error)
                    return@launch
                }
                is DomainResult.Success -> {
                    val dtbs = checkResult.data
                    if (dtbs.isEmpty()) {
                        _detectionState.value = UiState.Error(UiText.StringResource(R.string.gpu_prep_failed))
                        return@launch
                    }

                    // Prefer active DTB (matching running device) > first supported > fallback
                    val activeDtb = if (repository.activeDtbId != -1) {
                        dtbs.find { it.id == repository.activeDtbId }
                    } else {
                        null
                    }
                    if (activeDtb != null && activeDtb.type.strategyType.isNotEmpty()) {
                        syncPartitionState()
                        _activeDtbId.value = repository.activeDtbId
                        _detectionState.value = UiState.Success(dtbs)
                        _isFilesExtracted.value = true
                        selectChipset(activeDtb)
                        _dataReloadTrigger.value++
                    } else {
                        finalizeDetection(dtbs)
                    }
                }
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
                val isDtbo = magic.size >= 4 &&
                    ((((magic[0].toInt() and 0xFF) shl 24) or
                        ((magic[1].toInt() and 0xFF) shl 16) or
                        ((magic[2].toInt() and 0xFF) shl 8) or
                        (magic[3].toInt() and 0xFF)) == DTBO_TABLE_MAGIC)

                when {
                    isBootImage && !isRootMode -> {
                        // Boot images need root mode for unpacking
                        _detectionState.value = null
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.boot_images_require_root_mode), Toast.LENGTH_LONG).show()
                        }
                    }
                    isBootImage -> importBootImage(context, uri)
                    isDtbo -> importDtboImage(context, uri)
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
                    Toast.makeText(context, context.getString(R.string.import_failed_format, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
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
            val displayName = resolveDisplayName(context, uri, "boot.img")
            val inputStream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                ?: run {
                    applyDetectionFailure(AppError.IoError("Cannot read file - permission denied"))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.import_failed_permission_denied), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

            when (val setupResult = repository.setupEnv()) {
                is DomainResult.Failure -> {
                    applyDetectionFailure(setupResult.error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, mapAppErrorToMessage(setupResult.error), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                is DomainResult.Success -> Unit
            }

            inputStream.use { stream ->
                when (val importResult = repository.importBootImage(stream, displayName)) {
                    is DomainResult.Failure -> {
                        Log.e(TAG, "Boot image import failed: ${importResult.error.message}", importResult.error.cause)
                        applyDetectionFailure(importResult.error)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, mapAppErrorToMessage(importResult.error), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    is DomainResult.Success -> Unit
                }
            }

            when (val splitResult = repository.bootImage2dts()) {
                is DomainResult.Failure -> {
                    applyDetectionFailure(splitResult.error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, mapAppErrorToMessage(splitResult.error), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                is DomainResult.Success -> Unit
            }

            when (val checkResult = repository.checkDevice()) {
                is DomainResult.Failure -> {
                    applyDetectionFailure(checkResult.error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, mapAppErrorToMessage(checkResult.error), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                is DomainResult.Success -> {
                    val dtbs = checkResult.data
                    if (dtbs.isEmpty()) {
                        _detectionState.value = UiState.Error(UiText.DynamicString("No DTBs found in imported image."))
                        return@launch
                    }

                    finalizeDetection(dtbs)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.boot_image_imported_successfully), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun importDtboImage(context: Context, uri: Uri) {
        _detectionState.value = UiState.Loading
        viewModelScope.launch {
            val displayName = resolveDisplayName(context, uri, "dtbo.img")
            val inputStream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                ?: run {
                    applyDetectionFailure(AppError.IoError("Cannot read dtbo file - permission denied"))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.import_failed_permission_denied), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

            when (val setupResult = repository.setupEnv()) {
                is DomainResult.Failure -> {
                    applyDetectionFailure(setupResult.error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, mapAppErrorToMessage(setupResult.error), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                is DomainResult.Success -> Unit
            }

            inputStream.use { stream ->
                when (val importResult = repository.importDtboImage(stream, displayName)) {
                    is DomainResult.Failure -> {
                        applyDetectionFailure(importResult.error)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, mapAppErrorToMessage(importResult.error), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    is DomainResult.Success -> Unit
                }
            }

            when (val splitResult = repository.bootImage2dts()) {
                is DomainResult.Failure -> {
                    applyDetectionFailure(splitResult.error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, mapAppErrorToMessage(splitResult.error), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                is DomainResult.Success -> Unit
            }

            when (val checkResult = repository.checkDevice()) {
                is DomainResult.Failure -> {
                    applyDetectionFailure(checkResult.error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, mapAppErrorToMessage(checkResult.error), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                is DomainResult.Success -> {
                    finalizeDetection(checkResult.data)
                }
            }
        }
    }

    fun selectPartition(partition: TargetPartition) {
        viewModelScope.launch {
            if (partition == repository.selectedPartition && (repository.getDtbs(partition).isNotEmpty())) {
                syncPartitionState()
                _detectionState.value = UiState.Success(repository.getDtbs(partition))
                _activeDtbId.value = repository.activeDtbId
                return@launch
            }

            when (val result = repository.switchPartitionAndLoad(partition)) {
                is DomainResult.Failure -> {
                    // Keep current editor state stable; partition switch should not kick the user out.
                    syncPartitionState()
                    if (partition == TargetPartition.DTBO) {
                        _detectionState.value = UiState.Success(repository.getDtbs(partition))
                        _activeDtbId.value = repository.activeDtbId
                    }
                }
                is DomainResult.Success -> {
                    syncPartitionState()
                    _detectionState.value = UiState.Success(result.data)
                    _activeDtbId.value = repository.activeDtbId
                }
            }
        }
    }

    fun selectChipset(dtb: Dtb) {
        repository.chooseTarget(dtb)
        syncPartitionState()
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
            when (val setupResult = repository.setupEnv()) {
                is DomainResult.Failure -> {
                    Log.e(TAG, "Failed to setup environment for saved DTS load: ${setupResult.error.message}", setupResult.error.cause)
                    return@launch
                }
                is DomainResult.Success -> Unit
            }

            runCatching { repository.loadSavedDts() }
                .onSuccess { savedDtbs ->
                    if (savedDtbs.isNotEmpty()) {
                        syncPartitionState()
                        _detectionState.value = UiState.Success(ArrayList(repository.dtbs))
                        selectChipset(savedDtbs.last())
                        _isFilesExtracted.value = true
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load saved DTS", error)
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
                syncPartitionState()
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
            when (val repackResult = repository.dts2bootImage()) {
                is DomainResult.Failure -> {
                    applyRepackFailure(repackResult.error)
                    return@launch
                }
                is DomainResult.Success -> Unit
            }

            val flashResult = if (_selectedPartition.value == TargetPartition.DTBO) {
                repository.flashDtboImage()
            } else {
                repository.writeBootImage()
            }
            when (flashResult) {
                is DomainResult.Failure -> {
                    applyRepackFailure(flashResult.error)
                    return@launch
                }
                is DomainResult.Success -> {
                    _repackState.value = UiState.Success(UiText.StringResource(R.string.repack_flash_success))
                }
            }
        }
    }

    fun installToInactiveSlot(shouldBackup: Boolean) {
        if (!isRootMode) return
        _repackState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.installToInactiveSlot(shouldBackup)) {
                is DomainResult.Success -> {
                    _repackState.value = UiState.Success(UiText.DynamicString(result.data))
                }
                is DomainResult.Failure -> {
                    applyRepackFailure(result.error)
                }
            }
        }
    }

    fun reboot() {
        viewModelScope.launch { try { repository.reboot() } catch (e: Exception) {} }
    }

    fun getDeviceModel(): String = repository.getCurrent("model")
    fun getDeviceBrand(): String = repository.getCurrent("brand")
    fun getInactiveSlotSuffixOrNull(): String? {
        return when (repository.getCurrent("slot").trim()) {
            "_a", "a" -> "_b"
            "_b", "b" -> "_a"
            else -> null
        }
    }

    fun clearRepackState() {
        _repackState.value = null
    }
}