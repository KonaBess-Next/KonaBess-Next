package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlin.math.abs

@Composable
fun UnifiedDtsEditorScreen(sharedViewModel: SharedGpuViewModel) {
    val searchState by sharedViewModel.searchState.collectAsState()
    val lintErrorCount by sharedViewModel.lintErrorCount.collectAsState()
    val foldSessionKey by sharedViewModel.dtsEditorSessionKey.collectAsState()
    val editorState = sharedViewModel.dtsEditorState
    val persistedCollapsedFolds = remember(foldSessionKey) {
        sharedViewModel.getCollapsedFolds(foldSessionKey)
    }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    
    // Read initial persisted scroll once; then sync back with throttling.
    val initialTextScrollIdx = remember { sharedViewModel.textScrollIndex.value }
    val initialTextScrollOff = remember { sharedViewModel.textScrollOffset.value }
    
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
                    if (sharedViewModel.textScrollIndex.value != index) {
                        sharedViewModel.textScrollIndex.value = index
                    }
                    if (sharedViewModel.textScrollOffset.value != offset) {
                        sharedViewModel.textScrollOffset.value = offset
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
                    if (sharedViewModel.textScrollIndex.value != index) {
                        sharedViewModel.textScrollIndex.value = index
                    }
                    if (sharedViewModel.textScrollOffset.value != offset) {
                        sharedViewModel.textScrollOffset.value = offset
                    }
                }
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
            onCopyAll = { clipboardManager.setText(AnnotatedString(editorState.getText())) },
            onReformat = { sharedViewModel.reformatCode() },
            lintErrorCount = lintErrorCount
        )

        DtsEditor(
            editorState = editorState,
            onLinesChanged = { sharedViewModel.updateFromEditorLines(it, "Raw Edit") },
            foldSessionKey = foldSessionKey,
            persistedCollapsedFolds = persistedCollapsedFolds,
            onFoldStateChanged = { sharedViewModel.updateCollapsedFolds(foldSessionKey, it) },
            searchQuery = searchState.query,
            searchResultIndex = searchState.currentIndex,
            searchResults = searchState.results.map { LineSearchResult(it.lineIndex) },
            lintErrorsByLine = sharedViewModel.lintErrorsByLine,
            listState = listState
        )
    }
}
