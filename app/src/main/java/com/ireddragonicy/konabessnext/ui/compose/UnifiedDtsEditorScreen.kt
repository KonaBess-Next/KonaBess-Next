package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ireddragonicy.konabessnext.viewmodel.TextEditorViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlin.math.abs

@Composable
fun UnifiedDtsEditorScreen(
    textViewModel: TextEditorViewModel = hiltViewModel()
) {
    val searchState by textViewModel.searchState.collectAsState()
    val lintErrorCount by textViewModel.lintErrorCount.collectAsState()
    val foldSessionKey by textViewModel.dtsEditorSessionKey.collectAsState()
    val editorState = textViewModel.dtsEditorState
    val persistedCollapsedFolds = remember(foldSessionKey) {
        textViewModel.getCollapsedFolds(foldSessionKey)
    }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    
    // Read initial persisted scroll once; then sync back with throttling.
    val initialTextScrollIdx = remember { textViewModel.textScrollIndex.value }
    val initialTextScrollOff = remember { textViewModel.textScrollOffset.value }
    
    // Text Editor Scroll State (Persisted)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialTextScrollIdx,
        initialFirstVisibleItemScrollOffset = initialTextScrollOff
    )
    
    // Sync text scroll with throttling to avoid per-frame StateFlow writes.
    LaunchedEffect(listState) {
        var lastCommittedIndex = initialTextScrollIdx
        var lastCommittedOffset = initialTextScrollOff
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                val shouldPersist =
                    index != lastCommittedIndex || abs(offset - lastCommittedOffset) >= 24
                if (shouldPersist) {
                    if (textViewModel.textScrollIndex.value != index) {
                        textViewModel.textScrollIndex.value = index
                    }
                    if (textViewModel.textScrollOffset.value != offset) {
                        textViewModel.textScrollOffset.value = offset
                    }
                    lastCommittedIndex = index
                    lastCommittedOffset = offset
                }
            }
    }
    
    // Ensure final exact position is persisted when scrolling stops.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (!scrolling) {
                    val index = listState.firstVisibleItemIndex
                    val offset = listState.firstVisibleItemScrollOffset
                    if (textViewModel.textScrollIndex.value != index) {
                        textViewModel.textScrollIndex.value = index
                    }
                    if (textViewModel.textScrollOffset.value != offset) {
                        textViewModel.textScrollOffset.value = offset
                    }
                }
            }
    }
    
    Column {
        SearchAndToolsBar(
            query = searchState.query,
            matchCount = searchState.results.size,
            currentMatchIndex = searchState.currentIndex,
            onQueryChange = { textViewModel.search(it) },
            onNext = { textViewModel.nextSearchResult() },
            onPrev = { textViewModel.previousSearchResult() },
            onCopyAll = { clipboardManager.setText(AnnotatedString(editorState.getText())) },
            onReformat = { textViewModel.reformatCode() },
            lintErrorCount = lintErrorCount
        )

        DtsEditor(
            editorState = editorState,
            onLinesChanged = { textViewModel.updateFromEditorLines(it, "Raw Edit") },
            foldSessionKey = foldSessionKey,
            persistedCollapsedFolds = persistedCollapsedFolds,
            onFoldStateChanged = { textViewModel.updateCollapsedFolds(foldSessionKey, it) },
            searchQuery = searchState.query,
            searchResultIndex = searchState.currentIndex,
            searchResults = searchState.results.map { LineSearchResult(it.lineIndex) },
            lintErrorsByLine = textViewModel.lintErrorsByLine,
            listState = listState
        )
    }
}
