package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.LevelUiModel

/**
 * GpuLevelList with motion-rich interactions and stable item identity.
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
    // Pre-load resources once at the top level.
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

    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // Presentation list with stable IDs so LazyColumn can track item identity reliably.
    // Seed immediately on first composition to avoid a frame where only top/bottom buttons appear.
    val initialDisplayItems = remember {
        uiModels.mapIndexed { index, model ->
            AnimatedLevelItem(stableId = index.toLong(), uiModel = model)
        }
    }
    val displayItems = remember {
        mutableStateListOf<AnimatedLevelItem>().apply { addAll(initialDisplayItems) }
    }
    var nextStableId by remember { mutableStateOf(initialDisplayItems.size.toLong()) }
    var nextPulseToken by remember { mutableStateOf(0) }
    var pendingInsertAction by remember { mutableStateOf<PendingInsertAction?>(null) }

    // Drag state.
    var draggingItemId by remember { mutableStateOf(NO_DRAG_ID) }
    var draggingItemOffset by remember { mutableStateOf(0f) }
    var draggingStartOriginalIndex by remember { mutableStateOf(-1) }

    // Fixed thresholds (avoids costly runtime measuring).
    val density = LocalDensity.current
    val dragThresholdPx = remember(density) { with(density) { 70.dp.toPx() } }
    val itemHeightEstimatePx = remember(density) { with(density) { 110.dp.toPx() } }

    fun allocateStableId(): Long {
        val id = nextStableId
        nextStableId += 1
        return id
    }

    fun allocatePulseToken(): Int {
        nextPulseToken += 1
        return nextPulseToken
    }

    // Sync local animated list with latest VM list while preserving existing stable IDs.
    LaunchedEffect(uiModels) {
        if (displayItems.isEmpty()) {
            displayItems.addAll(
                uiModels.map { model ->
                    AnimatedLevelItem(stableId = allocateStableId(), uiModel = model)
                }
            )
            pendingInsertAction = null
            return@LaunchedEffect
        }

        val previousActiveItems = displayItems.filterNot { it.isRemoving }
        val previousById = previousActiveItems.associateBy { it.stableId }
        val stableIdQueues = previousActiveItems
            .groupBy { it.uiModel.identitySignature() }
            .mapValues { (_, items) -> ArrayDeque(items.map { it.stableId }) }
            .toMutableMap()

        val singleInsert = uiModels.size == previousActiveItems.size + 1
        val forcedInsertIndex = when (val action = pendingInsertAction) {
            PendingInsertAction.AddTop -> 0
            PendingInsertAction.AddBottom -> uiModels.lastIndex
            is PendingInsertAction.Duplicate ->
                (action.sourceOriginalIndex + 1).coerceIn(0, uiModels.lastIndex)
            null -> null
        }?.takeIf { singleInsert }

        val shouldAnimateInsert = pendingInsertAction != null
        var forcedInsertConsumed = false
        val reconciledActiveItems = uiModels.mapIndexed { index, model ->
            val stableId = if (!forcedInsertConsumed && forcedInsertIndex == index) {
                forcedInsertConsumed = true
                allocateStableId()
            } else {
                val queue = stableIdQueues[model.identitySignature()]
                if (queue != null && queue.isNotEmpty()) queue.removeFirst() else allocateStableId()
            }

            val existing = previousById[stableId]
            val pulseToken = if (existing != null) {
                existing.pulseToken
            } else if (pendingInsertAction is PendingInsertAction.Duplicate && forcedInsertIndex == index) {
                allocatePulseToken()
            } else {
                0
            }

            AnimatedLevelItem(
                stableId = stableId,
                uiModel = model,
                isNew = shouldAnimateInsert && existing == null,
                isRemoving = false,
                pulseToken = pulseToken
            )
        }

        val merged = mergeActiveItemsKeepingRemoving(
            previous = displayItems,
            newActiveItems = reconciledActiveItems
        )
        displayItems.clear()
        displayItems.addAll(merged)

        when {
            pendingInsertAction == PendingInsertAction.AddTop && singleInsert -> {
                listState.animateScrollToItem(0)
            }
            pendingInsertAction == PendingInsertAction.AddBottom && singleInsert -> {
                // Header is index 0, first level row is index 1.
                listState.animateScrollToItem(uiModels.lastIndex + 1)
            }
        }
        pendingInsertAction = null
    }

    val onDragStart: (Long) -> Unit = { stableId ->
        draggingItemId = stableId
        draggingItemOffset = 0f
        draggingStartOriginalIndex = displayItems
            .firstOrNull { it.stableId == stableId }
            ?.uiModel
            ?.originalIndex
            ?: -1
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val onDrag: (Float) -> Unit = onDrag@{ dragAmount ->
        if (draggingItemId == NO_DRAG_ID) return@onDrag
        draggingItemOffset += dragAmount

        if (draggingItemOffset > dragThresholdPx) {
            val moved = moveDraggedItem(displayItems, draggingItemId, direction = 1)
            if (moved) {
                draggingItemOffset -= itemHeightEstimatePx
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } else if (draggingItemOffset < -dragThresholdPx) {
            val moved = moveDraggedItem(displayItems, draggingItemId, direction = -1)
            if (moved) {
                draggingItemOffset += itemHeightEstimatePx
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val resetDragState: () -> Unit = {
        draggingItemId = NO_DRAG_ID
        draggingItemOffset = 0f
        draggingStartOriginalIndex = -1
    }

    val onDragEnd: () -> Unit = {
        if (draggingItemId != NO_DRAG_ID) {
            val activeItems = displayItems.filterNot { it.isRemoving }
            val currentActiveIndex = activeItems.indexOfFirst { it.stableId == draggingItemId }
            val targetRealIndex = uiModels.getOrNull(currentActiveIndex)?.originalIndex
            if (
                draggingStartOriginalIndex != -1 &&
                targetRealIndex != null &&
                targetRealIndex != draggingStartOriginalIndex
            ) {
                onReorder(draggingStartOriginalIndex, targetRealIndex)
            }
        }
        resetDragState()
    }

    val onDragCancel: () -> Unit = {
        resetDragState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
        ) {
            item(key = "hdr_add", contentType = "action") {
                OutlinedButton(
                    onClick = {
                        haptic.performActionHaptic()
                        pendingInsertAction = PendingInsertAction.AddTop
                        onAddLevelTop()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(iconAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.btn_add_freq_top))
                }
            }

            itemsIndexed(
                items = displayItems,
                key = { _, item -> item.stableId },
                contentType = { _, _ -> "level" }
            ) { _, item ->
                val isDragging = item.stableId == draggingItemId
                val itemAnimationModifier = Modifier
                    .fillMaxWidth()
                    .animateItem(
                        fadeInSpec = if (item.isNew) {
                            tween(durationMillis = 220)
                        } else {
                            null
                        },
                        placementSpec = if (isDragging) {
                            null
                        } else {
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        },
                        fadeOutSpec = tween(durationMillis = 180)
                    )

                AnimatedLevelRow(
                    modifier = itemAnimationModifier,
                    item = item,
                    isDragging = isDragging,
                    draggingOffset = if (isDragging) draggingItemOffset else 0f,
                    onRemovalAnimationFinished = { stableId ->
                        val removedDraggingItem = draggingItemId == stableId
                        displayItems.removeAll { it.stableId == stableId }
                        if (removedDraggingItem) resetDragState()
                    },
                    onLevelClick = { onLevelClick(item.uiModel.originalIndex) },
                    onDelete = {
                        val idx = displayItems.indexOfFirst { it.stableId == item.stableId }
                        if (idx != -1 && !displayItems[idx].isRemoving) {
                            haptic.performActionHaptic()
                            displayItems[idx] = displayItems[idx].copy(
                                isRemoving = true,
                                isNew = false
                            )
                            onDeleteLevel(item.uiModel.originalIndex)
                        }
                    },
                    onCopy = {
                        haptic.performActionHaptic()
                        pendingInsertAction = PendingInsertAction.Duplicate(item.uiModel.originalIndex)
                        onDuplicateLevel(item.uiModel.originalIndex)
                    },
                    onDragStart = { onDragStart(item.stableId) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    res = LevelCardResources(
                        iconCopy = iconCopy,
                        iconBusFreq = iconBusFreq,
                        iconMin = iconMin,
                        iconMax = iconMax,
                        iconLevel = iconLevel,
                        iconVolt = iconVolt,
                        iconMenu = iconMenu,
                        iconDelete = iconDelete
                    )
                )
            }

            item(key = "ftr_add", contentType = "action") {
                OutlinedButton(
                    onClick = {
                        haptic.performActionHaptic()
                        pendingInsertAction = PendingInsertAction.AddBottom
                        onAddLevelBottom()
                    },
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

@Composable
private fun AnimatedLevelRow(
    modifier: Modifier,
    item: AnimatedLevelItem,
    isDragging: Boolean,
    draggingOffset: Float,
    onRemovalAnimationFinished: (Long) -> Unit,
    onLevelClick: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    res: LevelCardResources
) {
    val visibilityState = remember(item.stableId) { MutableTransitionState(!item.isNew) }

    LaunchedEffect(item.isRemoving) {
        visibilityState.targetState = !item.isRemoving
    }

    LaunchedEffect(item.isRemoving, visibilityState.currentState, visibilityState.isIdle) {
        if (item.isRemoving && visibilityState.isIdle && !visibilityState.currentState) {
            onRemovalAnimationFinished(item.stableId)
        }
    }

    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "dragScale"
    )
    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "dragAlpha"
    )
    val dragShadow by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 2.dp,
        animationSpec = tween(durationMillis = 120),
        label = "dragShadow"
    )

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) draggingOffset else 0f
                scaleX = dragScale
                scaleY = dragScale
                alpha = dragAlpha
                shadowElevation = dragShadow.toPx()
            }
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(durationMillis = 220)
            ),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = 180)
            )
        ) {
            LevelCard(
                uiModel = item.uiModel,
                modifier = Modifier.fillMaxWidth(),
                onLevelClick = onLevelClick,
                onDelete = onDelete,
                onCopy = onCopy,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                pulseToken = item.pulseToken,
                res = res
            )
        }
    }
}

private const val NO_DRAG_ID = Long.MIN_VALUE

private sealed class PendingInsertAction {
    object AddTop : PendingInsertAction()
    object AddBottom : PendingInsertAction()
    data class Duplicate(val sourceOriginalIndex: Int) : PendingInsertAction()
}

private data class AnimatedLevelItem(
    val stableId: Long,
    val uiModel: LevelUiModel,
    val isNew: Boolean = false,
    val isRemoving: Boolean = false,
    val pulseToken: Int = 0
)

private fun LevelUiModel.identitySignature(): String = buildString {
    append(frequencyLabel.hashCode()).append('|')
    append(voltageLabel.hashCode()).append('|')
    append(busMin).append('|')
    append(busMax).append('|')
    append(busFreq).append('|')
    append(voltageVal)
}

private fun moveDraggedItem(
    items: SnapshotStateList<AnimatedLevelItem>,
    draggingItemId: Long,
    direction: Int
): Boolean {
    val activeDisplayIndices = items.indices.filter { !items[it].isRemoving }
    val draggingDisplayIndex = items.indexOfFirst { it.stableId == draggingItemId }
    if (draggingDisplayIndex == -1) return false

    val draggingActiveIndex = activeDisplayIndices.indexOf(draggingDisplayIndex)
    if (draggingActiveIndex == -1) return false

    val targetActiveIndex = draggingActiveIndex + direction
    if (targetActiveIndex !in activeDisplayIndices.indices) return false

    val targetDisplayIndex = activeDisplayIndices[targetActiveIndex]
    items.swap(draggingDisplayIndex, targetDisplayIndex)
    return true
}

private fun mergeActiveItemsKeepingRemoving(
    previous: List<AnimatedLevelItem>,
    newActiveItems: List<AnimatedLevelItem>
): List<AnimatedLevelItem> {
    if (previous.none { it.isRemoving }) return newActiveItems

    val merged = mutableListOf<AnimatedLevelItem>()
    var nextActive = 0
    previous.forEach { item ->
        if (item.isRemoving) {
            merged += item
        } else if (nextActive < newActiveItems.size) {
            merged += newActiveItems[nextActive++]
        }
    }
    while (nextActive < newActiveItems.size) {
        merged += newActiveItems[nextActive++]
    }
    return merged
}

private fun <T> SnapshotStateList<T>.swap(i: Int, j: Int) {
    if (i == j) return
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
}

private fun HapticFeedback.performActionHaptic() {
    performHapticFeedback(HapticFeedbackType.VirtualKey)
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
    onLevelClick: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    pulseToken: Int,
    res: LevelCardResources
) {
    val frequencyText = uiModel.frequencyLabel.asString()

    val pulseAmount = remember { Animatable(0f) }
    LaunchedEffect(pulseToken) {
        if (pulseToken > 0) {
            pulseAmount.snapTo(0f)
            pulseAmount.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
            )
            pulseAmount.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        }
    }

    // Colors
    val colorMin = Color(0xFF009688)
    val colorMax = Color(0xFF9C27B0)
    val colorFreq = Color(0xFF2196F3)
    val colorVolt = Color(0xFFE91E63)

    val containerColor = lerp(
        start = MaterialTheme.colorScheme.surface,
        stop = MaterialTheme.colorScheme.tertiaryContainer,
        fraction = pulseAmount.value
    )

    Card(
        onClick = onLevelClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
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
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .pointerInput(onDragStart, onDrag, onDragEnd, onDragCancel) {
                        detectVerticalDragGestures(
                            onDragStart = { onDragStart() },
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

                if (uiModel.busMin.isNotEmpty() || uiModel.busMax.isNotEmpty() || uiModel.busFreq.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiModel.busMin.isNotEmpty()) ColorIconText(res.iconMin, uiModel.busMin, colorMin)
                        if (uiModel.busMax.isNotEmpty()) ColorIconText(res.iconMax, uiModel.busMax, colorMax)
                        if (uiModel.busFreq.isNotEmpty()) ColorIconText(res.iconBusFreq, uiModel.busFreq, colorFreq)
                    }
                }

                if (uiModel.voltageVal.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorIconText(res.iconVolt, uiModel.voltageVal, colorVolt)
                    }
                }
            }

            Row {
                IconButton(onClick = onCopy) {
                    Icon(res.iconCopy, null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
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
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
