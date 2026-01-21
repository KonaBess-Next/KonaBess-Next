package com.ireddragonicy.konabessnext.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * A wrapper for ViewPager2 that allows system back gesture to work at screen edges.
 * 
 * This wrapper intercepts touch events and when a touch starts in the edge zone,
 * it prevents ViewPager2 from claiming the touch, allowing the system to handle
 * the predictive back gesture.
 *
 * Usage: Wrap your ViewPager2 in this container in XML layout.
 */
class EdgeAwareViewPager2Wrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Edge zone width in pixels - 40dp reserved for system back gesture
    private val edgeZonePx: Int = (40 * context.resources.displayMetrics.density).toInt()
    
    private var touchStartedInEdge = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartedInEdge = ev.x < edgeZonePx || ev.x > width - edgeZonePx
                
                if (touchStartedInEdge) {
                    // Prevent child (ViewPager2) from claiming this touch
                    // Let it propagate to system for back gesture
                    return false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchStartedInEdge) {
                    // Continue to not intercept for edge touches
                    return false
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
    
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartedInEdge = ev.x < edgeZonePx || ev.x > width - edgeZonePx
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && touchStartedInEdge) {
            // The child (ViewPager2) wants to claim the touch stream, preventing parents from intercepting.
            // However, the touch started in the edge zone, so we want the System (parent) to be able to
            // intercept it for the Back Gesture.
            // We ignore the child's request and tell the parent "Go ahead, you can intercept".
            super.requestDisallowInterceptTouchEvent(false)
        } else {
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }
}
