package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.ufs.UfsClockTarget
import com.ireddragonicy.konabessnext.model.ufs.UfsFreqTable
import java.util.Locale

/**
 * UFS Storage Overclock Editor screen.
 *
 * Two-level navigation:
 *  - Level 0: list of found UFS tables (ufshc, etc.)
 *  - Level 1: list of frequencies within a selected table, editable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UfsEditorScreen(
    ufsTables: List<UfsFreqTable>,
    onBack: () -> Unit,
    onEditClockFrequencies: (nodeName: String, clockName: String, minIndex: Int, newMinMHz: Long, maxIndex: Int, newMaxMHz: Long) -> Unit
) {
    var selectedTable by remember { mutableStateOf<UfsFreqTable?>(null) }

    // Update selectedTable reference when data changes
    val currentTable = selectedTable?.let { sel ->
        ufsTables.firstOrNull { it.nodeName == sel.nodeName }
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
                onEditClockFrequencies = onEditClockFrequencies
            )
        } else {
            // Level 0: Table selection
            TableSelectionView(
                ufsTables = ufsTables,
                onSelectTable = { selectedTable = it }
            )
        }
    }
}

@Composable
private fun TableSelectionView(
    ufsTables: List<UfsFreqTable>,
    onSelectTable: (UfsFreqTable) -> Unit
) {
    if (ufsTables.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No UFS Tables Found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ensure your DTS has a valid UFS node.",
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
            items(ufsTables.size) { index ->
                val table = ufsTables[index]
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
                            text = stringResource(R.string.freq_count_format, table.frequenciesHz.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
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
    table: UfsFreqTable,
    onEditClockFrequencies: (nodeName: String, clockName: String, minIndex: Int, newMinMHz: Long, maxIndex: Int, newMaxMHz: Long) -> Unit
) {
    var editingTarget by remember { mutableStateOf<UfsClockTarget?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

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

            items(table.clockTargets.size) { index ->
                val target = table.clockTargets[index]
                val isZero = target.minFreqHz == 0L && target.maxFreqHz == 0L
                Card(
                    onClick = {
                        editingTarget = target
                        showEditDialog = true
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isZero) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (isZero) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
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
                            if (isZero) {
                                Text(
                                    text = target.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Disabled / Padding (0x0)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            } else {
                                Text(
                                    text = target.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Min: ${formatHzToMhz(target.minFreqHz)} MHz | Max: ${formatHzToMhz(target.maxFreqHz)} MHz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit dialog
    if (showEditDialog) {
        editingTarget?.let { target ->
            var inputMinMhz by remember(target) {
                mutableStateOf(if (target.minFreqHz == 0L) "" else formatHzToMhzRaw(target.minFreqHz))
            }
            var inputMaxMhz by remember(target) {
                mutableStateOf(if (target.maxFreqHz == 0L) "" else formatHzToMhzRaw(target.maxFreqHz))
            }
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit ${target.name}") },
                text = {
                    Column {
                        Text(
                            text = "Current Min: ${if (target.minFreqHz == 0L) "0" else formatHzToMhz(target.minFreqHz)} MHz (0x${target.minFreqHz.toString(16)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputMinMhz,
                            onValueChange = { 
                                if (it.isEmpty() || it.all { char -> char.isDigit() }) inputMinMhz = it 
                            },
                            label = { Text("Min Frequency (MHz)") },
                            singleLine = true,
                            placeholder = { Text("e.g. 100 or 0") }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Current Max: ${if (target.maxFreqHz == 0L) "0" else formatHzToMhz(target.maxFreqHz)} MHz (0x${target.maxFreqHz.toString(16)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputMaxMhz,
                            onValueChange = { 
                                if (it.isEmpty() || it.all { char -> char.isDigit() }) inputMaxMhz = it 
                            },
                            label = { Text("Max Frequency (MHz)") },
                            singleLine = true,
                            placeholder = { Text("e.g. 403 or 0") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val minMhz = inputMinMhz.toLongOrNull() ?: 0L
                        val maxMhz = inputMaxMhz.toLongOrNull() ?: 0L
                        onEditClockFrequencies(
                            table.nodeName, target.name, target.minIndex, minMhz, target.maxIndex, maxMhz
                        )
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
    }
}

/**
 * Converts Hz to MHz display string. E.g. 402000000 Hz → "402"
 */
private fun formatHzToMhz(hz: Long): String {
    val mhz = hz / 1_000_000.0
    return if (mhz == mhz.toLong().toDouble()) {
        mhz.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", mhz)
    }
}

private fun formatHzToMhzRaw(hz: Long): String {
    return (hz / 1_000_000L).toString()
}

/**
 * Converts a DTS node name to a user-friendly display name.
 */
private fun formatTableDisplayName(nodeName: String): String {
    if (nodeName.startsWith("ufshc")) {
        return "UFS Host Controller"
    }
    return nodeName.replace("-", " ").replaceFirstChar { it.uppercase() }
}
