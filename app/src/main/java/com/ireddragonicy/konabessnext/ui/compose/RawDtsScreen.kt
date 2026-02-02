package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RawDtsScreen(viewModel: SharedGpuViewModel) {
    val dtsContent by viewModel.dtsContent.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    
    // Persistent States
    val textScrollIdx by viewModel.textScrollIndex.collectAsState()
    val textScrollOff by viewModel.textScrollOffset.collectAsState()
    
    val treeScrollIdx by viewModel.treeScrollIndex.collectAsState()
    val treeScrollOff by viewModel.treeScrollOffset.collectAsState()
    
    val parsedTree by viewModel.parsedTree.collectAsState()
    
    // View Mode (Local switch for standalone activity)
    var isVisualMode by remember { mutableStateOf(false) }
    
    // Text Editor Scroll State (Persisted)
    val textListState = rememberLazyListState(
        initialFirstVisibleItemIndex = textScrollIdx,
        initialFirstVisibleItemScrollOffset = textScrollOff
    )
    
    // Sync Text Scroll to VM
    LaunchedEffect(textListState) {
        snapshotFlow { Pair(textListState.firstVisibleItemIndex, textListState.firstVisibleItemScrollOffset) }
            .collectLatest { (index, offset) ->
                viewModel.textScrollIndex.value = index
                viewModel.textScrollOffset.value = offset
            }
    }
    
    // Tree Editor Scroll State (Persisted)
    val treeListState = rememberLazyListState(
        initialFirstVisibleItemIndex = treeScrollIdx,
        initialFirstVisibleItemScrollOffset = treeScrollOff
    )
    
    // Sync Tree Scroll to VM
    LaunchedEffect(treeListState) {
        snapshotFlow { Pair(treeListState.firstVisibleItemIndex, treeListState.firstVisibleItemScrollOffset) }
            .collectLatest { (index, offset) ->
                viewModel.treeScrollIndex.value = index
                viewModel.treeScrollOffset.value = offset
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Switcher
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
             TextButton(onClick = { isVisualMode = !isVisualMode }) {
                 Text(if (isVisualMode) "Switch to Text" else "Switch to Tree")
             }
        }
        
        if (isVisualMode) {
            // Use VM parsed tree if available
            DtsTreeScreen(
                rootNode = parsedTree,
                listState = treeListState,
                onNodeToggle = { path, expanded -> 
                    viewModel.toggleNodeExpansion(path, expanded) 
                }
            )
        } else {
            DtsEditor(
                content = dtsContent,
                onContentChanged = { viewModel.updateFromText(it, "Raw Edit") },
                searchQuery = searchState.query,
                searchResultIndex = searchState.currentIndex,
                searchResults = emptyList(),
                listState = textListState
            )
        }
    }
}
