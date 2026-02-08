package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.LevelUiModel

import androidx.compose.ui.graphics.Color

/**
 * GpuLevelList with performance optimizations.
 */
@Composable
fun GpuLevelList(
    uiModels: List<LevelUiModel>,
    onLevelClick: (index: Int) -> Unit,
    onAddLevelTop: () -> Unit,
    onAddLevelBottom: () -> Unit,
    onDuplicateLevel: (index: Int) -> Unit,
    onDeleteLevel: (index: Int) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onBack: () -> Unit,
    onOpenCurveEditor: () -> Unit
) {
    // 1. Pre-load ALL resources once at the top level
    val iconCopy = painterResource(R.drawable.ic_copy)
    val iconBusFreq = painterResource(R.drawable.ic_bus_freq)
    val iconMin = painterResource(R.drawable.ic_arrow_downward)
    val iconMax = painterResource(R.drawable.ic_arrow_upward)
    val iconLevel = painterResource(R.drawable.ic_tune)
    val iconVolt = painterResource(R.drawable.ic_voltage)
    val iconMenu = rememberVectorPainter(Icons.Default.Menu)
    val iconDelete = rememberVectorPainter(Icons.Default.Delete)
    val iconAdd = rememberVectorPainter(Icons.Default.Add)
    val iconBack = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack)
    val iconEdit = rememberVectorPainter(Icons.Filled.Edit)

    // 2. Mutable State for Drag-and-Drop
    val localList = remember(uiModels) { 
        mutableStateListOf<LevelUiModel>().apply { addAll(uiModels) }
    }

    // 3. Raw State Primitives (No boxing)
    var draggingItemIndex by remember { mutableStateOf(-1) }
    var draggingItemOffset by remember { mutableStateOf(0f) }
    
    // 4. Fixed Thresholds (Avoid onGloballyPositioned)
    val density = LocalDensity.current
    val dragThresholdPx = remember(density) { with(density) { 70.dp.toPx() } }
    val itemHeightEstimatePx = remember(density) { with(density) { 110.dp.toPx() } }

    val onDragStart: (Int) -> Unit = remember { { index ->
        draggingItemIndex = index
        draggingItemOffset = 0f
    }}

    val onDrag: (Float) -> Unit = remember { { dragAmount ->
        if (draggingItemIndex != -1) {
            draggingItemOffset += dragAmount
            
            if (draggingItemOffset > dragThresholdPx) {
                if (draggingItemIndex < localList.size - 1) {
                    val next = draggingItemIndex + 1
                    localList.add(next, localList.removeAt(draggingItemIndex))
                    draggingItemIndex = next
                    draggingItemOffset -= itemHeightEstimatePx
                }
            } else if (draggingItemOffset < -dragThresholdPx) {
                 if (draggingItemIndex > 0) {
                     val prev = draggingItemIndex - 1
                     localList.add(prev, localList.removeAt(draggingItemIndex))
                     draggingItemIndex = prev
                     draggingItemOffset += itemHeightEstimatePx
                 }
            }
        }
    }}

    val onDragEnd: () -> Unit = remember(uiModels) { {
        if (draggingItemIndex != -1) {
            val item = localList[draggingItemIndex]
            val targetRealIndex = uiModels.getOrNull(draggingItemIndex)?.originalIndex
            if (targetRealIndex != null && targetRealIndex != item.originalIndex) {
                 onReorder(item.originalIndex, targetRealIndex)
            }
        }
        draggingItemIndex = -1
        draggingItemOffset = 0f
    }}
    
    val onDragCancel: () -> Unit = remember(uiModels) { {
        draggingItemIndex = -1
        draggingItemOffset = 0f
        localList.clear()
        localList.addAll(uiModels)
    }}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
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
                    Text(androidx.compose.ui.res.stringResource(R.string.btn_back))
                }
                
                Button(
                    onClick = onOpenCurveEditor,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(iconEdit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.btn_curve_editor))
                }
            }
        }

        // High-Performance List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            // Optimization: SpacedBy removes the need for individual Spacer items
            verticalArrangement = Arrangement.spacedBy(8.dp), 
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
        ) {
            item(key = "hdr_add", contentType = "action") {
                OutlinedButton(
                    onClick = onAddLevelTop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(iconAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.btn_add_freq_top))
                }
            }

            itemsIndexed(
                items = localList,
                key = { _, uiModel -> uiModel.originalIndex },
                contentType = { _, _ -> "level" }
            ) { listIndex, uiModel ->
                
                val isDragging = listIndex == draggingItemIndex
                
                LevelCard(
                    uiModel = uiModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = draggingItemOffset
                                scaleX = 1.05f
                                scaleY = 1.05f
                                alpha = 0.95f
                                shadowElevation = 8.dp.toPx()
                            }
                        },
                    listIndex = listIndex,
                    onLevelClick = onLevelClick,
                    onDelete = onDeleteLevel,
                    onCopy = onDuplicateLevel,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    // Pass resources
                    res = LevelCardResources(
                        iconCopy, iconBusFreq, iconMin, iconMax, iconLevel, iconVolt, iconMenu, iconDelete
                    )
                )
            }

            item(key = "ftr_add", contentType = "action") {
                OutlinedButton(
                    onClick = onAddLevelBottom,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(iconAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.btn_add_freq_bottom))
                }
            }
        }
    }
}

// Resource holder struct
data class LevelCardResources(
    val iconCopy: Painter,
    val iconBusFreq: Painter,
    val iconMin: Painter,
    val iconMax: Painter,
    val iconLevel: Painter,
    val iconVolt: Painter,
    val iconMenu: Painter,
    val iconDelete: Painter
)

@Composable
fun LevelCard(
    uiModel: LevelUiModel,
    modifier: Modifier = Modifier,
    listIndex: Int,
    onLevelClick: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onCopy: (Int) -> Unit,
    onDragStart: (Int) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    res: LevelCardResources
) {
    val frequencyText = uiModel.frequencyLabel.asString()
    
    // Colors
    val ColorMin = Color(0xFF009688)
    val ColorMax = Color(0xFF9C27B0)
    val ColorFreq = Color(0xFF2196F3)
    val ColorLevel = Color(0xFFFF5722)
    val ColorVolt = Color(0xFFE91E63)

    Card(
        onClick = { onLevelClick(uiModel.originalIndex) },
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { onDragStart(listIndex) },
                            onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                 Icon(
                     painter = res.iconMenu,
                     contentDescription = null,
                     tint = MaterialTheme.colorScheme.onSurfaceVariant
                 )
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = frequencyText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Row 1: Bus Info (Min, Max, Freq)
                if (uiModel.busMin.isNotEmpty() || uiModel.busMax.isNotEmpty() || uiModel.busFreq.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiModel.busMin.isNotEmpty()) ColorIconText(res.iconMin, uiModel.busMin, ColorMin)
                        if (uiModel.busMax.isNotEmpty()) ColorIconText(res.iconMax, uiModel.busMax, ColorMax)
                        if (uiModel.busFreq.isNotEmpty()) ColorIconText(res.iconBusFreq, uiModel.busFreq, ColorFreq)
                    }
                }

                // Row 2: Level and Voltage
                if (uiModel.voltageVal.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Level value removed, integrated into voltageVal with label
                        ColorIconText(res.iconVolt, uiModel.voltageVal, ColorVolt)
                    }
                }
            }

            Row {
                IconButton(onClick = { onCopy(uiModel.originalIndex) }) {
                    Icon(res.iconCopy, null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onDelete(uiModel.originalIndex) }) {
                    Icon(res.iconDelete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ColorIconText(icon: Painter, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}