package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.utils.ChipStringHelper
import com.ireddragonicy.konabessnext.utils.DtsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuParamEditor(
    level: Level,
    levelStrings: Array<String>,
    levelValues: IntArray,
    ignoreVoltTable: Boolean,
    oppVoltage: Long? = null, // OPP-derived voltage for devices without qcom,level
    levelFrequency: Long = 0L, // The frequency of this level (for OPP matching)
    onBack: () -> Unit,
    onUpdateParam: (lineIndex: Int, encodedLine: String, historyMsg: String) -> Unit,
    onUpdateOppVoltage: ((newVolt: Long) -> Unit)? = null, // Callback for OPP voltage updates
    onDeleteLevel: () -> Unit
) {
    val context = LocalContext.current
    
    // Parse lines into displayable items
    val params = remember(level) {
        level.lines.mapIndexed { index, line ->
            val decoded = DtsHelper.decode_hex_line(line)
            val name = decoded.name
            val rawValue = decoded.value
            val title = ChipStringHelper.convertLevelParams(name, context)
            
            // Format value for display
            val displayValue = try {
                if (name == "qcom,gpu-freq") {
                    val hz = if (rawValue.startsWith("0x")) java.lang.Long.decode(rawValue) else rawValue.toLong()
                    com.ireddragonicy.konabessnext.utils.FrequencyFormatter.format(context, hz)
                } else if (name == "qcom,level" || name == "qcom,cx-level") {
                    val lvl = if (rawValue.startsWith("0x")) java.lang.Long.decode(rawValue) else rawValue.toLong()
                    val idx = com.ireddragonicy.konabessnext.core.editor.LevelOperations.levelint2int(lvl, levelValues)
                    levelStrings.getOrNull(idx) ?: rawValue
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
                onCancel = { editingParam = null },
                levelStrings = levelStrings,
                levelValues = levelValues,
                ignoreVoltTable = ignoreVoltTable
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_level)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = onDeleteLevel) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        // Separate "header" params (bus-min, bus-max, bus-freq) from the rest
        val headerKeys = setOf("qcom,bus-min", "qcom,bus-max", "qcom,bus-freq")
        val paramsMap = params.associateBy { it.rawName }
        
        // Explicit order: Min, Max, Freq
        val headerParams = listOfNotNull(
            paramsMap["qcom,bus-min"],
            paramsMap["qcom,bus-max"],
            paramsMap["qcom,bus-freq"]
        )
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    headerParams.forEach { item ->
                        HeaderParamBox(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { editingParam = item }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Properties",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Remaining params in scrollable list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Show OPP voltage editor if available and no built-in voltage property exists
                val hasBuiltInVoltage = params.any { it.rawName == "qcom,level" || it.rawName == "qcom,cx-level" }
                if (!hasBuiltInVoltage && oppVoltage != null && oppVoltage > 0) {
                    item {
                        OppVoltageEditableCard(
                            oppVoltage = oppVoltage,
                            levelStrings = levelStrings,
                            levelValues = levelValues,
                            onUpdateVoltage = { newVolt ->
                                onUpdateOppVoltage?.invoke(newVolt)
                            }
                        )
                    }
                }
                
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

@Composable
fun EditParamSheetContent(
    param: ParamItem,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
    levelStrings: Array<String>,
    levelValues: IntArray,
    ignoreVoltTable: Boolean
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
            text = ChipStringHelper.help(param.rawName, context, ignoreVoltTable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(24.dp))

        if (isVoltage) {
            VoltageSelector(param, onSave, levelStrings, levelValues)
        } else if (isFrequency) {
            FrequencyEditor(param, onSave)
        } else {
            GenericEditor(param, onSave)
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun VoltageSelector(param: ParamItem, onSave: (String, String) -> Unit, levels: Array<String>, values: IntArray) {
    // Voltage Logic
    
    val currentLong = try { 
        if (param.rawValue.startsWith("0x")) java.lang.Long.decode(param.rawValue) else param.rawValue.toLong() 
    } catch (e: Exception) { -1L }
    
    // Check if it matches a preset
    val matchIndex = values.indexOfFirst { it.toLong() == currentLong }
    
    var customText by remember { mutableStateOf(if (matchIndex == -1) param.rawValue else "") }
    var selectedIndex by remember { mutableStateOf(matchIndex) } // -1 means custom
    
    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp)
    ) {
        // Standard Presets
        itemsIndexed(levels) { index, label ->
            val isSelected = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton
                    ) {
                         selectedIndex = index
                         // Auto-save on selection of preset
                         val value = if (index >= 0 && index < values.size) values[index] else 0
                         val encoded = DtsHelper.encodeIntOrHexLine(param.rawName, value.toString())
                         onSave(encoded, "Updated Voltage to $label")
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
        
        // Custom Option
        item {
            val isCustom = selectedIndex == -1
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isCustom,
                            role = Role.RadioButton
                        ) { selectedIndex = -1 }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isCustom, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.custom_value),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                if (isCustom) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            label = { Text(stringResource(R.string.raw_value_hint)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val encoded = DtsHelper.encodeIntOrHexLine(param.rawName, customText)
                            onSave(encoded, "Updated Voltage to Custom: $customText")
                        }) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
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
    val units = listOf(stringResource(R.string.hz), stringResource(R.string.mhz), stringResource(R.string.ghz))
    
    // Init logic - handle hex values
    LaunchedEffect(Unit) {
        try {
            val hz = if (param.rawValue.startsWith("0x")) java.lang.Long.decode(param.rawValue) else param.rawValue.toLong()
            text = (hz / 1000000).toString() // Default to MHz display
        } catch (e: Exception) { text = param.rawValue }
    }
    
    // Output Preview
    val hzValue = remember(text, unitIndex) {
        parseFrequencyToHz(text, unitIndex)
    }
    
    // Localized format for preview
    val previewText = if (hzValue > 0) "= ${stringResource(R.string.format_hz, hzValue)}" else "= ? ${stringResource(R.string.hz)}"

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.frequency)) },
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
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
                    val label = com.ireddragonicy.konabessnext.utils.FrequencyFormatter.format(context, hzValue)
                    onSave(encoded, "Updated Frequency to $label")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save))
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
            label = { Text(stringResource(R.string.value)) },
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
            Text(stringResource(R.string.save))
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
    // Determined icon and color based on property type
    val (iconPainter, iconTint) = when {
        item.rawName.contains("gpu-freq") ->  
            painterResource(R.drawable.ic_frequency) to androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
        item.rawName.contains("level") -> // Voltage Level
            painterResource(R.drawable.ic_voltage) to androidx.compose.ui.graphics.Color(0xFFE91E63) // Pink
        else -> 
            painterResource(R.drawable.ic_edit) to MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.displayValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.rawName.isNotEmpty()) {
                    Text(
                        text = item.rawName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
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
    val (icon, color) = when {
        item.rawName.contains("bus-min") -> 
            painterResource(R.drawable.ic_arrow_downward) to androidx.compose.ui.graphics.Color(0xFF009688) // Teal
        item.rawName.contains("bus-max") -> 
            painterResource(R.drawable.ic_arrow_upward) to androidx.compose.ui.graphics.Color(0xFF9C27B0) // Purple
        item.rawName.contains("bus-freq") -> 
            painterResource(R.drawable.ic_bus_freq) to androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
        else -> 
            painterResource(R.drawable.ic_edit) to MaterialTheme.colorScheme.primary
    }
    
    Card(
        onClick = onClick,
        modifier = modifier.height(90.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.displayValue,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = when {
                    item.rawName.contains("min") -> "Bus Min"
                    item.rawName.contains("max") -> "Bus Max"
                    item.rawName.contains("freq") -> "Bus Freq"
                    else -> item.title
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OppVoltageEditableCard(
    oppVoltage: Long,
    levelStrings: Array<String>,
    levelValues: IntArray,
    onUpdateVoltage: (Long) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    
    // Find the current voltage level name
    val currentLevelName = remember(oppVoltage, levelValues, levelStrings) {
        val idx = levelValues.indexOfFirst { it.toLong() == oppVoltage }
        if (idx >= 0 && idx < levelStrings.size) levelStrings[idx] else "Custom"
    }
    
    Card(
        onClick = { showSheet = true },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_voltage),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = androidx.compose.ui.graphics.Color(0xFFE91E63) // Pink
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Voltage (OPP Table)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "$currentLevelName ($oppVoltage)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Tap to edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit voltage",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    
    // Voltage selection bottom sheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            OppVoltageSelectorContent(
                currentVolt = oppVoltage,
                levelStrings = levelStrings,
                levelValues = levelValues,
                onSave = { newVolt ->
                    onUpdateVoltage(newVolt)
                    showSheet = false
                },
                onCancel = { showSheet = false }
            )
        }
    }
}

@Composable
fun OppVoltageSelectorContent(
    currentVolt: Long,
    levelStrings: Array<String>,
    levelValues: IntArray,
    onSave: (Long) -> Unit,
    onCancel: () -> Unit
) {
    // Find current selection index
    val matchIndex = remember(currentVolt, levelValues) {
        levelValues.indexOfFirst { it.toLong() == currentVolt }
    }
    
    var selectedIndex by remember { mutableStateOf(matchIndex) }
    var customText by remember { mutableStateOf(if (matchIndex == -1) currentVolt.toString() else "") }
    val isCustom = selectedIndex == -1
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Select Voltage Level",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "This changes the opp-microvolt value in the OPP table.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Standard Presets
            itemsIndexed(levelStrings.toList()) { index, label ->
                val value = if (index < levelValues.size) levelValues[index] else 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedIndex == index,
                            onClick = {
                                selectedIndex = index
                                customText = ""
                                // Auto-save on selection
                                onSave(value.toLong())
                            },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    RadioButton(
                        selected = selectedIndex == index,
                        onClick = {
                            selectedIndex = index
                            customText = ""
                            onSave(value.toLong())
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$label ($value)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Custom Option
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isCustom,
                            onClick = { selectedIndex = -1 },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    RadioButton(
                        selected = isCustom,
                        onClick = { selectedIndex = -1 }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (isCustom) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            label = { Text("Raw Value") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val value = customText.toLongOrNull()
                                if (value != null) {
                                    onSave(value)
                                }
                            },
                            enabled = customText.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Parse a frequency string to Hz based on the selected unit.
 * @param value The input string value
 * @param unitIndex 0=Hz, 1=MHz, 2=GHz
 * @return The frequency in Hz, or -1 if parsing fails
 */
private fun parseFrequencyToHz(value: String, unitIndex: Int): Long {
    return try {
        if (value.isBlank()) return -1
        val inputValue = value.trim().toDouble()
        when (unitIndex) {
            0 -> inputValue.toLong() // Hz
            1 -> (inputValue * 1_000_000).toLong() // MHz
            2 -> (inputValue * 1_000_000_000).toLong() // GHz
            else -> inputValue.toLong()
        }
    } catch (e: NumberFormatException) {
        -1
    }
}
