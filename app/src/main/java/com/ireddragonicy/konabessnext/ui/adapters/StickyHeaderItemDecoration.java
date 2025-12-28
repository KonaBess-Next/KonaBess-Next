package com.ireddragonicy.konabessnext.ui.adapters;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * ItemDecoration that draws sticky headers over the RecyclerView.
 */
public class StickyHeaderItemDecoration extends RecyclerView.ItemDecoration {

    private final StickyHeaderInterface mListener;
    private View mCurrentHeader;
    private int mCurrentHeaderPos = -1;

    public StickyHeaderItemDecoration(StickyHeaderInterface listener) {
        mListener = listener;
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDrawOver(c, parent, state);

        View topChild = parent.getChildAt(0);
        if (topChild == null) {
            return;
        }

        int topChildPosition = parent.getChildAdapterPosition(topChild);
        if (topChildPosition == RecyclerView.NO_POSITION) {
            return;
        }

        int headerPos = mListener.getHeaderPositionForItem(topChildPosition);
        if (headerPos == -1) {
            // No header for this item, try previous one if we are just scrolling into void?
            // Usually getHeaderPositionForItem returns the current section header.
            // If -1, maybe we are at top and no header yet?
            return;
        }

        View currentHeader = getHeaderView(parent, headerPos);
        fixLayoutSize(parent, currentHeader);

        int contactPoint = currentHeader.getBottom();
        View childInContact = getChildInContact(parent, contactPoint, headerPos);

        // Push effect: If the next item is a header, push the current header up
        int translationY = 0;
        if (childInContact != null && mListener.isHeader(parent.getChildAdapterPosition(childInContact))) {
            translationY = childInContact.getTop() - currentHeader.getHeight();
            // If translationY is positive, it means child is below header, no push needed
            // yet unless...
            // Sticky logic: header sticks at top (0). If next header comes at 'top', it
            // should push existing one up.
            // childInContact.getTop() is decreasing as it scrolls up.
            // Once childInContact.getTop() < currentHeader.getHeight(), we start pushing.
            // But we want to push ONLY if translationY < 0? No.
            // Normal state: translationY = 0.
            // Pushing state: translationY goes negative as child pushes up.

            // Wait, logic:
            // We want header to be at 0.
            // If next header (childInContact) is at 50, and header height is 100.
            // We want header to be at 0.
            // If next header is at 80. Header at 0.
            // If next header is at 10 (almost top). Header should be at 10 - 100 = -90.
            // So translationY = childInContact.getTop() - currentHeader.getHeight().
            // Correct.
        }

        // Only draw if we have a valid header
        c.save();
        c.translate(0, translationY);

        // Ensure background is opaque for the header, otherwise content shows through
        // We can check if it has background, if not draw a default one?
        // Rely on bindHeaderData or layout to set background.

        currentHeader.draw(c);
        c.restore();
    }

    private View getHeaderView(RecyclerView parent, int position) {
        // Simple caching for same position
        if (mCurrentHeaderPos == position && mCurrentHeader != null) {
            return mCurrentHeader;
        }

        int layoutResId = mListener.getHeaderLayout(position);
        View header = LayoutInflater.from(parent.getContext()).inflate(layoutResId, parent, false);
        mListener.bindHeaderData(header, position);

        mCurrentHeader = header;
        mCurrentHeaderPos = position;
        return header;
    }

    private void fixLayoutSize(ViewGroup parent, View view) {
        // Specs for parent (RecyclerView)
        int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);

        // Specs for child (header)
        int childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec,
                parent.getPaddingLeft() + parent.getPaddingRight(), view.getLayoutParams().width);
        int childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec,
                parent.getPaddingTop() + parent.getPaddingBottom(), view.getLayoutParams().height);

        view.measure(childWidthSpec, childHeightSpec);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private View getChildInContact(RecyclerView parent, int contactPoint, int currentHeaderPos) {
        View childInContact = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            // Check if this child is potentially the 'next' header
            // We want the child that is overlapped by the bottom of the header?
            // Or rather, the child that is approaching the top.

            if (child.getBottom() > contactPoint) {
                if (child.getTop() <= contactPoint) {
                    // This child is crossing the contact point
                    childInContact = child;
                    break;
                }
            }
        }
        return childInContact;
    }

    /**
     * Handle touch events to detect clicks on the sticky header.
     */
    public void attachToRecyclerView(RecyclerView recyclerView) {
        recyclerView.addItemDecoration(this);
        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_UP) { // Handle click on UP
                    if (mCurrentHeader != null) {
                        // Check if touch is within header bounds
                        // Header is always at top (y=0) normally, but might be translated if pushing.
                        // For simplicity, assume it's at top (0 to height).
                        // If we implemented translation logic properly we should track
                        // currentTranslationY.
                        // But usually users click the fully visible header.

                        // NOTE: This simple check assumes header is at 0, 0.
                        if (e.getY() <= mCurrentHeader.getHeight()) {
                            // potential click
                            if (mCurrentHeaderPos != -1) {
                                mListener.onHeaderClicked(mCurrentHeaderPos);
                                return true; // Consume event
                            }
                        }
                    }
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                // Do nothing
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                // Do nothing
            }
        });
    }
}
