package com.ireddragonicy.konabessnext.ui.compose

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty

// ─── Performance: Pre-allocated TextStyles (avoid allocation per row) ───
private val MonoSemiBold = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold
)
private val MonoRegular = androidx.compose.ui.text.TextStyle(
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
    onSearchMatchesChanged: ((Int) -> Unit)? = null
) {
    if (rootNode == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // ─── Performance: flatten once, re-flatten only on expansion changes ───
    @SuppressLint("MutableCollectionMutableState")
    var flattenedList by remember(rootNode) { mutableStateOf(flattenTree(rootNode)) }

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

    // ─── Performance: cache colors outside LazyColumn to avoid per-item lookups ───
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeHighlight = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    val passiveHighlight = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val iconTint = primaryColor.copy(alpha = 0.7f)

    // ─── Performance: pre-merge TextStyles with colors ───
    val nodeTextStyle = MaterialTheme.typography.bodyMedium.merge(MonoSemiBold)
    val propNameStyle = MaterialTheme.typography.bodyMedium.merge(MonoRegular)
    val propValueStyle = propNameStyle.copy(color = onSurfaceColor)
    val badgeStyle = MaterialTheme.typography.labelSmall

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        items(
            items = flattenedList,
            key = { it.id },
            contentType = { it.type }  // Performance: separate node/property pools
        ) { item ->
            val isSearchMatch = item.id in matchIdSet
            val isActiveMatch = item.id == activeMatchId

            TreeRow(
                item = item,
                isSearchMatch = isSearchMatch,
                isActiveMatch = isActiveMatch,
                activeHighlight = activeHighlight,
                passiveHighlight = passiveHighlight,
                primaryColor = primaryColor,
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                dividerColor = dividerColor,
                iconTint = iconTint,
                nodeTextStyle = nodeTextStyle,
                propNameStyle = propNameStyle,
                propValueStyle = propValueStyle,
                badgeStyle = badgeStyle,
                onToggleExpand = {
                    item.node?.let { node ->
                        node.isExpanded = !node.isExpanded
                        onNodeToggle?.invoke(node.getFullPath(), node.isExpanded)
                        flattenedList = flattenTree(rootNode)
                    }
                },
                onPropertyChange = { prop, newValue ->
                    prop.updateFromDisplayValue(newValue)
                }
            )
        }
    }
}

// ─── Data classes ───

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
// - All colors/styles passed as params (no MaterialTheme lookups per row)
// - Pre-computed indent/icon/childCount in TreeItem
// - contentType separation for LazyColumn recycling pools
// - Stable remembered InteractionSource per item type
// ─────────────────────────────────────────────────────────

@Composable
private fun TreeRow(
    item: TreeItem,
    isSearchMatch: Boolean,
    isActiveMatch: Boolean,
    activeHighlight: Color,
    passiveHighlight: Color,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    dividerColor: Color,
    iconTint: Color,
    nodeTextStyle: androidx.compose.ui.text.TextStyle,
    propNameStyle: androidx.compose.ui.text.TextStyle,
    propValueStyle: androidx.compose.ui.text.TextStyle,
    badgeStyle: androidx.compose.ui.text.TextStyle,
    onToggleExpand: () -> Unit,
    onPropertyChange: (DtsProperty, String) -> Unit
) {
    val rowBackground = when {
        isActiveMatch -> activeHighlight
        isSearchMatch -> passiveHighlight
        else -> Color.Transparent
    }

    // Column instead of Surface wrapping — lighter composition
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .then(
                if (item.type == ItemType.NODE)
                    Modifier.clickable(onClick = onToggleExpand)
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pre-computed indentation
            Spacer(modifier = Modifier.width(item.indent))

            if (item.type == ItemType.NODE) {
                // Expand/collapse arrow with smooth animation
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
                    tint = primaryColor.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(4.dp))

                // Node-type icon (pre-resolved in TreeItem)
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint
                )
                Spacer(modifier = Modifier.width(6.dp))

                // Node name
                Text(
                    text = item.display,
                    style = nodeTextStyle,
                    color = onSurfaceColor,
                    maxLines = 1
                )

                // Child count badge
                if (item.childCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${item.childCount}",
                        style = badgeStyle,
                        color = onSurfaceVariantColor.copy(alpha = 0.45f)
                    )
                }
            } else {
                // Property row with icon
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = onSurfaceVariantColor.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(6.dp))

                val prop = item.property!!
                val displayValue = remember(prop) { mutableStateOf(prop.getDisplayValue()) }

                // Property name
                Text(
                    text = "${item.display} = ",
                    style = propNameStyle,
                    color = primaryColor,
                    maxLines = 1
                )

                // Editable value
                BasicTextField(
                    value = displayValue.value,
                    onValueChange = {
                        displayValue.value = it
                        onPropertyChange(prop, it)
                    },
                    textStyle = propValueStyle,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    singleLine = true
                )

                Text(
                    text = ";",
                    style = propNameStyle,
                    color = onSurfaceVariantColor.copy(alpha = 0.4f)
                )
            }
        }

        // Indented divider
        HorizontalDivider(
            modifier = Modifier.padding(start = item.indent),
            thickness = 0.5.dp,
            color = dividerColor
        )
    }
}
