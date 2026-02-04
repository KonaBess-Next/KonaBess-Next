package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel

/**
 * Jetpack Compose Toolbar for GPU Frequency Editor.
 * Polished "Top Agency" Material 3 design with animations and haptics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuEditorToolbar(
    isDirty: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    historyCount: Int,
    currentViewMode: SharedGpuViewModel.ViewMode,
    showChipsetSelector: Boolean = false,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShowHistory: () -> Unit,
    onViewModeChanged: (SharedGpuViewModel.ViewMode) -> Unit,
    onChipsetClick: () -> Unit = {},
    onFlashClick: () -> Unit,
    applyStatusBarPadding: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Row 1: Save, Undo, Redo, History
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated Save Button
                val saveContainerColor by animateColorAsState(
                    targetValue = if (isDirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                    animationSpec = tween(durationMillis = 300),
                    label = "SaveButtonColor"
                )
                val saveContentColor by animateColorAsState(
                    targetValue = if (isDirty) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
                    animationSpec = tween(durationMillis = 300),
                    label = "SaveButtonContentColor"
                )

                Button(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave() 
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = saveContainerColor,
                        contentColor = saveContentColor
                    ),
                    modifier = Modifier.weight(1f),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isDirty) 6.dp else 0.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save), 
                        contentDescription = null, 
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isDirty) androidx.compose.ui.res.stringResource(R.string.save_changes) else androidx.compose.ui.res.stringResource(R.string.saved), 
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Undo
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUndo()
                    },
                    enabled = canUndo,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo),
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.undo),
                        modifier = Modifier.alpha(if (canUndo) 1f else 0.38f)
                    )
                }

                // Redo
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRedo()
                    },
                    enabled = canRedo,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_redo),
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.redo),
                        modifier = Modifier.alpha(if (canRedo) 1f else 0.38f)
                    )
                }

                // History
                BadgedBox(
                    badge = {
                        if (historyCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ) { Text(historyCount.toString()) }
                        }
                    }
                ) {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShowHistory()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history), 
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.history),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Row 2: View Mode + Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Segmented Button
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    SharedGpuViewModel.ViewMode.values().forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = currentViewMode == mode,
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onViewModeChanged(mode) 
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, SharedGpuViewModel.ViewMode.values().size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = when (mode) {
                                    SharedGpuViewModel.ViewMode.MAIN_EDITOR -> androidx.compose.ui.res.stringResource(R.string.view_mode_gui)
                                    SharedGpuViewModel.ViewMode.TEXT_ADVANCED -> androidx.compose.ui.res.stringResource(R.string.view_mode_text)
                                    SharedGpuViewModel.ViewMode.VISUAL_TREE -> androidx.compose.ui.res.stringResource(R.string.view_mode_tree)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                if (showChipsetSelector) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onChipsetClick()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors()
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_developer_board), contentDescription = androidx.compose.ui.res.stringResource(R.string.chipset))
                    }
                }

                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFlashClick()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(painter = painterResource(R.drawable.ic_flash), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.flash), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// Helper for alpha value
fun Modifier.alpha(alpha: Float) = this.then(Modifier.wrapContentSize(Alignment.Center).graphicsLayer(alpha = alpha))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndToolsBar(
    query: String,
    matchCount: Int,
    currentMatchIndex: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onCopyAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Field
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                placeholder = { Text(androidx.compose.ui.res.stringResource(android.R.string.search_go), fontSize = 12.sp) }, // Fallback string or generic
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (matchCount > 0) {
                                Text(
                                    text = "${currentMatchIndex + 1}/$matchCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            ) // Removed invalid colors parameter that contained contentPadding

            // Navigation
            IconButton(onClick = onPrev, enabled = matchCount > 0) {
                Icon(painter = painterResource(R.drawable.ic_arrow_upward), contentDescription = "Prev")
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(painter = painterResource(R.drawable.ic_arrow_downward), contentDescription = "Next")
            }

            // Copy All
            IconButton(onClick = onCopyAll) {
                Icon(painter = painterResource(R.drawable.ic_content_copy), contentDescription = "Copy All")
            }
        }
    }
}
