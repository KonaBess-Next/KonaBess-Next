package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.display.DisplayProperty
import com.ireddragonicy.konabessnext.viewmodel.DisplayViewModel

@Composable
fun DtboTimingEditor(
    displayViewModel: DisplayViewModel
) {
    val snapshot by displayViewModel.displaySnapshot.collectAsState()
    val panelsWithTimings by displayViewModel.panelsWithTimings.collectAsState()
    val selectedIndex by displayViewModel.selectedPanelIndex.collectAsState()
    val selectedTimingIndex by displayViewModel.selectedTimingIndex.collectAsState()
    var customFps by remember(snapshot?.timing?.panelFramerate) {
        mutableStateOf(snapshot?.timing?.panelFramerate?.toString().orEmpty())
    }

    var editingProperty by remember { mutableStateOf<DisplayProperty?>(null) }
    var editingValue by remember { mutableStateOf("") }

    if (editingProperty != null) {
        AlertDialog(
            onDismissRequest = { editingProperty = null },
            title = { Text(stringResource(R.string.dtbo_edit_property, editingProperty?.name ?: "")) },
            text = {
                OutlinedTextField(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10,
                    singleLine = false,
                    label = { Text(stringResource(R.string.dtbo_property_value_hint)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val prop = editingProperty
                        if (prop != null) {
                            displayViewModel.updateTimingProperty(prop.name, editingValue)
                        }
                        editingProperty = null
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProperty = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (snapshot == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.dtbo_no_timing_found),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val fpsPresets = listOf(60, 90, 120, 144)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.dtbo_editor_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dtbo_editor_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Panel Selector ---
        if (panelsWithTimings.size > 1) {
            item {
                PanelSelectorCard(
                    panels = panelsWithTimings,
                    selectedIndex = selectedIndex,
                    onSelectPanel = { displayViewModel.selectPanel(it) }
                )
            }
        }

        // --- Timing Selector ---
        val currentPanel = panelsWithTimings.getOrNull(
            selectedIndex.coerceIn(0, (panelsWithTimings.size - 1).coerceAtLeast(0))
        )
        if (currentPanel != null && currentPanel.timings.size > 1) {
            item {
                TimingSelectorCard(
                    timings = currentPanel.timings,
                    selectedTimingIndex = selectedTimingIndex,
                    onSelectTiming = { displayViewModel.selectTiming(it) }
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.dtbo_node_name, snapshot?.timing?.timingNodeName ?: "timing@0"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.dtbo_panel_fps, snapshot?.timing?.panelFramerate ?: 0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(
                            R.string.dtbo_panel_resolution,
                            snapshot?.timing?.panelWidth ?: 0,
                            snapshot?.timing?.panelHeight ?: 0
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.dtbo_panel_clock, snapshot?.timing?.panelClockRate ?: 0L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.dtbo_fps_quick_presets),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fpsPresets.forEach { fps ->
                            ElevatedAssistChip(
                                onClick = { displayViewModel.updatePanelFramerate(fps) },
                                label = { Text("${fps}Hz") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dtbo_custom_fps),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customFps,
                            onValueChange = { customFps = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Hz") }
                        )
                        OutlinedButton(
                            onClick = {
                                val value = customFps.toIntOrNull() ?: return@OutlinedButton
                                displayViewModel.updatePanelFramerate(value)
                            }
                        ) {
                            Text(stringResource(R.string.dtbo_apply))
                        }
                    }

                    Text(
                        text = stringResource(R.string.dtbo_overclock_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // --- DFPS list section ---
        item {
            DfpsListCard(
                dfpsList = snapshot?.dfpsList ?: emptyList(),
                onUpdateList = { displayViewModel.updateDfpsList(it) }
            )
        }

        item {
            PanelClockRateCard(
                currentClock = snapshot?.timing?.panelClockRate ?: 0L,
                onUpdateClock = { displayViewModel.updatePanelClockRate(it) }
            )
        }

        item {
            Text(
                text = stringResource(R.string.dtbo_timing_properties),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(snapshot?.timing?.properties ?: emptyList(), key = { it.name }) { prop ->
            val preview = remember(prop.value) {
                val normalized = prop.value.replace("\n", " ").trim()
                if (normalized.length > 140) normalized.take(140) + "…" else normalized
            }

            Card(
                onClick = {
                    editingProperty = prop
                    editingValue = prop.value
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = prop.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DfpsListCard(
    dfpsList: List<Int>,
    onUpdateList: (List<Int>) -> Unit
) {
    var addFpsText by remember { mutableStateOf("") }

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.dtbo_dfps_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.dtbo_dfps_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (dfpsList.isEmpty()) {
                Text(
                    text = stringResource(R.string.dtbo_dfps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dfpsList.sortedDescending().forEach { fps ->
                        InputChip(
                            selected = false,
                            onClick = {
                                // Remove this FPS from the list
                                onUpdateList(dfpsList.filter { it != fps })
                            },
                            label = { Text("${fps}Hz") },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(InputChipDefaults.IconSize)
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = addFpsText,
                    onValueChange = { addFpsText = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.dtbo_dfps_add_hint)) }
                )
                FilledTonalButton(
                    onClick = {
                        val fps = addFpsText.toIntOrNull() ?: return@FilledTonalButton
                        if (fps > 0 && fps !in dfpsList) {
                            onUpdateList((dfpsList + fps).sortedDescending())
                            addFpsText = ""
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(R.string.dtbo_dfps_add))
                }
            }
        }
    }
}

@Composable
private fun PanelSelectorCard(
    panels: List<com.ireddragonicy.konabessnext.model.display.DisplayPanel>,
    selectedIndex: Int,
    onSelectPanel: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, (panels.size - 1).coerceAtLeast(0))
    val selected = panels.getOrNull(safeIndex)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.dtbo_select_panel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = stringResource(R.string.dtbo_panel_count, panels.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )

            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selected?.panelName?.ifBlank { selected.nodeName } ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.dtbo_fragment_label, selected?.fragmentIndex ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    panels.forEachIndexed { index, panel ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = panel.panelName.ifBlank { panel.nodeName },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (index == safeIndex) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = stringResource(R.string.dtbo_fragment_label, panel.fragmentIndex) +
                                                " • ${panel.timings.firstOrNull()?.panelFramerate ?: 0}Hz" +
                                                " • ${panel.timings.firstOrNull()?.panelWidth ?: 0}×${panel.timings.firstOrNull()?.panelHeight ?: 0}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            onClick = {
                                onSelectPanel(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingSelectorCard(
    timings: List<com.ireddragonicy.konabessnext.model.display.DisplayTiming>,
    selectedTimingIndex: Int,
    onSelectTiming: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = selectedTimingIndex.coerceIn(0, (timings.size - 1).coerceAtLeast(0))
    val selected = timings.getOrNull(safeIndex)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.dtbo_select_timing),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = stringResource(R.string.dtbo_timing_count, timings.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )

            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selected?.timingNodeName ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${selected?.panelFramerate ?: 0}Hz • ${selected?.panelWidth ?: 0}×${selected?.panelHeight ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    timings.forEachIndexed { index, timing ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = timing.timingNodeName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (index == safeIndex) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "${timing.panelFramerate}Hz • ${timing.panelWidth}×${timing.panelHeight}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            onClick = {
                                onSelectTiming(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelClockRateCard(
    currentClock: Long,
    onUpdateClock: (Long) -> Unit
) {
    var clockText by remember(currentClock) { mutableStateOf(currentClock.toString()) }

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.dtbo_panel_clockrate_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.dtbo_panel_clockrate_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = clockText,
                    onValueChange = { clockText = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Hz") }
                )
                OutlinedButton(
                    onClick = {
                        val value = clockText.toLongOrNull() ?: return@OutlinedButton
                        onUpdateClock(value)
                    }
                ) {
                    Text(stringResource(R.string.dtbo_apply))
                }
            }
        }
    }
}
