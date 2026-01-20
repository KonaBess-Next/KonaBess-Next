package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.ui.SettingsActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuLevelList(
    levels: List<Level>,
    onLevelClick: (index: Int) -> Unit,
    onAddLevelTop: () -> Unit,
    onAddLevelBottom: () -> Unit,
    onDuplicateLevel: (index: Int) -> Unit,
    onDeleteLevel: (index: Int) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onBack: () -> Unit,
    onOpenCurveEditor: () -> Unit
) {
    // Filter out invalid/0Hz levels to match legacy behavior
    // Map to preserve original indices for callbacks
    val visibleLevels = remember(levels) {
        levels.mapIndexed { index, level -> IndexedLevel(index, level) }
            .filter { 
               com.ireddragonicy.konabessnext.core.editor.LevelOperations.getFrequencyFromLevel(it.level) > 0L
            }
    }

    // Local state for Drag & Drop
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingItemOffset by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }
    
    // We use a local list for immediate visual feedback during drag
    // Initialize it whenever visibleLevels changes (external update)
    val localList = remember(visibleLevels) { 
        val list = mutableStateListOf<IndexedLevel>()
        list.addAll(visibleLevels)
        list
    }

    fun onDragStart(index: Int) {
        draggingItemIndex = index
        draggingItemOffset = 0f
    }

    fun onDrag(dragAmount: Float) {
        val draggedIndex = draggingItemIndex ?: return
        draggingItemOffset += dragAmount
        
        // Simple swap logic based on offset threshold (approx item height)
        // This gives a "ratchet" feel which is safe and functional
        val currentHeight = itemHeights[draggedIndex] ?: 150 // Fallback height
        val threshold = currentHeight * 0.7f // Swap when passed 70%
        
        if (draggingItemOffset > threshold) {
            // Move Down
            if (draggedIndex < localList.size - 1) {
                // Swap in local list
                val nextIndex = draggedIndex + 1
                localList.add(nextIndex, localList.removeAt(draggedIndex))
                draggingItemIndex = nextIndex
                draggingItemOffset -= currentHeight // Adjust offset to keep finger relative
            }
        } else if (draggingItemOffset < -threshold) {
             // Move Up
             if (draggedIndex > 0) {
                 val prevIndex = draggedIndex - 1
                 localList.add(prevIndex, localList.removeAt(draggedIndex))
                 draggingItemIndex = prevIndex
                 draggingItemOffset += currentHeight
             }
        }
    }

    fun onDragEnd() {
        val draggedIndex = draggingItemIndex ?: return
        val item = localList[draggedIndex] // This is the wrapper with ORIGINAL index
        
        // We need to find where it landed relative to the ORIGINAL visibleLevels
        // "localList" has the NEW visual order.
        // We want to commit the move from "item.index" to... where?
        
        // Simplified approach: Calculate the target index based on the neighbor
        // But onReorder(from, to) expects the absolute "To" index in the dataset.
        // It's tricky to map multiple swaps to one "Reorder" call if we dragged far.
        // Ideally we should disable the drag if we can't map it.
        
        // BETTER STRATEGY: 
        // We know the item.index is the "Real From".
        // We need "Real To".
        // In the localList, look at the item currently at 'draggedIndex'.
        // Its intended position determines the target.
        // However, 'localList' mixes up items.
        
        // If we simply rely on the visual swap, we might desync.
        // Let's trigger onReorder immediately on swap? 
        // NO, that causes fetch and rebuild.
        
        // Let's rely on the "Ratchet". 
        // When we swap in `onDrag`, we ALREADY performed the visual swap.
        // We just need to commit it.
        // BUT if we dragged multiple steps, we need multiple commits? Or one big one?
        // One big one is `reorder(from, to)`.
        
        // Logic:
        // The item started at `item.index` (Real).
        // It ended up at `draggedIndex` (Visual).
        // Content at `draggedIndex` should correspond to...
        // Let's look at `visibleLevels[draggedIndex].index`. 
        // That is the "Real Index" of the slot where we dropped it (approximately).
        
        val targetRealIndex = visibleLevels.getOrNull(draggedIndex)?.index
        
        if (targetRealIndex != null && targetRealIndex != item.index) {
             onReorder(item.index, targetRealIndex)
        }
        
        draggingItemIndex = null
        draggingItemOffset = 0f
    }
    
    fun onDragCancel() {
        draggingItemIndex = null
        draggingItemOffset = 0f
        // Revert local changes?
        localList.clear()
        localList.addAll(visibleLevels)
    }

    com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background, // Force background
        ) { paddingValues ->
            // Use Column + VerticalScroll for simpler Drag handling (No recycling)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Actions: Back & Curve Editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Back Button
                    Button(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Back")
                    }
                    
                    // Curve Editor Button
                    Button(
                        onClick = onOpenCurveEditor,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Curve Editor")
                    }
                }
                
                Spacer(Modifier.height(8.dp))

                // Header Action: Add Top
                OutlinedButton(
                    onClick = onAddLevelTop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                     colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Frequency to Top")
                }
                
                Spacer(Modifier.height(8.dp))

                // Draggable List Items
                localList.forEachIndexed { listIndex, indexedLevel ->
                    // Key is critical for Column reordering to look good
                    // We us index? No, we need stable key. 
                    // Use hashCode of level (content) + original index to be safe
                    key(indexedLevel.index) {
                        val isDragging = listIndex == draggingItemIndex
                        val offset = if (isDragging) draggingItemOffset else 0f
                        val zIndex = if (isDragging) 1f else 0f
                        val scale = if (isDragging) 1.05f else 1f
                        val shadow = if (isDragging) 8.dp else 2.dp
                        
                        Box(
                            modifier = Modifier
                                .zIndex(zIndex)
                                .graphicsLayer {
                                    translationY = offset
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .onGloballyPositioned { coordinates ->
                                    itemHeights[listIndex] = coordinates.size.height
                                }
                        ) {
                             LevelCard(
                                level = indexedLevel.level,
                                index = listIndex, // Display index
                                totalCount = localList.size,
                                onClick = { onLevelClick(indexedLevel.index) }, // Pass ORIGINAL index
                                onDelete = { onDeleteLevel(indexedLevel.index) }, // Pass ORIGINAL index
                                onCopy = { onDuplicateLevel(indexedLevel.index) },
                                onDragStart = { onDragStart(listIndex) },
                                onDrag = { change -> onDrag(change) },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragCancel() }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Footer Action: Add Bottom
                OutlinedButton(
                    onClick = onAddLevelBottom,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Frequency to Bottom")
                }
                
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

data class IndexedLevel(
    val index: Int,
    val level: Level
)

@Composable
fun LevelCard(
    level: Level,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    // Parse values
    val context = LocalContext.current
    val (freqStr, details) = remember(level, context) {
        val freq = com.ireddragonicy.konabessnext.core.editor.LevelOperations.getFrequencyFromLevel(level)
        var busMax = ""
        var busMin = ""
        var busFreq = ""
        var volt = ""
        
        try {
            for (line in level.lines) {
                val decoded = com.ireddragonicy.konabessnext.utils.DtsHelper.decode_int_line(line)
                when (decoded.name) {
                    "qcom,bus-max" -> busMax = decoded.value.toString()
                    "qcom,bus-min" -> busMin = decoded.value.toString()
                    "qcom,bus-freq" -> busFreq = decoded.value.toString()
                    "qcom,level", "qcom,cx-level" -> volt = com.ireddragonicy.konabessnext.core.GpuVoltEditor.levelint2str(decoded.value)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        val fStr = if (freq >= 0) com.ireddragonicy.konabessnext.ui.SettingsActivity.formatFrequency(freq, context) else "Unknown"
        
        // Return structured data for UI
        fStr to LevelDetails(busMin, busMax, busFreq, volt)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { _ -> onDragStart() },
                            onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                 Icon(
                     imageVector = Icons.Filled.Menu,
                     contentDescription = "Drag",
                     tint = MaterialTheme.colorScheme.onSurfaceVariant
                 )
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Row 1: Frequency
                Text(
                    text = freqStr,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(Modifier.height(4.dp))
                
                // Row 2: Bus Specs (Icons + Values)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (details.busMin.isNotEmpty()) IconWithText(Icons.Default.KeyboardArrowDown, details.busMin)
                    if (details.busMax.isNotEmpty()) IconWithText(Icons.Default.KeyboardArrowUp, details.busMax)
                    if (details.busFreq.isNotEmpty()) IconWithText(painterResource(com.ireddragonicy.konabessnext.R.drawable.ic_bus_freq), details.busFreq)
                }

                Spacer(Modifier.height(4.dp))
                
                // Row 3: Voltage
                if (details.voltage.isNotEmpty()) {
                    Text(
                        text = "Level ${details.voltage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions: Copy & Delete
            Row {
                IconButton(onClick = onCopy) {
                    Icon(
                        painterResource(com.ireddragonicy.konabessnext.R.drawable.ic_copy),
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

data class LevelDetails(val busMin: String, val busMax: String, val busFreq: String, val voltage: String)

@Composable
fun IconWithText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun IconWithText(icon: androidx.compose.ui.graphics.painter.Painter, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
