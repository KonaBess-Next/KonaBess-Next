package com.ireddragonicy.konabessnext.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.compose.DtsEditor
import com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Raw DTS Text Editor Fragment (Unified Workbench).
 * Fully migrated to Jetpack Compose.
 */
@AndroidEntryPoint
class UnifiedRawDtsFragment : Fragment() {

    private val sharedViewModel: SharedGpuViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                KonaBessTheme {
                    UnifiedDtsEditorScreen(sharedViewModel)
                }
            }
        }
    }

    companion object {
        fun newInstance() = UnifiedRawDtsFragment()
    }
}

@Composable
fun UnifiedDtsEditorScreen(viewModel: SharedGpuViewModel) {
    val context = LocalContext.current
    val dtsContent by viewModel.dtsContent.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val workbenchState by viewModel.workbenchState.collectAsState()
    
    // Local UI state
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var lineCount by remember { mutableStateOf(0) } // We can get this from editor content or viewmodel
    
    // Sync search query from ViewModel if needed (optional)
    LaunchedEffect(searchState.query) {
        if (searchState.query.isNotEmpty() && !showSearchBar) {
            showSearchBar = true
            searchQuery = searchState.query
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Toolbar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Copy All
            FilledTonalIconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("DTS Content", dtsContent)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_content_copy), contentDescription = "Copy All")
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Search Toggle
            FilledTonalIconButton(
                onClick = { 
                    showSearchBar = !showSearchBar 
                    if (!showSearchBar) {
                         viewModel.clearSearch()
                         searchQuery = ""
                    }
                },
                colors = if (showSearchBar) IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_search), contentDescription = "Search")
            }
        }

        // --- Search Bar ---
        if (showSearchBar) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it 
                            viewModel.search(it)
                        },
                        placeholder = { Text("Search") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        )
                    )
                    
                    Text(
                        text = if (searchState.results.isNotEmpty()) 
                                "${searchState.currentIndex + 1}/${searchState.results.size}" 
                               else "0/0",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    IconButton(onClick = { viewModel.previousSearchResult() }) {
                        Icon(androidx.compose.ui.res.painterResource(android.R.drawable.arrow_up_float), contentDescription = "Prev")
                    }
                    IconButton(onClick = { viewModel.nextSearchResult() }) {
                        Icon(androidx.compose.ui.res.painterResource(android.R.drawable.arrow_down_float), contentDescription = "Next")
                    }
                    IconButton(onClick = { 
                        showSearchBar = false 
                        viewModel.clearSearch()
                        searchQuery = ""
                    }) {
                        Icon(androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_close_clear_cancel), contentDescription = "Close")
                    }
                }
            }
        }

        // --- Loading Overlay ---
        if (workbenchState is SharedGpuViewModel.WorkbenchState.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        
        // --- Editor ---
        // Debounce logic handled in DtsEditor wrapper or here?
        // DtsEditor wrapper (AndroidView) calls onContentChanged immediately.
        // We should debounce here to avoid spamming ViewModel.
        val scope = rememberCoroutineScope()
        
        DtsEditor(
            content = dtsContent,
            onContentChanged = { newText ->
                // TODO: Debounce
                // For now direct update, ViewModel debounces?
                // SharedGpuViewModel.updateFromText usually updates state synchronously then parses async.
                // But updateFromText updates dtsContent StateFlow.
                // If we update dtsContent immediately, DtsEditor (AndroidView) sees change and might setText() again??
                // The AndroidView wrapper checks `if (view.text != content)`.
                // If user types 'a', view has '...a'. callback sends '...a'. VM updates '...a'.
                // AndroidView recomposes with '...a'. view.text is '...a'. No loop. Good.
                
                 viewModel.updateFromText(newText, "Text edit")
                 lineCount = newText.count { it == '\n' } + 1
            },
            searchQuery = searchQuery,
            searchResultIndex = searchState.currentIndex,
            // Map results if needed, DtsEditor just needs highlighting
            modifier = Modifier.weight(1f)
        )
        
        // --- Status Bar ---
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                 Text(
                     text = "Lines: $lineCount", // Simple count
                     style = MaterialTheme.typography.labelSmall,
                     fontFamily = FontFamily.Monospace
                 )
            }
        }
    }
}
