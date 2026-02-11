package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.scanner.DtsScanResult
import com.ireddragonicy.konabessnext.core.scanner.VoltageType
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.model.LevelPresets
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualChipsetSetupScreen(
    dtbIndex: Int,
    autoStartScan: Boolean = false,
    existingDefinition: ChipDefinition? = null,
    onDeepScan: suspend () -> DtsScanResult,
    onSave: (ChipDefinition) -> Unit,
    onCancel: () -> Unit
) {
    // Initialize form fields from existing definition if available, otherwise use defaults
    var chipName by remember { mutableStateOf(existingDefinition?.name ?: "Custom Snapdragon Device") }
    var strategy by remember { mutableStateOf(existingDefinition?.strategyType ?: "MULTI_BIN") }
    var maxLevels by remember { mutableStateOf((existingDefinition?.maxTableLevels ?: 15).toString()) }
    var voltagePattern by remember { mutableStateOf(existingDefinition?.voltTablePattern ?: "") }
    var ignoreVoltTable by remember { mutableStateOf(existingDefinition?.ignoreVoltTable ?: false) }
    
    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<DtsScanResult?>(null) }

    // Effect to run scan logic
    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(500) // Small delay for UX visibility
            val result = onDeepScan()
            
            // Apply results
            if (result.isValid || result.recommendedStrategy != "UNKNOWN") {
                strategy = result.recommendedStrategy
                maxLevels = result.maxLevels.toString()
                voltagePattern = result.voltageTablePattern ?: ""
                chipName = result.detectedModel ?: "Detected Device"
                ignoreVoltTable = result.voltageType != VoltageType.OPP_TABLE
                scanResult = result
            }
            isScanning = false
        }
    }

    // Trigger auto-start if requested
    LaunchedEffect(Unit) {
        if (autoStartScan) {
            isScanning = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_tune),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Manual Configuration",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "Configure parser settings for DTB $dtbIndex",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Smart Scan Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Detect",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (isScanning) "Scanning DTS structure..." else "Analyze file structure automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                IconButton(
                    onClick = { isScanning = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = "Deep Scan",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        
        // Smart Detection Results Card
        AnimatedVisibility(visible = scanResult != null) {
            val result = scanResult
            if (result != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header with confidence badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Detection Results",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = when (result.confidence) {
                                    "High" -> MaterialTheme.colorScheme.primary
                                    "Medium" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${result.confidence} Confidence",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // GPU Info
                        if (result.gpuModel != null || result.chipId != null) {
                            ScanResultRow("GPU", buildString {
                                if (result.gpuModel != null) append(result.gpuModel)
                                if (result.chipId != null) {
                                    if (result.gpuModel != null) append(" (${result.chipId})")
                                    else append(result.chipId)
                                }
                            })
                        }

                        // Strategy & Bins
                        ScanResultRow("Strategy", "${result.recommendedStrategy} (${result.binCount} bins)")
                        ScanResultRow("Levels", "${result.levelCount} per bin")

                        // Voltage Type
                        ScanResultRow("Voltage", when (result.voltageType) {
                            VoltageType.OPP_TABLE -> "OPP Table: ${result.voltageTablePattern}"
                            VoltageType.INLINE_LEVEL -> "Inline (qcom,level)"
                            VoltageType.NONE -> "None detected"
                        })

                        // GPU Node
                        if (result.gpuNodeName != null) {
                            ScanResultRow("GPU Node", result.gpuNodeName)
                        }

                        // Detected Properties
                        if (result.detectedProperties.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Detected Level Properties:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                result.detectedProperties.forEach { prop ->
                                    val isImportant = prop in listOf(
                                        "qcom,gpu-freq", "qcom,level", "qcom,bus-freq",
                                        "qcom,bus-min", "qcom,bus-max", "qcom,acd-level"
                                    )
                                    Surface(
                                        color = if (isImportant)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = prop,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Form Fields
        OutlinedTextField(
            value = chipName,
            onValueChange = { chipName = it },
            label = { Text("Device Name") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Structure Strategy", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = strategy == "MULTI_BIN", onClick = { strategy = "MULTI_BIN" })
            Text("Multi Bin", modifier = Modifier.padding(end = 16.dp))
            
            RadioButton(selected = strategy == "SINGLE_BIN", onClick = { strategy = "SINGLE_BIN" })
            Text("Single Bin")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = maxLevels,
            onValueChange = { if (it.all { c -> c.isDigit() }) maxLevels = it },
            label = { Text("Max Levels") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = voltagePattern,
            onValueChange = { voltagePattern = it },
            label = { Text("Voltage Table Pattern (Optional)") },
            placeholder = { Text("e.g. gpu-opp-table") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ignoreVoltTable, onCheckedChange = { ignoreVoltTable = it })
            Text("Ignore Voltage Table Editing")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val def = ChipDefinition(
                        id = existingDefinition?.id ?: "custom_${System.currentTimeMillis()}",
                        name = chipName,
                        maxTableLevels = maxLevels.toIntOrNull() ?: 11,
                        ignoreVoltTable = ignoreVoltTable,
                        minLevelOffset = existingDefinition?.minLevelOffset ?: 1,
                        voltTablePattern = if (voltagePattern.isBlank()) null else voltagePattern,
                        strategyType = strategy,
                        levelCount = existingDefinition?.levelCount ?: 480,
                        levels = existingDefinition?.levels ?: mapOf(),
                        levelPreset = existingDefinition?.levelPreset
                            ?: LevelPresets.inferPreset(existingDefinition?.models?.firstOrNull(), existingDefinition?.levelCount ?: 480),
                        binDescriptions = existingDefinition?.binDescriptions,
                        needsCaTargetOffset = existingDefinition?.needsCaTargetOffset ?: false,
                        models = existingDefinition?.models ?: listOf("Custom")
                    )
                    onSave(def)
                }
            ) {
                Text("Save & Open")
            }
        }
    }
}

@Composable
private fun ScanResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}