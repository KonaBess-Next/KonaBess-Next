package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.utils.ChipStringHelper
import com.ireddragonicy.konabessnext.utils.DtsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuParamEditor(
    level: Level,
    onBack: () -> Unit,
    onUpdateParam: (lineIndex: Int, encodedLine: String, historyMsg: String) -> Unit,
    onDeleteLevel: () -> Unit
) {
    val context = LocalContext.current
    
    com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
        // Parse lines into displayable items
        val params = remember(level) {
            level.lines.mapIndexed { index, line ->
                val decoded = DtsHelper.decode_hex_line(line)
                val name = decoded.name ?: ""
                val rawValue = decoded.value ?: ""
                val title = ChipStringHelper.convertLevelParams(name, context)
                
                // Format value for display
                val displayValue = try {
                    if (name == "qcom,gpu-freq") {
                        val hz = if (rawValue.startsWith("0x")) java.lang.Long.decode(rawValue) else rawValue.toLong()
                        com.ireddragonicy.konabessnext.ui.SettingsActivity.formatFrequency(hz, context)
                    } else if (name == "qcom,level" || name == "qcom,cx-level") {
                        val lvl = if (rawValue.startsWith("0x")) java.lang.Long.decode(rawValue) else rawValue.toLong()
                        val idx = com.ireddragonicy.konabessnext.core.editor.LevelOperations.levelint2int(lvl)
                        com.ireddragonicy.konabessnext.core.ChipInfo.rpmh_levels.level_str().getOrNull(idx) ?: rawValue
                    } else if (rawValue.startsWith("0x")) {
                        // Try to convert generic hex to nice decimal if short
                        val longVal = java.lang.Long.decode(rawValue)
                        if (longVal < 1000) longVal.toString() else rawValue
                    } else {
                        rawValue
                    }
                } catch (e: Exception) {
                    rawValue
                }
                
                ParamItem(index, name, rawValue, displayValue, title, line)
            }
        }

        var editingParam by remember { mutableStateOf<ParamItem?>(null) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        if (editingParam != null) {
            ModalBottomSheet(
                onDismissRequest = { editingParam = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                EditParamSheetContent(
                    param = editingParam!!,
                    onSave = { encodedLine, historyMsg ->
                        onUpdateParam(editingParam!!.index, encodedLine, historyMsg)
                        editingParam = null
                    },
                    onCancel = { editingParam = null }
                )
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Edit Level") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = onDeleteLevel) {
                             Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        ) { paddingValues ->
            // Separate "header" params (bus-min, bus-max, bus-freq) from the rest
            val headerKeys = setOf("qcom,bus-min", "qcom,bus-max", "qcom,bus-freq")
            val headerParams = params.filter { it.rawName in headerKeys }
            val otherParams = params.filterNot { it.rawName in headerKeys }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                // Top 3 Boxes Header Row
                if (headerParams.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        headerParams.forEach { item ->
                            HeaderParamBox(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = { editingParam = item }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                }

                // Remaining params in scrollable list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(otherParams) { _, item ->
                        ParamCard(
                            item = item,
                            onClick = { editingParam = item }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditParamSheetContent(
    param: ParamItem,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val isVoltage = param.rawName == "qcom,level" || param.rawName == "qcom,cx-level"
    val isFrequency = param.rawName == "qcom,gpu-freq"
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding() // Hande gesture bar
    ) {
        Text(
            text = "Edit ${param.title}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = ChipStringHelper.help(param.rawName, context),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(24.dp))

        if (isVoltage) {
            VoltageSelector(param, onSave)
        } else if (isFrequency) {
            FrequencyEditor(param, onSave)
        } else {
            GenericEditor(param, onSave)
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun VoltageSelector(param: ParamItem, onSave: (String, String) -> Unit) {
    // Voltage Logic
    val levels = com.ireddragonicy.konabessnext.core.ChipInfo.rpmh_levels?.level_str() ?: emptyArray()
    val values = com.ireddragonicy.konabessnext.core.ChipInfo.rpmh_levels?.levels() ?: intArrayOf()
    
    // Find initial index
    val initialIndex = try {
        com.ireddragonicy.konabessnext.core.editor.LevelOperations.levelint2int(param.rawValue.toLong())
    } catch (e: Exception) { 0 }
    
    LazyColumn(
        modifier = Modifier.heightIn(max = 300.dp) // Limit height
    ) {
        itemsIndexed(levels) { index, label ->
            val isSelected = index == initialIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) {
                         val value = if (index >= 0 && index < values.size) values[index] else 0
                         val newValue = value.toString()
                         val encoded = DtsHelper.encodeIntOrHexLine(param.rawName, newValue)
                         val historyMsg = "Updated Voltage to $label"
                         onSave(encoded, historyMsg)
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequencyEditor(param: ParamItem, onSave: (String, String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var unitIndex by remember { mutableStateOf(1) } // Default MHz
    var unitsExpanded by remember { mutableStateOf(false) }
    val units = listOf("Hz", "MHz", "GHz")
    
    // Init logic - handle hex values
    LaunchedEffect(Unit) {
        try {
            val hz = if (param.rawValue.startsWith("0x")) java.lang.Long.decode(param.rawValue) else param.rawValue.toLong()
            text = (hz / 1000000).toString() // Default to MHz display
        } catch (e: Exception) { text = param.rawValue }
    }
    
    // Output Preview
    val hzValue = remember(text, unitIndex) {
        com.ireddragonicy.konabessnext.core.editor.FrequencyDialogHelper.parseFrequencyToHz(text, unitIndex)
    }
    val previewText = if (hzValue > 0) "= ${String.format(java.util.Locale.US, "%,d", hzValue)} Hz" else "= ? Hz"

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Frequency") },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                singleLine = true
            )
            
            Spacer(Modifier.width(8.dp))
            
            ExposedDropdownMenuBox(
                expanded = unitsExpanded,
                onExpandedChange = { unitsExpanded = !unitsExpanded },
                modifier = Modifier.width(100.dp)
            ) {
                OutlinedTextField(
                    value = units[unitIndex],
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitsExpanded) },
                    modifier = Modifier.menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = unitsExpanded,
                    onDismissRequest = { unitsExpanded = false }
                ) {
                    units.forEachIndexed { index, unit ->
                        DropdownMenuItem(
                            text = { Text(unit) },
                            onClick = {
                                unitIndex = index
                                unitsExpanded = false
                            }
                        )
                    }
                }
            }
        }
        
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (hzValue > 0) {
                    val encoded = DtsHelper.encodeIntOrHexLine(param.rawName, hzValue.toString())
                    val label = com.ireddragonicy.konabessnext.ui.SettingsActivity.formatFrequency(hzValue, context)
                    onSave(encoded, "Updated Frequency to $label")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

@Composable
fun GenericEditor(param: ParamItem, onSave: (String, String) -> Unit) {
    // Parse hex to decimal for display
    val initialValue = try {
        if (param.rawValue.startsWith("0x")) {
            java.lang.Long.decode(param.rawValue).toString()
        } else {
            param.rawValue
        }
    } catch (e: Exception) { param.rawValue }
    
    var text by remember { mutableStateOf(initialValue) }
    val isHex = remember { DtsHelper.shouldUseHex(param.rawName) }
    
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Value") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = {
                val encoded = DtsHelper.encodeIntOrHexLine(param.rawName, text)
                onSave(encoded, "Updated ${param.title} to $text")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

data class ParamItem(
    val index: Int,
    val rawName: String,
    val rawValue: String,
    val displayValue: String,
    val title: String,
    val originalLine: String
)

@Composable
fun ParamCard(
    item: ParamItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Edit, // Generic icon for now
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.displayValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.rawName.isNotEmpty()) {
                    Text(
                        text = item.rawName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable

fun HeaderParamBox(
    item: ParamItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Choose icon based on param type
    val icon = when {
        item.rawName.contains("bus-min") -> androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.KeyboardArrowDown)
        item.rawName.contains("bus-max") -> androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.KeyboardArrowUp)
        item.rawName.contains("bus-freq") || item.rawName.contains("gpu-freq") -> painterResource(com.ireddragonicy.konabessnext.R.drawable.ic_bus_freq)
        else -> androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Edit)
    }
    
    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.displayValue,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1
            )
            Text(
                text = item.title.replace("qcom,", "").replace("-", " ").replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
