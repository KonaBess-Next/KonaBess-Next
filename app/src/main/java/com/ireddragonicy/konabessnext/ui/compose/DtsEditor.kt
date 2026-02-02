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
    // Global Pre-calculated Highlight Cache
    // Map index -> Cached Highlight data (AnnotatedString)
    val highlightCache = remember { mutableStateMapOf<Int, AnnotatedString>() }
    // Map index -> Signature of cached content to verify validity
    val cacheContentSignature = remember { mutableStateMapOf<Int, String>() }

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
    
    // Background Global Highlighting Job
    // This runs continuously to ensure all lines in 'lines' are highlighted in cache
    LaunchedEffect(lines.size, lines.toList(), searchQuery) {
        withContext(Dispatchers.Default) {
            // Processing settings
            val chunk = 50 // Process 50 lines per batch
            var processed = 0
            
            // Loop through all currently known lines
            for (i in lines.indices) {
                if (!isActive) break 
                
                val lineContent = lines.getOrElse(i) { "" }
                val signature = "$lineContent||$searchQuery"
                
                // If cache miss or outdated
                if (cacheContentSignature[i] != signature) {
                    val highlighted = ComposeHighlighter.highlight(lineContent, searchQuery)
                    
                    // Update State on Main Thread
                    withContext(Dispatchers.Main) {
                        highlightCache[i] = highlighted
                        cacheContentSignature[i] = signature
                    }
                    
                    processed++
                    // Yield every chunk to prevent starving Main Thread (even though we are on Default, UI updates cost)
                    if (processed >= chunk) {
                        processed = 0
                        delay(5) 
                    }
                }
            }
        }
    }
    
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
                // HEAVY: TextField - Always synchronous
                val focusRequester = remember { FocusRequester() }
                var tfValue by remember { mutableStateOf(TextFieldValue(content, selection = TextRange(content.length))) }

                if (tfValue.text != content) {
                     tfValue = tfValue.copy(text = content)
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                val visualTransformation = remember(content, searchQuery) {
                    VisualTransformation { text ->
                        val annotatedString = ComposeHighlighter.highlight(text.text, searchQuery)
                        TransformedText(annotatedString, OffsetMapping.Identity)
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
