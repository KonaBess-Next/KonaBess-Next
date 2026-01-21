package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RawDtsScreen(viewModel: SharedGpuViewModel) {
    val dtsContent by viewModel.dtsContent.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    
    var isVisualMode by remember { mutableStateOf(false) }
    var rootNode by remember { mutableStateOf<DtsNode?>(null) }
    
    // Parse for visual mode
    if (isVisualMode) {
        LaunchedEffect(dtsContent) {
            withContext(Dispatchers.Default) {
                try {
                    val root = DtsTreeHelper.parse(dtsContent)
                    withContext(Dispatchers.Main) { rootNode = root }
                } catch(e: Exception) {}
            }
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
            DtsTreeScreen(rootNode = rootNode)
        } else {
            DtsEditor(
                content = dtsContent,
                onContentChanged = { viewModel.updateFromText(it, "Raw Edit") },
                searchQuery = searchState.query,
                searchResultIndex = searchState.currentIndex,
                searchResults = emptyList()
            )
        }
    }
}
