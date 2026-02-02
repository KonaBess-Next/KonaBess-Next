package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.scanner.DtsScanResult
import com.ireddragonicy.konabessnext.model.ChipDefinition
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualChipsetSetupScreen(
    dtbIndex: Int,
    autoStartScan: Boolean = false, // New Flag
    onDeepScan: suspend () -> DtsScanResult,
    onSave: (ChipDefinition) -> Unit,
    onCancel: () -> Unit
) {
    var chipName by remember { mutableStateOf("Custom Snapdragon Device") }
    var strategy by remember { mutableStateOf("MULTI_BIN") }
    var maxLevels by remember { mutableStateOf("15") }
    var voltagePattern by remember { mutableStateOf("") }
    var ignoreVoltTable by remember { mutableStateOf(false) }
    
    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<DtsScanResult?>(null) }

    // Logic for running the scan
    val runScan = {
        isScanning = true
        // We run this in a LaunchedEffect usually, but here we just toggle state for UI
        // The actual computation should probably be async, but onDeepScan currently might be blocking or fast enough.
        // We'll simulate a small UI delay if it's instant to show feedback.
    }

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
        
        AnimatedVisibility(visible = scanResult != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Scan Confidence: ${scanResult?.confidence}",
                        style = MaterialTheme.typography.labelMedium
                    )
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
                        id = "custom_${System.currentTimeMillis()}",
                        name = chipName,
                        maxTableLevels = maxLevels.toIntOrNull() ?: 11,
                        ignoreVoltTable = ignoreVoltTable,
                        minLevelOffset = 1,
                        voltTablePattern = if (voltagePattern.isBlank()) null else voltagePattern,
                        strategyType = strategy,
                        levelCount = 480,
                        levels = mapOf(),
                        models = listOf("Custom")
                    )
                    onSave(def)
                }
            ) {
                Text("Save & Open")
            }
        }
    }
}