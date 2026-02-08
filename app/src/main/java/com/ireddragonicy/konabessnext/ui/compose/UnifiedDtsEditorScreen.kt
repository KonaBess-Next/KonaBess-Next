package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
fun UnifiedDtsEditorScreen(sharedViewModel: SharedGpuViewModel) {
    val dtsContent by sharedViewModel.dtsContent.collectAsState()
    val searchState by sharedViewModel.searchState.collectAsState()
    val lintErrorCount by sharedViewModel.lintErrorCount.collectAsState()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    
    // Persistent States
    val textScrollIdx by sharedViewModel.textScrollIndex.collectAsState()
    val textScrollOff by sharedViewModel.textScrollOffset.collectAsState()
    
    // Text Editor Scroll State (Persisted)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = textScrollIdx,
        initialFirstVisibleItemScrollOffset = textScrollOff
    )
    
    // Sync Text Scroll to VM
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collectLatest { (index, offset) ->
                sharedViewModel.textScrollIndex.value = index
                sharedViewModel.textScrollOffset.value = offset
            }
    }
    
    Column {
        SearchAndToolsBar(
            query = searchState.query,
            matchCount = searchState.results.size,
            currentMatchIndex = searchState.currentIndex,
            onQueryChange = { sharedViewModel.search(it) },
            onNext = { sharedViewModel.nextSearchResult() },
            onPrev = { sharedViewModel.previousSearchResult() },
            onCopyAll = { clipboardManager.setText(AnnotatedString(dtsContent)) },
            onReformat = { sharedViewModel.reformatCode() },
            lintErrorCount = lintErrorCount
        )

        DtsEditor(
            content = dtsContent,
            onContentChanged = { sharedViewModel.updateFromText(it, "Raw Edit") },
            searchQuery = searchState.query,
            searchResultIndex = searchState.currentIndex,
            searchResults = searchState.results.map { LineSearchResult(it.lineIndex) },
            lintErrorsByLine = sharedViewModel.lintErrorsByLine,
            listState = listState
        )
    }
}
