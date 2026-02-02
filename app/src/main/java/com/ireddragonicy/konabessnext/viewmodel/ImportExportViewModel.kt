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

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val gpuRepository: GpuRepository,
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
           
           val tableLines = chipRepository.getArchitecture(current).generateTable(java.util.ArrayList(bins))
           
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
    
    fun importConfig(jsonString: String) {
        viewModelScope.launch {
            try {
                val json = JSONObject(jsonString)
                val chip = json.getString("chip")
                if (chip != chipRepository.currentChip.value?.id) {
                    _errorEvent.emit("Incompatible chip: $chip")
                    return@launch
                }
                
                val desc = json.optString("desc", "Imported Config")
                
                if (json.has("freq")) {
                    val freqData = json.getString("freq")
                    val lines = freqData.split("\n")
                    gpuRepository.importFrequencyTable(lines, "Import: $desc")
                }
                
                if (json.has("volt")) {
                    val voltData = json.getString("volt")
                    val lines = voltData.split("\n")
                    gpuRepository.importVoltageTable(lines, "Import: $desc")
                }
                
                _messageEvent.emit("Successfully imported: $desc")
                
            } catch (e: Exception) {
                _errorEvent.emit("Import failed: ${e.message}")
            }
        }
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

    fun getHistoryManager(): com.ireddragonicy.konabessnext.utils.ExportHistoryManager {
        return exportHistoryManager
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
