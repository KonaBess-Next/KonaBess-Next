package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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

@Composable
fun ImportExportScreen(
    isPrepared: Boolean,
    onExportHistory: () -> Unit,
    onImportFromFile: () -> Unit,
    onExportToFile: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onExportToClipboard: () -> Unit,
    onExportRawDts: () -> Unit,
    onBackupBootImage: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                                2 -> onExportToFile()
                                3 -> onImportFromClipboard()
                                4 -> onExportToClipboard()
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
