package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R

data class ActionItem(
    val iconRes: Int,
    val title: String,
    val description: String,
    val enabled: Boolean = true
)

enum class BottomSheetType {
    NONE, EXPORT_FILE, IMPORT_CLIPBOARD, EXPORT_CLIPBOARD, EXPORT_RESULT
}

sealed class ImportExportListItem {
    data class Header(val title: String) : ImportExportListItem()
    data class Action(val item: ActionItem, val index: Int) : ImportExportListItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    isPrepared: Boolean,
    onExportHistory: () -> Unit,
    onImportFromFile: () -> Unit,
    onExportToFile: (String) -> Unit,
    onImportFromClipboard: (String) -> Unit,
    onExportToClipboard: (String) -> Unit,
    onExportRawDts: () -> Unit,
    onBackupBootImage: () -> Unit,
    onBatchDtbToDts: () -> Unit,
    onDismissResult: () -> Unit,
    onOpenFile: (String) -> Unit,
    lastExportedResult: String? = null,
    importPreview: com.ireddragonicy.konabessnext.viewmodel.ImportPreview? = null,
    onConfirmImport: () -> Unit = {},
    onCancelImport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    var sheetType by remember { mutableStateOf(BottomSheetType.NONE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textInput by remember { mutableStateOf("") }

    if (importPreview != null) {
        val expandedStates = remember(importPreview) { mutableStateMapOf<Int, Boolean>() }
        
        AlertDialog(
            onDismissRequest = onCancelImport,
            icon = { Icon(painter = painterResource(R.drawable.ic_save), contentDescription = null) },
            title = { Text("Confirm Import", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header Info
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Chip: ${importPreview.chip}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (importPreview.description.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = importPreview.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Bin Details
                    Text(
                        text = "Configuration Details",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp), // Increased height for expansion
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(importPreview.bins) { bin ->
                            val isExpanded = expandedStates[bin.binId] ?: false
                            
                            OutlinedCard(
                                onClick = { expandedStates[bin.binId] = !isExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Speed Bin ${bin.binId}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Badge {
                                                Text("${bin.frequencies.size} Levels")
                                            }
                                            Icon(
                                                painter = painterResource(if (isExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
                                                contentDescription = null,
                                                modifier = Modifier.padding(start = 8.dp).size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    Spacer(Modifier.height(8.dp))
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_frequency),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "${bin.minFreqMhz} MHz - ${bin.maxFreqMhz} MHz",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    if (bin.voltageCount > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_flash),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Voltage Control Enabled",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                    
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(modifier = Modifier.padding(top = 12.dp)) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Spacer(Modifier.height(8.dp))
                                            
                                            // Table Header
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "FREQUENCY",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Text(
                                                    text = "VOLTAGE / LEVEL",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            
                                            Spacer(Modifier.height(8.dp))
                                            
                                            // List Items
                                            bin.levels.forEach { level ->
                                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${level.freqMhz} MHz",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = level.voltageLabel.ifEmpty { "-" },
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    
                                                    // Bus Info Row (if available)
                                                    if (level.busMin != null || level.busMax != null || level.busFreq != null) {
                                                        Spacer(Modifier.height(2.dp))
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                        ) {
                                                            if (level.busMin != null || level.busMax != null) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        painter = painterResource(R.drawable.ic_bus),
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(12.dp),
                                                                        tint = MaterialTheme.colorScheme.outline
                                                                    )
                                                                    Spacer(Modifier.width(4.dp))
                                                                    Text(
                                                                        text = "${level.busMin ?: "?"}-${level.busMax ?: "?"}",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.outline
                                                                    )
                                                                }
                                                            }
                                                            
                                                            if (level.busFreq != null) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        painter = painterResource(R.drawable.ic_bus_freq),
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(12.dp),
                                                                        tint = MaterialTheme.colorScheme.outline
                                                                    )
                                                                    Spacer(Modifier.width(4.dp))
                                                                    Text(
                                                                        text = "${level.busFreq}",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.outline
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (importPreview.bins.isEmpty()) {
                            item {
                                Text(
                                    text = "No valid frequency tables found in this config.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        if (importPreview.legacyVoltCount > 0) {
                             item {
                                 Text(
                                     text = "+ Legacy Voltage Table (${importPreview.legacyVoltCount} entries)",
                                     style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.outline
                                 )
                             }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onConfirmImport) {
                    Text("Import Config")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelImport) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(lastExportedResult) {
        if (!lastExportedResult.isNullOrEmpty()) {
            textInput = lastExportedResult
            sheetType = BottomSheetType.EXPORT_RESULT
            showSheet = true
        }
    }

    // Build grouped list items
    val listItems = remember(isPrepared) {
        buildList {
            // History section
            add(ImportExportListItem.Header("History"))
            add(ImportExportListItem.Action(ActionItem(R.drawable.ic_history, "Export History", "View and export edit history", true), 0))

            // File Operations section
            add(ImportExportListItem.Header("File Operations"))
            add(ImportExportListItem.Action(ActionItem(R.drawable.ic_import_modern, "Import from File", "Load GPU configuration from file", isPrepared), 1))
            add(ImportExportListItem.Action(ActionItem(R.drawable.ic_save, "Export to File", "Save GPU configuration to file", isPrepared), 2))

            // Clipboard section
            add(ImportExportListItem.Header("Clipboard"))
            add(ImportExportListItem.Action(ActionItem(R.drawable.ic_clipboard_import, "Import from Clipboard", "Paste configuration from clipboard", isPrepared), 3))
            add(ImportExportListItem.Action(ActionItem(R.drawable.ic_clipboard_export, "Export to Clipboard", "Copy configuration to clipboard", isPrepared), 4))

            // Advanced section
            add(ImportExportListItem.Header("Advanced"))
            add(ImportExportListItem.Action(ActionItem(R.drawable.ic_code, "Export Raw DTS", "Export device tree source", isPrepared), 5))
            add(ImportExportListItem.Action(ActionItem(R.drawable.ic_backup, "Backup Boot Image", "Create boot image backup", isPrepared), 6))
            add(ImportExportListItem.Action(ActionItem(R.drawable.ic_code, "Batch DTB to DTS", "Convert multiple DTB files", true), 7))
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showSheet = false 
                sheetType = BottomSheetType.NONE
                onDismissResult()
            },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val titleRes = when (sheetType) {
                    BottomSheetType.EXPORT_FILE -> R.string.export_to_file
                    BottomSheetType.IMPORT_CLIPBOARD -> R.string.import_from_clipboard
                    BottomSheetType.EXPORT_CLIPBOARD -> R.string.export_to_clipboard
                    BottomSheetType.EXPORT_RESULT -> R.string.success
                    else -> 0
                }
                
                val hintRes = when (sheetType) {
                    BottomSheetType.EXPORT_FILE, BottomSheetType.EXPORT_CLIPBOARD -> R.string.export_data_msg
                    BottomSheetType.IMPORT_CLIPBOARD -> R.string.paste_here
                    else -> 0
                }

                if (titleRes != 0) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    if (sheetType == BottomSheetType.EXPORT_RESULT) {
                        val isBatchResult = textInput.contains(".dts")
                        val resultLines = remember(textInput) { textInput.trim().split("\n").filter { it.isNotBlank() } }

                        if (isBatchResult) {
                            Text(
                                text = stringResource(R.string.converted_files),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            @Suppress("DEPRECATION")
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            Box(modifier = Modifier.padding(16.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    resultLines.forEach { line ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = if (isBatchResult) line.substringAfterLast("/") else line,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            IconButton(
                                                onClick = {
                                                    if (isBatchResult) {
                                                        onOpenFile(line)
                                                    } else {
                                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(line))
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(if (isBatchResult) R.drawable.ic_folder else R.drawable.ic_content_copy),
                                                    contentDescription = if (isBatchResult) stringResource(R.string.open_file) else stringResource(R.string.copy),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                showSheet = false
                                sheetType = BottomSheetType.NONE
                                textInput = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.close))
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                            label = { Text(stringResource(hintRes)) },
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (sheetType == BottomSheetType.IMPORT_CLIPBOARD) {
                                    IconButton(
                                        onClick = {
                                            val clipText = clipboardManager.getText()
                                            if (clipText != null) {
                                                textInput = clipText.text
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_clipboard_import),
                                            contentDescription = "Paste from Clipboard",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                when (sheetType) {
                                    BottomSheetType.EXPORT_FILE -> {
                                        onExportToFile(textInput)
                                        showSheet = false
                                        sheetType = BottomSheetType.NONE
                                        textInput = ""
                                    }
                                    BottomSheetType.IMPORT_CLIPBOARD -> {
                                        onImportFromClipboard(textInput)
                                        showSheet = false
                                        sheetType = BottomSheetType.NONE
                                        textInput = ""
                                    }
                                    BottomSheetType.EXPORT_CLIPBOARD -> {
                                        onExportToClipboard(textInput)
                                    }
                                    else -> {}
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = textInput.isNotEmpty()
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(listItems) { listItem ->
                when (listItem) {
                    is ImportExportListItem.Header -> {
                        SectionHeader(title = listItem.title)
                    }
                    is ImportExportListItem.Action -> {
                        ActionListItem(
                            icon = painterResource(listItem.item.iconRes),
                            title = listItem.item.title,
                            description = listItem.item.description,
                            enabled = listItem.item.enabled,
                            onClick = {
                                when (listItem.index) {
                                    0 -> onExportHistory()
                                    1 -> onImportFromFile()
                                    2 -> {
                                        sheetType = BottomSheetType.EXPORT_FILE
                                        textInput = ""
                                        showSheet = true
                                    }
                                    3 -> {
                                        sheetType = BottomSheetType.IMPORT_CLIPBOARD
                                        textInput = ""
                                        showSheet = true
                                    }
                                    4 -> {
                                        sheetType = BottomSheetType.EXPORT_CLIPBOARD
                                        textInput = ""
                                        showSheet = true
                                    }
                                    5 -> onExportRawDts()
                                    6 -> onBackupBootImage()
                                    7 -> onBatchDtbToDts()
                                }
                            }
                        )
                    }
                }
            }
            
            // Bottom spacer for navigation bar
            item {
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@Composable
private fun ActionListItem(
    icon: Painter,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}
