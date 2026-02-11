package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@Composable
fun RawDtsScreen(viewModel: SharedGpuViewModel) {
    val searchState by viewModel.searchState.collectAsState()
    val foldSessionKey by viewModel.dtsEditorSessionKey.collectAsState()
    val editorState = viewModel.dtsEditorState
    val persistedCollapsedFolds = remember(foldSessionKey) {
        viewModel.getCollapsedFolds(foldSessionKey)
    }
    
    // Read initial persisted positions once; then sync back with throttling.
    val initialTextScrollIdx = remember { viewModel.textScrollIndex.value }
    val initialTextScrollOff = remember { viewModel.textScrollOffset.value }
    val initialTreeScrollIdx = remember { viewModel.treeScrollIndex.value }
    val initialTreeScrollOff = remember { viewModel.treeScrollOffset.value }

    val parsedTree by viewModel.parsedTree.collectAsState()
    
    // View Mode (Local switch for standalone activity)
    var isVisualMode by remember { mutableStateOf(false) }
    
    // Text Editor Scroll State (Persisted)
    val textListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialTextScrollIdx,
        initialFirstVisibleItemScrollOffset = initialTextScrollOff
    )
    
    // Sync text scroll with throttling to avoid per-frame StateFlow writes.
    LaunchedEffect(textListState) {
        var lastCommittedIndex = initialTextScrollIdx
        var lastCommittedOffset = initialTextScrollOff
        snapshotFlow { Pair(textListState.firstVisibleItemIndex, textListState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                val shouldPersist =
                    index != lastCommittedIndex || abs(offset - lastCommittedOffset) >= 24
                if (shouldPersist) {
                    if (viewModel.textScrollIndex.value != index) {
                        viewModel.textScrollIndex.value = index
                    }
                    if (viewModel.textScrollOffset.value != offset) {
                        viewModel.textScrollOffset.value = offset
                    }
                    lastCommittedIndex = index
                    lastCommittedOffset = offset
                }
            }
    }
    
    LaunchedEffect(textListState) {
        snapshotFlow { textListState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (!scrolling) {
                    val index = textListState.firstVisibleItemIndex
                    val offset = textListState.firstVisibleItemScrollOffset
                    if (viewModel.textScrollIndex.value != index) {
                        viewModel.textScrollIndex.value = index
                    }
                    if (viewModel.textScrollOffset.value != offset) {
                        viewModel.textScrollOffset.value = offset
                    }
                }
            }
    }
    
    // Tree Editor Scroll State (Persisted)
    val treeListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialTreeScrollIdx,
        initialFirstVisibleItemScrollOffset = initialTreeScrollOff
    )
    
    // Sync tree scroll with throttling to avoid per-frame StateFlow writes.
    LaunchedEffect(treeListState) {
        var lastCommittedIndex = initialTreeScrollIdx
        var lastCommittedOffset = initialTreeScrollOff
        snapshotFlow { Pair(treeListState.firstVisibleItemIndex, treeListState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                val shouldPersist =
                    index != lastCommittedIndex || abs(offset - lastCommittedOffset) >= 24
                if (shouldPersist) {
                    if (viewModel.treeScrollIndex.value != index) {
                        viewModel.treeScrollIndex.value = index
                    }
                    if (viewModel.treeScrollOffset.value != offset) {
                        viewModel.treeScrollOffset.value = offset
                    }
                    lastCommittedIndex = index
                    lastCommittedOffset = offset
                }
            }
    }
    
    LaunchedEffect(treeListState) {
        snapshotFlow { treeListState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (!scrolling) {
                    val index = treeListState.firstVisibleItemIndex
                    val offset = treeListState.firstVisibleItemScrollOffset
                    if (viewModel.treeScrollIndex.value != index) {
                        viewModel.treeScrollIndex.value = index
                    }
                    if (viewModel.treeScrollOffset.value != offset) {
                        viewModel.treeScrollOffset.value = offset
                    }
                }
            }
    }

    // Tree-local search navigation for visual mode
    var treeMatchCount by remember { mutableIntStateOf(0) }
    var treeCurrentIndex by remember { mutableIntStateOf(-1) }
    
    LaunchedEffect(searchState.query) {
        treeCurrentIndex = if (searchState.query.isNotEmpty()) 0 else -1
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Switcher
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
             TextButton(onClick = { isVisualMode = !isVisualMode }) {
                 Text(if (isVisualMode) "Switch to Text" else "Switch to Tree")
             }
        }
        
        if (isVisualMode) {
            DtsTreeScreen(
                rootNode = parsedTree,
                listState = treeListState,
                searchQuery = searchState.query,
                searchMatchIndex = treeCurrentIndex,
                onNodeToggle = { path, expanded -> 
                    viewModel.toggleNodeExpansion(path, expanded) 
                },
                onSearchMatchesChanged = { count ->
                    treeMatchCount = count
                    if (treeCurrentIndex >= count) {
                        treeCurrentIndex = if (count > 0) 0 else -1
                    }
                },
                onTreeModified = { viewModel.syncTreeToText() }
            )
        } else {
            DtsEditor(
                editorState = editorState,
                onLinesChanged = { viewModel.updateFromEditorLines(it, "Raw Edit") },
                foldSessionKey = foldSessionKey,
                persistedCollapsedFolds = persistedCollapsedFolds,
                onFoldStateChanged = { viewModel.updateCollapsedFolds(foldSessionKey, it) },
                searchQuery = searchState.query,
                searchResultIndex = searchState.currentIndex,
                searchResults = emptyList(),
                lintErrorsByLine = viewModel.lintErrorsByLine,
                listState = textListState
            )
        }
    }
}
