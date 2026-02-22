package com.ireddragonicy.konabessnext.viewmodel

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.TargetPartition
import com.ireddragonicy.konabessnext.model.dts.DtsError
import com.ireddragonicy.konabessnext.model.dts.Severity
import com.ireddragonicy.konabessnext.repository.DisplayRepository
import com.ireddragonicy.konabessnext.repository.DtsDataProvider
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.utils.DtsEditorDebug
import com.ireddragonicy.konabessnext.utils.DtsFormatter
import com.ireddragonicy.konabessnext.utils.DtsLexer
import com.ireddragonicy.konabessnext.utils.DtsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel responsible for all raw text editing concerns:
 * - DTS content delivery to the Sora Editor
 * - Lint (error checking) via DtsLexer/DtsParser
 * - Content persistence via repositories
 * - Reformat code
 *
 * Search and highlighting are now handled by Sora Editor directly.
 *
 * Partition-aware: switches between [GpuRepository] and [DisplayRepository]
 * based on the active partition set via [setActivePartition].
 */
@HiltViewModel
class TextEditorViewModel @Inject constructor(
    private val gpuRepository: GpuRepository,
    private val displayRepository: DisplayRepository
) : ViewModel() {

    // --- Partition-aware provider switching ---
    private val _activePartition = MutableStateFlow(TargetPartition.VENDOR_BOOT)

    private val activeProvider: DtsDataProvider
        get() = if (_activePartition.value == TargetPartition.DTBO) displayRepository else gpuRepository

    fun setActivePartition(partition: TargetPartition) {
        if (_activePartition.value == partition) return
        _activePartition.value = partition
        resetEditorState()
    }

    // --- Expose repo lines that switch based on partition ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val dtsLines: StateFlow<List<String>> = _activePartition
        .flatMapLatest { partition ->
            if (partition == TargetPartition.DTBO) displayRepository.dtsLines
            else gpuRepository.dtsLines
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dtsContent: StateFlow<String> = _activePartition
        .flatMapLatest { partition ->
            if (partition == TargetPartition.DTBO) displayRepository.dtsContent
            else gpuRepository.dtsContent
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    // --- Lint State ---
    val lintErrorsByLine: SnapshotStateMap<Int, List<DtsError>> = mutableStateMapOf()
    private val _lintErrorCount = MutableStateFlow(0)
    val lintErrorCount: StateFlow<Int> = _lintErrorCount.asStateFlow()
    private val _lintErrors = MutableStateFlow<List<DtsError>>(emptyList())
    val lintErrors: StateFlow<List<DtsError>> = _lintErrors.asStateFlow()
    private var lintJob: Job? = null
    private var lintGeneration = 0L
    private val lintDispatcher = Dispatchers.Default.limitedParallelism(1)

    // --- Init ---
    init {
        startEditorContentSync()
    }

    // --- Editor Content Sync (triggers lint on new content) ---
    private fun startEditorContentSync() {
        viewModelScope.launch {
            dtsLines.collect { lines ->
                scheduleLint(lines)
            }
        }
    }

    // --- Text Update Actions ---
    fun updateFromText(content: String, description: String) {
        updateFromEditorLines(content.split("\n"), description)
    }

    fun updateFromEditorLines(lines: List<String>, description: String) {
        val current = dtsLines.value
        if (current.size == lines.size && current.indices.all { current[it] == lines[it] }) {
            return
        }
        DtsEditorDebug.logUpdateFromText(estimateCharCount(lines), lines.size)
        activeProvider.updateContent(lines, description)
    }

    // --- Reformat ---
    fun reformatCode() {
        viewModelScope.launch {
            val current = dtsContent.value
            if (current.isBlank()) return@launch

            val startTime = System.nanoTime()
            val formatted = withContext(Dispatchers.Default) {
                DtsFormatter.format(current)
            }
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            DtsEditorDebug.logReformat(current.length, formatted.length, durationMs)
            if (formatted != current) {
                val lines = formatted.split("\n")
                activeProvider.updateContent(lines, "Reformat code")
            }
        }
    }

    // --- Lint ---
    private fun scheduleLint(lines: List<String>) {
        val snapshot = lines.toList()
        lintJob?.let {
            it.cancel()
            DtsEditorDebug.logLintJobCancel()
        }
        val generation = ++lintGeneration
        DtsEditorDebug.logLintJobStart()
        lintJob = viewModelScope.launch {
            delay(600)
            val startTime = System.nanoTime()
            val grouped = withContext(lintDispatcher) {
                ensureActive()
                try {
                    val content = snapshot.joinToString("\n")
                    val checkCancelled = { ensureActive() }

                    val lexer = DtsLexer(content)
                    val tokens = lexer.tokenize(
                        DtsLexer.LexOptions(
                            checkCancelled = checkCancelled
                        )
                    )

                    ensureActive()

                    val parser = DtsParser(tokens)
                    parser.parse(
                        DtsParser.ParseOptions(
                            checkCancelled = checkCancelled,
                            maxErrors = MAX_LINT_ERRORS
                        )
                    )

                    parser.getLintResult().errors.groupBy { it.line }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    emptyMap()
                }
            }
            ensureActive()
            if (generation != lintGeneration) return@launch

            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            // Diff-update the SnapshotStateMap so only changed lines recompose
            val oldKeys = lintErrorsByLine.keys.toSet()
            val newKeys = grouped.keys
            for (key in oldKeys - newKeys) {
                lintErrorsByLine.remove(key)
            }
            for ((key, value) in grouped) {
                val existing = lintErrorsByLine[key]
                if (existing != value) {
                    lintErrorsByLine[key] = value
                }
            }
            val sortedErrors = grouped.values
                .asSequence()
                .flatten()
                .sortedWith(
                    compareBy<DtsError> { it.line }
                        .thenBy { it.column }
                        .thenBy { if (it.severity == Severity.ERROR) 0 else 1 }
                )
                .toList()

            _lintErrors.value = sortedErrors
            _lintErrorCount.value = sortedErrors.size
            DtsEditorDebug.logLintJobComplete(sortedErrors.size, durationMs)
        }
    }

    // --- Reset (called on new DTS load) ---
    fun resetEditorState() {
        lintErrorsByLine.clear()
        _lintErrors.value = emptyList()
        _lintErrorCount.value = 0
        lintJob?.cancel()
    }

    // --- Private Helpers ---
    private fun estimateCharCount(lines: List<String>): Int {
        var total = if (lines.isEmpty()) 0 else lines.size - 1
        for (line in lines) total += line.length
        return total
    }

    private companion object {
        private const val MAX_LINT_ERRORS = 1000
    }
}
