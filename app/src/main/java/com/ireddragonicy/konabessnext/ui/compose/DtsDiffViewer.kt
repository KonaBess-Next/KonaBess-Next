package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.utils.BinDiffResult
import com.ireddragonicy.konabessnext.utils.DiffNode
import com.ireddragonicy.konabessnext.utils.DiffType

enum class DiffCommitAction {
    SAVE,
    EXPORT_IMAGE,
    FLASH_DEVICE,
    INSTALL_INACTIVE_SLOT;

    fun confirmText(): String {
        return when (this) {
            SAVE -> "Confirm Save"
            EXPORT_IMAGE -> "Confirm Export"
            FLASH_DEVICE -> "Confirm Flash"
            INSTALL_INACTIVE_SLOT -> "Confirm Install"
        }
    }
}

private enum class DiffViewMode {
    GUI,
    DTS_FORMAT
}

private data class DiffSplit(
    val guiResults: List<BinDiffResult>,
    val dtsFormatResults: List<BinDiffResult>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtsDiffViewer(
    action: DiffCommitAction,
    diffResults: List<BinDiffResult>,
    isLoading: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleResults = remember(diffResults) {
        diffResults.mapNotNull { result ->
            val visibleChanges = result.changes.filter { it.type != DiffType.UNCHANGED }
            if (visibleChanges.isEmpty()) {
                null
            } else {
                result.copy(changes = visibleChanges)
            }
        }
    }

    val split = remember(visibleResults) { splitDiffResults(visibleResults) }
    var viewMode by rememberSaveable { mutableStateOf(DiffViewMode.GUI) }
    val effectiveMode = when {
        viewMode == DiffViewMode.GUI && split.guiResults.isEmpty() && split.dtsFormatResults.isNotEmpty() -> DiffViewMode.DTS_FORMAT
        viewMode == DiffViewMode.DTS_FORMAT && split.dtsFormatResults.isEmpty() && split.guiResults.isNotEmpty() -> DiffViewMode.GUI
        else -> viewMode
    }
    val activeResults = if (effectiveMode == DiffViewMode.GUI) split.guiResults else split.dtsFormatResults

    val totalChanges = remember(activeResults) { activeResults.sumOf { it.changes.size } }
    val addedCount = remember(activeResults) { activeResults.sumOf { result -> result.changes.count { it.type == DiffType.ADDED } } }
    val removedCount = remember(activeResults) { activeResults.sumOf { result -> result.changes.count { it.type == DiffType.REMOVED } } }
    val modifiedCount = remember(activeResults) { activeResults.sumOf { result -> result.changes.count { it.type == DiffType.MODIFIED } } }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = "Diff overview",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Review DTS Changes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isLoading) {
                            "Calculating differences..."
                        } else {
                            if (effectiveMode == DiffViewMode.GUI) {
                                "$totalChanges GUI changes across ${activeResults.size} sections"
                            } else {
                                "$totalChanges DTS format changes"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Spacer(Modifier.height(12.dp))

            if (!isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = effectiveMode == DiffViewMode.GUI,
                        onClick = { viewMode = DiffViewMode.GUI },
                        enabled = split.guiResults.isNotEmpty(),
                        label = { Text("GUI Diff") }
                    )
                    FilterChip(
                        selected = effectiveMode == DiffViewMode.DTS_FORMAT,
                        onClick = { viewMode = DiffViewMode.DTS_FORMAT },
                        enabled = split.dtsFormatResults.isNotEmpty(),
                        label = { Text("DTS Format Diff") }
                    )
                }
                Spacer(Modifier.height(10.dp))

                DiffStatsRow(
                    addedCount = addedCount,
                    removedCount = removedCount,
                    modifiedCount = modifiedCount
                )
                Spacer(Modifier.height(10.dp))
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                activeResults.isEmpty() -> {
                    EmptyDiffState(
                        modifier = Modifier.weight(1f),
                        subtitle = if (effectiveMode == DiffViewMode.GUI) {
                            "No GUI-level changes detected."
                        } else {
                            "No DTS format-level changes detected."
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = activeResults,
                            key = { result -> "${effectiveMode.name}-${result.binId}-${result.binIndex}" }
                        ) { binResult ->
                            BinDiffSection(result = binResult, viewMode = effectiveMode)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(action.confirmText())
                }
            }
        }
    }
}

@Composable
private fun DiffStatsRow(
    addedCount: Int,
    removedCount: Int,
    modifiedCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DiffCountChip(label = "Added", count = addedCount, color = Color(0xFF2E7D32), container = Color(0x1A4CAF50))
        DiffCountChip(label = "Removed", count = removedCount, color = Color(0xFFC62828), container = Color(0x1AF44336))
        DiffCountChip(label = "Modified", count = modifiedCount, color = Color(0xFFEF6C00), container = Color(0x1AFFC107))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DiffCountChip(
    label: String,
    count: Int,
    color: Color,
    container: Color
) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(10.dp),
        color = container
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BinDiffSection(result: BinDiffResult, viewMode: DiffViewMode) {
    val isGeneralSection = result.binId < 0
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (isGeneralSection) {
                if (viewMode == DiffViewMode.GUI) "General GUI Changes" else "DTS Format Changes"
            } else {
                "Bin ${result.binId}"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        result.changes.forEach { change ->
            DiffNodeRow(change = change, isGeneralSection = isGeneralSection)
        }
    }
}

@Composable
private fun DiffNodeRow(change: DiffNode, isGeneralSection: Boolean) {
    val rowBackground = when (change.type) {
        DiffType.ADDED -> Color(0x1A4CAF50)
        DiffType.REMOVED -> Color(0x1AF44336)
        DiffType.MODIFIED -> Color(0x1AFFC107)
        DiffType.UNCHANGED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    }
    val accentColor = when (change.type) {
        DiffType.ADDED -> Color(0xFF2E7D32)
        DiffType.REMOVED -> Color(0xFFC62828)
        DiffType.MODIFIED -> Color(0xFFEF6C00)
        DiffType.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (change.type) {
        DiffType.ADDED -> Icons.Rounded.PlayArrow
        DiffType.REMOVED -> Icons.Rounded.DeleteOutline
        DiffType.MODIFIED -> Icons.Rounded.Edit
        DiffType.UNCHANGED -> Icons.Rounded.CheckCircleOutline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = when (change.type) {
                    DiffType.ADDED -> "Added level ${change.levelIndex}: ${change.newDescription.orEmpty()}"
                    DiffType.REMOVED -> "Removed level ${change.levelIndex}: ${change.oldDescription.orEmpty()}"
                    DiffType.MODIFIED -> "Modified level ${change.levelIndex}: ${change.oldDescription.orEmpty()} to ${change.newDescription.orEmpty()}"
                    DiffType.UNCHANGED -> "Unchanged level ${change.levelIndex}"
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = rowBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.padding(top = 2.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isGeneralSection) {
                        if (change.levelIndex > 0) "Line ${change.levelIndex}" else "Global"
                    } else {
                        "Level ${change.levelIndex}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )

                when (change.type) {
                    DiffType.MODIFIED -> {
                        Text(
                            text = "Before: ${change.oldDescription.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "After: ${change.newDescription.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DiffType.ADDED -> {
                        Text(
                            text = change.newDescription.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DiffType.REMOVED -> {
                        Text(
                            text = change.oldDescription.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                    DiffType.UNCHANGED -> {
                        Text(
                            text = change.newDescription.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDiffState(
    modifier: Modifier = Modifier,
    subtitle: String = "Current bins match the original snapshot."
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircleOutline,
                contentDescription = "No changes detected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "No changes detected",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun splitDiffResults(results: List<BinDiffResult>): DiffSplit {
    if (results.isEmpty()) return DiffSplit(emptyList(), emptyList())

    val gui = ArrayList<BinDiffResult>()
    val dts = ArrayList<BinDiffResult>()

    results.forEach { result ->
        if (result.binId < 0) {
            val guiChanges = result.changes.filter { it.levelIndex <= 0 }
            val dtsChanges = result.changes.filter { it.levelIndex > 0 }
            if (guiChanges.isNotEmpty()) {
                gui.add(result.copy(changes = guiChanges))
            }
            if (dtsChanges.isNotEmpty()) {
                dts.add(result.copy(changes = dtsChanges))
            }
        } else {
            gui.add(result)
        }
    }

    return DiffSplit(guiResults = gui, dtsFormatResults = dts)
}
