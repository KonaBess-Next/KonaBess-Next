package com.ireddragonicy.konabessnext.utils

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ireddragonicy.konabessnext.ui.adapters.GpuFreqAdapter

open class ItemTouchHelperCallback(private val adapter: GpuFreqAdapter) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean {
        return true // Enable drag by long-pressing anywhere on the card
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false // No swipe to dismiss
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = 0
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView, 
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition

        if (fromPosition < 0 || toPosition < 0 || 
            fromPosition >= adapter.itemCount || 
            toPosition >= adapter.itemCount) {
            return false
        }

        val fromItem = adapter.items[fromPosition]
        val toItem = adapter.items[toPosition]

        // Actually the adapter items might not be accessible directly if not public?
        // GpuFreqAdapter usually has `items` or `mValues`.
        // The Java code accessed `adapter.getItems()`. 
        // I need to ensure GpuFreqAdapter has getItems() or property access.
        // Assuming conversion allows access or I'll fix adapter later.
        // The check relies on isHeader/isFooter.
        // If I haven't converted adapter yet, it's Java.
        
        if (fromItem.isHeader || fromItem.isFooter || toItem.isHeader || toItem.isFooter) {
            return false
        }

        adapter.onItemMove(fromPosition, toPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            // Item is being dragged or swiped
            viewHolder?.itemView?.let {
                it.alpha = 0.7f
                it.scaleX = 1.05f
                it.scaleY = 1.05f
            }
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.apply {
            alpha = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
        }
    }
}
