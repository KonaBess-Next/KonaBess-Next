package com.ireddragonicy.konabessnext.viewmodel

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.editor.EditorSession
import com.ireddragonicy.konabessnext.editor.text.DtsEditorState
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
 * - DTS editor state & session management
 * - Syntax highlighting session
 * - Lint (error checking)
 * - Text search (query, navigation)
 * - Code folding persistence
 * - Text scroll position persistence
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
        // Reset editor state for the new partition's data
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

    // --- Search State ---
    data class SearchState(
        val query: String = "",
        val results: List<SearchResult> = emptyList(),
        val currentIndex: Int = -1
    )
    data class SearchResult(val lineIndex: Int)

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // --- Lint State ---
    val lintErrorsByLine: SnapshotStateMap<Int, List<DtsError>> = mutableStateMapOf()
    private val _lintErrorCount = MutableStateFlow(0)
    val lintErrorCount: StateFlow<Int> = _lintErrorCount.asStateFlow()
    private val _lintErrors = MutableStateFlow<List<DtsError>>(emptyList())
    val lintErrors: StateFlow<List<DtsError>> = _lintErrors.asStateFlow()
    private var lintJob: Job? = null
    private var lintGeneration = 0L
    private val lintDispatcher = Dispatchers.Default.limitedParallelism(1)

    // --- Editor State ---
    val dtsEditorState = DtsEditorState("")
    private val _dtsEditorSessionKey = MutableStateFlow("session:default")
    val dtsEditorSessionKey: StateFlow<String> = _dtsEditorSessionKey.asStateFlow()
    private val collapsedFoldsBySession: SnapshotStateMap<String, Map<Int, Int>> = mutableStateMapOf()

    // --- Scroll Persistence ---
    val textScrollIndex = MutableStateFlow(0)
    val textScrollOffset = MutableStateFlow(0)

    // --- Init ---
    init {
        refreshEditorSessionKey()
        startEditorStateSync()
        startHighlightSession()
    }

    // --- Highlight Session ---
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startHighlightSession() {
        viewModelScope.launch {
            combine(dtsLines, _searchState) { lines, search ->
                lines to search.query
            }
                .debounce(300)
                .distinctUntilChanged()
                .collect { (lines, query) ->
                    DtsEditorDebug.logEditorSessionUpdate(lines.size, query)
                    EditorSession.update(lines, query, viewModelScope)
                }
        }
    }

    // --- Editor State Sync ---
    private fun startEditorStateSync() {
        viewModelScope.launch {
            dtsLines.collect { lines ->
                if (isEditorOutOfSync(lines)) {
                    dtsEditorState.replaceLines(lines)
                }
                refreshEditorSessionKey()
                scheduleLint(lines)
            }
        }
    }

    fun refreshEditorSessionKey() {
        val path = activeProvider.currentDtsPath()
        if (!path.isNullOrBlank()) {
            _dtsEditorSessionKey.value = "session:path:$path"
            return
        }

        if (_dtsEditorSessionKey.value == "session:default") {
            val seed = System.currentTimeMillis()
            _dtsEditorSessionKey.value = "session:memory:$seed"
        }
    }

    // --- Fold Persistence ---
    fun getCollapsedFolds(sessionKey: String): Map<Int, Int> {
        return collapsedFoldsBySession[sessionKey].orEmpty()
    }

    fun updateCollapsedFolds(sessionKey: String, folds: Map<Int, Int>) {
        if (folds.isEmpty()) {
            collapsedFoldsBySession.remove(sessionKey)
        } else {
            collapsedFoldsBySession[sessionKey] = folds.toMap()
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

    // --- Search ---
    fun search(query: String) {
        if (query.isEmpty()) { _searchState.value = SearchState(); return }

        val lines = dtsLines.value
        val results = mutableListOf<SearchResult>()
        lines.forEachIndexed { i, line ->
            if (line.contains(query, true)) results.add(SearchResult(i))
        }
        _searchState.value = SearchState(query, results, if (results.isNotEmpty()) 0 else -1)
    }

    fun nextSearchResult() {
        val current = _searchState.value
        if (current.results.isEmpty()) return
        val nextIdx = (current.currentIndex + 1) % current.results.size
        _searchState.value = current.copy(currentIndex = nextIdx)
    }

    fun previousSearchResult() {
        val current = _searchState.value
        if (current.results.isEmpty()) return
        val prevIdx = if (current.currentIndex - 1 < 0) current.results.size - 1 else current.currentIndex - 1
        _searchState.value = current.copy(currentIndex = prevIdx)
    }

    fun clearSearch() { _searchState.value = SearchState() }

    // --- Reset (called on new DTS load) ---
    fun resetEditorState() {
        textScrollIndex.value = 0
        textScrollOffset.value = 0
        _searchState.value = SearchState()
        lintErrorsByLine.clear()
        _lintErrors.value = emptyList()
        _lintErrorCount.value = 0
        lintJob?.cancel()
    }

    // --- Private Helpers ---
    private fun isEditorOutOfSync(lines: List<String>): Boolean {
        if (dtsEditorState.lines.size != lines.size) return true
        for (i in lines.indices) {
            if (dtsEditorState.lines[i].text != lines[i]) return true
        }
        return false
    }

    private fun estimateCharCount(lines: List<String>): Int {
        var total = if (lines.isEmpty()) 0 else lines.size - 1
        for (line in lines) total += line.length
        return total
    }

    private companion object {
        private const val MAX_LINT_ERRORS = 1000
    }
}
