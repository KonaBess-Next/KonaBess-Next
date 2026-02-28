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
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.repository.DeviceRepositoryInterface
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.utils.BinDiffResult
import com.ireddragonicy.konabessnext.utils.BinDiffUtil
import com.ireddragonicy.konabessnext.utils.DtsChangeDiffUtil
import com.ireddragonicy.konabessnext.utils.DtsEditorDebug
import com.ireddragonicy.konabessnext.utils.DtboDiffUtil
import com.ireddragonicy.konabessnext.domain.TouchDomainManager
import com.ireddragonicy.konabessnext.domain.SpeakerDomainManager
import com.ireddragonicy.konabessnext.repository.DisplayDomainManager
import com.ireddragonicy.konabessnext.model.TargetPartition
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import javax.inject.Inject

/**
 * Slimmed-down SharedDtsViewModel — retains only global coordination:
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
class SharedDtsViewModel @Inject constructor(
    private val application: Application,
    val gpuRepository: GpuRepository,
    val dtboRepository: com.ireddragonicy.konabessnext.repository.DtboRepository,
    private val deviceRepository: DeviceRepositoryInterface,
    private val chipRepository: com.ireddragonicy.konabessnext.repository.ChipRepository,
    private val settingsRepository: com.ireddragonicy.konabessnext.repository.SettingsRepository,
    private val userMessageManager: com.ireddragonicy.konabessnext.utils.UserMessageManager,
    private val displayDomainManager: DisplayDomainManager,
    private val touchDomainManager: TouchDomainManager,
    private val speakerDomainManager: SpeakerDomainManager
) : AndroidViewModel(application) {

    private val repository get() = gpuRepository

    private val activeProvider: com.ireddragonicy.konabessnext.repository.DtsDataProvider
        get() = if (deviceRepository.selectedPartition == TargetPartition.DTBO) dtboRepository else gpuRepository

    enum class ViewMode { MAIN_EDITOR, TEXT_ADVANCED, VISUAL_TREE }

    private val _viewMode = MutableStateFlow(ViewMode.MAIN_EDITOR)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    val gpuModelName: StateFlow<String> = repository.dtsLines
        .map { lines ->
            repository.extractGpuModelNameFast(lines)
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun updateGpuModelName(newName: String) {
        repository.updateGpuModelName(newName)
    }

    // --- State Proxies from Repository (SSOT) ---

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dtsContent: StateFlow<String> = deviceRepository.selectedPartitionFlow
        .flatMapLatest { activeProvider.dtsContent }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")
    val bins: StateFlow<List<Bin>> = repository.bins
    val opps: StateFlow<List<Opp>> = repository.opps
    val memoryTables = repository.memoryTables
    val gmuTables = repository.gmuTables
    private val _detectedActiveBinIndex = MutableStateFlow(-1)
    val detectedActiveBinIndex: StateFlow<Int> = _detectedActiveBinIndex.asStateFlow()
    private val _runtimeGpuFrequencies = MutableStateFlow<List<Long>>(emptyList())
    val runtimeGpuFrequencies: StateFlow<List<Long>> = _runtimeGpuFrequencies.asStateFlow()

    // Preview / Transient State
    private val _binOffsets = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val binOffsets = _binOffsets.asStateFlow()
    private val _originalBins = MutableStateFlow<List<Bin>>(emptyList())
    val originalBins: StateFlow<List<Bin>> = _originalBins.asStateFlow()
    private val _originalDtsLines = MutableStateFlow<List<String>>(emptyList())
    val originalDtsLines: StateFlow<List<String>> = _originalDtsLines.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isDirty: StateFlow<Boolean> = deviceRepository.selectedPartitionFlow
        .flatMapLatest { activeProvider.isDirty }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val canUndo: StateFlow<Boolean> = deviceRepository.selectedPartitionFlow
        .flatMapLatest { activeProvider.canUndo }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val canRedo: StateFlow<Boolean> = deviceRepository.selectedPartitionFlow
        .flatMapLatest { activeProvider.canRedo }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<String>> = deviceRepository.selectedPartitionFlow
        .flatMapLatest { activeProvider.history }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
        _originalBins.value = emptyList()
        _originalDtsLines.value = emptyList()
        _detectedActiveBinIndex.value = -1
        _runtimeGpuFrequencies.value = emptyList()
        _viewMode.value = ViewMode.MAIN_EDITOR
    }

    // --- Actions ---

    fun loadData() {
        resetEditorState()

        _isLoading.value = true
        _workbenchState.value = WorkbenchState.Loading

        viewModelScope.launch {
            try {
                val loadResult = activeProvider.loadTable()
                if (loadResult is com.ireddragonicy.konabessnext.core.model.DomainResult.Failure) {
                    _isLoading.value = false
                    _workbenchState.value = WorkbenchState.Error(loadResult.error.message)
                    return@launch
                }

                val lines = activeProvider.dtsLines.value
                if (lines.isNotEmpty()) {
                    withTimeoutOrNull(3000) {
                        if (activeProvider is GpuRepository) {
                            (activeProvider as GpuRepository).bins.drop(1).first()
                        }
                    }
                }

                _originalDtsLines.value = activeProvider.dtsLines.value.toList()
                if (activeProvider is GpuRepository) {
                    val gpuRepo = activeProvider as GpuRepository
                    _originalBins.value = deepCopyBins(gpuRepo.bins.value)
                    detectActiveBinIndex(gpuRepo.bins.value)
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

    suspend fun calculateDiff(includeUnchanged: Boolean = false): List<BinDiffResult> {
        val isDtbo = deviceRepository.selectedPartition == TargetPartition.DTBO
        val originalLines = _originalDtsLines.value.toList()
        val currentLines = activeProvider.dtsLines.value.toList()

        return withContext(Dispatchers.Default) {
            val comprehensiveDiff = ArrayList<BinDiffResult>()

            if (isDtbo) {
                val oldTree = DtsTreeHelper.parse(originalLines.joinToString("\n"))
                val newTree = DtsTreeHelper.parse(currentLines.joinToString("\n"))
                comprehensiveDiff.addAll(
                    DtboDiffUtil.calculateDiff(oldTree, newTree, displayDomainManager, touchDomainManager, speakerDomainManager)
                )
            } else {
                val semanticBinDiff = BinDiffUtil.calculateDiff(
                    originalBins = _originalBins.value.map { it.copyBin() },
                    currentBins = gpuRepository.bins.value.map { it.copyBin() },
                    includeUnchanged = includeUnchanged
                )
                comprehensiveDiff.addAll(semanticBinDiff)
            }

            DtsChangeDiffUtil.calculateGeneralDiff(originalLines, currentLines)?.let { rawDiff ->
                comprehensiveDiff.add(rawDiff)
            }

            comprehensiveDiff
        }
    }

    fun undo() = activeProvider.undo()
    fun redo() = activeProvider.redo()

    fun save(showToast: Boolean = true) {
        viewModelScope.launch {
            val result = activeProvider.saveTable()
            if (result is com.ireddragonicy.konabessnext.core.model.DomainResult.Failure) {
                userMessageManager.emitError("Save Failed", result.error.message)
            } else if (showToast) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "Saved successfully", Toast.LENGTH_SHORT).show()
                }
            }
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
                    Toast.makeText(context, context.getString(R.string.dts_saved), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.save_failed_format, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    suspend fun tryExportRawDtsToDefault(context: Context): Boolean {
        val defaultUriStr = settingsRepository.getDefaultExportUri() ?: return false
        val defaultUri = Uri.parse(defaultUriStr)
        return try {
            val content = dtsContent.value
            withContext(Dispatchers.IO) {
                val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, defaultUri)
                if (dir == null || !dir.canWrite()) return@withContext false

                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val name = "gpu_config_$timestamp.dts"
                val newFile = dir.createFile("text/plain", name) 
                    ?: return@withContext false
                
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    output.write(content.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to ${settingsRepository.getExportPathDisplay()}/$name", Toast.LENGTH_SHORT).show()
                }
                true
            }
        } catch (e: Exception) {
            false
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

    suspend fun detectActiveBinIndex(parsedBins: List<Bin>) {
        if (parsedBins.isEmpty()) {
            _detectedActiveBinIndex.value = -1
            _runtimeGpuFrequencies.value = emptyList()
            return
        }

        val runtimeFrequencies = when (val result = deviceRepository.getRunTimeGpuFrequencies()) {
            is com.ireddragonicy.konabessnext.core.model.DomainResult.Success -> result.data
            is com.ireddragonicy.konabessnext.core.model.DomainResult.Failure -> emptyList()
        }

        val normalizedRuntime = normalizeFrequencyList(runtimeFrequencies)
        _runtimeGpuFrequencies.value = normalizedRuntime
        if (normalizedRuntime.isEmpty()) {
            _detectedActiveBinIndex.value = -1
            return
        }

        var bestMatchIndex = -1
        var bestMatchScore = 0.0
        var bestSizeDelta = Int.MAX_VALUE

        parsedBins.forEachIndexed { index, bin ->
            val binFrequencies = normalizeFrequencyList(bin.levels.map { it.frequency })
            if (binFrequencies.isEmpty()) return@forEachIndexed

            val score = scoreBinMatch(normalizedRuntime, binFrequencies)
            val sizeDelta = abs(normalizedRuntime.size - binFrequencies.size)
            if (score > bestMatchScore || (score == bestMatchScore && sizeDelta < bestSizeDelta)) {
                bestMatchScore = score
                bestMatchIndex = index
                bestSizeDelta = sizeDelta
            }
        }

        _detectedActiveBinIndex.value = if (bestMatchScore >= ACTIVE_BIN_MIN_SCORE) {
            bestMatchIndex
        } else {
            -1
        }
    }

    private fun normalizeFrequencyList(values: List<Long>): List<Long> {
        val positiveValues = values.filter { it > 0L }
        if (positiveValues.isEmpty()) return emptyList()

        val normalizedToHz = normalizeToHz(positiveValues)
        return normalizedToHz.distinct().sortedDescending()
    }

    private fun normalizeToHz(values: List<Long>): List<Long> {
        val maxValue = values.maxOrNull() ?: return values
        return if (maxValue in 1L until 10_000_000L) {
            values.map { it * 1_000L }
        } else {
            values
        }
    }

    private fun scoreBinMatch(runtimeFrequencies: List<Long>, binFrequencies: List<Long>): Double {
        if (runtimeFrequencies.isEmpty() || binFrequencies.isEmpty()) return 0.0
        if (runtimeFrequencies == binFrequencies || runtimeFrequencies == binFrequencies.asReversed()) return 1.0

        val runtimeSet = runtimeFrequencies.toSet()
        val binSet = binFrequencies.toSet()
        if (runtimeSet == binSet) return 0.98

        val overlapCount = runtimeSet.intersect(binSet).size
        val unionCount = runtimeSet.union(binSet).size
        val jaccard = if (unionCount == 0) 0.0 else overlapCount.toDouble() / unionCount.toDouble()
        val runtimeCoverage = overlapCount.toDouble() / runtimeSet.size.toDouble()
        val binCoverage = overlapCount.toDouble() / binSet.size.toDouble()
        val fuzzyCoverage = fuzzyCoverage(runtimeFrequencies, binFrequencies)

        return (jaccard * 0.55) +
            (runtimeCoverage * 0.25) +
            (binCoverage * 0.10) +
            (fuzzyCoverage * 0.10)
    }

    private fun fuzzyCoverage(runtimeFrequencies: List<Long>, binFrequencies: List<Long>): Double {
        if (runtimeFrequencies.isEmpty() || binFrequencies.isEmpty()) return 0.0
        val matchedCount = runtimeFrequencies.count { runtimeFreq ->
            binFrequencies.any { binFreq -> isFrequencyClose(runtimeFreq, binFreq) }
        }
        return matchedCount.toDouble() / runtimeFrequencies.size.toDouble()
    }

    private fun isFrequencyClose(leftHz: Long, rightHz: Long): Boolean {
        val diff = abs(leftHz - rightHz)
        val relativeTolerance = (maxOf(leftHz, rightHz) * 0.01).toLong()
        return diff <= ABSOLUTE_FREQ_TOLERANCE_HZ || diff <= relativeTolerance
    }

    private fun mapBinsToUiModels(bins: List<Bin>, offsets: Map<Int, Float> = emptyMap(), oppList: List<Opp> = emptyList()): Map<Int, List<LevelUiModel>> {
        val context = application.applicationContext
        return bins.mapIndexed { i, bin ->
            val offsetMhz = offsets[bin.id] ?: 0f
            i to bin.levels.mapIndexedNotNull { j, lvl -> parseLevelToUi(j, lvl, context, offsetMhz, oppList) }
        }.toMap()
    }

    private fun deepCopyBins(source: List<Bin>): List<Bin> {
        return source.map { it.copyBin() }
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

    private companion object {
        const val ACTIVE_BIN_MIN_SCORE = 0.80
        const val ABSOLUTE_FREQ_TOLERANCE_HZ = 2_000_000L
    }
}
