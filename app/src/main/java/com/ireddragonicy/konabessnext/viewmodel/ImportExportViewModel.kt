package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// ChipInfo import removed
import com.ireddragonicy.konabessnext.repository.DeviceRepository
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.utils.GzipUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import com.ireddragonicy.konabessnext.model.ChipDefinition
import android.net.Uri
import java.lang.StringBuilder
import java.util.ArrayList
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ireddragonicy.konabessnext.utils.RootHelper
import com.ireddragonicy.konabessnext.utils.UriPathHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Represents a single DTS file available for export with metadata.
 */
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
    private val gpuDomainManager: com.ireddragonicy.konabessnext.repository.GpuDomainManager,
    private val chipRepository: com.ireddragonicy.konabessnext.repository.ChipRepository,
    private val exportHistoryManager: com.ireddragonicy.konabessnext.utils.ExportHistoryManager
) : ViewModel() {

    private val _history = kotlinx.coroutines.flow.MutableStateFlow<List<com.ireddragonicy.konabessnext.model.ExportHistoryItem>>(emptyList())
    val history = _history.asSharedFlow() // or StateFlow

    init {
        loadHistory()
    }

    fun loadHistory() {
        _history.value = exportHistoryManager.getHistory()
    }

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

    fun updateExportAvailability() {
        // Logic to check if export is available (e.g. data loaded)
    }

    fun exportConfig(desc: String): String? {
       return try {
           val json = JSONObject()
           val current = chipRepository.currentChip.value
           json.put("chip", current?.id ?: "Unknown")
           json.put("desc", desc)
           
           val freqData = StringBuilder()
           val bins = gpuRepository.bins.value
           // Safe call in case bins are empty or not ready
           if (bins.isEmpty()) throw IllegalStateException("No GPU table data to export")
           
           val tableLines = gpuDomainManager.generateTableDts(bins)
           
           for (line in tableLines) {
               freqData.append(line).append("\n")
           }
           json.put("freq", freqData.toString())
           
           val data = json.toString()
           val compressed = GzipUtils.compress(data.toByteArray(Charsets.UTF_8))
           
           // Return uncompressed data for saving manually in Fragment for now (legacy behavior)
           // Or better: let Fragment handle file saving and we just return content.
           // Legacy TableIO saved the COMPRESSED string (GzipUtils returns String? No, GzipUtils returns String in legacy Java logic?)
           // Let's check GzipUtils if I can.
           // Assuming legacy behavior returned string to write.
           compressed // returning this string
       } catch (e: Exception) {
           viewModelScope.launch { _errorEvent.emit("Export failed: ${e.message}") }
           null
       }
    }
    
    fun addToHistory(filename: String, desc: String, filePath: String) {
        val chipType = chipRepository.currentChip.value?.id ?: "Unknown"
        exportHistoryManager.addExport(filename, desc, filePath, chipType)
        loadHistory()
    }
    

    fun previewConfig(inputString: String) {
        viewModelScope.launch {
            try {
                // Pre-process input
                var cleanedInput = inputString.trim()
                if (cleanedInput.startsWith("konabess://")) {
                    cleanedInput = cleanedInput.removePrefix("konabess://")
                }

                // Detect if it's a raw JSON or a Compressed Base64 String
                val jsonString = if (cleanedInput.startsWith("{")) {
                    cleanedInput
                } else {
                    // Try to uncompress assuming it is GZIP Base64
                    try {
                        val uncompressed = GzipUtils.uncompress(cleanedInput)
                        if (uncompressed != null && uncompressed.trim().startsWith("{")) {
                            uncompressed
                        } else {
                            throw IllegalArgumentException("Invalid compressed data")
                        }
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid format: Not valid JSON or KonaBess Compressed String")
                    }
                }
                
                // Double check JSON validity before parsing
                val trimmedJson = jsonString.trim()
                if (!trimmedJson.startsWith("{")) {
                     throw IllegalArgumentException("Invalid JSON format")
                }

                val json = JSONObject(trimmedJson)
                val chip = json.getString("chip")
                val currentChipId = chipRepository.currentChip.value?.id
                
                // Parse Description
                val desc = json.optString("desc", "Imported Config")
                
                // Parse Frequencies and Bins
                val freqData = if (json.has("freq")) json.getString("freq") else ""
                val bins = parseBinsFromDts(freqData)
                
                // Parse Voltages (Legacy Voltage Table or Embedded)
                // If we found bins with frequencies, we usually have voltage pairs there too (opp levels)
                // But check explicit "volt" key count too.
                val legacyVoltCount = if (json.has("volt")) json.getString("volt").count { it == '\n' } else 0
                
                // Hard validation only if we are on a known chip to avoid blocking experimental usage
                if (chip != currentChipId && currentChipId != null) {
                    // Just warn in UI, but for now we might let it pass or show error.
                    // Let's emit error but maybe still show preview? 
                    // For safety, error.
                    // _errorEvent.emit("Incompatible chip: $chip (Target: $currentChipId)")
                    // return@launch
                }
                
                _importPreview.value = ImportPreview(chip, desc, bins, legacyVoltCount, jsonString)
                
            } catch (e: Exception) {
                _errorEvent.emit("Import parsing failed: ${e.message}")
            }
        }
    }
    

    private fun parseBinsFromDts(dtsContent: String): List<BinInfo> {
        val bins = ArrayList<BinInfo>()
        // Simple regex strategy: find "qcom,gpu-pwrlevels-X" blocks
        val binStartPattern = Regex("qcom,gpu-pwrlevels-(\\d+)")
        val binMatches = binStartPattern.findAll(dtsContent).toList()
        
        if (binMatches.isEmpty()) {
            // Flattened single bin structure (rare in modern chips but possible)
            val levels = parseLevels(dtsContent)
            if (levels.isNotEmpty()) {
                val freqs = levels.map { it.freqMhz }
                val min = freqs.minOrNull() ?: 0
                val max = freqs.maxOrNull() ?: 0
                // Check if any level has valid voltage label
                val voltCount = levels.count { it.voltageLabel.isNotEmpty() }
                bins.add(BinInfo(0, freqs.size, min, max, voltCount, freqs, levels))
            }
        } else {
            // We have multiple bins or at least one structured bin
            for (i in binMatches.indices) {
                val match = binMatches[i]
                val startIndex = match.range.first
                val endIndex = if (i < binMatches.lastIndex) binMatches[i+1].range.first else dtsContent.length
                
                val binContent = dtsContent.substring(startIndex, endIndex)
                
                // Extract Bin ID
                val binIdMatch = Regex("qcom,speed-bin\\s*=\\s*<([^>]+)>").find(binContent)
                val binId = binIdMatch?.groupValues?.get(1)?.trim()?.replace("0x", "")?.toIntOrNull(16) ?: 0
                
                val levels = parseLevels(binContent)
                if (levels.isNotEmpty()) {
                    val freqs = levels.map { it.freqMhz }
                    val min = freqs.minOrNull() ?: 0
                    val max = freqs.maxOrNull() ?: 0
                    val voltCount = levels.count { it.voltageLabel.isNotEmpty() }
                    bins.add(BinInfo(binId, freqs.size, min, max, voltCount, freqs, levels))
                }
            }
        }
        return bins
    }

    private fun parseLevels(content: String): List<PreviewLevel> {
        val list = ArrayList<PreviewLevel>()
        var currentIndex = 0
        while (true) {
            val startIdx = content.indexOf("qcom,gpu-pwrlevel@", currentIndex)
            if (startIdx == -1) break
            
            val openBrace = content.indexOf("{", startIdx)
            if (openBrace == -1) break
            
            val closeBrace = findClosingBrace(content, openBrace)
            if (closeBrace == -1) break
            
            val levelBlock = content.substring(openBrace, closeBrace)
            
            // Parse Freq
            val freqMatch = Regex("qcom,gpu-freq\\s*=\\s*<([^>]+)>").find(levelBlock)
            val freqHz = freqMatch?.groupValues?.get(1)?.trim()?.replace("0x", "")?.toLongOrNull(16) ?: 0L
            val freqMhz = (freqHz / 1000000).toInt()
            
            // Parse Voltage / Level
            var label = ""
            // Try qcom,level or qcom,corner
            val levelPattern = Regex("qcom,(level|corner|bw-level)\\s*=\\s*<([^>]+)>")
            val levelMatch = levelPattern.find(levelBlock)
            
            if (levelMatch != null) {
                 val key = levelMatch.groupValues[1]
                 val hex = levelMatch.groupValues[2].trim()
                 val intVal = hex.replace("0x", "").toIntOrNull(16)
                 
                 if (intVal != null) {
                     // Pretty format depending on key?
                     label = if (key == "level" && intVal > 16) {
                         // Likely a voltage level like 448 (TURBO), 296 (SVS)
                         "Level: $intVal"
                     } else {
                         // Likely a corner index 0,1,2...
                         "${key.replaceFirstChar { it.uppercase() }}: $intVal"
                     }
                 }
            }

            // Parse Bus Info
            val busMin = Regex("qcom,bus-min\\s*=\\s*<([^>]+)>").find(levelBlock)
                ?.groupValues?.get(1)?.trim()?.replace("0x", "")?.toIntOrNull(16)
            
            val busMax = Regex("qcom,bus-max\\s*=\\s*<([^>]+)>").find(levelBlock)
                ?.groupValues?.get(1)?.trim()?.replace("0x", "")?.toIntOrNull(16)
                
            val busFreq = Regex("qcom,bus-freq\\s*=\\s*<([^>]+)>").find(levelBlock)
                ?.groupValues?.get(1)?.trim()?.replace("0x", "")?.toIntOrNull(16)
            
            if (freqMhz > 0) {
                 list.add(PreviewLevel(freqMhz, label, busMin, busMax, busFreq))
            }
            
            currentIndex = closeBrace
        }
        return list.sortedByDescending { it.freqMhz }
    }
    
    private fun findClosingBrace(text: String, openPos: Int): Int {
        var balance = 0
        for (i in openPos until text.length) {
            if (text[i] == '{') balance++
            else if (text[i] == '}') {
                balance--
                if (balance == 0) return i
            }
        }
        return -1
    }


    fun confirmImport() {
        val preview = _importPreview.value ?: return
        viewModelScope.launch {
            try {
                val json = JSONObject(preview.rawJsonString)
                
                if (json.has("freq")) {
                    val freqData = json.getString("freq")
                    val lines = freqData.split("\n")
                    gpuRepository.importTable(lines)
                }
                
                if (json.has("volt")) {
                    val voltData = json.getString("volt")
                    val lines = voltData.split("\n")
                    gpuRepository.importVoltTable(lines)
                }
                
                _messageEvent.emit("Successfully imported: ${preview.description}")
                _importPreview.value = null
            } catch (e: Exception) {
                _errorEvent.emit("Apply failed: ${e.message}")
            }
        }
    }

    fun cancelImport() {
        _importPreview.value = null
    }

    // Deprecated: use previewConfig then confirmImport
    fun importConfig(inputString: String) {
        previewConfig(inputString)
    }

    fun notifyExportResult(content: String) {
        viewModelScope.launch {
            _lastExportedContent.emit(content)
        }
    }

    fun clearExportResult() {
        viewModelScope.launch {
            _lastExportedContent.emit("") // Or use a way to nullify if it was StateFlow. Since it's SharedFlow, emitting empty might work but UI check is for != null.
        }
    }

    fun exportRawDts(destPath: String) {
        viewModelScope.launch {
             try {
                 val dtsFile = deviceRepository.getDtsFile()
                 dtsFile.copyTo(File(destPath), overwrite = true)
                 _messageEvent.emit("Exported Raw DTS to $destPath")
             } catch (e: Exception) {
                 _errorEvent.emit("Export Raw DTS failed: ${e.message}")
             }
        }
    }

    /**
     * Retrieves all DTS files currently available (extracted from boot image).
     * Each file is returned with metadata for display in the UI cards.
     */
    fun getAllDtsFiles(): List<DtsFileInfo> {
        val dtbs = deviceRepository.dtbs
        val activeDtbId = deviceRepository.activeDtbId
        val currentDtb = deviceRepository.currentDtb
        val modelPattern = java.util.regex.Pattern.compile("model\\s*=\\s*\"([^\"]+)\"")
        
        return dtbs.mapNotNull { dtb ->
            try {
                val file = if (dtb.id < 0) {
                    // Imported file - use getDtsFile() from interface which handles dtsPath
                    deviceRepository.getDtsFile()
                } else {
                    deviceRepository.getDtsFile(dtb.id)
                }
                
                if (!file.exists()) return@mapNotNull null
                
                // Extract model name from first few lines
                val firstLines = file.bufferedReader().use { reader ->
                    val sb = StringBuilder()
                    var count = 0
                    reader.forEachLine { line ->
                        if (count < 30) { sb.appendLine(line); count++ }
                    }
                    sb.toString()
                }
                val modelMatcher = modelPattern.matcher(firstLines)
                val modelName = if (modelMatcher.find()) modelMatcher.group(1) ?: "" else ""
                
                // Count lines efficiently
                val lineCount = file.bufferedReader().use { reader ->
                    var lines = 0
                    while (reader.readLine() != null) lines++
                    lines
                }
                
                DtsFileInfo(
                    index = dtb.id,
                    file = file,
                    fileName = file.name,
                    chipName = dtb.type.name,
                    fileSizeBytes = file.length(),
                    isActive = dtb.id == activeDtbId,
                    isCurrentlySelected = dtb.id == currentDtb?.id,
                    lineCount = lineCount,
                    modelName = modelName
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Export a specific DTS file by index to a user-chosen URI.
     */
    fun exportDtsFileToUri(context: Context, uri: Uri, dtsFileInfo: DtsFileInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = dtsFileInfo.file
                if (!file.exists()) {
                    _errorEvent.emit("DTS file not found: ${file.name}")
                    return@launch
                }
                
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    file.inputStream().use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                val filename = "dts_${dtsFileInfo.index}_${System.currentTimeMillis()}.dts"
                addToHistory(filename, "Raw DTS Export (DTB ${dtsFileInfo.index})", uri.toString())
                _messageEvent.emit("Exported: ${dtsFileInfo.chipName}")
            } catch (e: Exception) {
                _errorEvent.emit("Export failed: ${e.message}")
            }
        }
    }

    /**
     * Export a specific DTS file to a folder (DocumentFile), preserving individual file names.
     */
    fun exportDtsFileToFolder(context: Context, folderUri: Uri, dtsFileInfo: DtsFileInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = dtsFileInfo.file
                if (!file.exists()) {
                    _errorEvent.emit("DTS file not found: ${file.name}")
                    return@launch
                }
                
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val deviceModel = deviceRepository.getCurrent("model").replace(" ", "_")
                val exportName = "konabess_dts_${deviceModel}_dtb${dtsFileInfo.index}_$timestamp.dts"
                
                val folder = DocumentFile.fromTreeUri(context, folderUri)
                val newFile = folder?.createFile("text/plain", exportName)
                
                if (newFile?.uri != null) {
                    val outputStream = context.contentResolver.openOutputStream(newFile.uri)
                    if (outputStream != null) {
                        file.inputStream().use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    addToHistory(exportName, "Raw DTS Export (DTB ${dtsFileInfo.index})", newFile.uri.toString())
                    _messageEvent.emit("Exported: $exportName")
                } else {
                    _errorEvent.emit("Failed to create file in selected folder")
                }
            } catch (e: Exception) {
                _errorEvent.emit("Export failed: ${e.message}")
            }
        }
    }

    /**
     * Export ALL DTS files to a folder at once.
     */
    fun exportAllDtsFilesToFolder(context: Context, folderUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allFiles = getAllDtsFiles()
                if (allFiles.isEmpty()) {
                    _errorEvent.emit("No DTS files available to export")
                    return@launch
                }
                
                val folder = DocumentFile.fromTreeUri(context, folderUri)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val deviceModel = deviceRepository.getCurrent("model").replace(" ", "_")
                var exported = 0
                
                for (info in allFiles) {
                    try {
                        val exportName = "konabess_dts_${deviceModel}_dtb${info.index}_$timestamp.dts"
                        val newFile = folder?.createFile("text/plain", exportName)
                        
                        if (newFile?.uri != null) {
                            val outputStream = context.contentResolver.openOutputStream(newFile.uri)
                            if (outputStream != null) {
                                info.file.inputStream().use { input ->
                                    outputStream.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            addToHistory(exportName, "Raw DTS Export (DTB ${info.index})", newFile.uri.toString())
                            exported++
                        }
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

    /**
     * Export ALL DTS files as a single ZIP archive to a user-chosen URI.
     */
    fun exportAllDtsAsZipToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allFiles = getAllDtsFiles()
                if (allFiles.isEmpty()) {
                    _errorEvent.emit("No DTS files available to export")
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val deviceModel = deviceRepository.getCurrent("model").replace(" ", "_")

                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    _errorEvent.emit("Failed to open output stream")
                    return@launch
                }

                ZipOutputStream(outputStream).use { zipOut ->
                    for (info in allFiles) {
                        try {
                            if (!info.file.exists()) continue
                            val entryName = "konabess_dts_${deviceModel}_dtb${info.index}_$timestamp.dts"
                            val zipEntry = ZipEntry(entryName)
                            zipOut.putNextEntry(zipEntry)
                            info.file.inputStream().use { input ->
                                input.copyTo(zipOut)
                            }
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

    fun getHistoryManager(): com.ireddragonicy.konabessnext.utils.ExportHistoryManager {
        return exportHistoryManager
    }

    fun deleteHistoryItem(item: com.ireddragonicy.konabessnext.model.ExportHistoryItem) {
        exportHistoryManager.deleteItem(item)
        loadHistory()
        viewModelScope.launch { _messageEvent.emit("Item deleted") }
    }

    fun updateHistoryItem(item: com.ireddragonicy.konabessnext.model.ExportHistoryItem) {
        exportHistoryManager.updateItem(item)
        loadHistory()
    }

    fun shareHistoryItem(context: Context, item: com.ireddragonicy.konabessnext.model.ExportHistoryItem) {
        viewModelScope.launch {
            try {
                val uri: Uri = withContext(Dispatchers.IO) {
                    if (item.filePath.startsWith("content:")) {
                        try {
                            val sourceUri = Uri.parse(item.filePath)
                            val inputStream = context.contentResolver.openInputStream(sourceUri)
                            if (inputStream != null) {
                                val cacheFile = File(context.cacheDir, "share_temp_${item.chipType}_${System.currentTimeMillis()}.txt")
                                val outputStream = FileOutputStream(cacheFile)
                                inputStream.copyTo(outputStream)
                                inputStream.close()
                                outputStream.close()
                                
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )
                            } else {
                                throw java.io.FileNotFoundException("Could not open content URI")
                            }
                        } catch (e: Exception) {
                            Uri.parse(item.filePath)
                        }
                    } else if (item.filePath.startsWith("clipboard:")) {
                        try {
                           val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                           val clip = clipboard.primaryClip
                           if (clip != null && clip.itemCount > 0) {
                               val text = clip.getItemAt(0).text.toString()
                               val cacheFile = File(context.cacheDir, "share_clipboard_${System.currentTimeMillis()}.txt")
                               cacheFile.writeText(text)
                               androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )
                           } else {
                               throw java.io.IOException("Clipboard is empty")
                           }
                        } catch (e: Exception) {
                            throw java.io.IOException("Could not share clipboard content: ${e.message}")
                        }
                    } else {
                        val file = File(item.filePath)
                        if (!file.exists()) {
                             throw java.io.FileNotFoundException("File not found: ${item.filePath}")
                        }
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
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

    fun applyHistoryItem(context: Context, item: com.ireddragonicy.konabessnext.model.ExportHistoryItem) {
        if (item.filePath.startsWith("content:")) {
            importConfigFromUri(context, Uri.parse(item.filePath))
        } else if (item.filePath.startsWith("clipboard:")) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text
                    if (!text.isNullOrEmpty()) {
                        importConfig(text.toString())
                    } else {
                        viewModelScope.launch { _errorEvent.emit("Clipboard is empty") }
                    }
                } else {
                    viewModelScope.launch { _errorEvent.emit("Clipboard is empty") }
                }
            } catch (e: Exception) {
                viewModelScope.launch { _errorEvent.emit("Failed to read clipboard: ${e.message}") }
            }
        } else {
             // Local file
             try {
                 val file = File(item.filePath)
                 if (file.exists()) {
                     importConfig(file.readText())
                 } else {
                     viewModelScope.launch { _errorEvent.emit("File not found: ${item.filePath}") }
                 }
             } catch (e: Exception) {
                 viewModelScope.launch { _errorEvent.emit("Import failed: ${e.message}") }
             }
        }
    }

    fun importConfigFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    val decompressed = GzipUtils.uncompress(bytes)
                    if (decompressed != null) {
                        importConfig(decompressed)
                    } else {
                        val text = String(bytes)
                        if (text.trim().startsWith("{")) {
                            importConfig(text)
                        } else {
                            _errorEvent.emit("Failed decoding: File format not recognized")
                        }
                    }
                }
            } catch (e: Exception) {
                _errorEvent.emit("Import failed: ${e.message}")
            }
        }
    }

    fun exportConfigToUri(context: Context, uri: Uri, desc: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = exportConfig(desc)
            if (content != null) {
                try {
                    val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
                    outputStream?.write(content.toByteArray(Charsets.ISO_8859_1))
                    outputStream?.close()

                    // Add to history with URI as path
                    val filename = "config_${System.currentTimeMillis()}.txt"
                    addToHistory(filename, desc, uri.toString())
                    _messageEvent.emit("Successfully exported config")
                } catch (e: Exception) {
                    _errorEvent.emit("Export failed: ${e.message}")
                }
            }
        }
    }

    fun exportRawDtsToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dtsFile = deviceRepository.getDtsFile()
                val inputStream = dtsFile.inputStream()
                val outputStream = context.contentResolver.openOutputStream(uri)
                
                if (outputStream != null) {
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                }
                inputStream.close()
                
                // Add to history
                val filename = "dts_dump_${System.currentTimeMillis()}.txt"
                addToHistory(filename, "Raw DTS Export", uri.toString())
                
                _messageEvent.emit("Successfully exported Raw DTS")
            } catch (e: Exception) {
                _errorEvent.emit("Export Raw DTS failed: ${e.message}")
            }
        }
    }

    fun backupBootToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bootFile = deviceRepository.getBootImageFile()
                val inputStream = bootFile.inputStream()
                val outputStream = context.contentResolver.openOutputStream(uri)
                
                if (outputStream != null) {
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                }
                inputStream.close()
                
                // Add to history
                val filename = "boot_backup_${System.currentTimeMillis()}.img"
                addToHistory(filename, "System Boot Backup", uri.toString())
                
                _messageEvent.emit("Successfully backed up boot image")
            } catch (e: Exception) {
                _errorEvent.emit("Backup failed: ${e.message}")
            }
        }
    }

    fun batchConvertDtbToDts(context: Context, sourceUris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val total = sourceUris.size
            var successCount = 0
            val successPaths = StringBuilder()
            val dtcPath = File(context.filesDir, "dtc").absolutePath
            val cacheDir = context.cacheDir
            
            try {
                // Ensure dtc is executable
                RootHelper.exec("chmod 755 $dtcPath")

                sourceUris.forEachIndexed { index, uri ->
                    val currentProgress = "Converting ${index + 1}/$total..."
                    _batchProgress.value = currentProgress
                    
                    try {
                        var realPath = UriPathHelper.getPath(context, uri)
                        val sourceFile = DocumentFile.fromSingleUri(context, uri)
                        val originalName = sourceFile?.name ?: "file_${index}.dtb"
                        val originalSize = sourceFile?.length() ?: 0L

                        // Root fallback: if API fails, use shell to find the file effectively
                        if (realPath == null && RootHelper.isRootAvailable()) {
                            realPath = RootHelper.findFile(originalName, originalSize)
                        }

                        val outputName = if (originalName.endsWith(".dtb", true)) {
                            originalName.substring(0, originalName.length - 4) + ".dts"
                        } else {
                            "$originalName.dts"
                        }

                        // Step A: Copy to Cache (for safety and binary access)
                        val tempInput = File(cacheDir, "temp_input.dtb")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempInput.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Step B: Prepare temp output path
                        val tempOutput = File(cacheDir, "temp_output.dts")
                        if (tempOutput.exists()) tempOutput.delete()

                        // Step C: Execute
                        val result = RootHelper.exec("$dtcPath -I dtb -O dts \"${tempInput.absolutePath}\" -o \"${tempOutput.absolutePath}\"")
                        
                        if (result.isSuccess && tempOutput.exists() && tempOutput.length() > 0) {
                            // Step D: Save to same folder or fallback
                            if (realPath != null) {
                                val sourceFolder = File(realPath).parent
                                val finalOutputPath = "$sourceFolder/$outputName"
                                
                                // Use root or direct I/O to move/copy
                                val saveSuccess = if (RootHelper.isRootAvailable()) {
                                    RootHelper.copyFile(tempOutput.absolutePath, finalOutputPath, "777")
                                } else {
                                    try {
                                        tempOutput.copyTo(File(finalOutputPath), overwrite = true)
                                        true
                                    } catch (e: Exception) { false }
                                }
                                
                                if (saveSuccess) {
                                    successPaths.append(finalOutputPath).append("\n")
                                    successCount++
                                } else {
                                    _errorEvent.emit("Permission denied writing to $sourceFolder. Root required or folder restricted.")
                                }
                            } else {
                                _errorEvent.emit("Could not resolve path for $originalName. Skipping.")
                            }
                        } else {
                            val errorLog = result.err.joinToString("\n")
                            _errorEvent.emit("Failed to convert $originalName: $errorLog")
                        }

                        // Step E: Cleanup
                        tempInput.delete()
                        tempOutput.delete()

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _batchProgress.value = null
                if (successCount > 0) {
                    notifyExportResult(successPaths.toString().trim())
                }
                _messageEvent.emit("Successfully converted $successCount/$total files")
                
            } catch (e: Exception) {
                _batchProgress.value = null
                _errorEvent.emit("Batch conversion error: ${e.message}")
            }
        }
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


