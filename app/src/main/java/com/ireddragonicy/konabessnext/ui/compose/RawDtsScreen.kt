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

    // View Mode (Local switch for standalone activity)
    var isVisualMode by remember { mutableStateOf(false) }

    // Sora editor state for text mode search
    val soraEditorState = rememberSoraEditorState()

    // Tree mode search state (local — not in Sora)
    var treeSearchQuery by remember { mutableStateOf("") }
    var treeMatchCount by remember { mutableIntStateOf(0) }
    var treeCurrentIndex by remember { mutableIntStateOf(-1) }

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
                rootNode = parsedTree,
                listState = treeListState,
                searchQuery = treeSearchQuery,
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
