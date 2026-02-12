package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.utils.ChipStringHelper
import com.ireddragonicy.konabessnext.utils.DtsHelper
import com.ireddragonicy.konabessnext.model.Bin

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import com.ireddragonicy.konabessnext.viewmodel.UiState

@Composable
fun GpuBinList(
    state: UiState<List<Bin>>,
    chipDef: com.ireddragonicy.konabessnext.model.ChipDefinition?,
    activeBinIndex: Int = -1,
    runtimeGpuFrequencies: List<Long> = emptyList(),
    onBinClick: (Int) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit = {}
) {
    val context = LocalContext.current
    var showMultiBinHelp by remember { mutableStateOf(false) }
    val binsForHelp = (state as? UiState.Success)?.data.orEmpty()
    
    // Icons
    val iconBack = painterResource(R.drawable.ic_arrow_back)
    val iconHelp = painterResource(R.drawable.ic_help)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with Back button (like GpuLevelList)
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
                    Icon(iconBack, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_back))
                }
                IconButton(
                    onClick = { showMultiBinHelp = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium
                        )
                ) {
                    Icon(
                        painter = iconHelp,
                        contentDescription = stringResource(R.string.gpu_bin_help_icon_desc),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Content
        Crossfade(targetState = state, label = "GpuBinListState") { uiState ->
            when (uiState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).padding(bottom = 16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = uiState.message.asString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onReload) {
                                Text(stringResource(R.string.reload_data))
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    val bins = uiState.data
                    if (bins.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_gpu_tables_found))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
                        ) {
                            itemsIndexed(bins) { index, bin ->
                                val realBinId = extractRealBinId(bin)

                                val binName = remember(realBinId, context, chipDef) {
                                    try {
                                        ChipStringHelper.convertBins(realBinId, context, chipDef)
                                    } catch (e: Exception) {
                                        context.getString(R.string.unknown_table) + realBinId
                                    }
                                }

                                BinItemCard(
                                    name = binName,
                                    isActive = index == activeBinIndex,
                                    onClick = { onBinClick(index) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMultiBinHelp) {
        Dialog(onDismissRequest = { showMultiBinHelp = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.gpu_bin_help_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                    ) {
                        MarkdownText(
                            markdown = multiBinHelpMarkdown(
                                bins = binsForHelp,
                                activeBinIndex = activeBinIndex,
                                runtimeGpuFrequencies = runtimeGpuFrequencies
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showMultiBinHelp = false }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BinItemCard(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = if (isActive) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_frequency),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = stringResource(R.string.active_bin_badge),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun multiBinHelpMarkdown(
    bins: List<Bin>,
    activeBinIndex: Int,
    runtimeGpuFrequencies: List<Long>
): String {
    val nodeNames = bins
        .map { "qcom,gpu-pwrlevels-${extractRealBinId(it)}" }
        .distinct()
    val dtsNodePreview = buildList {
        add("`qcom,gpu-pwrlevel-bins {`")
        add("`compatible = \"qcom,gpu-pwrlevels-bins\";`")
        if (nodeNames.isEmpty()) {
            add("`// no parsed bins yet`")
        } else {
            nodeNames.take(4).forEach { add("`$it { ... }`") }
            if (nodeNames.size > 4) {
                add("`... +${nodeNames.size - 4} more bins`")
            }
        }
    }

    val activeBin = bins.getOrNull(activeBinIndex)
    val activeBinRealId = activeBin?.let(::extractRealBinId)
    val activeBinFrequencies = activeBin
        ?.levels
        ?.map { it.frequency }
        ?.filter { it > 0L }
        ?.distinct()
        ?.sortedDescending()
        .orEmpty()

    val runtimeSet = runtimeGpuFrequencies.toSet()
    val activeSet = activeBinFrequencies.toSet()
    val overlapCount = if (runtimeSet.isNotEmpty() && activeSet.isNotEmpty()) {
        runtimeSet.intersect(activeSet).size
    } else {
        0
    }
    val overlapSummary = if (runtimeSet.isEmpty() || activeSet.isEmpty()) {
        "n/a"
    } else {
        "$overlapCount/${runtimeSet.size}"
    }

    val runtimePreview = formatFrequencyPreview(runtimeGpuFrequencies)
    val activePreview = formatFrequencyPreview(activeBinFrequencies)
    val activeBinSummary = when {
        activeBin == null -> "Not detected yet"
        activeBinRealId != null -> "Index $activeBinIndex (Speed Bin $activeBinRealId)"
        else -> "Index $activeBinIndex"
    }
    val parsedBinsSummary = if (nodeNames.isEmpty()) "None" else nodeNames.joinToString(", ")

    return """
## Why Are There Multiple GPU Bins?
- Not every chip has identical silicon quality. Vendors keep multiple safe profiles in one DTS.
- Kernel picks one profile at boot (often tied to fuse/speed-bin data), so editing the wrong bin may have no effect.

## Live DTS Evidence (from your current table)
${dtsNodePreview.joinToString("\n") { "- $it" }}
- Parsed bin nodes now: `$parsedBinsSummary`

## Live Runtime Evidence (from sysfs)
- Runtime frequencies read now: `$runtimePreview`
- Active bin detected: `$activeBinSummary`
- Active bin frequencies: `$activePreview`
- Exact overlap runtime vs active bin: `$overlapSummary`
    """.trimIndent()
}

private fun formatFrequencyPreview(values: List<Long>, limit: Int = 8): String {
    if (values.isEmpty()) return "Unavailable (root/sysfs permission or path issue)"
    val preview = values.take(limit).joinToString(", ")
    return if (values.size > limit) "$preview, ... (${values.size} total)" else preview
}

private fun extractRealBinId(bin: Bin): Int {
    val speedBinLine = bin.header.find { it.contains("qcom,speed-bin") } ?: return bin.id
    val extracted = DtsHelper.extractLongValue(speedBinLine)
    return if (extracted != -1L) extracted.toInt() else bin.id
}