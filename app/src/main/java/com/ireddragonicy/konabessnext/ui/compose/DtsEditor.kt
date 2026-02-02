package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ireddragonicy.konabessnext.editor.highlight.ComposeHighlighter

data class LineSearchResult(val lineIndex: Int)

@Composable
fun DtsEditor(
    content: String,
    onContentChanged: (String) -> Unit, // Not fully supported in read-only LazyColumn logic yet, but kept signature
    searchQuery: String = "",
    searchResultIndex: Int = -1,
    searchResults: List<LineSearchResult> = emptyList(),
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val lines = remember(content) { content.split("\n") }

    // Auto-scroll to search result
    LaunchedEffect(searchResultIndex) {
        if (searchResultIndex >= 0 && searchResultIndex < searchResults.size) {
            val result = searchResults[searchResultIndex]
            // Scroll to the line index
            listState.animateScrollToItem(result.lineIndex)
        }
    }
    
    // Also scroll if searchResults is empty but we have a query? 
    // The previous logic used specific index.
    
    SelectionContainer(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(lines) { index, line ->
                // Highlight line if it matches current search result
                val isCurrentResultLine = if (searchResultIndex >= 0 && searchResultIndex < searchResults.size) {
                    searchResults[searchResultIndex].lineIndex == index
                } else false
                
                // Highlight text content
                val highlightedText = remember(line, searchQuery) {
                    ComposeHighlighter.highlight(line, searchQuery)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrentResultLine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                ) {
                    // Line Number
                    Text(
                        text = (index + 1).toString(),
                        modifier = Modifier
                            .width(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(end = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    
                    // Code
                    Text(
                        text = highlightedText,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .fillMaxWidth(),
                        color = Color(0xFFE0E2E7), // Enforce light color for base text
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
