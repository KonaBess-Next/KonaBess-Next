package com.ireddragonicy.konabessnext.ui.adapters

import android.view.View

interface StickyHeaderInterface {
    /**
     * Get the position of the header that maps to the data at the given position.
     *
     * @param itemPosition The position of the item in the adapter data set.
     * @return The position of the header. -1 if no header found.
     */
    fun getHeaderPositionForItem(itemPosition: Int): Int

    /**
     * Get the header layout resource ID for the given header position.
     *
     * @param headerPosition The position of the header in the adapter data set.
     * @return The layout resource ID.
     */
    fun getHeaderLayout(headerPosition: Int): Int

    /**
     * Bind the header view with data from the header position.
     *
     * @param headerView     The instantiated header view.
     * @param headerPosition The position of the header in the adapter data set.
     */
    fun bindHeaderData(headerView: View, headerPosition: Int)

    /**
     * Check if the item at the given position is a header.
     *
     * @param itemPosition The position of the item.
     * @return True if the item is a header.
     */
    fun isHeader(itemPosition: Int): Boolean

    /**
     * Called when a sticky header is clicked.
     *
     * @param headerPosition The position of the header in the data set.
     */
    fun onHeaderClicked(headerPosition: Int)
}
