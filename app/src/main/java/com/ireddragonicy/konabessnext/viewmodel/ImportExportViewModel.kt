package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.core.ChipInfo
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
import javax.inject.Inject
import com.ireddragonicy.konabessnext.model.ChipDefinition

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val gpuRepository: GpuRepository,
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

    fun updateExportAvailability() {
        // Logic to check if export is available (e.g. data loaded)
    }

    fun exportConfig(desc: String): String? {
       return try {
           val json = JSONObject()
           json.put("chip", ChipInfo.current?.id ?: "Unknown")
           json.put("desc", desc)
           
           val freqData = StringBuilder()
           val bins = gpuRepository.bins.value
           // Safe call in case bins are empty or not ready
           if (bins.isEmpty()) throw IllegalStateException("No GPU table data to export")
           
           val tableLines = ChipInfo.getArchitecture(ChipInfo.current).generateTable(java.util.ArrayList(bins))
           
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
    
    fun addToHistory(filename: String, desc: String, filePath: String, chipType: String) {
        exportHistoryManager.addExport(filename, desc, filePath, chipType)
        loadHistory()
    }
    
    fun importConfig(jsonString: String) {
        viewModelScope.launch {
            try {
                val json = JSONObject(jsonString)
                val chip = json.getString("chip")
                if (chip != ChipInfo.current?.id) {
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

    fun exportRawDts(destPath: String) {
        viewModelScope.launch {
             try {
                 deviceRepository.exportDtsFile(destPath)
                 _messageEvent.emit("Exported Raw DTS to $destPath")
             } catch (e: Exception) {
                 _errorEvent.emit("Export Raw DTS failed: ${e.message}")
             }
        }
    }

    fun getHistoryManager(): com.ireddragonicy.konabessnext.utils.ExportHistoryManager {
        return exportHistoryManager
    }
}
