package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.gmu.GmuFreqTable
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GmuEditorScreen(
    gmuTables: List<GmuFreqTable>,
    onBack: () -> Unit,
    onEditPair: (nodeName: String, index: Int, newFreqKHz: Long, newVote: Long) -> Unit,
    onAddPair: (nodeName: String, freqKHz: Long, vote: Long) -> Unit,
    onDeletePair: (nodeName: String, index: Int) -> Unit
) {
    var selectedTable by remember { mutableStateOf<GmuFreqTable?>(null) }

    val currentTable = selectedTable?.let { sel ->
        gmuTables.firstOrNull { it.nodeName == sel.nodeName }
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
            PairListView(
                table = currentTable,
                onEditPair = onEditPair,
                onAddPair = onAddPair,
                onDeletePair = onDeletePair
            )
        } else {
            TableSelectionView(
                gmuTables = gmuTables,
                onSelectTable = { selectedTable = it }
            )
        }
    }
}

@Composable
private fun TableSelectionView(
    gmuTables: List<GmuFreqTable>,
    onSelectTable: (GmuFreqTable) -> Unit
) {
    if (gmuTables.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No GMU tables found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "There are no GMU tables in the loaded DTS.",
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
            items(gmuTables.size) { index ->
                val table = gmuTables[index]
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
                            text = "${table.pairs.size} Frequencies",
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
private fun PairListView(
    table: GmuFreqTable,
    onEditPair: (nodeName: String, index: Int, newFreqKHz: Long, newVote: Long) -> Unit,
    onAddPair: (nodeName: String, freqKHz: Long, vote: Long) -> Unit,
    onDeletePair: (nodeName: String, index: Int) -> Unit
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
                    text = table.nodeName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
            }

            itemsIndexed(table.pairs) { index, pair ->
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
                                text = "${formatHzToMhzDisplay(pair.freqHz)} MHz",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Vote: 0x${pair.vote.toString(16)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "0x${pair.freqHz.toString(16)} (${pair.freqHz} Hz)",
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
            Icon(Icons.Rounded.Add, contentDescription = "Add New GMU Frequency")
        }
    }

    if (showEditDialog && editingIndex >= 0 && editingIndex < table.pairs.size) {
        val currentPair = table.pairs[editingIndex]
        var inputMhz by remember(editingIndex, currentPair) {
            mutableStateOf(formatHzToMhzDisplay(currentPair.freqHz))
        }
        var inputVote by remember(editingIndex, currentPair) {
            mutableStateOf(currentPair.vote.toString(16))
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit GMU Frequency") },
            text = {
                Column {
                    Text(
                        text = "Current: ${formatHzToMhzDisplay(currentPair.freqHz)} MHz",
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputVote,
                        onValueChange = { inputVote = it },
                        label = { Text("Vote (Hex)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newKHz = parseMhzToKHz(inputMhz)
                        val newVote = inputVote.toLongOrNull(16)
                        if (newKHz != null && newVote != null) {
                            onEditPair(table.nodeName, editingIndex, newKHz, newVote)
                        }
                        showEditDialog = false
                    },
                    enabled = parseMhzToKHz(inputMhz) != null && inputVote.toLongOrNull(16) != null
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

    if (showDeleteDialog && deleteIndex >= 0 && deleteIndex < table.pairs.size) {
        val freqMhz = formatHzToMhzDisplay(table.pairs[deleteIndex].freqHz)
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(stringResource(R.string.delete_frequency_confirm) + "\n$freqMhz MHz")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePair(table.nodeName, deleteIndex)
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

    if (showAddDialog) {
        var addInputMhz by remember { mutableStateOf("") }
        var addInputVote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New GMU Frequency") },
            text = {
                Column {
                    OutlinedTextField(
                        value = addInputMhz,
                        onValueChange = { addInputMhz = it },
                        label = { Text("Frequency (MHz)") },
                        singleLine = true,
                        placeholder = { Text("e.g. 500") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addInputVote,
                        onValueChange = { addInputVote = it },
                        label = { Text("Vote (Hex)") },
                        singleLine = true,
                        placeholder = { Text("e.g. 40") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newKHz = parseMhzToKHz(addInputMhz)
                        val newVote = addInputVote.toLongOrNull(16)
                        if (newKHz != null && newVote != null) {
                            onAddPair(table.nodeName, newKHz, newVote)
                        }
                        showAddDialog = false
                    },
                    enabled = parseMhzToKHz(addInputMhz) != null && addInputVote.toLongOrNull(16) != null
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

private fun formatHzToMhzDisplay(hz: Long): String {
    val mhz = hz / 1_000_000.0
    return if (mhz == mhz.toLong().toDouble()) {
        mhz.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", mhz)
    }
}

private fun parseMhzToKHz(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    return try {
        val mhz = trimmed.toDouble()
        (mhz * 1000).toLong()
    } catch (_: NumberFormatException) {
        null
    }
}
