package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.Dtb

/**
 * Defines the types of sheets available in the GPU Workbench.
 */
enum class WorkbenchSheetType {
    NONE, HISTORY, CHIPSET
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuWorkbenchSheets(
    sheetType: WorkbenchSheetType,
    onDismiss: () -> Unit,
    // History Data
    history: List<String>,
    // Chipset Data
    dtbs: List<Dtb>,
    selectedDtbId: Int?,
    activeDtbId: Int,
    onChipsetSelect: (Dtb) -> Unit,
    onConfigureManual: (Int) -> Unit,
    onDeleteDts: (Int) -> Unit,
    onImportDts: (android.net.Uri) -> Unit
) {
    if (sheetType == WorkbenchSheetType.NONE) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            when (sheetType) {
                WorkbenchSheetType.HISTORY -> HistorySheetContent(history)
                WorkbenchSheetType.CHIPSET -> ChipsetSelectorContent(
                    dtbs = dtbs,
                    selectedDtbId = selectedDtbId,
                    activeDtbId = activeDtbId,
                    onSelect = onChipsetSelect,
                    onConfigure = onConfigureManual,
                    onDelete = onDeleteDts,
                    onImport = onImportDts
                )
            }
        }
    }
}

@Composable
private fun HistorySheetContent(history: List<String>) {
    Text(
        text = stringResource(R.string.history_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_recent_edits), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history) { editItem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = editItem,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipsetSelectorContent(
    dtbs: List<Dtb>,
    selectedDtbId: Int?,
    activeDtbId: Int,
    onSelect: (Dtb) -> Unit,
    onConfigure: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onImport: (android.net.Uri) -> Unit
) {
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImport(uri)
    }

    Text(
        text = stringResource(R.string.select_dts_index),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dtbs) { dtb ->
            val isOfficial = !dtb.type.id.startsWith("custom") && !dtb.type.id.startsWith("unsupported")
            val isActive = dtb.id >= 0 && dtb.id == activeDtbId
            val isSelected = dtb.id == selectedDtbId

            Card(
                onClick = { onSelect(dtb) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                   else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.dts_index_format, dtb.id), style = MaterialTheme.typography.labelMedium)
                            
                            if (isActive) {
                                Spacer(Modifier.width(8.dp))
                                ActiveBadge()
                            }

                            if (isOfficial) {
                                Spacer(Modifier.width(8.dp))
                                OfficialBadge()
                            }
                        }
                        Text(
                            text = dtb.type.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    
                    Row {
                        IconButton(onClick = { onConfigure(dtb.id) }) {
                            Icon(
                                Icons.Default.Build, 
                                contentDescription = stringResource(R.string.configure), 
                                tint = if (isOfficial) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (dtb.id < 0) {
                            IconButton(onClick = { onDelete(dtb.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_dts))
            }
        }
    }
}

@Composable
private fun ActiveBadge() {
    Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(12.dp), tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text(
                "ACTIVE", 
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), 
                color = Color.White
            )
        }
    }
}

@Composable
private fun OfficialBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiary,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onTertiary)
            Spacer(Modifier.width(2.dp))
            Text(
                "OFFICIAL", 
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), 
                color = MaterialTheme.colorScheme.onTertiary
            )
        }
    }
}