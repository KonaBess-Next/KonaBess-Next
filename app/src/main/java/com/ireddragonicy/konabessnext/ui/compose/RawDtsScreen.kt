package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val dtsContent by textViewModel.dtsContent.collectAsState()
    val parsedTree by treeViewModel.parsedTree.collectAsState()
    val flattenedList by treeViewModel.flattenedTreeState.collectAsState()

    // View Mode (Local switch for standalone activity)
    var isVisualMode by remember { mutableStateOf(false) }

    // Sora editor state for text mode search
    val soraEditorState = rememberSoraEditorState()

    // Tree mode search state
    val treeSearchQuery by treeViewModel.treeSearchQuery.collectAsState()
    val searchMatches by treeViewModel.searchMatches.collectAsState()
    var treeCurrentIndex by remember { mutableIntStateOf(-1) }

    // Reset tree search index when query changes
    LaunchedEffect(treeSearchQuery) {
        treeCurrentIndex = if (treeSearchQuery.isNotEmpty()) 0 else -1
    }

    // Tree Editor Scroll State
    val initialTreeScrollIdx = remember { treeViewModel.treeScrollIndex.value }
    val initialTreeScrollOff = remember { treeViewModel.treeScrollOffset.value }
    val treeListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialTreeScrollIdx,
        initialFirstVisibleItemScrollOffset = initialTreeScrollOff
    )

    // Sync tree scroll with throttling
    LaunchedEffect(treeListState) {
        var lastIdx = initialTreeScrollIdx
        var lastOff = initialTreeScrollOff
        snapshotFlow { Pair(treeListState.firstVisibleItemIndex, treeListState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                if (index != lastIdx || abs(offset - lastOff) >= 24) {
                    if (treeViewModel.treeScrollIndex.value != index) treeViewModel.treeScrollIndex.value = index
                    if (treeViewModel.treeScrollOffset.value != offset) treeViewModel.treeScrollOffset.value = offset
                    lastIdx = index
                    lastOff = offset
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
                    if (treeViewModel.treeScrollIndex.value != index) treeViewModel.treeScrollIndex.value = index
                    if (treeViewModel.treeScrollOffset.value != offset) treeViewModel.treeScrollOffset.value = offset
                }
            }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Switcher
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
             TextButton(onClick = {
                 focusManager.clearFocus()
                 isVisualMode = !isVisualMode
             }) {
                 Text(if (isVisualMode) "Switch to Text" else "Switch to Tree")
             }
        }

        if (isVisualMode) {
            DtsTreeScreen(
                rootNode = parsedTree, // null check inside DtsTreeScreen
                flattenedList = flattenedList,
                listState = treeListState,
                searchQuery = treeSearchQuery,
                searchMatches = searchMatches,
                searchMatchIndex = treeCurrentIndex,
                onNodeToggle = { path, expanded ->
                    treeViewModel.toggleNodeExpansion(path, expanded)
                },
                onExpandAncestors = { node ->
                    treeViewModel.expandAncestors(node)
                },
                onSearchMatchesChanged = { /* Now handled by ViewModel */ },
                onTreeModified = { treeViewModel.syncTreeToText() }
            )
        } else {
            DtsEditor(
                content = dtsContent,
                soraEditorState = soraEditorState,
                onContentChanged = { newText ->
                    textViewModel.updateFromText(newText, "Raw Edit")
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
