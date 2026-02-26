package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.dts.DeepMatch
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import com.ireddragonicy.konabessnext.model.dts.TreeItem
import com.ireddragonicy.konabessnext.ui.view.DtsTreeView

@Composable
fun DtsTreeScreen(
    rootNode: DtsNode?,
    flattenedList: List<TreeItem>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    searchQuery: String = "",
    searchMatches: List<DeepMatch> = emptyList(),
    searchMatchIndex: Int = -1,
    onNodeToggle: ((String, Boolean) -> Unit)? = null,
    onExpandAncestors: ((DtsNode) -> Unit)? = null,
    onSearchMatchesChanged: ((Int) -> Unit)? = null,
    onTreeModified: (() -> Unit)? = null
) {
    if (rootNode == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    var forceDraw by remember { mutableIntStateOf(0) }

    val currentOnNodeToggle by rememberUpdatedState(onNodeToggle)
    val currentOnTreeModified by rememberUpdatedState(onTreeModified)
    val currentOnExpandAncestors by rememberUpdatedState(onExpandAncestors)

    LaunchedEffect(searchMatches.size) {
        onSearchMatchesChanged?.invoke(searchMatches.size)
    }

    val activeMatchId = if (searchMatchIndex in searchMatches.indices) {
        searchMatches[searchMatchIndex].flatId
    } else ""

    // Navigate to match (expand ancestors if needed, view logic handles scroll)
    LaunchedEffect(searchMatchIndex, searchMatches) {
        if (searchMatchIndex in searchMatches.indices) {
            val match = searchMatches[searchMatchIndex]
            if (match.property != null) match.node.isExpanded = true
            currentOnExpandAncestors?.invoke(match.node)
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f).toArgb()
    val activeHighlight = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f).toArgb()
    val passiveHighlight = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f).toArgb()

    // --- Edit Dialog State ---
    var editingProperty by remember { mutableStateOf<DtsProperty?>(null) }
    var editingValue by remember { mutableStateOf(TextFieldValue("")) }

    // --- Material You Edit Dialog ---
    if (editingProperty != null) {
        AlertDialog(
            onDismissRequest = {
                editingProperty = null
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = editingProperty!!.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                OutlinedTextField(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    label = { Text("Value") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 1. O(1) Memory Mutation
                        editingProperty?.updateFromDisplayValue(editingValue.text)
                        
                        // 2. Force instant canvas redraw
                        forceDraw++ 
                        
                        // 3. Trigger background text sync
                        currentOnTreeModified?.invoke() 
                        editingProperty = null
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProperty = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // --- Canvas Tree View ---
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            DtsTreeView(ctx).apply {
                setColors(
                    surface = surfaceColor,
                    primary = primaryColor,
                    onSurface = onSurfaceColor,
                    onSurfaceVariant = onSurfaceVariantColor,
                    divider = dividerColor,
                    activeHighlight = activeHighlight,
                    passiveHighlight = passiveHighlight
                )
                this.onNodeToggle = { node ->
                    if (node.parent != null && node.name != "root") {
                        currentOnNodeToggle?.invoke(node.getFullPath(), !node.isExpanded)
                    }
                }
                this.onPropertyEditReq = { prop, initialValue, _ ->
                    editingProperty = prop
                    editingValue = TextFieldValue(initialValue, TextRange(initialValue.length))
                }
            }
        },
        update = { view ->
            // Read state to trigger recomposition
            forceDraw 
            
            view.setColors(
                surface = surfaceColor,
                primary = primaryColor,
                onSurface = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariantColor,
                divider = dividerColor,
                activeHighlight = activeHighlight,
                passiveHighlight = passiveHighlight
            )
            view.setTreeData(flattenedList, searchMatches, activeMatchId)
            if (activeMatchId.isNotEmpty()) {
                view.scrollToMatch(activeMatchId)
            }
            
            // Force immediate UI update using the mutated memory data
            view.invalidate() 
        }
    )
}
