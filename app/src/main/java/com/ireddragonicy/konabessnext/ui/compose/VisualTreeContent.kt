package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
fun VisualTreeContent(sharedViewModel: SharedGpuViewModel) {
    // Use Cached Tree from VM to persist object state (isExpanded flags)
    val rootNode by sharedViewModel.parsedTree.collectAsState()
    val searchState by sharedViewModel.searchState.collectAsState()
    val dtsContent by sharedViewModel.dtsContent.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    // Persistent Scroll State from VM
    val scrollIndex by sharedViewModel.treeScrollIndex.collectAsState()
    val scrollOffset by sharedViewModel.treeScrollOffset.collectAsState()
    
    // Initialize LazyListState with persistent values
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollIndex,
        initialFirstVisibleItemScrollOffset = scrollOffset
    )
    
    // Sync Scroll Changes Back to VM
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collectLatest { (index, offset) ->
                sharedViewModel.treeScrollIndex.value = index
                sharedViewModel.treeScrollOffset.value = offset
            }
    }

    if (rootNode == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column {
             SearchAndToolsBar(
                query = searchState.query,
                matchCount = searchState.results.size,
                currentMatchIndex = searchState.currentIndex,
                onQueryChange = { sharedViewModel.search(it) },
                onNext = { sharedViewModel.nextSearchResult() },
                onPrev = { sharedViewModel.previousSearchResult() },
                onCopyAll = { clipboardManager.setText(AnnotatedString(dtsContent)) }
            )

            DtsTreeScreen(
                rootNode = rootNode!!,
                listState = listState,
                onNodeToggle = { path, expanded ->
                    // Update expansion state in VM to persist across re-parses
                    sharedViewModel.toggleNodeExpansion(path, expanded)
                }
            )
        }
    }
}
