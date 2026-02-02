package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ireddragonicy.konabessnext.editor.highlight.ComposeHighlighter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type

data class LineSearchResult(val lineIndex: Int)

@Composable
fun DtsEditor(
    content: String,
    onContentChanged: (String) -> Unit,
    searchQuery: String = "",
    searchResultIndex: Int = -1,
    searchResults: List<LineSearchResult> = emptyList(),
    modifier: Modifier = Modifier
) {
    // Stable list state
    val lines = remember { mutableStateListOf<String>() }
    
    // Sync external content changes to local lines
    // We only update if the content effectively differs to avoid loop
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
    
    // Debounce Save to prevent UI lag on large files
    val scope = rememberCoroutineScope()
    // Simple state to track dirty
    
    fun requestSave() {
        val newContent = lines.joinToString("\n")
        // Check if actually changed to avoid loop
        if (newContent != content) {
            onContentChanged(newContent)
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to search result
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
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = lines, 
                key = { index, _ -> index } 
            ) { index, lineContent ->
                EditorLine(
                    index = index,
                    content = lineContent,
                    searchQuery = searchQuery,
                    onValueChange = { newValue, _ ->
                        // Detect newline for split
                        if (newValue.contains('\n')) {
                            val parts = newValue.split('\n')
                            lines.removeAt(index)
                            lines.addAll(index, parts)
                            // Focus management would go here (requires FocusRequester)
                        } else {
                            lines[index] = newValue
                        }
                        requestSave()
                    },
                    onBackspaceAtStart = {
                        if (index > 0) {
                            val prevLine = lines[index - 1]
                            val currentLine = lines[index]
                            lines[index - 1] = prevLine + currentLine
                            lines.removeAt(index)
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
    searchQuery: String,
    onValueChange: (String, Int) -> Unit,
    onBackspaceAtStart: () -> Unit
) {
    // Keep internal TF state to avoid recomposition reset cursor
    var tfValue by remember { mutableStateOf(TextFieldValue(content)) }
    
    // Sync content to tfValue ONLY if content changed externally
    // We compare text to avoid resetting checking selection
    if (tfValue.text != content) {
        // This means parent updated 'content' (e.g. via lines update)
        // We must preserve cursor if possible or just update text
        // If we are typing, tfValue is ahead of content?
        // Actually, lines[index] = newValue updates lines immediately.
        // So content becomes newValue.
        // So tfValue.text == content usually.
        // But if we scrolled away and back, 'remember' re-inits TFV(content).
        // Correct.
        tfValue = tfValue.copy(text = content)
    }

    val visualTransformation = remember(content, searchQuery) {
        VisualTransformation { text ->
             val annotatedString = ComposeHighlighter.highlight(text.text, searchQuery)
             TransformedText(annotatedString, OffsetMapping.Identity)
        }
    }

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
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

        BasicTextField(
            value = tfValue,
            onValueChange = { newValue ->
                 tfValue = newValue
                 onValueChange(newValue.text, newValue.selection.start)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown && tfValue.selection.start == 0) {
                        onBackspaceAtStart()
                        true
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color(0xFFE0E2E7)
            ),
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text)
        )
    }
}
