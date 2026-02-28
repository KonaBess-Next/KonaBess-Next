package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.memory.MemoryFreqTable
import java.util.Locale

/**
 * DDR / LLCC / DDR-QoS Memory Frequency Editor screen.
 *
 * Two-level navigation:
 *  - Level 0: list of found memory tables (ddr-freq-table, llcc-freq-table, etc.)
 *  - Level 1: list of frequencies within a selected table, editable/deletable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdrEditorScreen(
    memoryTables: List<MemoryFreqTable>,
    onBack: () -> Unit,
    onEditFrequency: (nodeName: String, index: Int, newFreqKHz: Long) -> Unit,
    onAddFrequency: (nodeName: String, freqKHz: Long) -> Unit,
    onDeleteFrequency: (nodeName: String, index: Int) -> Unit
) {
    var selectedTable by remember { mutableStateOf<MemoryFreqTable?>(null) }

    // Update selectedTable reference when data changes
    val currentTable = selectedTable?.let { sel ->
        memoryTables.firstOrNull { it.nodeName == sel.nodeName }
    }

    // If selected table was removed from data, reset
    LaunchedEffect(currentTable) {
        if (selectedTable != null && currentTable == null) {
            selectedTable = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (currentTable != null) {
                            selectedTable = null
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_back), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_back))
                }
            }
        }

        if (currentTable != null) {
            // Level 1: Frequency list for a specific table
            FrequencyListView(
                table = currentTable,
                onEditFrequency = onEditFrequency,
                onAddFrequency = onAddFrequency,
                onDeleteFrequency = onDeleteFrequency
            )
        } else {
            // Level 0: Table selection
            TableSelectionView(
                memoryTables = memoryTables,
                onSelectTable = { selectedTable = it }
            )
        }
    }
}

@Composable
private fun TableSelectionView(
    memoryTables: List<MemoryFreqTable>,
    onSelectTable: (MemoryFreqTable) -> Unit
) {
    if (memoryTables.isEmpty()) {
        // Empty state
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.no_memory_tables_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.no_memory_tables_found_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(memoryTables.size) { index ->
                val table = memoryTables[index]
                Card(
                    onClick = { onSelectTable(table) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = formatTableDisplayName(table.nodeName),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.freq_count_format, table.frequenciesKHz.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Show frequency range
                        val minMhz = formatKhzToMhz(table.frequenciesKHz.minOrNull() ?: 0L)
                        val maxMhz = formatKhzToMhz(table.frequenciesKHz.maxOrNull() ?: 0L)
                        Text(
                            text = "$minMhz – $maxMhz MHz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyListView(
    table: MemoryFreqTable,
    onEditFrequency: (nodeName: String, index: Int, newFreqKHz: Long) -> Unit,
    onAddFrequency: (nodeName: String, freqKHz: Long) -> Unit,
    onDeleteFrequency: (nodeName: String, index: Int) -> Unit
) {
    var editingIndex by remember { mutableStateOf(-1) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteIndex by remember { mutableStateOf(-1) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = formatTableDisplayName(table.nodeName),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
            }

            itemsIndexed(table.frequenciesKHz) { index, freqKHz ->
                Card(
                    onClick = {
                        editingIndex = index
                        showEditDialog = true
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${formatKhzToMhz(freqKHz)} MHz",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "0x${freqKHz.toString(16)} ($freqKHz kHz)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(
                            onClick = {
                                deleteIndex = index
                                showDeleteDialog = true
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_new_frequency))
        }
    }

    // Edit dialog
    if (showEditDialog && editingIndex >= 0 && editingIndex < table.frequenciesKHz.size) {
        val currentKHz = table.frequenciesKHz[editingIndex]
        var inputMhz by remember(editingIndex, currentKHz) {
            mutableStateOf(formatKhzToMhz(currentKHz))
        }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit_frequency)) },
            text = {
                Column {
                    Text(
                        text = "Current: ${formatKhzToMhz(currentKHz)} MHz (0x${currentKHz.toString(16)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputMhz,
                        onValueChange = { inputMhz = it },
                        label = { Text(stringResource(R.string.frequency_mhz_hint)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    parseMhzToKHz(inputMhz)?.let { newKHz ->
                        onEditFrequency(table.nodeName, editingIndex, newKHz)
                    }
                    showEditDialog = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog && deleteIndex >= 0 && deleteIndex < table.frequenciesKHz.size) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                val freqMhz = formatKhzToMhz(table.frequenciesKHz[deleteIndex])
                Text(stringResource(R.string.delete_frequency_confirm) + "\n$freqMhz MHz")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFrequency(table.nodeName, deleteIndex)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Add frequency dialog
    if (showAddDialog) {
        var addInputMhz by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_new_frequency)) },
            text = {
                Column {
                    Text(
                        text = "Enter the new frequency value in MHz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = addInputMhz,
                        onValueChange = { addInputMhz = it },
                        label = { Text(stringResource(R.string.frequency_mhz_hint)) },
                        singleLine = true,
                        placeholder = { Text("e.g. 3200") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parseMhzToKHz(addInputMhz)?.let { newKHz ->
                            onAddFrequency(table.nodeName, newKHz)
                        }
                        showAddDialog = false
                    },
                    enabled = parseMhzToKHz(addInputMhz) != null
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Converts kHz to MHz display string. E.g. 4761600 kHz → "4761.6"
 */
private fun formatKhzToMhz(kHz: Long): String {
    val mhz = kHz / 1000.0
    return if (mhz == mhz.toLong().toDouble()) {
        mhz.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", mhz)
    }
}

/**
 * Parses MHz input to kHz. Supports decimal (e.g. "4761.6") and hex (e.g. "0x489c00").
 */
private fun parseMhzToKHz(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    // If user entered raw hex, treat as kHz directly
    if (trimmed.startsWith("0x", ignoreCase = true)) {
        return try {
            java.lang.Long.decode(trimmed)
        } catch (_: NumberFormatException) {
            null
        }
    }

    // Otherwise treat as MHz → kHz
    return try {
        val mhz = trimmed.toDouble()
        (mhz * 1000).toLong()
    } catch (_: NumberFormatException) {
        null
    }
}

/**
 * Converts a DTS node name to a user-friendly display name.
 * E.g. "ddr-freq-table" → "DDR Frequency Table"
 */
private fun formatTableDisplayName(nodeName: String): String {
    return when (nodeName) {
        "ddr-freq-table" -> "DDR Frequency Table"
        "llcc-freq-table" -> "LLCC Cache Frequency Table"
        "ddrqos-freq-table" -> "DDR QoS Frequency Table"
        else -> nodeName.replace("-", " ")
            .replaceFirstChar { it.uppercase() }
    }
}
