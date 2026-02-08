package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
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
import com.ireddragonicy.konabessnext.editor.highlight.ComposeHighlighter
import com.ireddragonicy.konabessnext.model.dts.DtsError
import com.ireddragonicy.konabessnext.model.dts.Severity
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
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
                    onFocusRequest = { activeLineIndex = index },
                    onValueChange = { newValue ->
                        stableLine.content = newValue
                        requestSave()
                    },
                    onNewLine = {
                        if (index + 1 <= stableLines.size) {
                            stableLines.add(index + 1, StableLine(idGen.next(), ""))
                            activeLineIndex = index + 1
                            requestImmediateSave()
                        }
                    },
                    onBackspaceAtStart = {
                        if (index > 0) {
                            stableLines[index - 1].content += stableLine.content
                            stableLines.removeAt(index)
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
    onFocusRequest: () -> Unit,
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFocusRequest() }
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
                        onClick = { onFocusRequest() },
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
                if (showPopup && errors != null) {
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
                                text = errors.joinToString("\n") { it.message },
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
                    onValueChange = onValueChange,
                    onNewLine = onNewLine,
                    onBackspaceAtStart = onBackspaceAtStart
                )
            } else {
                Text(
                    text = cachedHighlight ?: AnnotatedString(content),
                    style = textStyle,
                    modifier = Modifier.fillMaxWidth()
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
    onValueChange: (String) -> Unit,
    onNewLine: () -> Unit,
    onBackspaceAtStart: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var tfValue by remember { mutableStateOf(TextFieldValue(content, TextRange(content.length))) }

    if (tfValue.text != content) {
        tfValue = tfValue.copy(text = content)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
            tfValue = newValue
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
