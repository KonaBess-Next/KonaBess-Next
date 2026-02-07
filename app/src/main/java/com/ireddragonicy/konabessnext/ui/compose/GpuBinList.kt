package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.zIndex
import com.ireddragonicy.konabessnext.viewmodel.UiState

@Composable
fun GpuBinList(
    state: UiState<List<Bin>>,
    chipDef: com.ireddragonicy.konabessnext.model.ChipDefinition?,
    onBinClick: (Int) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Icons
    val iconBack = painterResource(R.drawable.ic_arrow_back)

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
                    modifier = Modifier.fillMaxWidth(),
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
                                // Try to extract the real speed-bin ID from the header
                                val speedBinLine = bin.header.find { it.contains("qcom,speed-bin") }
                                val realBinId = if (speedBinLine != null) {
                                    val extracted = DtsHelper.extractLongValue(speedBinLine)
                                    if (extracted != -1L) extracted.toInt() else bin.id
                                } else {
                                    bin.id
                                }

                                val binName = remember(realBinId, context, chipDef) {
                                    try {
                                        ChipStringHelper.convertBins(realBinId, context, chipDef)
                                    } catch (e: Exception) {
                                        context.getString(R.string.unknown_table) + realBinId
                                    }
                                }

                                BinItemCard(
                                    name = binName,
                                    onClick = { onBinClick(index) }
                                )
                            }
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
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
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
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}