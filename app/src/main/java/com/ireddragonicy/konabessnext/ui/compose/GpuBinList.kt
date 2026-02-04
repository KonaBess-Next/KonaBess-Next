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

@Composable
fun GpuBinList(
    bins: List<Bin>,
    chipDef: com.ireddragonicy.konabessnext.model.ChipDefinition?,
    isLoading: Boolean, // Added to track actual state
    onBinClick: (Int) -> Unit,
    onReload: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // Fix double padding
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (bins.isEmpty()) {
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
                            text = stringResource(R.string.no_gpu_tables_found),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onReload) {
                            Text(stringResource(R.string.reload_data))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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