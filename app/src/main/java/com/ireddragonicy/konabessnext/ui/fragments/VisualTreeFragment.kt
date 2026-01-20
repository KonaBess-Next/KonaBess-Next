package com.ireddragonicy.konabessnext.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Visual Tree Editor fragment - displays DTS as an interactive tree structure.
 * Restored simplified tree view logic using DtsTreeHelper.
 */
@AndroidEntryPoint
class VisualTreeFragment : Fragment() {

    private val sharedViewModel: SharedGpuViewModel by activityViewModels()

    private var treeRecycler: RecyclerView? = null
    private var loadingView: View? = null
    private var errorView: TextView? = null
    private var adapter: DtsTreeAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_visual_tree, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        treeRecycler = view.findViewById(R.id.tree_recycler)
        loadingView = view.findViewById(R.id.loading_view)
        errorView = view.findViewById(R.id.error_view)

        treeRecycler?.layoutManager = LinearLayoutManager(requireContext())
        adapter = DtsTreeAdapter()
        treeRecycler?.adapter = adapter

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe DTS Content instead of ParseResult to show structure even if partial parse fails
                launch {
                    sharedViewModel.dtsContent.collectLatest { content ->
                        if (content.isNotEmpty()) {
                            parseAndDisplay(content)
                        } else {
                            // Show loading or empty state
                            loadingView?.isVisible = true
                            treeRecycler?.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private suspend fun parseAndDisplay(dtsContent: String) {
        withContext(Dispatchers.Default) {
             try {
                 val root = DtsTreeHelper.parse(dtsContent)
                 withContext(Dispatchers.Main) {
                     loadingView?.isVisible = false
                     errorView?.isVisible = false
                     treeRecycler?.isVisible = true
                     adapter?.setData(root)
                 }
             } catch (e: Exception) {
                 withContext(Dispatchers.Main) {
                     loadingView?.isVisible = false
                     treeRecycler?.isVisible = false
                     errorView?.isVisible = true
                     errorView?.text = "Failed to parse DTS Tree: ${e.message}"
                 }
             }
        }
    }

    // --- Adapter ---

    sealed class TreeItem {
        data class NodeItem(val node: DtsNode, val depth: Int) : TreeItem()
        data class PropItem(val property: DtsProperty, val depth: Int) : TreeItem()
    }

    private inner class DtsTreeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val visibleItems = ArrayList<TreeItem>()
        private var rootNode: DtsNode? = null

        fun setData(root: DtsNode) {
            this.rootNode = root
            rebuildList()
        }

        private fun rebuildList() {
            visibleItems.clear()
            rootNode?.let { buildRecursive(it, 0) }
            notifyDataSetChanged()
        }

        private fun buildRecursive(node: DtsNode, depth: Int) {
            // Add Node itself (Root usually hidden if depth is 0 and it's dummy, but DtsTreeHelper uses 'root' dummy)
            // If root name is "root", we might skip it or show it. Helper returns dummy root.
            
            val isDummyRoot = (node.name == "root" && depth == 0)
            
            if (!isDummyRoot) {
                visibleItems.add(TreeItem.NodeItem(node, depth))
            }

            // If expanded (or if it's the invisible root), show children
            if (isDummyRoot || node.isExpanded) {
                // Properties first
                for (prop in node.properties) {
                    visibleItems.add(TreeItem.PropItem(prop, if (isDummyRoot) depth else depth + 1))
                }
                // Children nodes
                for (child in node.children) {
                    buildRecursive(child, if (isDummyRoot) depth else depth + 1)
                }
            }
        }

        private val VIEW_TYPE_NODE = 1
        private val VIEW_TYPE_PROP = 2

        override fun getItemViewType(position: Int): Int {
            return when (visibleItems[position]) {
                is TreeItem.NodeItem -> VIEW_TYPE_NODE
                is TreeItem.PropItem -> VIEW_TYPE_PROP
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            /* 
               Ideally we inflate a layout. Assuming we can use simple list items 
               or constructing View programmatically for simplicity here.
            */
            val density = parent.resources.displayMetrics.density.toInt()
            
            val layout = android.widget.LinearLayout(parent.context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(16, 24, 16, 24)
                background = android.util.TypedValue().let { tv ->
                    parent.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                    parent.context.getDrawable(tv.resourceId)
                }
            }
            
            val icon = ImageView(parent.context).apply {
                id = 101
                layoutParams = android.widget.LinearLayout.LayoutParams(64, 64).apply { marginEnd = 16 }
            }
            
            val text = TextView(parent.context).apply {
                id = 102
                textSize = 16f
                setTextColor(0xFF333333.toInt()) // Fallback color
            }

             layout.addView(icon)
             layout.addView(text)

             return object : RecyclerView.ViewHolder(layout) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = visibleItems[position]
            val container = holder.itemView as android.widget.LinearLayout
            val icon = container.findViewById<ImageView>(101)
            val text = container.findViewById<TextView>(102)

            val density = holder.itemView.context.resources.displayMetrics.density
            val depth = when (item) {
                 is TreeItem.NodeItem -> item.depth
                 is TreeItem.PropItem -> item.depth
            }
            
            // Indentation
            container.setPadding((16 + depth * 32 * density).toInt(), 24, 16, 24)

            when (item) {
                is TreeItem.NodeItem -> {
                    icon.visibility = View.VISIBLE
                    text.text = item.node.name
                    
                    if (item.node.children.isNotEmpty() || item.node.properties.isNotEmpty()) {
                        icon.setImageResource(if (item.node.isExpanded) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float)
                        icon.setColorFilter(0xFF666666.toInt())
                        
                        holder.itemView.setOnClickListener {
                            item.node.isExpanded = !item.node.isExpanded
                            rebuildList()
                        }
                    } else {
                        icon.visibility = View.INVISIBLE
                        holder.itemView.setOnClickListener(null)
                    }
                    
                    text.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                is TreeItem.PropItem -> {
                    icon.visibility = View.INVISIBLE // Props don't collapse
                    // Display: property = value
                    val prop = item.property
                    val display = if (prop.originalValue.isNotEmpty()) "${prop.name} = ${prop.getDisplayValue()};" else "${prop.name};"
                    text.text = display
                    text.setTypeface(null, android.graphics.Typeface.NORMAL)
                    holder.itemView.setOnClickListener(null)
                }
            }
        }

        override fun getItemCount() = visibleItems.size
    }

    companion object {
        fun newInstance() = VisualTreeFragment()
    }
}
