package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.utils.ChipStringHelper
import com.ireddragonicy.konabessnext.utils.DtsHelper
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val currentLong = try {
        if (param.rawValue.startsWith("0x")) java.lang.Long.decode(param.rawValue) else param.rawValue.toLong()
    } catch (e: Exception) { -1L }

    val matchIndex = values.indexOfFirst { it.toLong() == currentLong }

    var selectedIndex by remember { mutableIntStateOf(matchIndex) }
    var textFieldValue by remember {
        mutableStateOf(
            if (matchIndex >= 0 && matchIndex < values.size) values[matchIndex].toString()
            else currentLong.toString()
        )
    }

    // Slider state
    val sliderRange = remember(values) {
        if (values.size > 1) 0f..((values.size - 1).toFloat())
        else 0f..1f
    }
    var sliderPosition by remember {
        mutableFloatStateOf(if (matchIndex >= 0) matchIndex.toFloat() else 0f)
    }

    // List state
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = matchIndex.coerceAtLeast(0)
    )

    // Sync: when selectedIndex changes, update text + scroll list
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in values.indices) {
            textFieldValue = values[selectedIndex].toString()
            sliderPosition = selectedIndex.toFloat()
            listState.animateScrollToItem(selectedIndex.coerceAtLeast(0))
        }
    }

    // ── Current Value Display ──
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Current",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = if (selectedIndex in levels.indices) levels[selectedIndex] else "Custom",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = textFieldValue,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // ── Slider ──
    if (values.size > 1) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (levels.isNotEmpty()) levels.first() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (levels.isNotEmpty()) levels.last() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = sliderPosition,
                onValueChange = { newPos ->
                    sliderPosition = newPos
                    val idx = newPos.toInt().coerceIn(values.indices)
                    if (idx != selectedIndex) {
                        selectedIndex = idx
                    }
                },
                onValueChangeFinished = {
                    val idx = sliderPosition.toInt().coerceIn(values.indices)
                    selectedIndex = idx
                },
                valueRange = sliderRange,
                steps = (values.size - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    // ── Direct Input Row ──
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newVal ->
                textFieldValue = newVal
                val parsed = newVal.toLongOrNull()
                if (parsed != null) {
                    val idx = values.indexOfFirst { it.toLong() == parsed }
                    selectedIndex = idx
                } else {
                    selectedIndex = -1
                }
            },
            label = { Text(stringResource(R.string.raw_value_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    val encoded = DtsHelper.encodeIntOrHexLine(param.rawName, textFieldValue)
                    onSave(encoded, "Updated Voltage to $textFieldValue")
                }
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FilledTonalIconButton(
                onClick = {
                    val nextIdx = (selectedIndex + 1).coerceAtMost(values.size - 1)
                    if (nextIdx in values.indices) selectedIndex = nextIdx
                },
                modifier = Modifier.size(36.dp),
                enabled = selectedIndex < values.size - 1
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Increase", modifier = Modifier.size(18.dp))
            }
            FilledTonalIconButton(
                onClick = {
                    val prevIdx = (selectedIndex - 1).coerceAtLeast(0)
                    if (prevIdx in values.indices) selectedIndex = prevIdx
                },
                modifier = Modifier.size(36.dp),
                enabled = selectedIndex > 0
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // ── Preset List ──
    Text(
        text = "Presets",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(levels.toList()) { index, label ->
                val value = if (index < values.size) values[index] else 0
                val isSelected = selectedIndex == index

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            selectedIndex = index
                            textFieldValue = value.toString()
                            sliderPosition = index.toFloat()
                            val encoded = DtsHelper.encodeIntOrHexLine(param.rawName, value.toString())
                            onSave(encoded, "Updated Voltage to $label")
                        },
                    color = if (isSelected)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // ── Apply Button ──
    Button(
        onClick = {
            val encoded = DtsHelper.encodeIntOrHexLine(param.rawName, textFieldValue)
            onSave(encoded, "Updated Voltage to $textFieldValue")
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        enabled = textFieldValue.isNotBlank()
    ) {
        Text(stringResource(R.string.save))
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
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Find current selection index
    val matchIndex = remember(currentVolt, levelValues) {
        levelValues.indexOfFirst { it.toLong() == currentVolt }
    }

    var selectedIndex by remember { mutableIntStateOf(matchIndex) }
    var textFieldValue by remember {
        mutableStateOf(
            if (matchIndex >= 0 && matchIndex < levelValues.size) levelValues[matchIndex].toString()
            else currentVolt.toString()
        )
    }

    // Slider state — map index in levelValues to slider position
    val sliderRange = remember(levelValues) {
        if (levelValues.size > 1) 0f..((levelValues.size - 1).toFloat())
        else 0f..1f
    }
    var sliderPosition by remember {
        mutableFloatStateOf(
            if (matchIndex >= 0) matchIndex.toFloat() else 0f
        )
    }

    // List state for scroll-sync
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (matchIndex.coerceAtLeast(0))
    )

    // Sync: when selectedIndex changes, update text + scroll list
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in levelValues.indices) {
            textFieldValue = levelValues[selectedIndex].toString()
            sliderPosition = selectedIndex.toFloat()
            // Scroll to make currently selected visible
            listState.animateScrollToItem(selectedIndex.coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // ── Header ──
        Text(
            text = "Voltage Level",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Set the opp-microvolt value. Use the slider, list, or type directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // ── Current Value Display ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Current",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = if (selectedIndex in levelStrings.indices) levelStrings[selectedIndex] else "Custom",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                // Raw value chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = textFieldValue,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Slider ──
        if (levelValues.size > 1) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (levelStrings.isNotEmpty()) levelStrings.first() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (levelStrings.isNotEmpty()) levelStrings.last() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = sliderPosition,
                    onValueChange = { newPos ->
                        sliderPosition = newPos
                        val idx = newPos.toInt().coerceIn(levelValues.indices)
                        if (idx != selectedIndex) {
                            selectedIndex = idx
                        }
                    },
                    onValueChangeFinished = {
                        val idx = sliderPosition.toInt().coerceIn(levelValues.indices)
                        selectedIndex = idx
                    },
                    valueRange = sliderRange,
                    steps = (levelValues.size - 2).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Direct Input Row ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newVal ->
                    textFieldValue = newVal
                    // Try to match to a preset
                    val parsed = newVal.toLongOrNull()
                    if (parsed != null) {
                        val idx = levelValues.indexOfFirst { it.toLong() == parsed }
                        selectedIndex = idx // -1 if custom
                    } else {
                        selectedIndex = -1
                    }
                },
                label = { Text("Raw value") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        val parsed = textFieldValue.toLongOrNull()
                        if (parsed != null) onSave(parsed)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            // Increment / Decrement buttons
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        val nextIdx = (selectedIndex + 1).coerceAtMost(levelValues.size - 1)
                        if (nextIdx in levelValues.indices) {
                            selectedIndex = nextIdx
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = selectedIndex < levelValues.size - 1
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Increase",
                        modifier = Modifier.size(18.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        val prevIdx = (selectedIndex - 1).coerceAtLeast(0)
                        if (prevIdx in levelValues.indices) {
                            selectedIndex = prevIdx
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = selectedIndex > 0
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Decrease",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Preset List ──
        Text(
            text = "Presets",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(levelStrings.toList()) { index, label ->
                    val value = if (index < levelValues.size) levelValues[index] else 0
                    val isSelected = selectedIndex == index

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selectedIndex = index
                                textFieldValue = value.toString()
                                sliderPosition = index.toFloat()
                                onSave(value.toLong())
                            },
                        color = if (isSelected)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Action Buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val parsed = textFieldValue.toLongOrNull()
                    if (parsed != null) onSave(parsed)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = textFieldValue.toLongOrNull() != null
            ) {
                Text("Apply")
            }
        }

        Spacer(Modifier.height(8.dp))
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
