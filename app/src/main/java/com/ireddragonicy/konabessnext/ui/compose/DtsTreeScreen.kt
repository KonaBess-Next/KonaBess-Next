package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.rotate

@Composable
fun DtsTreeScreen(
    rootNode: DtsNode?,
    modifier: Modifier = Modifier
) {
    if (rootNode == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Flatten tree into list for efficient LazyColumn rendering
    // We use a state that updates when expansion changes
    var flattenedList by remember(rootNode) { mutableStateOf(flattenTree(rootNode)) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        items(flattenedList, key = { it.id }) { item ->
            TreeRow(
                item = item,
                onToggleExpand = {
                    item.node?.let { node ->
                        node.isExpanded = !node.isExpanded
                        // Re-flatten efficiently? Ideally we only update this section
                        // For simplicity in non-huge trees, re-flattening is okay-ish
                        // But let's verify performance. DTS trees can be large.
                        flattenedList = flattenTree(rootNode)
                    }
                },
                onPropertyChange = { prop, newValue ->
                    prop.updateFromDisplayValue(newValue)
                    // No need to re-flatten for value change, just recompose row
                }
            )
        }
    }
}

data class TreeItem(
    val id: String, // Unique ID for LazyColumn key
    val display: String,
    val depth: Int,
    val type: ItemType,
    val node: DtsNode? = null,
    val property: DtsProperty? = null,
    val isExpanded: Boolean = false
)

enum class ItemType { NODE, PROPERTY }

// Helper to flatten tree based on expanded state
private fun flattenTree(root: DtsNode): List<TreeItem> {
    val result = ArrayList<TreeItem>()
    // Start with children of root (assuming root itself is dummy container "root")
    // If root name is "root", skip it.
    
    val startNodes = if (root.name == "root") root.children else listOf(root)
    val startProps = if (root.name == "root") root.properties else emptyList()
    
    // Actually DtsTreeHelper always returns a dummy root? Let's assume passed node is top-level.
    // If it's the "root" dummy, we iterate its children.
    
    fun recurse(node: DtsNode, depth: Int) {
        // Add Node
        if (node.name != "root" || depth > 0) {
             result.add(TreeItem(
                 id = "node_${node.hashCode()}",
                 display = node.name,
                 depth = depth,
                 type = ItemType.NODE,
                 node = node,
                 isExpanded = node.isExpanded
             ))
        }

        if (node.isExpanded || (node.name == "root" && depth == 0)) {
            val nextDepth = if (node.name == "root" && depth == 0) 0 else depth + 1
            
            // Properties
            node.properties.forEach { prop ->
                result.add(TreeItem(
                    id = "prop_${prop.hashCode()}",
                    display = prop.name,
                    depth = nextDepth,
                    type = ItemType.PROPERTY,
                    property = prop
                ))
            }
            
            // Children
            node.children.forEach { child ->
                recurse(child, nextDepth)
            }
        }
    }

    // If passed root is effectively the dummy root
    if (root.name == "root") {
         // Add its properties first? Usually root file has properties.
         root.properties.forEach { prop ->
             result.add(TreeItem(
                 id = "prop_${prop.hashCode()}",
                 display = prop.name,
                 depth = 0,
                 type = ItemType.PROPERTY,
                 property = prop
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

@Composable
private fun TreeRow(
    item: TreeItem,
    onToggleExpand: () -> Unit,
    onPropertyChange: (DtsProperty, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                enabled = item.type == ItemType.NODE
            ) { 
                if (item.type == ItemType.NODE) onToggleExpand() 
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indentation
        Spacer(modifier = Modifier.width((16 + item.depth * 24).dp))

        if (item.type == ItemType.NODE) {
            // Node Icon / arrow
            val arrowRotation = if (item.isExpanded) 0f else -90f
            Icon(
                painter = painterResource(id = android.R.drawable.arrow_down_float), // Standard arrow usually points down
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = item.display,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            // Property
            Spacer(modifier = Modifier.width(32.dp)) // Offset for missing icon
            
            val prop = item.property!!
            val displayValue = remember(prop) { mutableStateOf(prop.getDisplayValue()) }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${item.display} = ",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Editable Value
                BasicTextField(
                    value = displayValue.value,
                    onValueChange = { 
                        displayValue.value = it
                        onPropertyChange(prop, it)
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace, 
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = ";",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
