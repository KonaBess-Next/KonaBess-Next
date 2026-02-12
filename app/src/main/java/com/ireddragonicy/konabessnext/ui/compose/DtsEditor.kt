package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.ireddragonicy.konabessnext.editor.EditorSession
import com.ireddragonicy.konabessnext.editor.highlight.ComposeHighlighter
import com.ireddragonicy.konabessnext.editor.text.DtsEditorState
import com.ireddragonicy.konabessnext.editor.text.TextCursor
import com.ireddragonicy.konabessnext.editor.text.TextSelection
import com.ireddragonicy.konabessnext.model.dts.DtsError
import com.ireddragonicy.konabessnext.model.dts.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

data class LineSearchResult(val lineIndex: Int)
data class EditorNavigationRequest(
    val line: Int,
    val column: Int,
    val requestId: Long = System.nanoTime()
)

private const val IME_SENTINEL = "\u200B"
private const val TRIPLE_TAP_WINDOW_MS = 350L
private const val EXPAND_STAGE_WORD = 0
private const val EXPAND_STAGE_TOKEN = 1
private const val EXPAND_STAGE_LINE = 2

private data class SelectionRequest(val start: Int, val end: Int)
private data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val depth: Int,
    val parentStartLine: Int?,
    val headerText: String
)

private data class FoldModel(
    val regions: List<FoldRegion>,
    val regionsByStart: Map<Int, FoldRegion>
) {
    companion object {
        val EMPTY = FoldModel(emptyList(), emptyMap())
    }
}

private data class FoldMarker(
    val isFoldable: Boolean,
    val isCollapsed: Boolean,
    val hiddenLineCount: Int
)

private fun isWordChar(ch: Char): Boolean {
    return ch.isLetterOrDigit() || ch == '_'
}

private fun isTokenChar(ch: Char): Boolean {
    return !ch.isWhitespace()
}

private fun selectionAround(
    text: String,
    tapOffset: Int,
    predicate: (Char) -> Boolean
): SelectionRequest {
    if (text.isEmpty()) return SelectionRequest(0, 0)
    val cursor = tapOffset.coerceIn(0, text.length)
    val anchor = when {
        cursor < text.length && predicate(text[cursor]) -> cursor
        cursor > 0 && predicate(text[cursor - 1]) -> cursor - 1
        else -> -1
    }
    if (anchor < 0) return SelectionRequest(cursor, cursor)

    var start = anchor
    var end = anchor + 1
    while (start > 0 && predicate(text[start - 1])) start--
    while (end < text.length && predicate(text[end])) end++
    return SelectionRequest(start, end)
}

private fun wordSelection(text: String, tapOffset: Int): SelectionRequest {
    return selectionAround(text, tapOffset, ::isWordChar)
}

private fun tokenSelection(text: String, tapOffset: Int): SelectionRequest {
    return selectionAround(text, tapOffset, ::isTokenChar)
}

private fun lineSelection(text: String): SelectionRequest {
    return SelectionRequest(0, text.length)
}

private fun nextExpandedSelection(
    text: String,
    anchor: Int,
    previousStage: Int
): Pair<SelectionRequest, Int> {
    val stages = listOf(
        wordSelection(text, anchor) to EXPAND_STAGE_WORD,
        tokenSelection(text, anchor) to EXPAND_STAGE_TOKEN,
        lineSelection(text) to EXPAND_STAGE_LINE
    )

    val previousSelection = when (previousStage) {
        EXPAND_STAGE_WORD -> stages[0].first
        EXPAND_STAGE_TOKEN -> stages[1].first
        EXPAND_STAGE_LINE -> stages[2].first
        else -> null
    }
    var idx = when (previousStage) {
        EXPAND_STAGE_WORD -> 1
        EXPAND_STAGE_TOKEN -> 2
        EXPAND_STAGE_LINE -> 2
        else -> 0
    }

    while (idx < stages.size) {
        val candidate = stages[idx]
        if (previousSelection == null || candidate.first != previousSelection || idx == stages.lastIndex) {
            return candidate
        }
        idx++
    }
    return stages.last()
}

private fun isExpandSelectionShortcut(event: KeyEvent): Boolean {
    return event.type == KeyEventType.KeyDown &&
        event.isShiftPressed &&
        event.isAltPressed &&
        event.key == Key.DirectionRight
}

private fun selectionRangeForLine(
    selection: TextSelection?,
    lineIndex: Int,
    lineLength: Int
): TextRange? {
    val selected = selection ?: return null
    if (selected.isCollapsed) return null

    val start = selected.normalizedStart
    val end = selected.normalizedEnd
    if (lineIndex < start.line || lineIndex > end.line) return null

    val rawStart = if (lineIndex == start.line) start.column else 0
    val rawEnd = if (lineIndex == end.line) end.column else lineLength
    val safeStart = rawStart.coerceIn(0, lineLength)
    val safeEnd = rawEnd.coerceIn(0, lineLength)
    if (safeEnd <= safeStart) return null
    return TextRange(safeStart, safeEnd)
}

private fun normalizeFoldHeader(line: String): String {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return "{...}"
    val beforeBrace = trimmed.substringBefore('{').trim()
    val normalized = if (beforeBrace.isNotEmpty()) beforeBrace else trimmed.removeSuffix("{").trim()
    return (if (normalized.isEmpty()) trimmed else normalized).take(120)
}

private fun buildFoldModel(lines: List<String>): FoldModel {
    if (lines.isEmpty()) return FoldModel.EMPTY

    data class PendingRegion(
        val startLine: Int,
        val depth: Int,
        val parentStartLine: Int?,
        val headerText: String
    )

    val stack = ArrayDeque<PendingRegion>()
    val regions = ArrayList<FoldRegion>(lines.size / 6)
    lines.forEachIndexed { lineIndex, line ->
        for (ch in line) {
            when (ch) {
                '{' -> {
                    stack.addLast(
                        PendingRegion(
                            startLine = lineIndex,
                            depth = stack.size,
                            parentStartLine = stack.lastOrNull()?.startLine,
                            headerText = normalizeFoldHeader(line)
                        )
                    )
                }
                '}' -> {
                    val start = stack.removeLastOrNull() ?: continue
                    if (lineIndex > start.startLine) {
                        regions.add(
                            FoldRegion(
                                startLine = start.startLine,
                                endLine = lineIndex,
                                depth = start.depth,
                                parentStartLine = start.parentStartLine,
                                headerText = start.headerText
                            )
                        )
                    }
                }
            }
        }
    }

    val regionsByStart = HashMap<Int, FoldRegion>(regions.size)
    for (region in regions) {
        val existing = regionsByStart[region.startLine]
        if (existing == null || region.endLine > existing.endLine) {
            regionsByStart[region.startLine] = region
        }
    }
    return FoldModel(
        regions = regions.sortedBy { it.startLine },
        regionsByStart = regionsByStart
    )
}

private fun buildVisibleLineIndices(
    totalLines: Int,
    collapsedStarts: Map<Int, Int>
): List<Int> {
    if (totalLines <= 0) return emptyList()

    val collapsed = collapsedStarts.entries
        .asSequence()
        .filter { it.key in 0 until totalLines && it.value > it.key }
        .sortedBy { it.key }
        .toList()

    val visible = ArrayList<Int>(totalLines)
    var line = 0
    var collapseCursor = 0
    while (line < totalLines) {
        while (collapseCursor < collapsed.size && collapsed[collapseCursor].key < line) {
            collapseCursor++
        }
        if (collapseCursor < collapsed.size && collapsed[collapseCursor].key == line) {
            visible.add(line)
            line = (collapsed[collapseCursor].value + 1).coerceAtLeast(line + 1)
            collapseCursor++
        } else {
            visible.add(line)
            line++
        }
    }
    if (visible.isEmpty()) visible.add(0)
    return visible
}

private fun visibleIndexForLine(
    lineIndex: Int,
    visibleLineIndices: List<Int>,
    collapsedStarts: Map<Int, Int>
): Int {
    if (visibleLineIndices.isEmpty()) return 0
    val direct = visibleLineIndices.indexOf(lineIndex)
    if (direct >= 0) return direct

    val collapsedParentStart = collapsedStarts.entries.firstOrNull { (start, end) ->
        lineIndex in (start + 1)..end
    }?.key
    if (collapsedParentStart != null) {
        val parentVisible = visibleLineIndices.indexOf(collapsedParentStart)
        if (parentVisible >= 0) return parentVisible
    }
    return lineIndex.coerceIn(0, visibleLineIndices.lastIndex)
}

private fun buildStickyHeaderPath(
    firstVisibleLine: Int,
    foldModel: FoldModel
): List<String> {
    if (foldModel.regions.isEmpty()) return emptyList()

    var deepest: FoldRegion? = null
    for (region in foldModel.regions) {
        if (firstVisibleLine <= region.startLine) continue
        if (firstVisibleLine <= region.endLine) {
            if (deepest == null || region.depth > deepest.depth) {
                deepest = region
            }
        }
    }
    deepest ?: return emptyList()

    val path = ArrayDeque<String>()
    var cursor: FoldRegion? = deepest
    while (cursor != null) {
        path.addFirst(cursor.headerText)
        cursor = cursor.parentStartLine?.let { foldModel.regionsByStart[it] }
    }
    return path.toList()
}

@Composable
fun DtsEditor(
    editorState: DtsEditorState,
    onLinesChanged: (List<String>) -> Unit,
    foldSessionKey: String = "default",
    persistedCollapsedFolds: Map<Int, Int> = emptyMap(),
    onFoldStateChanged: (Map<Int, Int>) -> Unit = {},
    searchQuery: String = "",
    searchResultIndex: Int = -1,
    searchResults: List<LineSearchResult> = emptyList(),
    navigationRequest: EditorNavigationRequest? = null,
    lintErrorsByLine: SnapshotStateMap<Int, List<DtsError>> = remember { mutableStateMapOf() },
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val highlightScope = rememberCoroutineScope()
    val highlightCache = EditorSession.highlightCache
    val focusRequester = remember { FocusRequester() }
    var imeValue by remember { mutableStateOf(TextFieldValueWithSentinel()) }
    var hasInputFocus by remember { mutableStateOf(false) }
    var skipFirstRevision by remember(editorState) { mutableStateOf(true) }
    var expandAnchor by remember(editorState) { mutableStateOf<TextCursor?>(editorState.cursor) }
    val currentSelection = editorState.selection
    val hasExpandedSelection = currentSelection?.isCollapsed == false
    var foldModel by remember { mutableStateOf(FoldModel.EMPTY) }
    val collapsedStarts = remember { mutableStateMapOf<Int, Int>() }
    val visibleLineIndices by remember(editorState.lines.size, collapsedStarts, foldModel) {
        derivedStateOf {
            buildVisibleLineIndices(
                totalLines = editorState.lines.size,
                collapsedStarts = collapsedStarts
            )
        }
    }
    val stickyHeaderPath by remember(listState, visibleLineIndices, foldModel) {
        derivedStateOf {
            if (visibleLineIndices.isEmpty()) return@derivedStateOf emptyList<String>()
            val firstVisibleIdx = listState.firstVisibleItemIndex.coerceIn(0, visibleLineIndices.lastIndex)
            val firstVisibleLine = visibleLineIndices[firstVisibleIdx]
            buildStickyHeaderPath(firstVisibleLine, foldModel)
        }
    }
    val stickyHeaderTitle by remember(stickyHeaderPath) {
        derivedStateOf {
            if (stickyHeaderPath.isEmpty()) {
                ""
            } else {
                stickyHeaderPath.takeLast(3).joinToString(" > ")
            }
        }
    }

    fun resetIme() {
        imeValue = TextFieldValueWithSentinel()
    }

    fun updateLineHighlight(lineIndex: Int) {
        val text = editorState.lines.getOrNull(lineIndex)?.text ?: return
        EditorSession.updateLine(
            lineIndex = lineIndex,
            content = text,
            searchQuery = searchQuery,
            scope = highlightScope
        )
    }

    fun refreshVisibleHighlights() {
        EditorSession.update(editorState.copyLines(), searchQuery, highlightScope)
    }

    fun expandCollapsedForLine(targetLine: Int): Boolean {
        val toExpand = collapsedStarts.entries
            .filter { (start, end) -> targetLine in (start + 1)..end }
            .map { it.key }
        if (toExpand.isEmpty()) return false
        toExpand.forEach { collapsedStarts.remove(it) }
        return true
    }

    fun visibleIndexFor(lineIndex: Int): Int {
        return visibleIndexForLine(
            lineIndex = lineIndex,
            visibleLineIndices = visibleLineIndices,
            collapsedStarts = collapsedStarts
        )
    }

    fun toggleFoldAt(lineIndex: Int) {
        val region = foldModel.regionsByStart[lineIndex] ?: return
        if (collapsedStarts.containsKey(lineIndex)) {
            collapsedStarts.remove(lineIndex)
            return
        }

        collapsedStarts[lineIndex] = region.endLine
        val cursor = editorState.cursor
        if (cursor.line in (lineIndex + 1)..region.endLine) {
            val targetColumn = cursor.column.coerceAtMost(editorState.lines[lineIndex].length)
            editorState.moveCursor(lineIndex, targetColumn)
            expandAnchor = editorState.cursor
        }

        val selection = editorState.selection
        if (selection != null && !selection.isCollapsed) {
            val selectionStart = selection.normalizedStart
            val selectionEnd = selection.normalizedEnd
            val intersectsFoldedRange =
                selectionEnd.line > lineIndex && selectionStart.line <= region.endLine
            if (intersectsFoldedRange) {
                editorState.clearSelection()
            }
        }
    }

    fun applyNextExpandSelection() {
        if (editorState.lines.isEmpty()) return
        val cursor = editorState.cursor
        val lineIndex = cursor.line.coerceIn(0, editorState.lines.lastIndex)
        val lineText = editorState.lines[lineIndex].text
        val anchor = expandAnchor
            ?.takeIf { it.line == lineIndex }
            ?.column
            ?.coerceIn(0, lineText.length)
            ?: cursor.column.coerceIn(0, lineText.length)

        val currentSelection = editorState.selection
        val normalizedStart = currentSelection?.normalizedStart
        val normalizedEnd = currentSelection?.normalizedEnd
        val current = if (
            currentSelection != null &&
            !currentSelection.isCollapsed &&
            normalizedStart != null &&
            normalizedEnd != null &&
            normalizedStart.line == lineIndex &&
            normalizedEnd.line == lineIndex
        ) {
            SelectionRequest(normalizedStart.column, normalizedEnd.column)
        } else {
            SelectionRequest(cursor.column.coerceIn(0, lineText.length), cursor.column.coerceIn(0, lineText.length))
        }

        val word = wordSelection(lineText, anchor)
        val token = tokenSelection(lineText, anchor)
        val line = lineSelection(lineText)
        val previousStage = when (current) {
            line -> EXPAND_STAGE_LINE
            token -> EXPAND_STAGE_TOKEN
            word -> EXPAND_STAGE_WORD
            else -> -1
        }
        val (nextSelection, _) = nextExpandedSelection(lineText, anchor, previousStage)
        editorState.setSelection(
            start = TextCursor(lineIndex, nextSelection.start),
            end = TextCursor(lineIndex, nextSelection.end)
        )
        expandAnchor = TextCursor(lineIndex, anchor)
    }

    fun insertChar(ch: Char) {
        val beforeCursor = editorState.cursor
        val beforeLineCount = editorState.lines.size
        editorState.insert(ch) // O(1) amortized for non-newline
        expandAnchor = editorState.cursor

        if (ch == '\n') {
            val afterLineCount = editorState.lines.size
            if (afterLineCount == beforeLineCount + 1) {
                updateLineHighlight(beforeCursor.line)
                val inserted = editorState.lines.getOrNull(beforeCursor.line + 1)?.text.orEmpty()
                EditorSession.insertLine(
                    atIndex = beforeCursor.line + 1,
                    content = inserted,
                    searchQuery = searchQuery,
                    totalLines = beforeLineCount,
                    scope = highlightScope
                )
            } else {
                refreshVisibleHighlights()
            }
            return
        }
        updateLineHighlight(beforeCursor.line)
    }

    fun insertText(text: String) {
        if (text.isEmpty()) return
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.length == 1) {
            insertChar(normalized[0])
            return
        }

        val beforeLine = editorState.cursor.line
        editorState.insertText(normalized) // O(K), K = inserted chars
        expandAnchor = editorState.cursor
        if (!normalized.contains('\n')) {
            updateLineHighlight(beforeLine)
        } else {
            // Multi-line paste can affect many line indices; refresh visible cache only.
            refreshVisibleHighlights()
        }
    }

    fun deleteBackward() {
        val beforeCursor = editorState.cursor
        val beforeLineCount = editorState.lines.size
        val didDelete = editorState.deleteBackward()
        if (!didDelete) return
        expandAnchor = editorState.cursor

        val afterCursor = editorState.cursor
        val afterLineCount = editorState.lines.size
        when {
            beforeLineCount == afterLineCount -> {
                updateLineHighlight(afterCursor.line)
            }
            beforeLineCount == afterLineCount + 1 -> {
                updateLineHighlight(afterCursor.line)
                EditorSession.deleteLine(
                    atIndex = beforeCursor.line,
                    totalLines = afterLineCount
                )
            }
            else -> {
                refreshVisibleHighlights()
            }
        }
    }

    fun moveCursorLeft() {
        val cursor = editorState.cursor
        if (cursor.column > 0) {
            editorState.moveCursor(cursor.line, cursor.column - 1)
        } else if (cursor.line > 0) {
            val previousLength = editorState.lines[cursor.line - 1].length
            editorState.moveCursor(cursor.line - 1, previousLength)
        }
        expandAnchor = editorState.cursor
    }

    fun moveCursorRight() {
        val cursor = editorState.cursor
        val currentLength = editorState.lines[cursor.line].length
        if (cursor.column < currentLength) {
            editorState.moveCursor(cursor.line, cursor.column + 1)
        } else if (cursor.line < editorState.lines.lastIndex) {
            val collapsedEnd = collapsedStarts[cursor.line]
            val targetLine = if (collapsedEnd != null && collapsedEnd > cursor.line) {
                (collapsedEnd + 1).coerceAtMost(editorState.lines.lastIndex)
            } else {
                cursor.line + 1
            }
            editorState.moveCursor(targetLine, 0)
        }
        expandAnchor = editorState.cursor
    }

    fun moveCursorVertical(delta: Int) {
        if (visibleLineIndices.isEmpty()) return
        val cursor = editorState.cursor
        val currentVisible = visibleIndexFor(cursor.line)
        val targetVisible = (currentVisible + delta).coerceIn(0, visibleLineIndices.lastIndex)
        val targetLine = visibleLineIndices[targetVisible]
        val targetColumn = cursor.column.coerceAtMost(editorState.lines[targetLine].length)
        editorState.moveCursor(targetLine, targetColumn)
        expandAnchor = editorState.cursor
    }

    fun handlePreviewKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (isExpandSelectionShortcut(event)) {
            applyNextExpandSelection()
            return true
        }
        return when (event.key) {
            Key.Backspace -> {
                deleteBackward()
                true
            }
            Key.Enter, Key.NumPadEnter -> {
                insertChar('\n')
                true
            }
            Key.Tab -> {
                insertText("    ")
                true
            }
            Key.DirectionLeft -> {
                moveCursorLeft()
                true
            }
            Key.DirectionRight -> {
                moveCursorRight()
                true
            }
            Key.DirectionUp -> {
                moveCursorVertical(-1)
                true
            }
            Key.DirectionDown -> {
                moveCursorVertical(1)
                true
            }
            else -> false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        refreshVisibleHighlights()
    }

    LaunchedEffect(searchQuery) {
        refreshVisibleHighlights()
    }

    LaunchedEffect(foldSessionKey, persistedCollapsedFolds) {
        collapsedStarts.clear()
        val maxLine = (editorState.lines.size - 1).coerceAtLeast(0)
        persistedCollapsedFolds.forEach { (start, end) ->
            if (start in 0..maxLine && end > start) {
                collapsedStarts[start] = end.coerceAtMost(maxLine)
            }
        }
    }

    LaunchedEffect(editorState) {
        snapshotFlow { editorState.revision }.collectLatest {
            delay(180)
            val linesSnapshot = editorState.copyLines()
            val computed = withContext(Dispatchers.Default) {
                buildFoldModel(linesSnapshot)
            }
            foldModel = computed

            val staleCollapsed = collapsedStarts.entries
                .filter { (start, end) -> computed.regionsByStart[start]?.endLine != end }
                .map { it.key }
            staleCollapsed.forEach { collapsedStarts.remove(it) }
        }
    }

    LaunchedEffect(foldSessionKey, onFoldStateChanged) {
        snapshotFlow {
            collapsedStarts.entries
                .sortedBy { it.key }
                .associate { it.key to it.value }
        }.distinctUntilChanged().collectLatest { folded ->
            onFoldStateChanged(folded)
        }
    }

    LaunchedEffect(listState, visibleLineIndices) {
        snapshotFlow {
            val items = listState.layoutInfo.visibleItemsInfo
            if (items.isNotEmpty()) {
                val firstVisible = items.first().index.coerceIn(0, (visibleLineIndices.size - 1).coerceAtLeast(0))
                val lastVisible = items.last().index.coerceIn(0, (visibleLineIndices.size - 1).coerceAtLeast(0))
                val firstLine = visibleLineIndices.getOrElse(firstVisible) { 0 }
                val lastLine = visibleLineIndices.getOrElse(lastVisible) { firstLine }
                firstLine..lastLine
            } else {
                0..0
            }
        }.distinctUntilChanged().collect { range ->
            EditorSession.onViewportChanged(range, highlightScope)
        }
    }

    LaunchedEffect(searchResultIndex, searchResults, visibleLineIndices) {
        if (searchResultIndex >= 0 && searchResultIndex < searchResults.size) {
            val result = searchResults[searchResultIndex]
            val targetLine = result.lineIndex.coerceIn(0, (editorState.lines.size - 1).coerceAtLeast(0))
            val expanded = expandCollapsedForLine(targetLine)
            if (expanded) {
                delay(16)
            }
            val targetVisible = visibleIndexFor(targetLine)
            listState.animateScrollToItem(targetVisible)
        }
    }

    LaunchedEffect(navigationRequest?.requestId, visibleLineIndices) {
        val request = navigationRequest ?: return@LaunchedEffect
        if (editorState.lines.isEmpty()) return@LaunchedEffect

        val targetLine = request.line.coerceIn(0, editorState.lines.lastIndex)
        val expanded = expandCollapsedForLine(targetLine)
        if (expanded) {
            delay(16)
        }

        val targetColumn = request.column.coerceIn(0, editorState.lines[targetLine].length)
        editorState.moveCursor(targetLine, targetColumn)
        expandAnchor = editorState.cursor
        focusRequester.requestFocus()

        val targetVisible = visibleIndexFor(targetLine)
        listState.animateScrollToItem(targetVisible)
    }

    LaunchedEffect(editorState.cursor.line, visibleLineIndices) {
        val targetVisible = visibleIndexFor(editorState.cursor.line)
        val first = listState.firstVisibleItemIndex
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
        if (targetVisible < first || targetVisible > last) {
            listState.animateScrollToItem(targetVisible)
        }
    }

    LaunchedEffect(editorState) {
        snapshotFlow { editorState.revision }.collectLatest {
            if (skipFirstRevision) {
                skipFirstRevision = false
                return@collectLatest
            }
            delay(500) // debounce repository updates off typing hot-path
            onLinesChanged(editorState.copyLines())
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BasicTextField(
                value = imeValue,
                onValueChange = { newValue ->
                    val payload = buildString(newValue.text.length) {
                        newValue.text.forEach { ch ->
                            if (ch != IME_SENTINEL[0]) append(ch)
                        }
                    }
                    when {
                        newValue.text.isEmpty() -> {
                            deleteBackward()
                            resetIme()
                        }
                        payload.isNotEmpty() -> {
                            insertText(payload)
                            resetIme()
                        }
                        else -> {
                            imeValue = newValue
                        }
                    }
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { hasInputFocus = it.isFocused }
                    .onPreviewKeyEvent(::handlePreviewKey),
                textStyle = TextStyle(fontSize = 1.sp, color = Color.Transparent),
                singleLine = true,
                cursorBrush = SolidColor(Color.Transparent)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = if (stickyHeaderPath.isNotEmpty()) 32.dp else 0.dp,
                    bottom = 300.dp
                )
            ) {
                itemsIndexed(
                    items = visibleLineIndices,
                    key = { _, lineIndex -> editorState.lines[lineIndex].id },
                    contentType = { _, _ -> "editor_line" }
                ) { _, actualLineIndex ->
                    val line = editorState.lines[actualLineIndex]
                    val errorsForLine = lintErrorsByLine[actualLineIndex]
                    val foldRegion = foldModel.regionsByStart[actualLineIndex]
                    val isCollapsed = collapsedStarts.containsKey(actualLineIndex)
                    val collapsedEnd = collapsedStarts[actualLineIndex]
                    val hiddenLineCount = if (isCollapsed && collapsedEnd != null) {
                        (collapsedEnd - actualLineIndex).coerceAtLeast(0)
                    } else {
                        0
                    }
                    val lineSelectionRange = selectionRangeForLine(
                        selection = currentSelection,
                        lineIndex = actualLineIndex,
                        lineLength = line.length
                    )
                    EditorLineRow(
                        lineNumber = actualLineIndex + 1,
                        content = line.text,
                        isActive = actualLineIndex == editorState.cursor.line,
                        caretColumn = if (actualLineIndex == editorState.cursor.line) editorState.cursor.column else 0,
                        showCaret = hasInputFocus && actualLineIndex == editorState.cursor.line && !hasExpandedSelection,
                        foldMarker = FoldMarker(
                            isFoldable = foldRegion != null,
                            isCollapsed = isCollapsed,
                            hiddenLineCount = hiddenLineCount
                        ),
                        onToggleFold = if (foldRegion != null) {
                            { toggleFoldAt(actualLineIndex) }
                        } else {
                            null
                        },
                        selectionRange = lineSelectionRange,
                        searchQuery = searchQuery,
                        cachedHighlight = highlightCache[actualLineIndex],
                        errors = errorsForLine,
                        onTapOffset = { offset ->
                            editorState.moveCursor(actualLineIndex, offset)
                            expandAnchor = editorState.cursor
                            focusRequester.requestFocus()
                        },
                        onWordSelect = { offset ->
                            val selectedWord = wordSelection(line.text, offset)
                            editorState.setSelection(
                                start = TextCursor(actualLineIndex, selectedWord.start),
                                end = TextCursor(actualLineIndex, selectedWord.end)
                            )
                            expandAnchor = TextCursor(actualLineIndex, offset.coerceIn(0, line.length))
                            focusRequester.requestFocus()
                        },
                        onLineSelect = { offset ->
                            val selectedLine = lineSelection(line.text)
                            editorState.setSelection(
                                start = TextCursor(actualLineIndex, selectedLine.start),
                                end = TextCursor(actualLineIndex, selectedLine.end)
                            )
                            expandAnchor = TextCursor(actualLineIndex, offset.coerceIn(0, line.length))
                            focusRequester.requestFocus()
                        }
                    )
                }
            }

            if (stickyHeaderPath.isNotEmpty()) {
                val headerDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 30.dp)
                            .drawBehind {
                                drawLine(
                                    color = headerDividerColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1f
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .defaultMinSize(minHeight = 30.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        )
                        Text(
                            text = stickyHeaderTitle,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorLineRow(
    lineNumber: Int,
    content: String,
    isActive: Boolean,
    caretColumn: Int,
    showCaret: Boolean,
    foldMarker: FoldMarker,
    onToggleFold: (() -> Unit)?,
    selectionRange: TextRange?,
    searchQuery: String,
    cachedHighlight: AnnotatedString?,
    errors: List<DtsError>?,
    onTapOffset: (Int) -> Unit,
    onWordSelect: (Int) -> Unit,
    onLineSelect: (Int) -> Unit
) {
    val hasError = errors?.any { it.severity == Severity.ERROR } == true
    val hasWarning = !hasError && errors?.any { it.severity == Severity.WARNING } == true
    val hasIssue = hasError || hasWarning

    val baseTextColor = Color(0xFFE0E2E7)
    val textStyle = remember(hasError) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = baseTextColor,
            textDecoration = if (hasError) TextDecoration.Underline else TextDecoration.None
        )
    }

    val activeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val caretColor = MaterialTheme.colorScheme.primary
    val errorGutterBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    val warningGutterBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
    val normalGutterBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val gutterBackground = when {
        hasError -> errorGutterBg
        hasWarning -> warningGutterBg
        else -> normalGutterBg
    }

    val highlightedText = if (isActive) {
        remember(content, searchQuery) {
            if (content.isNotEmpty()) ComposeHighlighter.highlight(content, searchQuery) else AnnotatedString("")
        }
    } else {
        cachedHighlight ?: AnnotatedString(content)
    }
    val renderedText = remember(highlightedText, selectionRange) {
        if (selectionRange == null || selectionRange.collapsed) {
            highlightedText
        } else {
            AnnotatedString.Builder(highlightedText).apply {
                addStyle(
                    style = SpanStyle(
                        background = Color(0x66B39DDB)
                    ),
                    start = selectionRange.start,
                    end = selectionRange.end
                )
            }.toAnnotatedString()
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var lastDoubleTapTimestamp by remember { mutableLongStateOf(0L) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 24.dp)
            .then(if (isActive) Modifier.background(activeBackground) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .defaultMinSize(minHeight = 24.dp)
                .background(gutterBackground)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (foldMarker.isFoldable && onToggleFold != null) {
                Text(
                    text = if (foldMarker.isCollapsed) "▸" else "▾",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onToggleFold() }
                        )
                        .padding(start = 1.dp, end = 4.dp)
                )
            }

            if (hasIssue) {
                var showPopup by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTapOffset(content.length) },
                        onLongClick = { showPopup = true }
                    )
                ) {
                    Icon(
                        imageVector = if (hasError) Icons.Rounded.ErrorOutline else Icons.Rounded.Warning,
                        contentDescription = if (hasError) "Error" else "Warning",
                        tint = if (hasError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = lineNumber.toString(),
                        color = if (hasError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
                if (showPopup) {
                    Popup(
                        onDismissRequest = { showPopup = false },
                        alignment = Alignment.TopStart
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = errors.orEmpty().joinToString("\n") { it.message },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = lineNumber.toString(),
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 20.sp,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 24.dp)
                .padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                .drawBehind {
                    if (!showCaret) return@drawBehind
                    val layout = textLayoutResult ?: return@drawBehind
                    val safeColumn = caretColumn.coerceIn(0, content.length)
                    val rect = layout.getCursorRect(safeColumn)
                    drawLine(
                        color = caretColor,
                        start = Offset(rect.left, rect.top),
                        end = Offset(rect.left, rect.bottom),
                        strokeWidth = 2f
                    )
                }
                .pointerInput(content, renderedText) {
                    detectTapGestures(
                        onTap = { tapPosition ->
                            val offset = textLayoutResult?.let { layout ->
                                try {
                                    layout.getOffsetForPosition(Offset(tapPosition.x, tapPosition.y))
                                        .coerceIn(0, content.length)
                                } catch (_: Exception) {
                                    content.length
                                }
                            } ?: content.length
                            val now = android.os.SystemClock.uptimeMillis()
                            val shouldSelectLine =
                                lastDoubleTapTimestamp != 0L && (now - lastDoubleTapTimestamp) <= TRIPLE_TAP_WINDOW_MS
                            if (shouldSelectLine) {
                                onLineSelect(offset)
                                lastDoubleTapTimestamp = 0L
                            } else {
                                onTapOffset(offset)
                            }
                        },
                        onDoubleTap = { tapPosition ->
                            val offset = textLayoutResult?.let { layout ->
                                try {
                                    layout.getOffsetForPosition(Offset(tapPosition.x, tapPosition.y))
                                        .coerceIn(0, content.length)
                                } catch (_: Exception) {
                                    content.length
                                }
                            } ?: content.length
                            onWordSelect(offset)
                            lastDoubleTapTimestamp = android.os.SystemClock.uptimeMillis()
                        }
                    )
                }
        ) {
            Text(
                text = renderedText,
                style = textStyle,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier.fillMaxWidth()
            )
            if (foldMarker.isCollapsed && foldMarker.hiddenLineCount > 0) {
                Text(
                    text = "... ${foldMarker.hiddenLineCount} lines",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private fun TextFieldValueWithSentinel(): androidx.compose.ui.text.input.TextFieldValue {
    return androidx.compose.ui.text.input.TextFieldValue(
        text = IME_SENTINEL,
        selection = TextRange(IME_SENTINEL.length)
    )
}
