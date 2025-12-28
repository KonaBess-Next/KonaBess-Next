package com.ireddragonicy.konabessnext.ui.adapters;

import android.view.View;

public interface StickyHeaderInterface {
    /**
     * Get the position of the header that maps to the data at the given position.
     * 
     * @param itemPosition The position of the item in the adapter data set.
     * @return The position of the header. -1 if no header found.
     */
    int getHeaderPositionForItem(int itemPosition);

    /**
     * Get the header layout resource ID for the given header position.
     * 
     * @param headerPosition The position of the header in the adapter data set.
     * @return The layout resource ID.
     */
    int getHeaderLayout(int headerPosition);

    /**
     * Bind the header view with data from the header position.
     * 
     * @param headerView     The instantiated header view.
     * @param headerPosition The position of the header in the adapter data set.
     */
    void bindHeaderData(View headerView, int headerPosition);

    /**
     * Check if the item at the given position is a header.
     * 
     * @param itemPosition The position of the item.
     * @return True if the item is a header.
     */
    boolean isHeader(int itemPosition);

    /**
     * Called when a sticky header is clicked.
     * 
     * @param headerPosition The position of the header in the data set.
     */
    void onHeaderClicked(int headerPosition);
}
