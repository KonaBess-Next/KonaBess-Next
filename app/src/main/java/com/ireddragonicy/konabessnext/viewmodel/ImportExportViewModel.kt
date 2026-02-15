package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.ExportHistoryItem
import com.ireddragonicy.konabessnext.core.model.DomainResult
import com.ireddragonicy.konabessnext.repository.ChipRepository
import com.ireddragonicy.konabessnext.repository.DeviceRepository
import com.ireddragonicy.konabessnext.repository.GpuDomainManager
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.repository.SettingsRepository
import com.ireddragonicy.konabessnext.utils.ExportHistoryManager
import com.ireddragonicy.konabessnext.utils.GzipUtils
import com.ireddragonicy.konabessnext.utils.RootHelper
import com.ireddragonicy.konabessnext.utils.UriPathHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class DtsFileInfo(
    val index: Int,
    val file: File,
    val fileName: String,
    val chipName: String,
    val fileSizeBytes: Long,
    val isActive: Boolean,
    val isCurrentlySelected: Boolean,
    val lineCount: Int,
    val modelName: String
)

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val gpuRepository: GpuRepository,
    private val gpuDomainManager: GpuDomainManager,
    private val chipRepository: ChipRepository,
    private val exportHistoryManager: ExportHistoryManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val FDT_MAGIC = 0xD00DFEED.toInt()
        private const val DTBO_TABLE_MAGIC = 0xD7B7AB1E.toInt()
        private const val MIN_DTB_SIZE = 40
    }

    private data class ExtractedDtb(
        val bytes: ByteArray,
        val suffix: String
    )

    private data class DtcConversionResult(
        val success: Boolean,
        val details: String
    )

    private val _history = MutableStateFlow<List<ExportHistoryItem>>(emptyList())
    val history: SharedFlow<List<ExportHistoryItem>> = _history.asSharedFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private val _lastExportedContent = MutableSharedFlow<String>()
    val lastExportedContent: SharedFlow<String> = _lastExportedContent.asSharedFlow()

    private val _batchProgress = MutableStateFlow<String?>(null)
    val batchProgress: StateFlow<String?> = _batchProgress.asStateFlow()

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    init { loadHistory() }

    fun loadHistory() { _history.value = exportHistoryManager.getHistory() }

    // ── Export ────────────────────────────────────────────────────────

    // ── Export ────────────────────────────────────────────────────────

    fun exportConfig(desc: String): String? = try {
        val bins = gpuRepository.bins.value
        if (bins.isEmpty()) throw IllegalStateException("No GPU table data to export")
        val json = JSONObject().apply {
            put("chip", chipRepository.currentChip.value?.id ?: "Unknown")
            put("desc", desc)
            put("freq", gpuDomainManager.generateTableDts(bins).joinToString("\n"))
        }
        GzipUtils.compress(json.toString().toByteArray(Charsets.UTF_8))
    } catch (e: Exception) {
        viewModelScope.launch { _errorEvent.emit("Export failed: ${e.message}") }
        null
    }

    fun exportConfigToUri(context: Context, uri: Uri, desc: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = exportConfig(desc) ?: return@launch
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(content.toByteArray(Charsets.ISO_8859_1))
                }
                addToHistory("config_${System.currentTimeMillis()}.txt", desc, uri.toString())
                _messageEvent.emit("Successfully exported config")
            } catch (e: Exception) {
                _errorEvent.emit("Export failed: ${e.message}")
            }
        }
    }

    suspend fun tryExportConfigToDefault(context: Context, desc: String): Boolean {
        val defaultUri = settingsRepository.getValidExportUri()
        val content = exportConfig(desc) ?: return false
        val filename = "konabess_config_${System.currentTimeMillis()}.txt"

        if (defaultUri != null) {
            return try {
                withContext(Dispatchers.IO) {
                    val dir = DocumentFile.fromTreeUri(context, defaultUri)
                    if (dir == null || !dir.canWrite()) {
                        settingsRepository.setAndPersistExportUri(Uri.EMPTY) // clear invalid
                        return@withContext false
                    }

                    val file = dir.createFile("text/plain", filename) 
                        ?: throw java.io.IOException("Failed to create file in default directory")
                    
                    context.contentResolver.openOutputStream(file.uri)?.use {
                        it.write(content.toByteArray(Charsets.ISO_8859_1))
                    }
                    addToHistory(filename, desc, file.uri.toString())
                    _messageEvent.emit("Saved to ${settingsRepository.getExportPathDisplay()}/$filename")
                    true
                }
            } catch (e: Exception) {
                false
            }
        } else {
            return exportToLegacyDefault(filename, content.toByteArray(Charsets.ISO_8859_1), desc)
        }
    }

    suspend fun tryExportDtsToDefault(context: Context, dtsFileInfo: DtsFileInfo): Boolean {
        val defaultUri = settingsRepository.getValidExportUri()
        val file = dtsFileInfo.file
        if (!file.exists()) return false
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "konabess_dts_dtb${dtsFileInfo.index}_$timestamp.dts"

        if (defaultUri != null) {
            return try {
                withContext(Dispatchers.IO) {
                    val dir = DocumentFile.fromTreeUri(context, defaultUri)
                    if (dir == null || !dir.canWrite()) return@withContext false
                    
                    val newFile = dir.createFile("text/plain", name) ?: return@withContext false
                    
                    context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    addToHistory(name, "Raw DTS Export (DTB ${dtsFileInfo.index})", newFile.uri.toString())
                    _messageEvent.emit("Saved to ${settingsRepository.getExportPathDisplay()}/$name")
                    true
                }
            } catch (e: Exception) {
                false
            }
        } else {
            return exportToLegacyDefault(name, file.readBytes(), "Raw DTS Export (DTB ${dtsFileInfo.index})")
        }
    }

    fun exportRawDtsToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dtsFile = deviceRepository.getDtsFile()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    dtsFile.inputStream().use { it.copyTo(out) }
                }
                addToHistory("dts_dump_${System.currentTimeMillis()}.txt", "Raw DTS Export", uri.toString())
                _messageEvent.emit("Successfully exported Raw DTS")
            } catch (e: Exception) {
                _errorEvent.emit("Export Raw DTS failed: ${e.message}")
            }
        }
    }

    fun exportDtsFileToUri(context: Context, uri: Uri, dtsFileInfo: DtsFileInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = dtsFileInfo.file
                if (!file.exists()) { _errorEvent.emit("DTS file not found: ${file.name}"); return@launch }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                addToHistory("dts_${dtsFileInfo.index}_${System.currentTimeMillis()}.dts", "Raw DTS Export (DTB ${dtsFileInfo.index})", uri.toString())
                _messageEvent.emit("Exported: ${dtsFileInfo.chipName}")
            } catch (e: Exception) {
                _errorEvent.emit("Export failed: ${e.message}")
            }
        }
    }

    fun exportAllDtsFilesToFolder(context: Context, folderUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allFiles = getAllDtsFiles()
                if (allFiles.isEmpty()) { _errorEvent.emit("No DTS files available to export"); return@launch }

                val folder = DocumentFile.fromTreeUri(context, folderUri)
                val (timestamp, deviceModel) = exportMeta()
                var exported = 0

                for (info in allFiles) {
                    try {
                        val name = "konabess_dts_${deviceModel}_dtb${info.index}_$timestamp.dts"
                        val newFile = folder?.createFile("text/plain", name) ?: continue
                        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                            info.file.inputStream().use { it.copyTo(out) }
                        }
                        addToHistory(name, "Raw DTS Export (DTB ${info.index})", newFile.uri.toString())
                        exported++
                    } catch (e: Exception) {
                        _errorEvent.emit("Failed to export DTB ${info.index}: ${e.message}")
                    }
                }
                _messageEvent.emit("Exported $exported/${allFiles.size} DTS files")
            } catch (e: Exception) {
                _errorEvent.emit("Export all failed: ${e.message}")
            }
        }
    }

    suspend fun tryExportAllDtsZipToDefault(context: Context): Boolean {
        val defaultUri = settingsRepository.getValidExportUri()
        val (timestamp, deviceModel) = exportMeta()
        val zipName = "konabess_dts_${deviceModel}_$timestamp.zip"
        
        val allFiles = getAllDtsFiles()
        if (allFiles.isEmpty()) return false

        // Prepare zip in valid location first? No, stream it.
        // We'll stream to a byte array for the legacy fallback, which might be memory intensive but ok for DTS files.
        // Or generate tmp file.
        
        if (defaultUri != null) {
            return try {
                withContext(Dispatchers.IO) {
                    val dir = DocumentFile.fromTreeUri(context, defaultUri)
                    if (dir == null || !dir.canWrite()) return@withContext false
                    
                    val zipFile = dir.createFile("application/zip", zipName) ?: return@withContext false
                    
                    context.contentResolver.openOutputStream(zipFile.uri)?.use { outputStream ->
                        ZipOutputStream(outputStream).use { zipOut ->
                            for (info in allFiles) {
                                if (!info.file.exists()) continue
                                try {
                                    zipOut.putNextEntry(ZipEntry("konabess_dts_${deviceModel}_dtb${info.index}_$timestamp.dts"))
                                    info.file.inputStream().use { it.copyTo(zipOut) }
                                    zipOut.closeEntry()
                                } catch (e: Exception) { }
                            }
                        }
                    }
                    addToHistory(zipName, "Raw DTS Export (ZIP)", zipFile.uri.toString())
                    _messageEvent.emit("Saved to ${settingsRepository.getExportPathDisplay()}/$zipName")
                    true
                }
            } catch (e: Exception) {
                false
            }
        } else {
            // Generate zip to temp file then move
            val tempFile = File(context.cacheDir, zipName)
            return try {
                withContext(Dispatchers.IO) {
                    FileOutputStream(tempFile).use { outputStream ->
                         ZipOutputStream(outputStream).use { zipOut ->
                            for (info in allFiles) {
                                if (!info.file.exists()) continue
                                try {
                                    zipOut.putNextEntry(ZipEntry("konabess_dts_${deviceModel}_dtb${info.index}_$timestamp.dts"))
                                    info.file.inputStream().use { it.copyTo(zipOut) }
                                    zipOut.closeEntry()
                                } catch (e: Exception) { }
                            }
                        }
                    }
                    exportToLegacyDefault(zipName, tempFile.readBytes(), "Raw DTS Export (ZIP)")
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun tryBackupBootToDefault(context: Context): Boolean {
        val defaultUri = settingsRepository.getValidExportUri()
        val bootFile = deviceRepository.getBootImageFile()
        if (!bootFile.exists()) return false
        val name = "boot_backup_${System.currentTimeMillis()}.img"

        if (defaultUri != null) {
             return try {
                withContext(Dispatchers.IO) {
                    val dir = DocumentFile.fromTreeUri(context, defaultUri)
                    if (dir == null || !dir.canWrite()) return@withContext false
                    
                    val newFile = dir.createFile("application/octet-stream", name) ?: return@withContext false
                    
                    context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                        bootFile.inputStream().use { it.copyTo(out) }
                    }
                    addToHistory(name, "System Boot Backup", newFile.uri.toString())
                    _messageEvent.emit("Saved to ${settingsRepository.getExportPathDisplay()}/$name")
                    true
                }
            } catch (e: Exception) {
                false
            }
        } else {
             return exportToLegacyDefault(name, bootFile.readBytes(), "System Boot Backup")
        }
    }

    private suspend fun exportToLegacyDefault(filename: String, content: ByteArray, desc: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val targetDir = File("/storage/emulated/0/Download/KonaBess")
                if (!targetDir.exists()) {
                    if (!targetDir.mkdirs()) {
                        // Try root mkdir if normal fails
                         if (RootHelper.isRootAvailable()) {
                             RootHelper.exec("mkdir -p \"${targetDir.absolutePath}\"")
                         }
                    }
                }
                
                val targetFile = File(targetDir, filename)
                
                // Try standard write first
                try {
                     targetFile.writeBytes(content)
                } catch (e: Exception) {
                    // Fallback to root write
                    if (RootHelper.isRootAvailable()) {
                        val temp = File.createTempFile("root_write", null)
                        temp.writeBytes(content)
                        RootHelper.copyFile(temp.absolutePath, targetFile.absolutePath, "644")
                        temp.delete()
                    } else {
                        throw e
                    }
                }
                
                addToHistory(filename, desc, targetFile.absolutePath)
                _messageEvent.emit("Saved to /Download/KonaBess/$filename")
                true
            } catch (e: Exception) {
                _errorEvent.emit("Export failed: ${e.message}")
                false
            }
        }
    }

    fun exportAllDtsAsZipToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allFiles = getAllDtsFiles()
                if (allFiles.isEmpty()) { _errorEvent.emit("No DTS files available to export"); return@launch }
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: run { _errorEvent.emit("Failed to open output stream"); return@launch }

                val (timestamp, deviceModel) = exportMeta()
                ZipOutputStream(outputStream).use { zipOut ->
                    for (info in allFiles) {
                        if (!info.file.exists()) continue
                        try {
                            zipOut.putNextEntry(ZipEntry("konabess_dts_${deviceModel}_dtb${info.index}_$timestamp.dts"))
                            info.file.inputStream().use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        } catch (e: Exception) {
                            _errorEvent.emit("Failed to add DTB ${info.index} to ZIP: ${e.message}")
                        }
                    }
                }
                val zipName = "konabess_dts_${deviceModel}_$timestamp.zip"
                addToHistory(zipName, "Raw DTS Export (ZIP, ${allFiles.size} files)", uri.toString())
                _messageEvent.emit("Exported ${allFiles.size} DTS files as ZIP")
            } catch (e: Exception) {
                _errorEvent.emit("ZIP export failed: ${e.message}")
            }
        }
    }

    fun backupBootToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bootFile = deviceRepository.getBootImageFile()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bootFile.inputStream().use { it.copyTo(out) }
                }
                addToHistory("boot_backup_${System.currentTimeMillis()}.img", "System Boot Backup", uri.toString())
                _messageEvent.emit("Successfully backed up boot image")
            } catch (e: Exception) {
                _errorEvent.emit("Backup failed: ${e.message}")
            }
        }
    }

    // ── Import ───────────────────────────────────────────────────────

    fun importConfig(inputString: String) = previewConfig(inputString)

    fun previewConfig(inputString: String) {
        viewModelScope.launch {
            try {
                var input = inputString.trim().removePrefix("konabess://")

                val jsonString = if (input.startsWith("{")) input
                else GzipUtils.uncompress(input)
                    ?.takeIf { it.trim().startsWith("{") }
                    ?: throw IllegalArgumentException("Invalid format: Not valid JSON or KonaBess Compressed String")

                if (!jsonString.trim().startsWith("{")) throw IllegalArgumentException("Invalid JSON format")

                val json = JSONObject(jsonString.trim())
                val chip = json.getString("chip")
                val desc = json.optString("desc", "Imported Config")
                val freqData = json.optString("freq", "")
                val legacyVoltCount = if (json.has("volt")) json.getString("volt").count { it == '\n' } else 0

                _importPreview.value = ImportPreview(chip, desc, parseBinsFromDts(freqData), legacyVoltCount, jsonString)
            } catch (e: Exception) {
                _errorEvent.emit("Import parsing failed: ${e.message}")
            }
        }
    }

    fun confirmImport() {
        val preview = _importPreview.value ?: return
        viewModelScope.launch {
            try {
                val json = JSONObject(preview.rawJsonString)
                if (json.has("freq")) gpuRepository.importTable(json.getString("freq").split("\n"))
                if (json.has("volt")) gpuRepository.importVoltTable(json.getString("volt").split("\n"))
                _messageEvent.emit("Successfully imported: ${preview.description}")
                _importPreview.value = null
            } catch (e: Exception) {
                _errorEvent.emit("Apply failed: ${e.message}")
            }
        }
    }

    fun cancelImport() { _importPreview.value = null }

    fun importConfigFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch

                // Binary DTB — not a config format
                if (bytes.size >= 4 &&
                    bytes[0] == 0xD0.toByte() && bytes[1] == 0x0D.toByte() &&
                    bytes[2] == 0xFE.toByte() && bytes[3] == 0xED.toByte()) {
                    _errorEvent.emit("This is a binary DTB file, not a KonaBess config.\nUse the Home screen import to load DTB/DTS files.")
                    return@launch
                }

                val decompressed = GzipUtils.uncompress(bytes)
                if (decompressed != null) {
                    importConfig(decompressed)
                } else {
                    val text = String(bytes)
                    if (text.trim().startsWith("{")) importConfig(text)
                    else _errorEvent.emit("Failed decoding: File format not recognized")
                }
            } catch (e: Exception) {
                _errorEvent.emit("Import failed: ${e.message}")
            }
        }
    }

    // ── History ──────────────────────────────────────────────────────

    fun addToHistory(filename: String, desc: String, filePath: String) {
        exportHistoryManager.addExport(filename, desc, filePath, chipRepository.currentChip.value?.id ?: "Unknown")
        loadHistory()
    }

    fun deleteHistoryItem(item: ExportHistoryItem) {
        exportHistoryManager.deleteItem(item)
        loadHistory()
        viewModelScope.launch { _messageEvent.emit("Item deleted") }
    }

    fun updateHistoryItem(item: ExportHistoryItem) {
        exportHistoryManager.updateItem(item)
        loadHistory()
    }

    fun applyHistoryItem(context: Context, item: ExportHistoryItem) {
        when {
            item.filePath.startsWith("content:") -> importConfigFromUri(context, Uri.parse(item.filePath))
            item.filePath.startsWith("clipboard:") -> {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val text = clipboard.primaryClip?.getItemAt(0)?.text
                    if (!text.isNullOrEmpty()) importConfig(text.toString())
                    else viewModelScope.launch { _errorEvent.emit("Clipboard is empty") }
                } catch (e: Exception) {
                    viewModelScope.launch { _errorEvent.emit("Failed to read clipboard: ${e.message}") }
                }
            }
            else -> {
                try {
                    val file = File(item.filePath)
                    if (file.exists()) importConfig(file.readText())
                    else viewModelScope.launch { _errorEvent.emit("File not found: ${item.filePath}") }
                } catch (e: Exception) {
                    viewModelScope.launch { _errorEvent.emit("Import failed: ${e.message}") }
                }
            }
        }
    }

    fun shareHistoryItem(context: Context, item: ExportHistoryItem) {
        viewModelScope.launch {
            try {
                val uri: Uri = withContext(Dispatchers.IO) {
                    when {
                        item.filePath.startsWith("content:") -> {
                            val cacheFile = File(context.cacheDir, "share_temp_${item.chipType}_${System.currentTimeMillis()}.txt")
                            context.contentResolver.openInputStream(Uri.parse(item.filePath))?.use { input ->
                                FileOutputStream(cacheFile).use { input.copyTo(it) }
                            } ?: throw java.io.FileNotFoundException("Could not open content URI")
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
                        }
                        item.filePath.startsWith("clipboard:") -> {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                ?: throw java.io.IOException("Clipboard is empty")
                            val cacheFile = File(context.cacheDir, "share_clipboard_${System.currentTimeMillis()}.txt")
                            cacheFile.writeText(text)
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
                        }
                        else -> {
                            val file = File(item.filePath)
                            if (!file.exists()) throw java.io.FileNotFoundException("File not found: ${item.filePath}")
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        }
                    }
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Config"))
            } catch (e: Exception) {
                _errorEvent.emit("Share failed: ${e.message}")
            }
        }
    }

    // ── UI State ─────────────────────────────────────────────────────

    fun notifyExportResult(content: String) { viewModelScope.launch { _lastExportedContent.emit(content) } }
    fun clearExportResult() { viewModelScope.launch { _lastExportedContent.emit("") } }

    // ── DTS Files ────────────────────────────────────────────────────

    fun getAllDtsFiles(): List<DtsFileInfo> {
        val dtbs = deviceRepository.dtbs
        val activeDtbId = deviceRepository.activeDtbId
        val currentDtb = deviceRepository.currentDtb
        val modelPattern = java.util.regex.Pattern.compile("model\\s*=\\s*\"([^\"]+)\"")

        return dtbs.mapNotNull { dtb ->
            try {
                val file = deviceRepository.getDtsFile(dtb.id)
                if (!file.exists()) return@mapNotNull null

                val firstLines = file.bufferedReader().use { reader ->
                    buildString { repeat(30) { reader.readLine()?.let { appendLine(it) } ?: return@repeat } }
                }
                val modelMatcher = modelPattern.matcher(firstLines)
                val modelName = if (modelMatcher.find()) modelMatcher.group(1) ?: "" else ""
                val lineCount = file.bufferedReader().useLines { it.count() }

                DtsFileInfo(
                    index = dtb.id, file = file, fileName = file.name, chipName = dtb.type.name,
                    fileSizeBytes = file.length(), isActive = dtb.id == activeDtbId,
                    isCurrentlySelected = dtb.id == currentDtb?.id, lineCount = lineCount, modelName = modelName
                )
            } catch (_: Exception) { null }
        }
    }

    // ── Batch DTB → DTS ──────────────────────────────────────────────

    fun batchConvertDtbToDts(context: Context, sourceUris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val totalSources = sourceUris.size
            var totalDetectedDtbs = 0
            var successCount = 0
            val successPaths = StringBuilder()
            val cacheDir = context.cacheDir

            try {
                // Best effort: prepare env so dtc is extracted when needed.
                when (val setupResult = deviceRepository.setupEnv()) {
                    is DomainResult.Failure -> {
                        _errorEvent.emit("Warning: setup env failed before batch convert: ${setupResult.error.message}")
                    }
                    is DomainResult.Success -> Unit
                }

                val dtcPath = resolveUsableDtcPath(context)
                if (dtcPath.isNullOrBlank()) {
                    _batchProgress.value = null
                    _errorEvent.emit(
                        "DTB conversion tool (dtc) not found or not executable. " +
                            "Please reinstall app or switch mode once in Settings to reinitialize binaries."
                    )
                    return@launch
                }

                RootHelper.exec("chmod 755 \"$dtcPath\"")

                sourceUris.forEachIndexed { index, uri ->
                    _batchProgress.value = "Scanning ${index + 1}/$totalSources..."
                    try {
                        var realPath = UriPathHelper.getPath(context, uri)
                        val sourceFile = DocumentFile.fromSingleUri(context, uri)
                        val originalName = sourceFile?.name ?: "file_${index}.img"
                        val originalSize = sourceFile?.length() ?: 0L

                        if (realPath == null && RootHelper.isRootAvailable()) {
                            realPath = RootHelper.findFile(originalName, originalSize)
                        }

                        val sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (sourceBytes == null || sourceBytes.isEmpty()) {
                            _errorEvent.emit("Failed to read $originalName (empty or inaccessible file)")
                            return@forEachIndexed
                        }

                        val extractedDtbs = if (isDtbBinary(sourceBytes)) {
                            listOf(ExtractedDtb(sourceBytes, ""))
                        } else {
                            extractDtbsFromBinaryImage(sourceBytes)
                        }

                        if (extractedDtbs.isEmpty()) {
                            _errorEvent.emit(
                                "No DTB blobs found in $originalName. " +
                                    "If this is a boot/dtbo image, ensure it contains raw DTB entries."
                            )
                            return@forEachIndexed
                        }

                        totalDetectedDtbs += extractedDtbs.size

                        val outputDir = realPath?.let { File(it).parent }
                        if (outputDir == null) {
                            _errorEvent.emit("Could not resolve writable output path for $originalName")
                            return@forEachIndexed
                        }

                        val baseName = originalName.substringBeforeLast('.', originalName)

                        extractedDtbs.forEachIndexed { dtbIndex, extracted ->
                            _batchProgress.value =
                                "Converting ${index + 1}/$totalSources (${dtbIndex + 1}/${extractedDtbs.size})..."

                            val tempInput = File(cacheDir, "temp_input_${index}_$dtbIndex.dtb")
                            val tempOutput = File(cacheDir, "temp_output_${index}_$dtbIndex.dts")

                            try {
                                val conversionResult = convertDtbToDts(
                                    dtcPath = dtcPath,
                                    inputFile = tempInput,
                                    outputFile = tempOutput,
                                    dtbBytes = extracted.bytes
                                )

                                if (conversionResult.success && tempOutput.exists() && tempOutput.length() > 0) {
                                    val outputName = if (extractedDtbs.size == 1 && originalName.endsWith(".dtb", true)) {
                                        "$baseName.dts"
                                    } else {
                                        val suffix = extracted.suffix.ifBlank { "_dtb$dtbIndex" }
                                        "$baseName$suffix.dts"
                                    }

                                    val finalOutputPath = "$outputDir/$outputName"
                                    val saved = if (RootHelper.isRootAvailable()) {
                                        RootHelper.copyFile(tempOutput.absolutePath, finalOutputPath, "777")
                                    } else {
                                        runCatching {
                                            tempOutput.copyTo(File(finalOutputPath), overwrite = true)
                                        }.isSuccess
                                    }

                                    if (saved) {
                                        successPaths.appendLine(finalOutputPath)
                                        successCount++
                                    } else {
                                        _errorEvent.emit("Permission denied writing to $outputDir")
                                    }
                                } else {
                                    _errorEvent.emit(
                                        "Failed to convert $originalName segment ${dtbIndex + 1}: " +
                                            conversionResult.details
                                    )
                                }
                            } catch (e: Exception) {
                                _errorEvent.emit("Failed converting $originalName segment ${dtbIndex + 1}: ${e.message}")
                            } finally {
                                tempInput.delete()
                                tempOutput.delete()
                            }
                        }
                    } catch (e: Exception) {
                        _errorEvent.emit("Failed processing file ${index + 1}/$totalSources: ${e.message}")
                    }
                }

                _batchProgress.value = null
                if (successCount > 0) notifyExportResult(successPaths.toString().trim())
                _messageEvent.emit(
                    "Successfully converted $successCount/$totalDetectedDtbs DTB blobs from $totalSources file(s)"
                )
            } catch (e: Exception) {
                _batchProgress.value = null
                _errorEvent.emit("Batch conversion error: ${e.message}")
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────

    private fun exportMeta(): Pair<String, String> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val deviceModel = deviceRepository.getCurrent("model").replace(" ", "_")
        return timestamp to deviceModel
    }

    private fun resolveUsableDtcPath(context: Context): String? {
        val candidates = linkedSetOf(
            deviceRepository.getBinaryPath("dtc"),
            File(context.applicationInfo.nativeLibraryDir, "libdtc.so").absolutePath,
            File(context.filesDir, "dtc").absolutePath
        )

        for (path in candidates) {
            val file = File(path)
            if (!file.exists()) continue

            if (!file.canExecute()) {
                runCatching { file.setExecutable(true, false) }
                if (!file.canExecute() && RootHelper.isRootAvailable()) {
                    RootHelper.exec("chmod 755 \"$path\"")
                }
            }

            if (file.exists() && file.canExecute()) return path
        }

        return null
    }

    private fun isDtbBinary(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
        return magic == FDT_MAGIC
    }

    private fun extractDtbsFromBinaryImage(bytes: ByteArray): List<ExtractedDtb> {
        val dtboEntries = extractDtbsFromDtboTable(bytes)
        if (dtboEntries.isNotEmpty()) return dtboEntries
        return extractDtbsByMagicScan(bytes)
    }

    private fun extractDtbsFromDtboTable(bytes: ByteArray): List<ExtractedDtb> {
        if (bytes.size < 32) return emptyList()

        val magic = readUInt32Be(bytes, 0) ?: return emptyList()
        if (magic != DTBO_TABLE_MAGIC.toLong()) return emptyList()

        val entrySize = (readUInt32Be(bytes, 12) ?: return emptyList()).toInt()
        val entryCount = (readUInt32Be(bytes, 16) ?: return emptyList()).toInt()
        val entriesOffset = (readUInt32Be(bytes, 20) ?: return emptyList()).toInt()

        if (entrySize < 8 || entryCount <= 0) return emptyList()
        if (entryCount > 4096) return emptyList()

        val entriesTableEnd = entriesOffset.toLong() + entrySize.toLong() * entryCount.toLong()
        if (entriesOffset < 0 || entriesTableEnd > bytes.size.toLong()) return emptyList()

        val results = mutableListOf<ExtractedDtb>()
        for (i in 0 until entryCount) {
            val entryOffset = entriesOffset + (i * entrySize)
            if (entryOffset < 0 || entryOffset + 8 > bytes.size) continue

            val dtSize = (readUInt32Be(bytes, entryOffset) ?: continue).toInt()
            val dtOffset = (readUInt32Be(bytes, entryOffset + 4) ?: continue).toInt()
            if (dtOffset < 0 || dtOffset + MIN_DTB_SIZE > bytes.size) continue
            if (!isFdtMagicAt(bytes, dtOffset)) continue

            val headerSize = readFdtTotalSize(bytes, dtOffset)
            val selectedSize = when {
                headerSize != null -> headerSize
                dtSize >= MIN_DTB_SIZE && dtOffset.toLong() + dtSize.toLong() <= bytes.size.toLong() -> dtSize
                else -> continue
            }

            val end = dtOffset + selectedSize
            if (end > bytes.size || end <= dtOffset) continue

            results.add(
                ExtractedDtb(
                    bytes = bytes.copyOfRange(dtOffset, end),
                    suffix = "_entry$i"
                )
            )
        }

        return results
    }

    private fun extractDtbsByMagicScan(bytes: ByteArray): List<ExtractedDtb> {
        val segments = mutableListOf<Pair<Int, Int>>()
        var cursor = 0

        while (cursor + 8 <= bytes.size) {
            if (!isFdtMagicAt(bytes, cursor)) {
                cursor++
                continue
            }

            val dtbSize = (readUInt32Be(bytes, cursor + 4) ?: 0L).toInt()
            if (dtbSize < MIN_DTB_SIZE || cursor + dtbSize > bytes.size) {
                cursor++
                continue
            }

            segments.add(cursor to dtbSize)
            cursor += maxOf(dtbSize, 1)
        }

        if (segments.isEmpty()) return emptyList()

        return segments.mapIndexed { index, segment ->
            val (start, size) = segment
            val end = start + size
            val suffix = if (segments.size == 1) "" else "_dtb$index"
            ExtractedDtb(bytes = bytes.copyOfRange(start, end), suffix = suffix)
        }
    }

    private fun isFdtMagicAt(bytes: ByteArray, offset: Int): Boolean {
        if (offset < 0 || offset + 4 > bytes.size) return false
        val magic = ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
        return magic == FDT_MAGIC
    }

    private fun readFdtTotalSize(bytes: ByteArray, offset: Int = 0): Int? {
        if (!isFdtMagicAt(bytes, offset)) return null
        val size = (readUInt32Be(bytes, offset + 4) ?: return null).toInt()
        if (size < MIN_DTB_SIZE) return null
        if (offset < 0 || offset + size > bytes.size) return null
        return size
    }

    private fun trimToFdtTotalSize(bytes: ByteArray): ByteArray? {
        val size = readFdtTotalSize(bytes, 0) ?: return null
        if (size == bytes.size) return null
        return bytes.copyOfRange(0, size)
    }

    private fun runDtcDtbToDts(
        dtcPath: String,
        inputPath: String,
        outputPath: String,
        force: Boolean
    ) = RootHelper.exec(
        "\"$dtcPath\" ${if (force) "-f " else ""}-I dtb -O dts \"$inputPath\" -o \"$outputPath\""
    )

    private fun renderDtcShellOutput(err: List<String>, out: List<String>): String {
        val lines = (err + out).map { it.trim() }.filter { it.isNotEmpty() }
        return if (lines.isEmpty()) "Unknown dtc error (no stderr output)." else lines.joinToString("\n")
    }

    private fun convertDtbToDts(
        dtcPath: String,
        inputFile: File,
        outputFile: File,
        dtbBytes: ByteArray
    ): DtcConversionResult {
        val logs = mutableListOf<String>()

        fun attempt(label: String, force: Boolean): Boolean {
            if (outputFile.exists()) outputFile.delete()
            val result = runDtcDtbToDts(
                dtcPath = dtcPath,
                inputPath = inputFile.absolutePath,
                outputPath = outputFile.absolutePath,
                force = force
            )
            logs.add("$label: ${renderDtcShellOutput(result.err, result.out)}")
            return result.isSuccess && outputFile.exists() && outputFile.length() > 0
        }

        inputFile.writeBytes(dtbBytes)
        if (attempt("normal", force = false)) return DtcConversionResult(true, "OK")
        if (attempt("force", force = true)) return DtcConversionResult(true, "OK (forced)")

        val trimmed = trimToFdtTotalSize(dtbBytes)
        if (trimmed != null && trimmed.isNotEmpty()) {
            inputFile.writeBytes(trimmed)
            if (attempt("trimmed-normal", force = false)) {
                return DtcConversionResult(true, "OK (trimmed by FDT header)")
            }
            if (attempt("trimmed-force", force = true)) {
                return DtcConversionResult(true, "OK (trimmed + forced)")
            }
        }

        return DtcConversionResult(
            success = false,
            details = logs.joinToString("\n\n").ifBlank { "Unknown dtc conversion failure." }
        )
    }

    private fun readUInt32Be(bytes: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    private fun parseBinsFromDts(dtsContent: String): List<BinInfo> {
        val binMatches = Regex("qcom,gpu-pwrlevels-(\\d+)").findAll(dtsContent).toList()

        if (binMatches.isEmpty()) {
            val levels = parseLevels(dtsContent)
            return if (levels.isNotEmpty()) listOf(buildBinInfo(0, levels)) else emptyList()
        }

        return binMatches.mapIndexedNotNull { i, match ->
            val end = if (i < binMatches.lastIndex) binMatches[i + 1].range.first else dtsContent.length
            val binContent = dtsContent.substring(match.range.first, end)
            val binId = Regex("qcom,speed-bin\\s*=\\s*<([^>]+)>").find(binContent)
                ?.groupValues?.get(1)?.trim()?.removePrefix("0x")?.toIntOrNull(16) ?: 0
            val levels = parseLevels(binContent)
            if (levels.isNotEmpty()) buildBinInfo(binId, levels) else null
        }
    }

    private fun buildBinInfo(binId: Int, levels: List<PreviewLevel>): BinInfo {
        val freqs = levels.map { it.freqMhz }
        return BinInfo(binId, freqs.size, freqs.min(), freqs.max(), levels.count { it.voltageLabel.isNotEmpty() }, freqs, levels)
    }

    private fun parseLevels(content: String): List<PreviewLevel> {
        val list = mutableListOf<PreviewLevel>()
        var pos = 0
        while (true) {
            val start = content.indexOf("qcom,gpu-pwrlevel@", pos).takeIf { it >= 0 } ?: break
            val open = content.indexOf("{", start).takeIf { it >= 0 } ?: break
            val close = findClosingBrace(content, open).takeIf { it >= 0 } ?: break
            val block = content.substring(open, close)

            val freqHz = parseDtsProp(block, "qcom,gpu-freq")?.toLongOrNull(16) ?: 0L
            val freqMhz = (freqHz / 1_000_000).toInt()

            val label = Regex("qcom,(level|corner|bw-level)\\s*=\\s*<([^>]+)>").find(block)?.let { m ->
                val key = m.groupValues[1]
                val intVal = m.groupValues[2].trim().removePrefix("0x").toIntOrNull(16) ?: return@let ""
                if (key == "level" && intVal > 16) "Level: $intVal"
                else "${key.replaceFirstChar { it.uppercase() }}: $intVal"
            } ?: ""

            if (freqMhz > 0) {
                list.add(PreviewLevel(
                    freqMhz, label,
                    parseDtsProp(block, "qcom,bus-min")?.toIntOrNull(16),
                    parseDtsProp(block, "qcom,bus-max")?.toIntOrNull(16),
                    parseDtsProp(block, "qcom,bus-freq")?.toIntOrNull(16)
                ))
            }
            pos = close
        }
        return list.sortedByDescending { it.freqMhz }
    }

    private fun parseDtsProp(block: String, prop: String): String? =
        Regex("$prop\\s*=\\s*<([^>]+)>").find(block)?.groupValues?.get(1)?.trim()?.removePrefix("0x")

    private fun findClosingBrace(text: String, openPos: Int): Int {
        var balance = 0
        for (i in openPos until text.length) {
            when (text[i]) {
                '{' -> balance++
                '}' -> if (--balance == 0) return i
            }
        }
        return -1
    }
}

data class ImportPreview(
    val chip: String,
    val description: String,
    val bins: List<BinInfo>,
    val legacyVoltCount: Int,
    val rawJsonString: String
)

data class BinInfo(
    val binId: Int,
    val frequencyCount: Int,
    val minFreqMhz: Int,
    val maxFreqMhz: Int,
    val voltageCount: Int,
    val frequencies: List<Int>,
    val levels: List<PreviewLevel>
)

data class PreviewLevel(
    val freqMhz: Int,
    val voltageLabel: String,
    val busMin: Int?,
    val busMax: Int?,
    val busFreq: Int?
)


