package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DtsEditor(
    lines: List<String>,
    onLinesChanged: (List<String>) -> Unit,
    searchQuery: String = "",
    searchResultIndex: Int = -1,
    searchResults: List<com.ireddragonicy.konabessnext.viewmodel.RawDtsEditorViewModel.LineSearchResult> = emptyList(),
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Jump to search result
    LaunchedEffect(searchResultIndex) {
        if (searchResultIndex >= 0 && searchResultIndex < searchResults.size) {
            val result = searchResults[searchResultIndex]
            listState.animateScrollToItem(result.lineIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        itemsIndexed(lines) { index, line ->
            DtsLine(
                lineNumber = index + 1,
                text = line,
                onTextChanged = { newText ->
                    val newLines = lines.toMutableList()
                    newLines[index] = newText
                    onLinesChanged(newLines)
                },
                searchQuery = searchQuery,
                isSearchResult = searchResults.any { it.lineIndex == index },
                isCurrentResult = if (searchResultIndex >= 0 && searchResultIndex < searchResults.size) 
                                    searchResults[searchResultIndex].lineIndex == index 
                                 else false
            )
        }
    }
}

@Composable
fun DtsLine(
    lineNumber: Int,
    text: String,
    onTextChanged: (String) -> Unit,
    searchQuery: String,
    isSearchResult: Boolean,
    isCurrentResult: Boolean
) {
    val lineContent = remember(text, searchQuery, isCurrentResult) {
        highlightDts(text, searchQuery, isCurrentResult)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        // Line number gutter
        Text(
            text = lineNumber.toString().padStart(4),
            modifier = Modifier
                .width(48.dp)
                .padding(end = 8.dp),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        )

        // BasicTextField for high performance editing per line
        BasicTextField(
            value = text, // We use the raw text for the field
            onValueChange = onTextChanged,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            decorationBox = { innerTextField ->
                // If not focusing, we could show the annotated string for highlighting.
                // For simplicity and editing, we just use the text field.
                // Ideally we'd use a VisualTransformation for highlighting.
                innerTextField()
            }
        )
    }
}

private fun highlightDts(text: String, query: String, isCurrent: Boolean) = buildAnnotatedString {
    // Basic logic for highlighting could go here as a VisualTransformation
    // For now we'll just handle search highlights if we were using a Text view.
    // In a real editor, a custom VisualTransformation is better.
    append(text)
}
