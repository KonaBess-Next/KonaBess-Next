package com.ireddragonicy.konabessnext.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.model.LevelUiModel
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.model.UiText
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.utils.DtsEditorDebug
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Slimmed-down SharedGpuViewModel — retains only global coordination:
 * - ViewMode switching
 * - Loading / Workbench state
 * - GUI editor state (bins, opps, binUiModels, binOffsets)
 * - Global save / undo / redo
 * - Level manipulation (add/remove/duplicate)
 * - Export DTS
 *
 * Text editing, linting, search, and code-folding are in [TextEditorViewModel].
 * Visual tree manipulation and tree scroll are in [VisualTreeViewModel].
 */
@HiltViewModel
class SharedGpuViewModel @Inject constructor(
    private val application: Application,
    private val repository: GpuRepository,
    private val chipRepository: com.ireddragonicy.konabessnext.repository.ChipRepository
) : AndroidViewModel(application) {

    enum class ViewMode { MAIN_EDITOR, TEXT_ADVANCED, VISUAL_TREE }

    private val _viewMode = MutableStateFlow(ViewMode.MAIN_EDITOR)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val gpuModelName: StateFlow<String> = repository.dtsLines
        .debounce(2000)
        .map { repository.getGpuModelName() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    fun updateGpuModelName(newName: String) {
        repository.updateGpuModelName(newName)
    }

    // --- State Proxies from Repository (SSOT) ---

    val dtsContent: StateFlow<String> = repository.dtsContent
        .stateIn(viewModelScope, SharingStarted.Lazily, "")
    val bins: StateFlow<List<Bin>> = repository.bins
    val opps: StateFlow<List<Opp>> = repository.opps

    // Preview / Transient State
    private val _binOffsets = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val binOffsets = _binOffsets.asStateFlow()

    val isDirty = repository.isDirty
    val canUndo = repository.canUndo
    val canRedo = repository.canRedo
    val history = repository.history

    val currentChip = chipRepository.currentChip

    // --- UI State ---
    private val _isLoading = MutableStateFlow(true)

    val binListState: StateFlow<UiState<List<Bin>>> = combine(repository.bins, _isLoading) { bins, loading ->
        if (loading) {
            UiState.Loading
        } else if (bins.isNotEmpty()) {
            UiState.Success(bins)
        } else {
            UiState.Error(UiText.StringResource(com.ireddragonicy.konabessnext.R.string.no_gpu_tables_found))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    // --- Derived UI Models ---

    val binUiModels: StateFlow<Map<Int, List<LevelUiModel>>> = combine(bins, _binOffsets, currentChip, opps) { list, offsets, _, oppList ->
        mapBinsToUiModels(list, offsets, oppList)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /**
     * Resets global transient state when switching to a new device/DTS.
     * Child ViewModels (TextEditorViewModel, VisualTreeViewModel) reset their own state.
     */
    fun resetEditorState() {
        DtsEditorDebug.dumpCounters()
        DtsEditorDebug.resetCounters()
        _binOffsets.value = emptyMap()
        _viewMode.value = ViewMode.MAIN_EDITOR
    }

    // --- Actions ---

    fun loadData() {
        resetEditorState()

        _isLoading.value = true
        _workbenchState.value = WorkbenchState.Loading

        viewModelScope.launch {
            try {
                repository.loadTable()

                val lines = repository.dtsLines.value
                if (lines.isNotEmpty()) {
                    withTimeoutOrNull(3000) {
                        repository.bins.drop(1).first()
                    }
                }

                _isLoading.value = false
                _workbenchState.value = WorkbenchState.Ready
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
                _workbenchState.value = WorkbenchState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    // GUI -> Repository
    fun updateParameter(binIndex: Int, levelIndex: Int, lineIndex: Int, encodedLine: String, historyMsg: String) {
        val parts = encodedLine.split("=")
        if (parts.size >= 2) {
            val key = parts[0].trim()
            val valueWithBrackets = parts[1].trim().trim(';')
            val value = valueWithBrackets.replace("<", "").replace(">", "").trim()
            repository.updateParameterInBin(binIndex, levelIndex, key, value)
        }
    }

    fun updateOppVoltage(frequency: Long, newVolt: Long) {
        repository.updateOppVoltage(frequency, newVolt)
    }

    fun undo() = repository.undo()
    fun redo() = repository.redo()

    fun save(showToast: Boolean = true) {
        viewModelScope.launch {
            repository.saveTable()
        }
    }

    fun exportRawDts(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = dtsContent.value
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray())
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "DTS Saved", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateBinLevel(binIndex: Int, levelIndex: Int, freqMhz: Int, volt: Int) {
        val updates = mutableListOf<GpuRepository.ParameterUpdate>()
        val newFreqHz = freqMhz * 1_000_000L

        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,gpu-freq", newFreqHz.toString()))
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "opp-hz", newFreqHz.toString()))
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,level", volt.toString()))
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,cx-level", volt.toString()))
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,vdd-level", volt.toString()))
        updates.add(GpuRepository.ParameterUpdate(binIndex, levelIndex, "opp-microvolt", volt.toString()))

        repository.batchUpdateParameters(updates, "Set Bin $binIndex Level $levelIndex: ${freqMhz}MHz / $volt")
    }

    @Deprecated("Use updateBinLevel for atomic updates")
    fun updateFrequency(binIndex: Int, levelIndex: Int, newFreqMhz: Int) {
        val newFreq = newFreqMhz * 1_000_000L
        repository.updateParameterInBin(binIndex, levelIndex, "qcom,gpu-freq", newFreq.toString(), "Set Bin $binIndex Level $levelIndex Freq to ${newFreqMhz}MHz")
        repository.updateParameterInBin(binIndex, levelIndex, "opp-hz", newFreq.toString())
    }

    @Deprecated("Use updateBinLevel for atomic updates")
    fun updateVoltage(binIndex: Int, levelIndex: Int, newVolt: Int) {
         val updates = listOf(
             GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,level", newVolt.toString()),
             GpuRepository.ParameterUpdate(binIndex, levelIndex, "qcom,cx-level", newVolt.toString()),
             GpuRepository.ParameterUpdate(binIndex, levelIndex, "opp-microvolt", newVolt.toString())
         )
         repository.batchUpdateParameters(updates, "Set Bin $binIndex Level $levelIndex Volt to ${newVolt}")
    }

    fun setBinOffset(binId: Int, offsetMhz: Float) {
        val current = _binOffsets.value.toMutableMap()
        current[binId] = offsetMhz
        _binOffsets.value = current
    }

    fun applyGlobalOffset(binId: Int, offsetMhz: Int) {
        if (offsetMhz == 0) return
        val currentBins = bins.value
        val binIndex = currentBins.indexOfFirst { it.id == binId }
        val bin = currentBins.getOrNull(binIndex) ?: return

        val updates = mutableListOf<GpuRepository.ParameterUpdate>()

        bin.levels.forEachIndexed { i, level ->
            val currentFreq = level.frequency
            if (currentFreq > 0) {
                val newFreq = currentFreq + (offsetMhz * 1_000_000L)
                if (newFreq > 0) {
                    updates.add(GpuRepository.ParameterUpdate(binIndex, i, "qcom,gpu-freq", newFreq.toString()))
                    updates.add(GpuRepository.ParameterUpdate(binIndex, i, "opp-hz", newFreq.toString()))
                }
            }
        }

        if (updates.isNotEmpty()) {
            repository.batchUpdateParameters(updates, "Applied Global Offset ${offsetMhz}MHz to Bin $binId")
            val currentOffsets = _binOffsets.value.toMutableMap()
            currentOffsets.remove(binId)
            _binOffsets.value = currentOffsets
        }
    }

    fun switchViewMode(mode: ViewMode) { _viewMode.value = mode }

    fun getLevelStrings() = chipRepository.getLevelStringsForCurrentChip()
    fun getLevelValues() = chipRepository.getLevelsForCurrentChip()

    // --- Level Manipulation ---
    fun addFrequencyWrapper(binIndex: Int, toTop: Boolean) {
        repository.addLevel(binIndex, toTop)
    }

    fun duplicateFrequency(binIndex: Int, index: Int) {
        repository.duplicateLevelAt(binIndex, index)
    }

    fun removeFrequency(binIndex: Int, index: Int) {
        repository.deleteLevel(binIndex, index)
    }

    fun reorderFrequency(binIndex: Int, from: Int, to: Int) {}

    // --- Logic Helpers ---

    private fun mapBinsToUiModels(bins: List<Bin>, offsets: Map<Int, Float> = emptyMap(), oppList: List<Opp> = emptyList()): Map<Int, List<LevelUiModel>> {
        val context = application.applicationContext
        return bins.mapIndexed { i, bin ->
            val offsetMhz = offsets[bin.id] ?: 0f
            i to bin.levels.mapIndexedNotNull { j, lvl -> parseLevelToUi(j, lvl, context, offsetMhz, oppList) }
        }.toMap()
    }

    @Suppress("unused")
    private fun mapBinsToUiModels(bins: List<Bin>): Map<Int, List<LevelUiModel>> {
        return mapBinsToUiModels(bins, emptyMap(), emptyList())
    }

    private fun parseLevelToUi(index: Int, level: Level, context: android.content.Context, offsetMhz: Float = 0f, oppList: List<Opp> = emptyList()): LevelUiModel? {
        val freqRaw = level.frequency
        if (freqRaw <= 0) return null

        val freqWithOffset = if (offsetMhz != 0f) {
             freqRaw + (offsetMhz * 1_000_000L).toLong()
        } else {
             freqRaw
        }

        val unit = context.getSharedPreferences(SettingsViewModel.PREFS_NAME, 0)
            .getInt(SettingsViewModel.KEY_FREQ_UNIT, SettingsViewModel.FREQ_UNIT_MHZ)

        val freqStr = com.ireddragonicy.konabessnext.utils.FrequencyFormatter.format(context, freqWithOffset, unit)
        val finalFreqStr = if (offsetMhz != 0f) "$freqStr*" else freqStr

        val bMin = level.busMin
        val bMax = level.busMax
        val bFreq = level.busFreq

        var vLevel = level.voltageLevel
        var voltageDisplayStr = ""

        if (vLevel == -1 && oppList.isNotEmpty()) {
            val matchingOpp = oppList.find { it.frequency == freqRaw }
                ?: oppList.minByOrNull { kotlin.math.abs(it.frequency - freqRaw) }
            if (matchingOpp != null) {
                voltageDisplayStr = "Volt: ${matchingOpp.volt}"
            }
        } else if (vLevel != -1) {
            val chip = currentChip.value
            val levelName = if (chip != null) {
                 val raw = chip.resolvedLevels[vLevel - 1] ?: chip.resolvedLevels[vLevel] ?: ""
                 if (raw.contains(" - ")) raw.substringAfter(" - ") else raw
            } else ""
            val finalLevelName = if (levelName.isNotEmpty()) " - $levelName" else ""
            voltageDisplayStr = "Volt: $vLevel$finalLevelName"
        }

        return LevelUiModel(
            originalIndex = index,
            frequencyLabel = UiText.DynamicString(finalFreqStr),
            busMin = if (bMin > -1) "Min: $bMin" else "",
            busMax = if (bMax > -1) "Max: $bMax" else "",
            busFreq = if (bFreq > -1) "Freq: $bFreq" else "",
            voltageLabel = UiText.DynamicString(voltageDisplayStr),
            voltageVal = voltageDisplayStr,
            isVisible = true
        )
    }

    private val _workbenchState = MutableStateFlow<WorkbenchState>(WorkbenchState.Loading)
    val workbenchState = _workbenchState.asStateFlow()
    sealed class WorkbenchState { object Loading : WorkbenchState(); object Ready : WorkbenchState(); data class Error(val msg: String) : WorkbenchState() }
}