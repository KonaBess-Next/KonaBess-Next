package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.gpu.GpuBandwidthTable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuBandwidthScreen(
    bandwidthTables: List<GpuBandwidthTable>,
    onBack: () -> Unit,
    onEditBandwidth: (propertyName: String, index: Int, newValue: Long) -> Unit
) {
    var selectedTable by remember { mutableStateOf<GpuBandwidthTable?>(null) }

    val currentTable = selectedTable?.let { sel ->
        bandwidthTables.firstOrNull { it.propertyName == sel.propertyName }
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
            BandwidthListView(
                table = currentTable,
                onEditBandwidth = onEditBandwidth
            )
        } else {
            TableSelectionView(
                bandwidthTables = bandwidthTables,
                onSelectTable = { selectedTable = it }
            )
        }
    }
}

@Composable
private fun TableSelectionView(
    bandwidthTables: List<GpuBandwidthTable>,
    onSelectTable: (GpuBandwidthTable) -> Unit
) {
    if (bandwidthTables.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.no_gpu_bandwidth_tables),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.no_gpu_bandwidth_tables_desc),
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
            items(bandwidthTables.size) { index ->
                val table = bandwidthTables[index]
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
                            text = formatBandwidthTableDisplayName(table.propertyName),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.bandwidth_levels_format, table.bandwidths.size),
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
private fun BandwidthListView(
    table: GpuBandwidthTable,
    onEditBandwidth: (propertyName: String, index: Int, newValue: Long) -> Unit
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
                    text = formatBandwidthTableDisplayName(table.propertyName),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
            }

            itemsIndexed(table.bandwidths) { index, bandwidth ->
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
                                text = stringResource(R.string.index_format, index),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.bandwidth_hex_dec_format, bandwidth.toString(16), bandwidth),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
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

    if (showEditDialog && editingIndex >= 0 && editingIndex < table.bandwidths.size) {
        val currentBw = table.bandwidths[editingIndex]
        var inputVal by remember(editingIndex, currentBw) {
            mutableStateOf(currentBw.toString())
        }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.current_bandwidth_format, currentBw, currentBw.toString(16)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputVal,
                        onValueChange = { inputVal = it },
                        label = { Text(stringResource(R.string.new_bandwidth_value)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    parseBandwidthInput(inputVal)?.let { newBw ->
                        onEditBandwidth(table.propertyName, editingIndex, newBw)
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
}

private fun parseBandwidthInput(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith("0x", ignoreCase = true)) {
        return try {
            java.lang.Long.decode(trimmed)
        } catch (_: NumberFormatException) {
            null
        }
    }

    return trimmed.toLongOrNull()
}

private fun formatBandwidthTableDisplayName(propertyName: String): String {
    return when (propertyName) {
        "qcom,bus-table-ddr" -> "DDDR 总线表"
        "qcom,bus-table-cnoc" -> "CNOC 总线表"
        else -> propertyName
    }
}
