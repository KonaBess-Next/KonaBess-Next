package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.viewmodel.DtsFileInfo
import java.text.DecimalFormat

/**
 * Export format options for DTS files.
 */
enum class DtsExportFormat(val label: String) {
    INDIVIDUAL("Individual Files"),
    ZIP("ZIP Archive")
}

/**
 * Simple flat Material You DTS file export bottom sheet.
 * Each DTS is displayed as a clean card with metadata + export action.
 * Supports choosing between individual file export or ZIP archive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportRawDtsSheet(
    dtsFiles: List<DtsFileInfo>,
    deviceModel: String,
    deviceBrand: String,
    onExportSingle: (DtsFileInfo) -> Unit,
    onExportAll: () -> Unit,
    onExportAllAsZip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFormat by remember { mutableStateOf(DtsExportFormat.INDIVIDUAL) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(8.dp))
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // ─── Header ─────────────────────────────────────────────
            DtsSheetHeader(
                dtsCount = dtsFiles.size,
                deviceModel = deviceModel,
                deviceBrand = deviceBrand
            )

            Spacer(Modifier.height(12.dp))

            // ─── Format Picker (only for multiple files) ────────────
            if (dtsFiles.size > 1) {
                FormatPicker(
                    selected = selectedFormat,
                    onSelect = { selectedFormat = it }
                )
                Spacer(Modifier.height(8.dp))
            }

            // ─── DTS Cards List ─────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(dtsFiles, key = { _, info -> info.index }) { idx, dtsInfo ->
                    DtsFileCard(
                        dtsFileInfo = dtsInfo,
                        cardIndex = idx,
                        onExport = { onExportSingle(dtsInfo) }
                    )
                }

                item { Spacer(Modifier.height(4.dp)) }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Bottom Actions ─────────────────────────────────────
            BottomActions(
                fileCount = dtsFiles.size,
                selectedFormat = selectedFormat,
                onDismiss = onDismiss,
                onExportAll = onExportAll,
                onExportAllAsZip = onExportAllAsZip
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Header — Flat, no gradient
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DtsSheetHeader(
    dtsCount: Int,
    deviceModel: String,
    deviceBrand: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Flat icon container — no gradient
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_code),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Export Raw DTS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (deviceBrand.isNotEmpty() || deviceModel.isNotEmpty()) {
                    Text(
                        text = "$deviceBrand $deviceModel".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Count badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "$dtsCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

// ═══════════════════════════════════════════════════════════════
// Format Picker — ZIP or Individual
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FormatPicker(
    selected: DtsExportFormat,
    onSelect: (DtsExportFormat) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Export Format",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DtsExportFormat.entries.forEach { format ->
                val isSelected = selected == format
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(format) },
                    label = {
                        Text(
                            text = format.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (format) {
                                DtsExportFormat.INDIVIDUAL -> Icons.Rounded.InsertDriveFile
                                DtsExportFormat.ZIP -> Icons.Rounded.FolderZip
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// DTS File Card — Simple flat Material You
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DtsFileCard(
    dtsFileInfo: DtsFileInfo,
    cardIndex: Int,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index badge — flat, uniform color
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "${cardIndex + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dtsFileInfo.chipName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (dtsFileInfo.isActive) {
                        Spacer(Modifier.width(6.dp))
                        StatusChip(
                            text = "Active",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (dtsFileInfo.isCurrentlySelected) {
                        Spacer(Modifier.width(4.dp))
                        StatusChip(
                            text = "Selected",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                if (dtsFileInfo.modelName.isNotEmpty()) {
                    Text(
                        text = dtsFileInfo.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Metadata row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetaLabel(Icons.Rounded.Description, formatFileSize(dtsFileInfo.fileSizeBytes))
                    MetaLabel(Icons.Rounded.DataArray, "${formatNumber(dtsFileInfo.lineCount)} lines")
                    MetaLabel(
                        Icons.Rounded.Memory,
                        if (dtsFileInfo.index >= 0) "DTB ${dtsFileInfo.index}" else "Import"
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Export button — flat, uniform color
            FilledTonalIconButton(
                onClick = onExport,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = "Export",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Bottom Actions
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BottomActions(
    fileCount: Int,
    selectedFormat: DtsExportFormat,
    onDismiss: () -> Unit,
    onExportAll: () -> Unit,
    onExportAllAsZip: () -> Unit
) {
    if (fileCount > 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = {
                    when (selectedFormat) {
                        DtsExportFormat.INDIVIDUAL -> onExportAll()
                        DtsExportFormat.ZIP -> onExportAllAsZip()
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = when (selectedFormat) {
                        DtsExportFormat.INDIVIDUAL -> Icons.Rounded.SaveAlt
                        DtsExportFormat.ZIP -> Icons.Rounded.FolderZip
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (selectedFormat) {
                        DtsExportFormat.INDIVIDUAL -> "Export All"
                        DtsExportFormat.ZIP -> "Export ZIP"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    } else {
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Text("Close", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Small Components
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StatusChip(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetaLabel(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${DecimalFormat("#.#").format(bytes / 1024.0)} KB"
    else -> "${DecimalFormat("#.##").format(bytes / (1024.0 * 1024.0))} MB"
}

private fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> "${DecimalFormat("#.#").format(n / 1_000_000.0)}M"
    n >= 1_000 -> "${DecimalFormat("#.#").format(n / 1_000.0)}K"
    else -> n.toString()
}
