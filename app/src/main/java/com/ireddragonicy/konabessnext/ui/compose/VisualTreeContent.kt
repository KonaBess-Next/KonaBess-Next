package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.viewmodel.VisualTreeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlin.math.abs

@Composable
fun VisualTreeContent(
    treeViewModel: VisualTreeViewModel = hiltViewModel()
) {
    // Use Cached Tree from VM to persist object state (isExpanded flags)
    val rootNode by treeViewModel.parsedTree.collectAsState()
    val dtsContent by treeViewModel.dtsContent.collectAsState(initial = "")
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    
    // Tree view keeps local query to avoid triggering line-search pipeline.
    var treeQuery by rememberSaveable { mutableStateOf("") }
    
    // Read initial persisted scroll once; then sync back with throttling.
    val initialScrollIndex = remember { treeViewModel.treeScrollIndex.value }
    val initialScrollOffset = remember { treeViewModel.treeScrollOffset.value }
    
    // Initialize LazyListState with persistent values
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset
    )
    
    // Sync scroll changes with throttling to avoid per-frame StateFlow writes.
    LaunchedEffect(listState) {
        var lastCommittedIndex = initialScrollIndex
        var lastCommittedOffset = initialScrollOffset
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
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

    // Ensure final exact position is persisted when scrolling stops.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (!scrolling) {
                    val index = listState.firstVisibleItemIndex
                    val offset = listState.firstVisibleItemScrollOffset
                    if (treeViewModel.treeScrollIndex.value != index) {
                        treeViewModel.treeScrollIndex.value = index
                    }
                    if (treeViewModel.treeScrollOffset.value != offset) {
                        treeViewModel.treeScrollOffset.value = offset
                    }
                }
            }
    }

    // Tree-local search navigation — separate from ViewModel's line-based results
    var treeMatchCount by remember { mutableIntStateOf(0) }
    var treeCurrentIndex by remember { mutableIntStateOf(-1) }

    // Reset tree search index when query changes
    LaunchedEffect(treeQuery) {
        treeCurrentIndex = if (treeQuery.isNotEmpty()) 0 else -1
    }

    if (rootNode == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column {
             SearchAndToolsBar(
                query = treeQuery,
                matchCount = treeMatchCount,
                currentMatchIndex = treeCurrentIndex,
                onQueryChange = { treeQuery = it },
                onNext = {
                    if (treeMatchCount > 0) {
                        treeCurrentIndex = (treeCurrentIndex + 1) % treeMatchCount
                    }
                },
                onPrev = {
                    if (treeMatchCount > 0) {
                        treeCurrentIndex = if (treeCurrentIndex - 1 < 0) treeMatchCount - 1 else treeCurrentIndex - 1
                    }
                },
                onCopyAll = { clipboardManager.setText(AnnotatedString(dtsContent)) }
            )

            DtsTreeScreen(
                rootNode = rootNode!!,
                listState = listState,
                searchQuery = treeQuery,
                searchMatchIndex = treeCurrentIndex,
                onNodeToggle = { path, expanded ->
                    treeViewModel.toggleNodeExpansion(path, expanded)
                },
                onSearchMatchesChanged = { count ->
                    treeMatchCount = count
                    // Clamp current index if matches shrunk
                    if (treeCurrentIndex >= count) {
                        treeCurrentIndex = if (count > 0) 0 else -1
                    }
                },
                onTreeModified = { treeViewModel.syncTreeToText() }
            )
        }
    }
}
