package com.ireddragonicy.konabessnext.ui.compose

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import kotlin.math.roundToInt

enum class AxisLockMode {
    FREE, VERTICAL, HORIZONTAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurveEditorScreen(
    binId: Int,
    sharedViewModel: SharedGpuViewModel,
    onBack: () -> Unit,
    onRepack: () -> Unit,
    onExportDts: () -> Unit,
    onExportImg: () -> Unit,
    canFlashOrRepack: Boolean,
    isRootMode: Boolean = true
) {
    // State
    // We maintain a local selectedBinId to allow switching bins inside the editor
    var currentBinId by remember { mutableStateOf(binId) }
    
    val bins by sharedViewModel.bins.collectAsState()
    val bin = bins.firstOrNull { it.id == currentBinId }
    val isDirty by sharedViewModel.isDirty.collectAsState()
    val canUndo by sharedViewModel.canUndo.collectAsState()
    val canRedo by sharedViewModel.canRedo.collectAsState()
    val history by sharedViewModel.history.collectAsState()
    val currentChip by sharedViewModel.currentChip.collectAsState()

    var showBinDialog by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf(WorkbenchSheetType.NONE) }
    var axisLockMode by remember { mutableStateOf(AxisLockMode.FREE) }

    // Global Offset State (now from ViewModel)
    val binOffsets by sharedViewModel.binOffsets.collectAsState()
    val globalOffset = binOffsets[currentBinId] ?: 0f
    
    // Effective Dirty State: Repo is dirty OR we have unapplied offsets
    val isPendingChanges = globalOffset != 0f
    val effectiveIsDirty = isDirty || isPendingChanges

    // Fix for Bin Button Label (Sync with GpuBinList logic)
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentBinName = remember(bin, context, currentChip) {
        if (bin == null) return@remember "Bin $currentBinId"
        val speedBinLine = bin.header.find { it.contains("qcom,speed-bin") }
        val realBinId = if (speedBinLine != null) {
            val extracted = com.ireddragonicy.konabessnext.utils.DtsHelper.extractLongValue(speedBinLine)
            if (extracted != -1L) extracted.toInt() else bin.id
        } else {
            bin.id
        }
        try {
            com.ireddragonicy.konabessnext.utils.ChipStringHelper.convertBins(realBinId, context, currentChip)
        } catch (e: Exception) {
            "Bin $realBinId"
        }
    }

    val chartData = remember(bin, globalOffset) {
        if (bin == null) return@remember emptyList<Entry>()
        
        bin.levels.mapIndexed { index, level ->
            val freqMhz = (level.frequency / 1_000_000f) + globalOffset
            val volt = level.voltageLevel.toFloat()
            Entry(volt, freqMhz, index) // X=Volt, Y=Freq, Data=Index
        }.sortedBy { it.x }
    }
    
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f).toArgb()
    
    // History Sheet (Reusable)
    GpuWorkbenchSheets(
        sheetType = activeSheet,
        onDismiss = { activeSheet = WorkbenchSheetType.NONE },
        history = history,
        dtbs = emptyList(), // Chipset selection not supported in this screen
        selectedDtbId = -1,
        activeDtbId = -1,
        onChipsetSelect = {},
        onConfigureManual = {},
        onDeleteDts = {},
        onImportDts = {}
    )

    if (showBinDialog) {
        AlertDialog(
            onDismissRequest = { showBinDialog = false },
            title = { Text("Select Frequency Table (Bin)") },
            text = {
                val context = androidx.compose.ui.platform.LocalContext.current
                Column {
                    bins.forEach { item ->
                        val isSelected = item.id == currentBinId
                        
                        // Fix for Bin Naming (Sync with GpuBinList logic)
                        val speedBinLine = item.header.find { it.contains("qcom,speed-bin") }
                        val realBinId = if (speedBinLine != null) {
                            val extracted = com.ireddragonicy.konabessnext.utils.DtsHelper.extractLongValue(speedBinLine)
                            if (extracted != -1L) extracted.toInt() else item.id
                        } else {
                            item.id
                        }
                        
                        val binName = remember(realBinId, context, currentChip) {
                            try {
                                com.ireddragonicy.konabessnext.utils.ChipStringHelper.convertBins(realBinId, context, currentChip)
                            } catch (e: Exception) {
                                "Bin $realBinId"
                            }
                        }

                        Surface(
                            onClick = {
                                currentBinId = item.id
                                // We do NOT reset offset here anymore, as it persists in ViewModel per bin
                                showBinDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(binName)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GpuEditorToolbar(
                isDirty = effectiveIsDirty, // Pass effective dirty state
                canUndo = canUndo,
                canRedo = canRedo,
                historyCount = history.size,
                currentViewMode = SharedGpuViewModel.ViewMode.MAIN_EDITOR,
                showChipsetSelector = false,
                onSave = { 
                    // Apply pending offset then save
                    sharedViewModel.applyGlobalOffset(currentBinId, globalOffset.toInt())
                    sharedViewModel.save() 
                },
                onUndo = { sharedViewModel.undo() },
                onRedo = { sharedViewModel.redo() },
                onShowHistory = { activeSheet = WorkbenchSheetType.HISTORY },
                onViewModeChanged = { mode ->
                    sharedViewModel.switchViewMode(mode)
                    onBack() // Navigate back to main screen to show the new mode
                },
                onFlashClick = onRepack,
                onExportDts = onExportDts,
                onExportImg = onExportImg,
                canFlashOrRepack = canFlashOrRepack,
                isRootMode = isRootMode,
                applyStatusBarPadding = true
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Consistent Extra Toolbar (Like GpuLevelList)
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.zIndex(1f)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Back Button (Left)
                        Button(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Back")
                        }
    
                        // Bin Selection Button (Right)
                        Button(
                            onClick = { showBinDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(currentBinName)
                        }
                    }
                    
                    // Axis Lock Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 0.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lock Axis:", style = MaterialTheme.typography.labelMedium)
                        AxisLockMode.values().forEach { mode ->
                            FilterChip(
                                selected = axisLockMode == mode,
                                onClick = { axisLockMode = mode },
                                label = { Text(
                                    when(mode) {
                                        AxisLockMode.FREE -> "Free"
                                        AxisLockMode.VERTICAL -> "Vert (Freq)"
                                        AxisLockMode.HORIZONTAL -> "Horz (Volt)"
                                    }
                                ) }
                            )
                        }
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            // Chart Card
            if (bin != null) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))
                ) {
                    Column(Modifier.padding(16.dp).fillMaxSize()) {
                        Text(
                            text = "Frequency Curve (MHz)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                LineChart(context).apply {
                                    description.isEnabled = false
                                    setTouchEnabled(true)
                                    isDragEnabled = true // Enable drag for panning X
                                    setScaleEnabled(true)
                                    setPinchZoom(false) // Disable pinch zoom to enforce X-only scaling via separate flags
                                    setScaleYEnabled(false) // Disable Y scaling (zoom)
                                    setScaleXEnabled(true)  // Enable X scaling (zoom)
                                    
                                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                                    xAxis.textColor = onSurfaceColor
                                    xAxis.gridColor = gridColor
                                    xAxis.setDrawGridLines(true)
                                    
                                    axisLeft.textColor = onSurfaceColor
                                    axisLeft.gridColor = gridColor
                                    axisLeft.setDrawGridLines(true)
                                    
                                    axisRight.isEnabled = false
                                    legend.isEnabled = false
                                    
                                    xAxis.valueFormatter = object : ValueFormatter() {
                                        override fun getFormattedValue(value: Float): String {
                                            val volt = value.toInt()
                                            val chip = sharedViewModel.currentChip.value
                                            val rawName = chip?.resolvedLevels?.get(volt - 1) ?: chip?.resolvedLevels?.get(volt)
                                            val name = if (rawName?.contains(" - ") == true) rawName.substringAfter(" - ") else rawName
                                            return if (!name.isNullOrEmpty()) "$volt\n$name" else "$volt"
                                        }
                                    }
                                    
                                    axisLeft.valueFormatter = object : ValueFormatter() {
                                        override fun getFormattedValue(value: Float): String {
                                            return value.toInt().toString()
                                        }
                                    }
                                    
                                    setNoDataText("No Data Available")
                                    setNoDataTextColor(onSurfaceColor)
                                }
                            },
                            update = { chart ->
                                // Update Data
                                if (chartData.isNotEmpty()) {
                                    val dataSet = LineDataSet(chartData, "V/F Curve").apply {
                                        color = primaryColor
                                        setCircleColor(primaryColor)
                                        lineWidth = 3f
                                        circleRadius = 5f
                                        setDrawCircleHole(true)
                                        circleHoleRadius = 2.5f
                                        circleHoleColor = surfaceColor
                                        
                                        valueTextColor = onSurfaceColor
                                        valueTextSize = 10f
                                        valueFormatter = object : ValueFormatter() {
                                            override fun getFormattedValue(value: Float): String {
                                                return value.toInt().toString()
                                            }
                                        }
                                        
                                        mode = LineDataSet.Mode.CUBIC_BEZIER
                                        setDrawFilled(true)
                                        fillColor = primaryColor
                                        fillAlpha = 60
                                        
                                        setDrawHighlightIndicators(true)
                                        highLightColor = AndroidColor.WHITE
                                    }
                                    
                                    val lineData = LineData(dataSet)
                                    chart.data = lineData
                                    
                                    // Setup Touch Listener for Dragging Points and Handling Zoom
                                    // We set it here to capture the latest 'bins', 'currentBinId', 'globalOffset', 'sharedViewModel'
                                    chart.maxHighlightDistance = 30f // Reduce highlight distance to allow easier zooming
                                    
                                    chart.setOnTouchListener(object : View.OnTouchListener {
                                        var draggedEntry: Entry? = null
                                        var dragStartPos: Pair<Float, Float>? = null

                                        override fun onTouch(v: View, event: MotionEvent): Boolean {
                                            val viewChart = v as LineChart

                                            when (event.action) {
                                                MotionEvent.ACTION_DOWN -> {
                                                    // Only start drag if we are strictly ON a point
                                                    val h = viewChart.getHighlightByTouchPoint(event.x, event.y)
                                                    
                                                    // If highlight is found and distance is very close?
                                                    // getHighlightByTouchPoint respects maxHighlightDistance automatically.
                                                    if (h != null) {
                                                        val set = viewChart.data.getDataSetByIndex(h.dataSetIndex)
                                                        val e = set.getEntryForXValue(h.x, h.y)
                                                        
                                                        draggedEntry = e
                                                        dragStartPos = Pair(e.x, e.y)
                                                        
                                                        // Disable chart panning/zooming while dragging a point
                                                        // But let the chart handle the DOWN event internally initially?
                                                        // No, we must CONSUME if we are dragging, otherwise chart will zoom.
                                                        
                                                        viewChart.isDragEnabled = false 
                                                        return true
                                                    }
                                                    // If not dragging, we return false so viewChart.onTouchEvent(event) 
                                                    // is called at the end, allowing Zoom/Pan.
                                                }
                                                MotionEvent.ACTION_MOVE -> {
                                                    draggedEntry?.let { entry ->
                                                        val trans = viewChart.getTransformer(YAxis.AxisDependency.LEFT)
                                                        val values = trans.getValuesByTouchPoint(event.x, event.y)
                                                        
                                                        // Update visual entry temporarily
                                                        // X = Voltage, Y = Frequency
                                                        var newX = values.x.toFloat()
                                                        var newY = values.y.toFloat()
                                                        
                                                        when (axisLockMode) {
                                                            AxisLockMode.VERTICAL -> newX = dragStartPos?.first ?: newX // Lock X, allow Y
                                                            AxisLockMode.HORIZONTAL -> newY = dragStartPos?.second ?: newY // Lock Y, allow X
                                                            AxisLockMode.FREE -> {}
                                                        }

                                                        entry.x = newX
                                                        entry.y = newY
                                                        
                                                        viewChart.notifyDataSetChanged()
                                                        viewChart.invalidate()
                                                        return true
                                                    }
                                                }
                                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                    draggedEntry?.let { entry ->
                                                        // Commit changes to ViewModel
                                                        val levelIdx = entry.data as Int
                                                        val binIdx = bins.indexOfFirst { it.id == currentBinId }
                                                        
                                                        if (binIdx != -1) {
                                                            // Calculate raw Frequency (subtract visual offset)
                                                            val rawFreq = (entry.y - globalOffset).roundToInt()
                                                            // Keep min 0
                                                            val finalFreq = if (rawFreq < 0) 0 else rawFreq
                                                            // X is Voltage
                                                            val finalVolt = entry.x.roundToInt()
                                                            
                                                            // This commits to repo + history immediately
                                                            sharedViewModel.updateBinLevel(binIdx, levelIdx, finalFreq, finalVolt)
                                                        }
                                                        
                                                        draggedEntry = null
                                                        dragStartPos = null
                                                        viewChart.isDragEnabled = true // Restore chart panning
                                                        return true
                                                    }
                                                }
                                            }
                                            // Fallback to default chart gestures (Zoom, Pan)
                                            return viewChart.onTouchEvent(event)
                                        }
                                    })
                                    
                                    chart.invalidate()
                                }
                            }
                        )
                    }
                }
                
                // Bottom Card: Global Offset
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text("Global Offset", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Shift all frequencies up or down. Tap Save to apply.", 
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = { sharedViewModel.setBinOffset(currentBinId, globalOffset - 10f) },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Decrease")
                            }
                            
                            Slider(
                                value = globalOffset,
                                onValueChange = { sharedViewModel.setBinOffset(currentBinId, it) },
                                valueRange = -500f..500f,
                                modifier = Modifier.weight(1f)
                            )
                            
                            FilledIconButton(
                                onClick = { sharedViewModel.setBinOffset(currentBinId, globalOffset + 10f) },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Increase")
                            }
                        }
                        
                        Text(
                            text = "${globalOffset.toInt()} MHz",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (globalOffset != 0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bin not found", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    }
}