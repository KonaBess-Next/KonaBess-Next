package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.isp.CamIspFreqTable
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamIspEditorScreen(
    ispTables: List<CamIspFreqTable>,
    onBack: () -> Unit,
    onEditFrequency: (nodeName: String, index: Int, newFreqHz: Long) -> Unit
) {
    var selectedTable by remember { mutableStateOf<CamIspFreqTable?>(null) }

    val currentTable = selectedTable?.let { sel ->
        ispTables.firstOrNull { it.nodeName == sel.nodeName }
    }

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
            FrequencyListView(
                table = currentTable,
                onEditFrequency = onEditFrequency
            )
        } else {
            TableSelectionView(
                ispTables = ispTables,
                onSelectTable = { selectedTable = it }
            )
        }
    }
}

@Composable
private fun TableSelectionView(
    ispTables: List<CamIspFreqTable>,
    onSelectTable: (CamIspFreqTable) -> Unit
) {
    if (ispTables.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Camera ISP nodes found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unable to find supported spectra/ife nodes.",
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
            items(ispTables.size) { index ->
                val table = ispTables[index]
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
                            text = table.nodeName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${table.levels.size} Power Levels",
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
    table: CamIspFreqTable,
    onEditFrequency: (nodeName: String, index: Int, newFreqHz: Long) -> Unit
) {
    var editingIndex by remember { mutableStateOf(-1) }
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
                    text = table.nodeName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
            }

            itemsIndexed(table.levels) { index, levelName ->
                // Ensure we don't crash if sizes are mismatched somehow, we padded or took maxes.
                val freqHz = table.freqHzList.getOrNull(index) ?: 0L
                val freqMhz = formatHzToMhzDisplay(freqHz)

                Card(
                    onClick = {
                        if (freqHz > 0) {
                            editingIndex = index
                            showEditDialog = true
                        }
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
                                text = levelName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (freqHz > 0) "$freqMhz MHz" else "Unused/Zero",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (freqHz > 0) {
                                Text(
                                    text = "0x${freqHz.toString(16)} ($freqHz Hz)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        if (freqHz > 0) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && editingIndex >= 0 && editingIndex < table.freqHzList.size) {
        val currentFreqHz = table.freqHzList[editingIndex]
        val levelName = table.levels.getOrNull(editingIndex) ?: "Unknown"
        var inputMhz by remember(editingIndex, currentFreqHz) {
            mutableStateOf(formatHzToMhzDisplay(currentFreqHz))
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit_frequency)) },
            text = {
                Column {
                    Text(
                        text = "Level: $levelName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputMhz,
                        onValueChange = { inputMhz = it },
                        label = { Text("Frequency (MHz)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newKHz = parseMhzToHz(inputMhz)
                        if (newKHz != null) {
                            onEditFrequency(table.nodeName, editingIndex, newKHz)
                        }
                        showEditDialog = false
                    },
                    enabled = parseMhzToHz(inputMhz) != null
                ) {
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

private fun formatHzToMhzDisplay(hz: Long): String {
    val mhz = hz / 1_000_000.0
    return if (mhz == mhz.toLong().toDouble()) {
        mhz.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", mhz)
    }
}

private fun parseMhzToHz(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    return try {
        val mhz = trimmed.toDouble()
        (mhz * 1_000_000).toLong()
    } catch (_: NumberFormatException) {
        null
    }
}
