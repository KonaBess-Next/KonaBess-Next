package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ireddragonicy.konabessnext.editor.highlight.ComposeHighlighter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.delay

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
    
    // Debounced save effect - only commits after 500ms of idle time
    LaunchedEffect(pendingContent) {
        pendingContent?.let { newContent ->
            delay(500) // Wait for typing to stop
            if (newContent != lastCommittedContent) {
                lastCommittedContent = newContent
                onContentChanged(newContent)
            }
        }
    }
    
    // External Change Sync (undo/redo/load)
    LaunchedEffect(content) {
        if (content == lastCommittedContent) return@LaunchedEffect
        
        // External change — rebuild stable lines with fresh IDs
        stableLines.clear()
        val newLines = if (content.isNotEmpty()) content.split("\n") else listOf("")
        newLines.forEach { stableLines.add(StableLine(idGen.next(), it)) }
        lastCommittedContent = content
        pendingContent = null
    }
    
    fun requestSave() {
        val newContent = stableLines.joinToString("\n") { it.content }
        if (newContent != lastCommittedContent) {
            pendingContent = newContent
        }
    }
    
    fun requestImmediateSave() {
        val newContent = stableLines.joinToString("\n") { it.content }
        if (newContent != lastCommittedContent) {
            lastCommittedContent = newContent
            pendingContent = null
            onContentChanged(newContent)
        }
    }

    LaunchedEffect(searchResultIndex) {
        if (searchResultIndex >= 0 && searchResultIndex < searchResults.size) {
            val result = searchResults[searchResultIndex]
            listState.animateScrollToItem(result.lineIndex)
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
                
                EditorLineRow(
                    index = index,
                    content = stableLine.content,
                    isActive = index == activeLineIndex,
                    searchQuery = searchQuery,
                    cachedHighlight = cachedHighlight,
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
 */
@Composable
private fun EditorLineRow(
    index: Int,
    content: String,
    isActive: Boolean,
    searchQuery: String,
    cachedHighlight: AnnotatedString?,
    onFocusRequest: () -> Unit,
    onValueChange: (String) -> Unit,
    onNewLine: () -> Unit,
    onBackspaceAtStart: () -> Unit
) {
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = Color(0xFFE0E2E7)
    )

    val activeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)

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
        // Line Number Gutter — Material 3 surface variant
        Box(
            modifier = Modifier
                .width(48.dp)
                .defaultMinSize(minHeight = 24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
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
