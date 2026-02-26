package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.display.TouchPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchOverclockScreen(
    touchPanels: List<TouchPanel>,
    onBack: () -> Unit,
    onSaveFrequency: (String, Int, Long) -> Unit
) {
    var selectedPanel by remember { mutableStateOf<TouchPanel?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            modifier = Modifier.zIndex(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBack,
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

        if (touchPanels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No compatible touch devices found in DTBO.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(touchPanels) { panel ->
                    Card(
                        onClick = {
                            selectedPanel = panel
                            showEditDialog = true
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Node: ${panel.nodeName} (Fragment ${panel.fragmentIndex})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Compatible: ${panel.compatible}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "SPI Max Frequency: ${panel.spiMaxFrequency} Hz",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && selectedPanel != null) {
        var inputFreq by remember { mutableStateOf(selectedPanel!!.spiMaxFrequency.toString()) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit SPI Max Frequency") },
            text = {
                OutlinedTextField(
                    value = inputFreq,
                    onValueChange = { inputFreq = it },
                    label = { Text("Frequency (Hz)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    parseLongSafe(inputFreq)?.let { newFreq ->
                        onSaveFrequency(selectedPanel!!.nodeName, selectedPanel!!.fragmentIndex, newFreq)
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

private fun parseLongSafe(input: String): Long? {
    val trimmed = input.trim()
    return try {
        if (trimmed.startsWith("0x", ignoreCase = true)) {
            java.lang.Long.decode(trimmed)
        } else {
            trimmed.toLongOrNull()
        }
    } catch (e: Exception) {
        null
    }
}
