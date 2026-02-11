package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.input.pointer.pointerInput
import com.ireddragonicy.konabessnext.editor.highlight.ComposeHighlighter
import com.ireddragonicy.konabessnext.model.dts.DtsError
import com.ireddragonicy.konabessnext.model.dts.Severity
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import com.ireddragonicy.konabessnext.utils.DtsEditorDebug

data class LineSearchResult(val lineIndex: Int)

/**
 * A line with a stable identity for LazyColumn keying.
 * Stable IDs survive insert/delete operations, preventing full-list recomposition.
 */
private class StableLine(val id: Long, initialContent: String) {
    var content by mutableStateOf(initialContent)
}

private data class SelectionRequest(val start: Int, val end: Int)

private const val TRIPLE_TAP_WINDOW_MS = 350L
private const val EXPAND_STAGE_WORD = 0
private const val EXPAND_STAGE_TOKEN = 1
private const val EXPAND_STAGE_LINE = 2

private fun caretSelection(offset: Int): SelectionRequest = SelectionRequest(offset, offset)

private fun lineSelection(text: String): SelectionRequest = SelectionRequest(0, text.length)

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
    if (anchor < 0) return caretSelection(cursor)

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

private fun isExpandSelectionShortcut(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    return event.type == KeyEventType.KeyDown &&
        event.isShiftPressed &&
        event.isAltPressed &&
        event.key == Key.DirectionRight
}

@Composable
fun DtsEditor(
    content: String,
    onContentChanged: (String) -> Unit,
    searchQuery: String = "",
    searchResultIndex: Int = -1,
    searchResults: List<LineSearchResult> = emptyList(),
    lintErrorsByLine: SnapshotStateMap<Int, List<DtsError>> = remember { mutableStateMapOf() },
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    // Stable Line ID Generator — avoids compose state overhead for counter
    val idGen = remember { object { var nextId = 0L; fun next() = nextId++ } }
    
    // Stable Line Model — stable IDs prevent full LazyColumn invalidation on insert/delete
    val stableLines = remember {
        val list = mutableStateListOf<StableLine>()
        val initial = if (content.isNotEmpty()) content.split("\n") else listOf("")
        initial.forEach { list.add(StableLine(idGen.next(), it)) }
        list
    }
    
    // Global Pre-calculated Highlight Cache (Singleton)
    val highlightCache = com.ireddragonicy.konabessnext.editor.EditorSession.highlightCache
    
    var activeLineIndex by remember { mutableIntStateOf(-1) }
    val pendingSelectionByLineId = remember { mutableStateMapOf<Long, SelectionRequest>() }
    
    // Debounced Save State
    // pendingContent holds the latest content that needs to be saved
    // When it changes, we wait 500ms before actually calling onContentChanged
    var pendingContent by remember { mutableStateOf<String?>(null) }
    var lastCommittedContent by remember { mutableStateOf(content) }
    
    // Flag-based save: avoids building the full 24K-line string on every keystroke.
    // The actual joinToString only happens once after the 500ms debounce fires.
    var saveNeeded by remember { mutableStateOf(false) }
    
    fun buildContent(): String {
        val start = System.nanoTime()
        val sb = StringBuilder(stableLines.size * 40) // pre-allocate estimate
        for (i in stableLines.indices) {
            if (i > 0) sb.append('\n')
            sb.append(stableLines[i].content)
        }
        val result = sb.toString()
        DtsEditorDebug.logBuildContent(stableLines.size, (System.nanoTime() - start) / 1_000_000, result.length)
        return result
    }
    
    fun requestSave() {
        DtsEditorDebug.logRequestSave()
        saveNeeded = true
        pendingContent = "dirty" // trigger the LaunchedEffect debounce
    }
    
    fun requestImmediateSave() {
        val newContent = buildContent()
        if (newContent != lastCommittedContent) {
            DtsEditorDebug.logOnContentChanged(newContent.length)
            lastCommittedContent = newContent
            pendingContent = null
            saveNeeded = false
            onContentChanged(newContent)
        }
    }
    
    // Debounced save effect - only builds the full content string after 500ms idle
    LaunchedEffect(pendingContent) {
        if (saveNeeded) {
            delay(500) // Wait for typing to stop
            val newContent = buildContent()
            if (newContent != lastCommittedContent) {
                DtsEditorDebug.logOnContentChanged(newContent.length)
                lastCommittedContent = newContent
                saveNeeded = false
                onContentChanged(newContent)
            }
        }
    }
    
    // External Change Sync (undo/redo/load)
    LaunchedEffect(content) {
        val willRebuild = content != lastCommittedContent
        DtsEditorDebug.logExternalSync(content.length, willRebuild)
        if (!willRebuild) return@LaunchedEffect
        
        // External change — rebuild stable lines with fresh IDs
        DtsEditorDebug.logStableLines(stableLines.size, "CLEARING for external sync")
        stableLines.clear()
        pendingSelectionByLineId.clear()
        val newLines = if (content.isNotEmpty()) content.split("\n") else listOf("")
        newLines.forEach { stableLines.add(StableLine(idGen.next(), it)) }
        lastCommittedContent = content
        pendingContent = null
        DtsEditorDebug.logStableLines(stableLines.size, "REBUILT from external sync")
        DtsEditorDebug.logMemory("after external sync rebuild")
    }
    

    LaunchedEffect(searchResultIndex) {
        if (searchResultIndex >= 0 && searchResultIndex < searchResults.size) {
            val result = searchResults[searchResultIndex]
            listState.animateScrollToItem(result.lineIndex)
        }
    }

    // MEMOIZATION: Track visible viewport and highlight on-demand during scroll
    // distinctUntilChanged avoids redundant calls when range hasn't moved (same frame)
    val highlightScope = rememberCoroutineScope()
    LaunchedEffect(listState) {
        snapshotFlow {
            val items = listState.layoutInfo.visibleItemsInfo
            if (items.isNotEmpty()) items.first().index..items.last().index else 0..0
        }.distinctUntilChanged().collect { range ->
            com.ireddragonicy.konabessnext.editor.EditorSession.onViewportChanged(range, highlightScope)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 300.dp)
        ) {
            itemsIndexed(
                items = stableLines,
                key = { _, line -> line.id },
                contentType = { _, _ -> "editor_line" }
            ) { index, stableLine ->
                val cachedHighlight = highlightCache[index]
                // Granular snapshot read: only THIS line recomposes when its errors change
                val errorsForLine = lintErrorsByLine[index]
                
                EditorLineRow(
                    index = index,
                    content = stableLine.content,
                    isActive = index == activeLineIndex,
                    searchQuery = searchQuery,
                    cachedHighlight = cachedHighlight,
                    errors = errorsForLine,
                    requestedSelection = if (index == activeLineIndex) pendingSelectionByLineId[stableLine.id] else null,
                    onSelectionRequestConsumed = { pendingSelectionByLineId.remove(stableLine.id) },
                    onFocusRequest = { selection ->
                        activeLineIndex = index
                        if (selection != null) {
                            pendingSelectionByLineId[stableLine.id] = selection
                        } else {
                            pendingSelectionByLineId.remove(stableLine.id)
                        }
                    },
                    onValueChange = { newValue ->
                        stableLine.content = newValue
                        com.ireddragonicy.konabessnext.editor.EditorSession.updateLine(
                            lineIndex = index,
                            content = newValue,
                            searchQuery = searchQuery,
                            scope = highlightScope
                        )
                        requestSave()
                    },
                    onNewLine = {
                        if (index + 1 <= stableLines.size) {
                            val oldSize = stableLines.size
                            stableLines.add(index + 1, StableLine(idGen.next(), ""))
                            com.ireddragonicy.konabessnext.editor.EditorSession.insertLine(
                                atIndex = index + 1,
                                content = "",
                                searchQuery = searchQuery,
                                totalLines = oldSize,
                                scope = highlightScope
                            )
                            activeLineIndex = index + 1
                            requestImmediateSave()
                        }
                    },
                    onBackspaceAtStart = {
                        if (index > 0) {
                            val mergedLine = stableLines[index - 1].content + stableLine.content
                            stableLines[index - 1].content = mergedLine
                            stableLines.removeAt(index)
                            com.ireddragonicy.konabessnext.editor.EditorSession.updateLine(
                                lineIndex = index - 1,
                                content = mergedLine,
                                searchQuery = searchQuery,
                                scope = highlightScope
                            )
                            com.ireddragonicy.konabessnext.editor.EditorSession.deleteLine(
                                atIndex = index,
                                totalLines = stableLines.size
                            )
                            activeLineIndex = index - 1
                            requestImmediateSave()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Single editor line — Material 3 styled with performance optimizations.
 * - No IntrinsicSize.Min (avoids expensive double-measurement pass)
 * - Active line uses synchronous single-line highlight (< 1ms for typical DTS)
 * - Inactive lines use pre-computed cache from EditorSession
 * - Lines with lint errors show gutter indicators and underlined text
 * - Lightweight Popup instead of heavy TooltipBox to avoid lag
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorLineRow(
    index: Int,
    content: String,
    isActive: Boolean,
    searchQuery: String,
    cachedHighlight: AnnotatedString?,
    errors: List<DtsError>?,
    requestedSelection: SelectionRequest?,
    onSelectionRequestConsumed: () -> Unit,
    onFocusRequest: (SelectionRequest?) -> Unit,
    onValueChange: (String) -> Unit,
    onNewLine: () -> Unit,
    onBackspaceAtStart: () -> Unit
) {
    val hasError = errors?.any { it.severity == Severity.ERROR } == true
    val hasWarning = !hasError && errors?.any { it.severity == Severity.WARNING } == true
    val hasIssue = hasError || hasWarning

    // Cache TextStyle — only the textDecoration differs between error/non-error
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
    val errorGutterBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    val warningGutterBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
    val normalGutterBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    val gutterBackground = when {
        hasError -> errorGutterBg
        hasWarning -> warningGutterBg
        else -> normalGutterBg
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 24.dp)
            .then(if (isActive) Modifier.background(activeBackground) else Modifier)
            .then(
                if (!isActive) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onFocusRequest(caretSelection(content.length)) }
                } else {
                    Modifier
                }
            )
    ) {
        // Line Number Gutter — with lightweight lint error indicator
        Box(
            modifier = Modifier
                .width(48.dp)
                .defaultMinSize(minHeight = 24.dp)
                .background(gutterBackground)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (hasIssue) {
                // Lightweight long-press popup instead of heavy TooltipBox
                var showPopup by remember { mutableStateOf(false) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onFocusRequest(caretSelection(content.length)) },
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
                        text = (index + 1).toString(),
                        color = if (hasError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }

                // Lightweight error popup — only composed when visible
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
                // Normal gutter — no error
                Text(
                    text = (index + 1).toString(),
                    color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 20.sp,
                    maxLines = 1
                )
            }
        }

        // Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 24.dp)
                .padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
        ) {
            if (isActive) {
                ActiveLineEditor(
                    content = content,
                    searchQuery = searchQuery,
                    textStyle = textStyle,
                    initialSelectionRequest = requestedSelection,
                    onInitialSelectionApplied = onSelectionRequestConsumed,
                    onValueChange = onValueChange,
                    onNewLine = onNewLine,
                    onBackspaceAtStart = onBackspaceAtStart
                )
            } else {
                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                var lastDoubleTapTimestamp by remember { mutableLongStateOf(0L) }
                Text(
                    text = cachedHighlight ?: AnnotatedString(content),
                    style = textStyle,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(content, cachedHighlight) {
                            detectTapGestures(
                                onTap = { tapPosition ->
                                    val cursorOffset = textLayoutResult?.let { layout ->
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
                                        onFocusRequest(lineSelection(content))
                                        lastDoubleTapTimestamp = 0L
                                    } else {
                                        onFocusRequest(caretSelection(cursorOffset))
                                    }
                                },
                                onDoubleTap = { tapPosition ->
                                    val cursorOffset = textLayoutResult?.let { layout ->
                                        try {
                                            layout.getOffsetForPosition(Offset(tapPosition.x, tapPosition.y))
                                                .coerceIn(0, content.length)
                                        } catch (_: Exception) {
                                            content.length
                                        }
                                    } ?: content.length
                                    onFocusRequest(wordSelection(content, cursorOffset))
                                    lastDoubleTapTimestamp = android.os.SystemClock.uptimeMillis()
                                }
                            )
                        }
                )
            }
        }
    }
}

/**
 * Active (focused) line editor — synchronous highlighting for instant feedback.
 * Single DTS line highlight is < 1ms, so no need for async produceState.
 */
@Composable
private fun ActiveLineEditor(
    content: String,
    searchQuery: String,
    textStyle: TextStyle,
    initialSelectionRequest: SelectionRequest?,
    onInitialSelectionApplied: () -> Unit,
    onValueChange: (String) -> Unit,
    onNewLine: () -> Unit,
    onBackspaceAtStart: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var tfValue by remember { mutableStateOf(TextFieldValue(content, TextRange(content.length))) }
    var expandAnchor by remember { mutableIntStateOf(-1) }

    if (tfValue.text != content) {
        val clampedStart = tfValue.selection.start.coerceIn(0, content.length)
        val clampedEnd = tfValue.selection.end.coerceIn(0, content.length)
        tfValue = tfValue.copy(text = content, selection = TextRange(clampedStart, clampedEnd))
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    LaunchedEffect(initialSelectionRequest) {
        val request = initialSelectionRequest ?: return@LaunchedEffect
        val start = request.start.coerceIn(0, tfValue.text.length)
        val end = request.end.coerceIn(0, tfValue.text.length)
        if (tfValue.selection.start != start || tfValue.selection.end != end) {
            tfValue = tfValue.copy(selection = TextRange(start, end))
        }
        expandAnchor = start
        onInitialSelectionApplied()
    }

    fun applyNextExpandSelection() {
        val textNow = tfValue.text
        val anchor = when {
            expandAnchor in 0..textNow.length -> expandAnchor
            tfValue.selection.start in 0..textNow.length -> tfValue.selection.start
            else -> textNow.length
        }
        val current = SelectionRequest(
            tfValue.selection.start.coerceIn(0, textNow.length),
            tfValue.selection.end.coerceIn(0, textNow.length)
        )
        val word = wordSelection(textNow, anchor)
        val token = tokenSelection(textNow, anchor)
        val line = lineSelection(textNow)
        val previousStage = when (current) {
            line -> EXPAND_STAGE_LINE
            token -> EXPAND_STAGE_TOKEN
            word -> EXPAND_STAGE_WORD
            else -> -1
        }
        val (nextSelection, _) = nextExpandedSelection(textNow, anchor, previousStage)
        tfValue = tfValue.copy(selection = TextRange(nextSelection.start, nextSelection.end))
        expandAnchor = anchor
    }

    // Synchronous highlight — computed once per content change, not per frame
    val highlight = remember(content, searchQuery) {
        if (content.isNotEmpty()) ComposeHighlighter.highlight(content, searchQuery) else null
    }

    val visualTransformation = remember(highlight, content) {
        VisualTransformation { text ->
            val h = highlight
            if (h != null && h.text == text.text) {
                TransformedText(h, OffsetMapping.Identity)
            } else {
                TransformedText(AnnotatedString(text.text), OffsetMapping.Identity)
            }
        }
    }

    BasicTextField(
        value = tfValue,
        onValueChange = { newValue ->
            val didSelectionChange = newValue.selection != tfValue.selection
            val didTextChange = newValue.text != tfValue.text
            tfValue = newValue
            if (didSelectionChange || didTextChange) {
                expandAnchor = newValue.selection.start.coerceIn(0, newValue.text.length)
            }
            if (newValue.text.contains('\n') && !content.contains('\n')) {
                onNewLine()
            } else {
                onValueChange(newValue.text)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                when {
                    isExpandSelectionShortcut(event) -> {
                        applyNextExpandSelection()
                        true
                    }
                    event.key == Key.Backspace && event.type == KeyEventType.KeyDown && tfValue.selection.start == 0 -> {
                        onBackspaceAtStart()
                        true
                    }
                    event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                        onNewLine()
                        true
                    }
                    else -> false
                }
            },
        textStyle = textStyle,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = false
    )
}
