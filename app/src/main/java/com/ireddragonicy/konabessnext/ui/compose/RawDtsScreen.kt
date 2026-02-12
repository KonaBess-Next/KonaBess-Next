package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ireddragonicy.konabessnext.viewmodel.TextEditorViewModel
import com.ireddragonicy.konabessnext.viewmodel.VisualTreeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@Composable
fun RawDtsScreen(
    textViewModel: TextEditorViewModel = hiltViewModel(),
    treeViewModel: VisualTreeViewModel = hiltViewModel()
) {
    val searchState by textViewModel.searchState.collectAsState()
    val foldSessionKey by textViewModel.dtsEditorSessionKey.collectAsState()
    val editorState = textViewModel.dtsEditorState
    val persistedCollapsedFolds = remember(foldSessionKey) {
        textViewModel.getCollapsedFolds(foldSessionKey)
    }
    
    // Read initial persisted positions once; then sync back with throttling.
    val initialTextScrollIdx = remember { textViewModel.textScrollIndex.value }
    val initialTextScrollOff = remember { textViewModel.textScrollOffset.value }
    val initialTreeScrollIdx = remember { treeViewModel.treeScrollIndex.value }
    val initialTreeScrollOff = remember { treeViewModel.treeScrollOffset.value }

    val parsedTree by treeViewModel.parsedTree.collectAsState()
    
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
    
    LaunchedEffect(textListState) {
        snapshotFlow { textListState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (!scrolling) {
                    val index = textListState.firstVisibleItemIndex
                    val offset = textListState.firstVisibleItemScrollOffset
                    if (textViewModel.textScrollIndex.value != index) {
                        textViewModel.textScrollIndex.value = index
                    }
                    if (textViewModel.textScrollOffset.value != offset) {
                        textViewModel.textScrollOffset.value = offset
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
                    if (treeViewModel.treeScrollIndex.value != index) {
                        treeViewModel.treeScrollIndex.value = index
                    }
                    if (treeViewModel.treeScrollOffset.value != offset) {
                        treeViewModel.treeScrollOffset.value = offset
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
                    if (treeViewModel.treeScrollIndex.value != index) {
                        treeViewModel.treeScrollIndex.value = index
                    }
                    if (treeViewModel.treeScrollOffset.value != offset) {
                        treeViewModel.treeScrollOffset.value = offset
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
                    treeViewModel.toggleNodeExpansion(path, expanded) 
                },
                onSearchMatchesChanged = { count ->
                    treeMatchCount = count
                    if (treeCurrentIndex >= count) {
                        treeCurrentIndex = if (count > 0) 0 else -1
                    }
                },
                onTreeModified = { treeViewModel.syncTreeToText() }
            )
        } else {
            DtsEditor(
                editorState = editorState,
                onLinesChanged = { textViewModel.updateFromEditorLines(it, "Raw Edit") },
                foldSessionKey = foldSessionKey,
                persistedCollapsedFolds = persistedCollapsedFolds,
                onFoldStateChanged = { textViewModel.updateCollapsedFolds(foldSessionKey, it) },
                searchQuery = searchState.query,
                searchResultIndex = searchState.currentIndex,
                searchResults = emptyList(),
                lintErrorsByLine = textViewModel.lintErrorsByLine,
                listState = textListState
            )
        }
    }
}
