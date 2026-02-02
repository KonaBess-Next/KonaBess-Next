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
    
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var lineCount by remember { mutableStateOf(0) }
    
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("DTS Content", dtsContent)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_content_copy), contentDescription = "Copy All")
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
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

        androidx.compose.animation.AnimatedVisibility(
            visible = showSearchBar,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Surface(
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_search), 
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    TextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it 
                            viewModel.search(it)
                        },
                        placeholder = { Text("Search...", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    
                    if (searchState.results.isNotEmpty()) {
                        Text(
                            text = "${searchState.currentIndex + 1}/${searchState.results.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

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

        if (workbenchState is SharedGpuViewModel.WorkbenchState.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        
        DtsEditor(
            content = dtsContent,
            onContentChanged = { newText ->
                 viewModel.updateFromText(newText, "Text edit")
                 lineCount = newText.count { it == '\n' } + 1
            },
            searchQuery = searchQuery,
            searchResultIndex = searchState.currentIndex,
            searchResults = searchState.results.map { com.ireddragonicy.konabessnext.ui.compose.LineSearchResult(it.lineIndex) },
            modifier = Modifier.weight(1f)
        )
        
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                 Text(
                     text = "Lines: $lineCount",
                     style = MaterialTheme.typography.labelSmall,
                     fontFamily = FontFamily.Monospace
                 )
            }
        }
    }
}