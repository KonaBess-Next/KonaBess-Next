package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    lastExportedResult: String? = null,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    var sheetType by remember { mutableStateOf(BottomSheetType.NONE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textInput by remember { mutableStateOf("") }

    LaunchedEffect(lastExportedResult) {
        if (lastExportedResult != null && showSheet && sheetType == BottomSheetType.EXPORT_CLIPBOARD) {
            textInput = lastExportedResult
            sheetType = BottomSheetType.EXPORT_RESULT
        }
    }

    val actionItems = remember(isPrepared) {
        listOf(
            ActionItem(R.drawable.ic_history, "Export History", "View all previous exports", true),
            ActionItem(R.drawable.ic_import_modern, "Import from File", "Load configuration from a file", isPrepared),
            ActionItem(R.drawable.ic_save, "Export to File", "Save configuration to a file", isPrepared),
            ActionItem(R.drawable.ic_clipboard_import, "Import from Clipboard", "Paste configuration from clipboard", isPrepared),
            ActionItem(R.drawable.ic_clipboard_export, "Export to Clipboard", "Copy configuration to clipboard", isPrepared),
            ActionItem(R.drawable.ic_code, "Export Raw DTS", "Save raw device tree source", isPrepared),
            ActionItem(R.drawable.ic_backup, "Backup Boot Image", "Create a backup of your boot image", isPrepared)
        )
    }

    com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showSheet = false 
                    sheetType = BottomSheetType.NONE
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
                        BottomSheetType.EXPORT_RESULT -> R.string.text_copied_to_clipboard
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
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = textInput,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 40.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textInput))
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_content_copy),
                                            contentDescription = "Copy Again",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
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
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(hintRes)) },
                                shape = RoundedCornerShape(12.dp)
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(actionItems) { index, item ->
                    ActionCard(
                        icon = painterResource(item.iconRes),
                        title = item.title,
                        description = item.description,
                        enabled = item.enabled,
                        onClick = {
                            when (index) {
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
                            }
                        }
                    )
                }
                
                // Bottom spacer for navigation bar
                item {
                    Spacer(Modifier.height(88.dp))
                }
            }
        }
    }
}

@Composable
fun ActionCard(
    icon: Painter,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}
