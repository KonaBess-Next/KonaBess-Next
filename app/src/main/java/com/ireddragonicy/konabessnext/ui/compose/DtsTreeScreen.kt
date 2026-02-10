package com.ireddragonicy.konabessnext.ui.compose

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// ─── Performance: Pre-allocated TextStyles (avoid allocation per row) ───
private val MonoSemiBold = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold
)
private val MonoRegular = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace
)
private val BreadcrumbBold = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold
)
private val BreadcrumbNormal = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace
)

@Composable
fun DtsTreeScreen(
    rootNode: DtsNode?,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    searchQuery: String = "",
    searchMatchIndex: Int = -1,
    onNodeToggle: ((String, Boolean) -> Unit)? = null,
    onSearchMatchesChanged: ((Int) -> Unit)? = null,
    onTreeModified: (() -> Unit)? = null
) {
    if (rootNode == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Root is always expanded — force it before every flatten
    rootNode.isExpanded = true

    // ─── Performance: flatten once, re-flatten only on expansion changes ───
    @SuppressLint("MutableCollectionMutableState")
    val flatListState = remember(rootNode) {
        mutableStateOf(flattenTree(rootNode))
    }
    var flattenedList by flatListState

    // ─── Stable callbacks (same instance across recompositions → enables TreeRow skipping) ───
    val currentOnNodeToggle by rememberUpdatedState(onNodeToggle)
    val stableOnToggle: (DtsNode) -> Unit = remember(rootNode, flatListState) {
        { node: DtsNode ->
            if (node.parent != null && node.name != "root") {
                node.isExpanded = !node.isExpanded
                currentOnNodeToggle?.invoke(node.getFullPath(), node.isExpanded)
                flatListState.value = flattenTree(rootNode)
            }
        }
    }
    val stableOnPropertyChange: (DtsProperty, String) -> Unit = remember {
        { prop: DtsProperty, newValue: String -> prop.updateFromDisplayValue(newValue) }
    }

    // Stable callback for tree-to-text sync after editing completes
    val currentOnTreeModified = rememberUpdatedState(onTreeModified)
    val stableOnEditComplete: () -> Unit = remember {
        { currentOnTreeModified.value?.invoke() }
    }

    // ─── Deep search entire tree — O(N), includes collapsed subtrees ───
    val deepMatches = remember(rootNode, searchQuery) {
        deepSearchTree(rootNode, searchQuery)
    }

    // Report match count to parent
    LaunchedEffect(deepMatches.size) {
        onSearchMatchesChanged?.invoke(deepMatches.size)
    }

    // ─── Performance: O(1) set for highlight lookup ───
    val matchIdSet = remember(deepMatches) {
        deepMatches.mapTo(HashSet(deepMatches.size)) { it.flatId }
    }
    val activeMatchId = if (searchMatchIndex in deepMatches.indices) {
        deepMatches[searchMatchIndex].flatId
    } else ""

    // Navigate to match: expand ancestors → re-flatten → scroll
    LaunchedEffect(searchMatchIndex, deepMatches) {
        if (searchMatchIndex in deepMatches.indices) {
            val match = deepMatches[searchMatchIndex]
            if (match.property != null) match.node.isExpanded = true
            expandAncestors(match.node)
            flattenedList = flattenTree(rootNode)
            val targetIndex = flattenedList.indexOfFirst { it.id == match.flatId }
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    // ─── Performance: cache colors outside LazyColumn ───
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer

    // ─── Bundle ALL per-row styles into one @Immutable object ───
    // → Single equals() check instead of 14 param comparisons per TreeRow skip-check
    val style = TreeRowStyle(
        activeHighlight = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        passiveHighlight = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
        primaryColor = primaryColor,
        onSurfaceColor = onSurfaceColor,
        onSurfaceVariantColor = onSurfaceVariantColor,
        dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        iconTint = primaryColor.copy(alpha = 0.7f),
        arrowTint = primaryColor.copy(alpha = 0.6f),
        propIconTint = onSurfaceVariantColor.copy(alpha = 0.4f),
        semicolonColor = onSurfaceVariantColor.copy(alpha = 0.4f),
        badgeColor = onSurfaceVariantColor.copy(alpha = 0.45f),
        nodeTextStyle = MaterialTheme.typography.bodyMedium.merge(MonoSemiBold),
        propNameStyle = MaterialTheme.typography.bodyMedium.merge(MonoRegular),
        propValueStyle = MaterialTheme.typography.bodyMedium.merge(MonoRegular).copy(color = onSurfaceColor),
        badgeStyle = MaterialTheme.typography.labelSmall,
    )

    Column(modifier = modifier.fillMaxSize()) {
        // ─── Breadcrumb: scoped composable — reads scroll state internally ───
        // → Recomposition on scroll is isolated HERE, never reaches LazyColumn
        ScopedBreadcrumb(
            listState = listState,
            flattenedList = flattenedList,
            surfaceColor = surfaceContainerColor,
            primaryColor = primaryColor,
            onSurfaceVariantColor = onSurfaceVariantColor,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor)
        ) {
            items(
                items = flattenedList,
                key = { it.id },
                contentType = { it.type }
            ) { item ->
                TreeRow(
                    item = item,
                    isSearchMatch = item.id in matchIdSet,
                    isActiveMatch = item.id == activeMatchId,
                    style = style,
                    onToggleExpand = stableOnToggle,
                    onPropertyChange = stableOnPropertyChange,
                    onEditComplete = stableOnEditComplete
                )
            }
        }
    }
}

// ─── Sticky Breadcrumb Header ───

/**
 * Compute breadcrumb segments by walking up the node ancestry.
 * Skips synthetic "root" wrapper and "/" DTS root to avoid duplicates.
 */
private fun computeBreadcrumb(item: TreeItem): List<String> {
    val node = item.node ?: return emptyList()
    val segments = mutableListOf<String>()
    var current: DtsNode? = node
    while (current != null) {
        when (current.name) {
            "root", "/" -> { /* skip — represented by leading "/" */ }
            else -> segments.add(0, current.name)
        }
        current = current.parent
    }
    segments.add(0, "/")
    return segments
}

/**
 * Scoped breadcrumb: reads LazyListState internally.
 *
 * KEY OPTIMIZATIONS:
 * 1. snapshotFlow + distinctUntilChanged — only recomposes when breadcrumb
 *    TEXT actually changes, NOT on every scroll pixel. Adjacent items often
 *    share the same parent path, so this skips 80%+ of updates during fling.
 * 2. Cached TextStyle merges via remember — zero allocation per recompose.
 * 3. Isolated composable — recomposition never propagates to parent/LazyColumn.
 */
@Composable
private fun ScopedBreadcrumb(
    listState: LazyListState,
    flattenedList: List<TreeItem>,
    surfaceColor: Color,
    primaryColor: Color,
    onSurfaceVariantColor: Color,
) {
    // Flow-based: only emits when breadcrumb path actually changes
    var breadcrumbPath by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(flattenedList) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { idx ->
                if (idx < flattenedList.size) computeBreadcrumb(flattenedList[idx])
                else emptyList()
            }
            .distinctUntilChanged()
            .collect { breadcrumbPath = it }
    }

    if (breadcrumbPath.isEmpty()) return

    // Cached style merges — zero allocation per recompose
    val labelMedium = MaterialTheme.typography.labelMedium
    val boldStyle = remember(labelMedium) { labelMedium.merge(BreadcrumbBold) }
    val normalStyle = remember(labelMedium) { labelMedium.merge(BreadcrumbNormal) }
    val chevronTint = remember(onSurfaceVariantColor) { onSurfaceVariantColor.copy(alpha = 0.4f) }
    val normalColor = remember(onSurfaceVariantColor) { onSurfaceVariantColor.copy(alpha = 0.7f) }

    Surface(
        color = surfaceColor,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            breadcrumbPath.forEachIndexed { index, segment ->
                if (index > 0) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = chevronTint
                    )
                }
                val isLast = index == breadcrumbPath.lastIndex
                Text(
                    text = segment,
                    style = if (isLast) boldStyle else normalStyle,
                    color = if (isLast) primaryColor else normalColor,
                    maxLines = 1
                )
            }
        }
    }
}

// ─── Data classes ───

@Immutable
data class TreeItem(
    val id: String,
    val display: String,
    val depth: Int,
    val type: ItemType,
    val node: DtsNode? = null,
    val property: DtsProperty? = null,
    val isExpanded: Boolean = false,
    val icon: ImageVector,           // Pre-resolved icon
    val childCount: Int = 0,          // Pre-computed count
    val indent: Dp = 16.dp           // Pre-computed indentation
)

enum class ItemType { NODE, PROPERTY }

/**
 * Bundles all per-row styling into a single @Immutable object.
 * Compose skip-check compares 1 reference instead of 14 individual params.
 */
@Immutable
private data class TreeRowStyle(
    val activeHighlight: Color,
    val passiveHighlight: Color,
    val primaryColor: Color,
    val onSurfaceColor: Color,
    val onSurfaceVariantColor: Color,
    val dividerColor: Color,
    val iconTint: Color,
    val arrowTint: Color,
    val propIconTint: Color,
    val semicolonColor: Color,
    val badgeColor: Color,
    val nodeTextStyle: androidx.compose.ui.text.TextStyle,
    val propNameStyle: androidx.compose.ui.text.TextStyle,
    val propValueStyle: androidx.compose.ui.text.TextStyle,
    val badgeStyle: androidx.compose.ui.text.TextStyle,
)

// ─── Flatten ───

private fun flattenTree(root: DtsNode): List<TreeItem> {
    val result = ArrayList<TreeItem>(512)

    fun recurse(node: DtsNode, depth: Int) {
        val nodePath = node.getFullPath()
        if (node.name != "root" || depth > 0) {
            result.add(TreeItem(
                id = "node:$nodePath",
                display = node.name,
                depth = depth,
                type = ItemType.NODE,
                node = node,
                isExpanded = node.isExpanded,
                icon = DtsNodeIcon.forNode(node.name),
                childCount = node.children.size + node.properties.size,
                indent = (16 + depth * 20).dp
            ))
        }

        if (node.isExpanded || (node.name == "root" && depth == 0)) {
            val nextDepth = if (node.name == "root" && depth == 0) 0 else depth + 1
            val nextIndent = (16 + nextDepth * 20).dp

            node.properties.forEachIndexed { idx, prop ->
                result.add(TreeItem(
                    id = "prop:$nodePath/${prop.name}#$idx",
                    display = prop.name,
                    depth = nextDepth,
                    type = ItemType.PROPERTY,
                    node = node,
                    property = prop,
                    icon = DtsNodeIcon.propertyIcon,
                    indent = nextIndent
                ))
            }

            node.children.forEach { child ->
                recurse(child, nextDepth)
            }
        }
    }

    if (root.name == "root") {
        root.properties.forEachIndexed { idx, prop ->
            result.add(TreeItem(
                id = "prop:root/${prop.name}#$idx",
                display = prop.name,
                depth = 0,
                type = ItemType.PROPERTY,
                node = root,
                property = prop,
                icon = DtsNodeIcon.propertyIcon,
                indent = 16.dp
            ))
        }
        root.children.forEach { child ->
            recurse(child, 0)
        }
    } else {
        recurse(root, 0)
    }

    return result
}

// ─── Deep search ───

private data class DeepMatch(
    val node: DtsNode,
    val property: DtsProperty?,
    val propertyIndex: Int,
    val flatId: String
)

private fun deepSearchTree(root: DtsNode, query: String): List<DeepMatch> {
    if (query.isEmpty()) return emptyList()
    val results = mutableListOf<DeepMatch>()

    fun recurse(node: DtsNode) {
        val nodePath = node.getFullPath()
        if (node.name != "root" && node.name.contains(query, ignoreCase = true)) {
            results.add(DeepMatch(node, null, -1, "node:$nodePath"))
        }
        node.properties.forEachIndexed { idx, prop ->
            val hit = prop.name.contains(query, ignoreCase = true)
                    || prop.originalValue.contains(query, ignoreCase = true)
                    || prop.getDisplayValue().contains(query, ignoreCase = true)
            if (hit) {
                results.add(DeepMatch(node, prop, idx, "prop:$nodePath/${prop.name}#$idx"))
            }
        }
        node.children.forEach { recurse(it) }
    }

    recurse(root)
    return results
}

private fun expandAncestors(node: DtsNode) {
    var current = node.parent
    while (current != null) {
        current.isExpanded = true
        current = current.parent
    }
}

// ─────────────────────────────────────────────────────────
// TreeRow: Performance-optimized composable
// - @Immutable TreeRowStyle bundles all colors/styles (1 skip-check vs 14)
// - Scoped breadcrumb (scroll never recomposes LazyColumn)
// - Pre-computed indent/icon/childCount in TreeItem
// - contentType separation for LazyColumn recycling pools
// - Properties render as Text by default, BasicTextField only on tap
// ─────────────────────────────────────────────────────────

@Composable
private fun TreeRow(
    item: TreeItem,
    isSearchMatch: Boolean,
    isActiveMatch: Boolean,
    style: TreeRowStyle,
    onToggleExpand: (DtsNode) -> Unit,
    onPropertyChange: (DtsProperty, String) -> Unit,
    onEditComplete: () -> Unit
) {
    val rowBackground = when {
        isActiveMatch -> style.activeHighlight
        isSearchMatch -> style.passiveHighlight
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .then(
                if (item.type == ItemType.NODE && item.node != null)
                    Modifier.clickable { onToggleExpand(item.node!!) }
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(item.indent))

            if (item.type == ItemType.NODE) {
                NodeRow(item = item, style = style)
            } else {
                PropertyRow(
                    item = item,
                    style = style,
                    onPropertyChange = onPropertyChange,
                    onEditComplete = onEditComplete
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = item.indent),
            thickness = 0.5.dp,
            color = style.dividerColor
        )
    }
}

@Composable
private fun RowScope.NodeRow(
    item: TreeItem,
    style: TreeRowStyle
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (item.isExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 150),
        label = "arrow"
    )

    Icon(
        imageVector = Icons.Rounded.ExpandMore,
        contentDescription = null,
        modifier = Modifier
            .size(18.dp)
            .rotate(arrowRotation),
        tint = style.arrowTint
    )
    Spacer(modifier = Modifier.width(4.dp))
    Icon(
        imageVector = item.icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = style.iconTint
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = item.display,
        style = style.nodeTextStyle,
        color = style.onSurfaceColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    if (item.childCount > 0) {
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${item.childCount}",
            style = style.badgeStyle,
            color = style.badgeColor
        )
    }
}

/**
 * Property row: renders as lightweight Text by default.
 * Switches to BasicTextField only when tapped for editing.
 *
 * PERF: FocusRequester + LocalFocusManager are DEFERRED — only created
 * when isEditing=true. For a tree with 500+ property rows, this avoids
 * 500 FocusRequester allocations and 500 composition local lookups.
 */
@Composable
private fun RowScope.PropertyRow(
    item: TreeItem,
    style: TreeRowStyle,
    onPropertyChange: (DtsProperty, String) -> Unit,
    onEditComplete: () -> Unit
) {
    val prop = item.property!!
    var isEditing by remember { mutableStateOf(false) }
    var tapCursorOffset by remember { mutableIntStateOf(-1) }

    Icon(
        imageVector = item.icon,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = style.propIconTint
    )
    Spacer(modifier = Modifier.width(6.dp))

    Text(
        text = "${item.display} = ",
        style = style.propNameStyle,
        color = style.primaryColor,
        maxLines = 1
    )

    if (isEditing) {
        // Heavy editing infra — only composed when user taps to edit
        val initialValue = remember { prop.getDisplayValue() }
        // Place cursor at tapped position, or end of text if unknown
        val initialCursor = remember {
            val pos = tapCursorOffset
            if (pos in 0..initialValue.length) pos else initialValue.length
        }
        var editValue by remember {
            mutableStateOf(TextFieldValue(
                text = initialValue,
                selection = TextRange(initialCursor)
            ))
        }
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        // Guard: only exit edit mode after focus has been gained at least once.
        // Prevents onFocusChanged initial-state callback from killing edit mode.
        var hasFocused by remember { mutableStateOf(false) }

        BasicTextField(
            value = editValue,
            onValueChange = {
                editValue = it
                onPropertyChange(prop, it.text)
            },
            textStyle = style.propValueStyle,
            cursorBrush = SolidColor(style.primaryColor),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hasFocused = true
                    } else if (hasFocused) {
                        isEditing = false
                        if (editValue.text != initialValue) {
                            onEditComplete()
                        }
                    }
                },
            maxLines = 1,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            )
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    } else {
        // Lightweight read-only display — zero editing overhead
        val displayValue = remember(prop) { prop.getDisplayValue() }
        // Capture text layout to convert tap position → character offset
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = displayValue.ifEmpty { "(empty)" },
            style = if (displayValue.isEmpty())
                style.propValueStyle.copy(color = style.badgeColor)
            else
                style.propValueStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 24.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val press = event.changes.firstOrNull()
                            if (press != null && press.pressed) {
                                press.consume()
                                // Convert tap X to character offset
                                val layout = textLayoutResult
                                tapCursorOffset = if (layout != null) {
                                    try {
                                        layout.getOffsetForPosition(
                                            Offset(press.position.x, press.position.y)
                                        )
                                    } catch (_: Exception) { -1 }
                                } else -1
                                isEditing = true
                            }
                        }
                    }
                }
        )
    }

    Text(
        text = ";",
        style = style.propNameStyle,
        color = style.semicolonColor
    )
}
