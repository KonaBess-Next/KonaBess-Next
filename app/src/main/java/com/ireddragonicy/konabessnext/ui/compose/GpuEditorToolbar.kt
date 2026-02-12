package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.dts.DtsError
import com.ireddragonicy.konabessnext.model.dts.Severity
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
    onInstallToInactiveSlot: () -> Unit,
    onExportDts: () -> Unit,
    onExportImg: () -> Unit,
    canFlashOrRepack: Boolean,
    isRootMode: Boolean = true,
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

                // Build Menu
                var showBuildMenu by remember { mutableStateOf(false) }

                Box {
                    FilledTonalButton(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showBuildMenu = true 
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Build", style = MaterialTheme.typography.labelLarge)
                    }

                    DropdownMenu(
                        expanded = showBuildMenu,
                        onDismissRequest = { showBuildMenu = false }
                    ) {
                        val showImageActions = canFlashOrRepack || !isRootMode

                        DropdownMenuItem(
                            text = { Text("Export .dts Source") },
                            onClick = { 
                                onExportDts() 
                                showBuildMenu = false 
                            },
                            leadingIcon = { Icon(Icons.Rounded.Code, null) }
                        )

                        if (isRootMode || showImageActions) {
                            HorizontalDivider()
                        }

                        if (isRootMode) {
                            DropdownMenuItem(
                                text = { Text("Install to Inactive Slot (OTA)") },
                                onClick = {
                                    onInstallToInactiveSlot()
                                    showBuildMenu = false
                                },
                                leadingIcon = { Icon(Icons.Rounded.SystemUpdate, null) }
                            )
                        }

                        // Only show Image/Flash options if we have a valid base boot image
                        if (showImageActions) {
                            if (isRootMode) {
                                HorizontalDivider()
                            }
                            
                            DropdownMenuItem(
                                text = { Text(if (isRootMode) "Export .img File" else "Repack & Export .img") },
                                onClick = { 
                                    onExportImg() 
                                    showBuildMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Rounded.Save, null) }
                            )
                            
                            // Flash to device is only available in root mode
                            if (isRootMode && canFlashOrRepack) {
                                DropdownMenuItem(
                                    text = { Text("Flash to Device") },
                                    onClick = { 
                                        onFlashClick() 
                                        showBuildMenu = false 
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.FlashOn, null) },
                                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
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
    onReformat: (() -> Unit)? = null,
    lintErrorCount: Int = 0,
    lintErrors: List<DtsError> = emptyList(),
    onLintErrorClick: (DtsError) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showLintSheet by remember { mutableStateOf(false) }
    val lintSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortedLintErrors = remember(lintErrors) {
        lintErrors.sortedWith(
            compareBy<DtsError> { it.line }
                .thenBy { it.column }
                .thenBy { if (it.severity == Severity.ERROR) 0 else 1 }
        )
    }
    val errorCount = remember(sortedLintErrors) { sortedLintErrors.count { it.severity == Severity.ERROR } }
    val warningCount = remember(sortedLintErrors) { sortedLintErrors.count { it.severity == Severity.WARNING } }

    if (showLintSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLintSheet = false },
            sheetState = lintSheetState,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            LintIssuesBottomSheet(
                issues = sortedLintErrors,
                errorCount = errorCount,
                warningCount = warningCount,
                onIssueClick = { issue ->
                    showLintSheet = false
                    onLintErrorClick(issue)
                },
                onClose = { showLintSheet = false }
            )
        }
    }

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
            )

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

            // Reformat Code
            if (onReformat != null) {
                IconButton(onClick = onReformat) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Reformat Code",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Lint diagnostics quick navigator (right of wrench/reformat icon)
            if (lintErrorCount > 0) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(
                                text = lintErrorCount.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                ) {
                    IconButton(onClick = { showLintSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Lint issues",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LintIssuesBottomSheet(
    issues: List<DtsError>,
    errorCount: Int,
    warningCount: Int,
    onIssueClick: (DtsError) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lint Issues",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$errorCount errors • $warningCount warnings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap an issue to jump to source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = issues.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(issues) { _, issue ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIssueClick(issue) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = issue.message,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "Line ${issue.line + 1}, Column ${issue.column + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = if (issue.severity == Severity.ERROR) {
                                    Icons.Rounded.ErrorOutline
                                } else {
                                    Icons.Rounded.Warning
                                },
                                contentDescription = null,
                                tint = if (issue.severity == Severity.ERROR) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        ) {
            Text("Close")
        }
    }
}
