package com.ireddragonicy.konabessnext.ui.adapters

import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import java.util.ArrayList
import java.util.Stack

class DtsTreeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StickyHeaderInterface {

    private val visibleItems = ArrayList<TreeItem>()
    private var rootNode: DtsNode? = null
    private var recyclerView: RecyclerView? = null

    // Search State
    private var searchQuery = ""
    private val searchResults = ArrayList<TreeItem>()
    private var currentSearchIndex = -1

    companion object {
        private const val TYPE_NODE = 0
        private const val TYPE_PROPERTY = 1
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    fun setRootNode(root: DtsNode) {
        this.rootNode = root
        rebuildVisibleList()
    }

    private fun rebuildVisibleList() {
        visibleItems.clear()
        if (rootNode != null) {
            // Use iterative approach to prevent StackOverflow on deep trees
            val traverseStack = Stack<NodeDepth>()
            traverseStack.push(NodeDepth(rootNode!!, 0))

            while (!traverseStack.isEmpty()) {
                val wrapper = traverseStack.pop()
                val node = wrapper.node
                val depth = wrapper.depth

                visibleItems.add(TreeItem(node, depth))

                if (node.isExpanded) {
                    // Push children in reverse so they come out in order
                    for (i in node.children.indices.reversed()) {
                        traverseStack.push(NodeDepth(node.children[i], depth + 1))
                    }

                    // Add properties immediately (reverse order not needed if adding directly, but standard loop)
                    // Wait, original logic: added properties THEN pushed children.
                    // Stack processing Order: LIFO.
                    // We popped Node. Added Node.
                    // If we push Child 2, Child 1.
                    // Stack: [Child 2, Child 1]. Pop Child 1. Process.
                    // Effectively processing children in order.
                    // Properties should be processed BEFORE children?
                    // Original code:
                    // visibleItems.add(node)
                    // Pushed children.
                    // Added properties to visibleItems.
                    // Then loop continues (processes next stack item = first child).
                    // So visible items order: Node, Properties, Child 1, Child 1 props...
                    // Correct.

                    for (prop in node.properties) {
                        visibleItems.add(TreeItem(prop, depth + 1))
                    }
                }
            }
        }
        notifyDataSetChanged()
    }

    // Helper for stack
    private class NodeDepth(val node: DtsNode, val depth: Int)

    // ============================================================================================
    // Search Logic
    // ============================================================================================

    fun search(query: String?): Boolean {
        this.searchQuery = query ?: ""
        this.searchResults.clear()
        this.currentSearchIndex = -1

        if (this.searchQuery.isEmpty()) {
            for (item in visibleItems) {
                item.isMatch = false
            }
            notifyDataSetChanged()
            return false
        }

        if (rootNode == null) return false

        val path = Stack<DtsNode>()
        expandForMatches(rootNode!!, searchQuery.lowercase(java.util.Locale.ROOT), path)

        rebuildVisibleList()

        for (item in visibleItems) {
            var isMatch = false
            if (item.item is DtsNode) {
                if ((item.item as DtsNode).name.lowercase(java.util.Locale.ROOT).contains(searchQuery.lowercase(java.util.Locale.ROOT))) {
                    isMatch = true
                }
            } else if (item.item is DtsProperty) {
                val prop = item.item as DtsProperty
                if (prop.name.lowercase(java.util.Locale.ROOT).contains(searchQuery.lowercase(java.util.Locale.ROOT)) ||
                    prop.displayValue.lowercase(java.util.Locale.ROOT).contains(searchQuery.lowercase(java.util.Locale.ROOT))
                ) {
                    isMatch = true
                }
            }

            item.isMatch = isMatch
            if (isMatch) {
                searchResults.add(item)
            }
        }

        return if (searchResults.isNotEmpty()) {
            currentSearchIndex = 0
            scrollToResult(searchResults[0])
            notifyDataSetChanged()
            true
        } else {
            notifyDataSetChanged()
            false
        }
    }

    private fun expandForMatches(node: DtsNode, query: String, path: Stack<DtsNode>): Boolean {
        path.push(node)
        var hasMatch = false

        if (node.name.lowercase(java.util.Locale.ROOT).contains(query)) hasMatch = true

        for (prop in node.properties) {
            if (prop.name.lowercase(java.util.Locale.ROOT).contains(query) || prop.displayValue.lowercase(java.util.Locale.ROOT).contains(query)) {
                hasMatch = true
            }
        }

        for (child in node.children) {
            if (expandForMatches(child, query, path)) {
                hasMatch = true
            }
        }

        if (hasMatch) {
            node.isExpanded = true
        }

        path.pop()
        return hasMatch
    }

    fun nextSearchResult(): Boolean {
        if (searchResults.isEmpty()) return false
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
        scrollToResult(searchResults[currentSearchIndex])
        notifyDataSetChanged()
        return true
    }

    fun previousSearchResult(): Boolean {
        if (searchResults.isEmpty()) return false
        currentSearchIndex = (currentSearchIndex - 1 + searchResults.size) % searchResults.size
        scrollToResult(searchResults[currentSearchIndex])
        notifyDataSetChanged()
        return true
    }

    fun clearSearch() {
        search(null)
    }

    private fun scrollToResult(item: TreeItem) {
        val index = visibleItems.indexOf(item)
        if (index != -1 && recyclerView != null) {
            recyclerView!!.scrollToPosition(index)
            (recyclerView!!.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 200)
        }
    }

    // ============================================================================================

    override fun getItemViewType(position: Int): Int {
        return if (visibleItems[position].item is DtsNode) TYPE_NODE else TYPE_PROPERTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_NODE) {
            val v = inflater.inflate(R.layout.item_dts_visual_node, parent, false)
            NodeViewHolder(v)
        } else {
            val v = inflater.inflate(R.layout.item_dts_visual_property, parent, false)
            PropertyViewHolder(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = visibleItems[position]
        if (holder is NodeViewHolder) {
            holder.bind(item.item as DtsNode, item.depth, item.isMatch)
        } else if (holder is PropertyViewHolder) {
            holder.bind(item.item as DtsProperty, item.depth, item.isMatch)
        }
    }

    override fun getItemCount(): Int {
        return visibleItems.size
    }

    // ============================================================================================
    // Sticky Header Implementation
    // ============================================================================================

    override fun getHeaderPositionForItem(itemPosition: Int): Int {
        if (itemPosition >= visibleItems.size) return -1

        // If current item is a Node, it is its own header (start of section)
        if (getItemViewType(itemPosition) == TYPE_NODE) {
            return itemPosition
        }

        // Otherwise, search backwards for the first Node (parent)
        for (i in itemPosition - 1 downTo 0) {
            if (getItemViewType(i) == TYPE_NODE) {
                return i
            }
        }
        return -1
    }

    override fun getHeaderLayout(headerPosition: Int): Int {
        return R.layout.item_dts_visual_node
    }

    override fun bindHeaderData(headerView: View, headerPosition: Int) {
        if (headerPosition != -1 && headerPosition < visibleItems.size) {
            val item = visibleItems[headerPosition]
            if (item.item is DtsNode) {
                val node = item.item as DtsNode
                // Reuse NodeViewHolder logic but for a detached view
                // We create a temporary holder just for binding
                val holder = NodeViewHolder(headerView)
                holder.bind(node, item.depth, item.isMatch)

                // Override text to show full path
                holder.nameText.text = node.getFullPath()

                // Force opaque background for sticky header
                val outValue = TypedValue()
                headerView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, outValue, true)
                if (outValue.resourceId != 0) {
                    headerView.setBackgroundResource(outValue.resourceId)
                } else {
                    headerView.setBackgroundColor(outValue.data)
                }

                headerView.elevation = 8f
            }
        }
    }

    override fun isHeader(itemPosition: Int): Boolean {
        return getItemViewType(itemPosition) == TYPE_NODE
    }

    override fun onHeaderClicked(headerPosition: Int) {
        if (headerPosition != -1 && headerPosition < visibleItems.size) {
            val item = visibleItems[headerPosition]
            if (item.item is DtsNode) {
                val node = item.item as DtsNode
                node.isExpanded = !node.isExpanded
                rebuildVisibleList()

                // If we collapsed it, we might be way down the list. Scroll to the header.
                if (!node.isExpanded) {
                    scrollToResult(item)
                }
            }
        }
    }

    // ============================================================================================
    // View Holders
    // ============================================================================================

    inner class NodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val indentation: View = itemView.findViewById(R.id.node_indentation)
        private val expandIcon: ImageView = itemView.findViewById(R.id.btn_expand)
        val nameText: TextView = itemView.findViewById(R.id.node_name)

        init {
            itemView.setOnClickListener { toggleExpand() }
            expandIcon.setOnClickListener { toggleExpand() }
        }

        private fun toggleExpand() {
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return

            val item = visibleItems[pos]
            val node = item.item as DtsNode
            node.isExpanded = !node.isExpanded

            rebuildVisibleList()
        }

        fun bind(node: DtsNode, depth: Int, isMatch: Boolean) {
            indentation.layoutParams.width = depth * 40
            indentation.requestLayout()

            if (isMatch && searchQuery.isNotEmpty()) {
                nameText.text = highlightText(node.name, searchQuery)
            } else {
                nameText.text = node.name
            }
            expandIcon.rotation = if (node.isExpanded) 0f else -90f

            if (currentSearchIndex != -1 && searchResults.size > currentSearchIndex && searchResults[currentSearchIndex].item == node) {
                itemView.setBackgroundColor(Color.parseColor("#4Dffeb3b"))
            } else {
                val outValue = TypedValue()
                itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                itemView.setBackgroundResource(outValue.resourceId)
            }
        }
    }

    inner class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val indentation: View = itemView.findViewById(R.id.prop_indentation)
        private val keyText: TextView = itemView.findViewById(R.id.prop_key)
        private val valueInput: EditText = itemView.findViewById(R.id.prop_value)
        private var currentWatcher: TextWatcher? = null

        fun bind(prop: DtsProperty, depth: Int, isMatch: Boolean) {
            indentation.layoutParams.width = depth * 40
            indentation.requestLayout()

            if (isMatch && searchQuery.isNotEmpty()) {
                keyText.text = highlightText(prop.name, searchQuery)
            } else {
                keyText.text = prop.name
            }

            if (currentWatcher != null) {
                valueInput.removeTextChangedListener(currentWatcher)
            }

            val displayVal = prop.displayValue
            valueInput.setText(displayVal)

            currentWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    prop.updateFromDisplayValue(s.toString())
                }
            }
            valueInput.addTextChangedListener(currentWatcher)

            if (currentSearchIndex != -1 && searchResults.size > currentSearchIndex && searchResults[currentSearchIndex].item == prop) {
                itemView.setBackgroundColor(Color.parseColor("#4Dffeb3b"))
            } else {
                val outValue = TypedValue()
                itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                itemView.setBackgroundResource(outValue.resourceId)
            }
        }
    }

    private fun highlightText(text: String, query: String?): SpannableString {
        val spannable = SpannableString(text)
        if (query.isNullOrEmpty()) return spannable

        val lowerText = text.lowercase(java.util.Locale.ROOT)
        val lowerQuery = query.lowercase(java.util.Locale.ROOT)

        var start = lowerText.indexOf(lowerQuery)
        while (start != -1) {
            val end = start + lowerQuery.length
            spannable.setSpan(BackgroundColorSpan(Color.parseColor("#ffeb3b")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.BLACK), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = lowerText.indexOf(lowerQuery, end)
        }
        return spannable
    }

    class TreeItem(val item: Any, val depth: Int) {
        var isMatch: Boolean = false
    }
}
