package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.ExportHistoryItem
import com.ireddragonicy.konabessnext.repository.ChipRepository
import com.ireddragonicy.konabessnext.repository.DeviceRepository
import com.ireddragonicy.konabessnext.repository.GpuDomainManager
import com.ireddragonicy.konabessnext.repository.GpuRepository
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
    private val exportHistoryManager: ExportHistoryManager
) : ViewModel() {

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
                val file = if (dtb.id < 0) deviceRepository.getDtsFile() else deviceRepository.getDtsFile(dtb.id)
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
            val total = sourceUris.size
            var successCount = 0
            val successPaths = StringBuilder()
            val dtcPath = File(context.filesDir, "dtc").absolutePath
            val cacheDir = context.cacheDir

            try {
                RootHelper.exec("chmod 755 $dtcPath")

                sourceUris.forEachIndexed { index, uri ->
                    _batchProgress.value = "Converting ${index + 1}/$total..."
                    try {
                        var realPath = UriPathHelper.getPath(context, uri)
                        val sourceFile = DocumentFile.fromSingleUri(context, uri)
                        val originalName = sourceFile?.name ?: "file_${index}.dtb"
                        val originalSize = sourceFile?.length() ?: 0L

                        if (realPath == null && RootHelper.isRootAvailable()) {
                            realPath = RootHelper.findFile(originalName, originalSize)
                        }

                        val outputName = if (originalName.endsWith(".dtb", true))
                            originalName.dropLast(4) + ".dts" else "$originalName.dts"

                        val tempInput = File(cacheDir, "temp_input.dtb")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempInput.outputStream().use { input.copyTo(it) }
                        }

                        val tempOutput = File(cacheDir, "temp_output.dts")
                        if (tempOutput.exists()) tempOutput.delete()

                        val result = RootHelper.exec("$dtcPath -I dtb -O dts \"${tempInput.absolutePath}\" -o \"${tempOutput.absolutePath}\"")

                        if (result.isSuccess && tempOutput.exists() && tempOutput.length() > 0 && realPath != null) {
                            val finalOutputPath = "${File(realPath).parent}/$outputName"
                            val saved = if (RootHelper.isRootAvailable()) {
                                RootHelper.copyFile(tempOutput.absolutePath, finalOutputPath, "777")
                            } else {
                                runCatching { tempOutput.copyTo(File(finalOutputPath), overwrite = true) }.isSuccess
                            }
                            if (saved) { successPaths.appendLine(finalOutputPath); successCount++ }
                            else _errorEvent.emit("Permission denied writing to ${File(realPath).parent}")
                        } else if (!result.isSuccess) {
                            _errorEvent.emit("Failed to convert $originalName: ${result.err.joinToString("\n")}")
                        } else if (realPath == null) {
                            _errorEvent.emit("Could not resolve path for $originalName")
                        }

                        tempInput.delete()
                        tempOutput.delete()
                    } catch (e: Exception) { e.printStackTrace() }
                }

                _batchProgress.value = null
                if (successCount > 0) notifyExportResult(successPaths.toString().trim())
                _messageEvent.emit("Successfully converted $successCount/$total files")
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


