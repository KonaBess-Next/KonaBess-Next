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
                .statusBarsPadding() // Padding inside, so background extends behind status bar
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
                        text = if (isDirty) "Save Changes" else "Saved", 
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
                        contentDescription = "Undo",
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
                        contentDescription = "Redo",
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
                            contentDescription = "History",
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
                                    SharedGpuViewModel.ViewMode.MAIN_EDITOR -> "GUI"
                                    SharedGpuViewModel.ViewMode.TEXT_ADVANCED -> "Text"
                                    SharedGpuViewModel.ViewMode.VISUAL_TREE -> "Tree"
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
                        Icon(painter = painterResource(R.drawable.ic_developer_board), contentDescription = "Chipset")
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
                    Text("Flash", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// Helper for alpha value
fun Modifier.alpha(alpha: Float) = this.then(Modifier.wrapContentSize(Alignment.Center).graphicsLayer(alpha = alpha))
