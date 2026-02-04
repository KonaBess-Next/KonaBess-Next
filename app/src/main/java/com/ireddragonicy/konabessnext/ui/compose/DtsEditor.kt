package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class LineSearchResult(val lineIndex: Int)

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
    val lines = remember { 
        val list = mutableStateListOf<String>()
        if (content.isNotEmpty()) {
            list.addAll(content.split("\n"))
        } else {
            list.add("")
        }
        list
    }
    
    // Global Pre-calculated Highlight Cache (Singleton)
    val highlightCache = com.ireddragonicy.konabessnext.editor.EditorSession.highlightCache
    
    var activeLineIndex by remember { mutableIntStateOf(-1) }
    
    // Initial Load & Line Sync
    LaunchedEffect(content) {
        val current = lines.joinToString("\n")
        if (current != content) {
            lines.clear()
            if (content.isNotEmpty()) {
                lines.addAll(content.split("\n"))
            } else {
                lines.add("")
            }
        }
    }
    
    // EditorSession handles highlighting externally. DtsEditor is just a viewer/editor.
    // If lines change locally (edit), we should ideally notify session, but DtsEditor calls onContentChanged which updates Repo -> VM -> EditorSession.
    // So loop is closed. But local edits might flicker briefly unhighlighted?
    // User edits -> lines update -> requestSave -> onContentChanged -> Repo Update -> VM Flow -> EditorSession Update -> highlightCache update.
    // This is fast enough for typing.
    
    fun requestSave() {
        val newContent = lines.joinToString("\n")
        if (newContent != content) {
            onContentChanged(newContent)
        }
    }

    LaunchedEffect(searchResultIndex) {
        if (searchResultIndex >= 0 && searchResultIndex < searchResults.size) {
            val result = searchResults[searchResultIndex]
            listState.animateScrollToItem(result.lineIndex)
        }
    }

    Row(modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)) {
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 300.dp)
        ) {
            itemsIndexed(
                items = lines, 
                key = { index, _ -> index } 
            ) { index, lineContent ->
                // Check Global Cache
                val cachedHighlight = highlightCache[index]
                
                EditorLine(
                    index = index,
                    content = lineContent,
                    isActive = index == activeLineIndex,
                    searchQuery = searchQuery,
                    cachedHighlight = cachedHighlight,
                    onFocusRequest = { activeLineIndex = index },
                    onValueChange = { newValue ->
                        lines[index] = newValue
                        requestSave()
                    },
                    onNewLine = {
                        if (index + 1 <= lines.size) {
                            lines.add(index + 1, "")
                            activeLineIndex = index + 1
                            requestSave()
                        }
                    },
                    onBackspaceAtStart = {
                        if (index > 0) {
                            val prevLine = lines[index - 1]
                            val currentLine = lines[index]
                            lines[index - 1] = prevLine + currentLine
                            lines.removeAt(index)
                            activeLineIndex = index - 1
                            requestSave()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EditorLine(
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
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Color(0xFFE0E2E7)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFocusRequest() }
    ) {
        // Line Number
        Text(
            text = (index + 1).toString(),
            modifier = Modifier
                .width(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(end = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        // Hybrid Content
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)) {
            
            if (isActive) {
                // HEAVY: TextField - Now Async Highlighted
                val focusRequester = remember { FocusRequester() }
                var tfValue by remember { mutableStateOf(TextFieldValue(content, selection = TextRange(content.length))) }

                // State to hold the async result
                var asyncHighlight by remember { mutableStateOf<AnnotatedString?>(null) }

                // Trigger async calculation
                LaunchedEffect(content, searchQuery) {
                    withContext(Dispatchers.Default) {
                        val result = ComposeHighlighter.highlight(content, searchQuery)
                        withContext(Dispatchers.Main) {
                            asyncHighlight = result
                        }
                    }
                }

                if (tfValue.text != content) {
                     tfValue = tfValue.copy(text = content)
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                val visualTransformation = remember(asyncHighlight, content) {
                    VisualTransformation { text ->
                        // If we have a highlight AND it matches current text, use it.
                        // Otherwise fallback to plain text to avoid blocking UI.
                        val currentHighlight = asyncHighlight
                        if (currentHighlight != null && currentHighlight.text == text.text) {
                            TransformedText(currentHighlight, OffsetMapping.Identity)
                        } else {
                            TransformedText(AnnotatedString(text.text), OffsetMapping.Identity)
                        }
                    }
                }

                BasicTextField(
                    value = tfValue,
                    onValueChange = { 
                        tfValue = it
                         if (it.text.length > content.length && it.text.contains('\n') && !content.contains('\n')) {
                             onNewLine()
                        } else {
                            onValueChange(it.text)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                             if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown && tfValue.selection.start == 0) {
                                 onBackspaceAtStart()
                                 true
                             } else if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                 onNewLine() 
                                 true
                             } else {
                                 false
                             }
                        },
                    textStyle = textStyle,
                    visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true
                )
            } else {
                // LIGHT: Text
                // Use Global Cache or Fallback to Plain Text (Sync)
                // We do NOT compute here anymore to avoid duplicate work.
                
                val displayText = cachedHighlight ?: AnnotatedString(content)
                
                Text(
                    text = displayText,
                    style = textStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
