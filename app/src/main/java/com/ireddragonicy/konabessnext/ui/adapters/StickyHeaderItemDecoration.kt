package com.ireddragonicy.konabessnext.ui.adapters

import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration

/**
 * ItemDecoration that draws sticky headers over the RecyclerView.
 */
class StickyHeaderItemDecoration(private val mListener: StickyHeaderInterface) : ItemDecoration() {
    private var mCurrentHeader: View? = null
    private var mCurrentHeaderPos = -1

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val topChild = parent.getChildAt(0) ?: return

        val topChildPosition = parent.getChildAdapterPosition(topChild)
        if (topChildPosition == RecyclerView.NO_POSITION) {
            return
        }

        val headerPos = mListener.getHeaderPositionForItem(topChildPosition)
        if (headerPos == -1) {
            return
        }

        val currentHeader = getHeaderView(parent, headerPos)
        fixLayoutSize(parent, currentHeader)

        val contactPoint = currentHeader.bottom
        val childInContact = getChildInContact(parent, contactPoint, headerPos)

        // Push effect: If the next item is a header, push the current header up
        var translationY = 0
        if (childInContact != null && mListener.isHeader(parent.getChildAdapterPosition(childInContact))) {
            translationY = childInContact.top - currentHeader.height
        }

        c.save()
        c.translate(0f, translationY.toFloat())

        currentHeader.draw(c)
        c.restore()
    }

    private fun getHeaderView(parent: RecyclerView, position: Int): View {
        if (mCurrentHeaderPos == position && mCurrentHeader != null) {
            return mCurrentHeader!!
        }

        val layoutResId = mListener.getHeaderLayout(position)
        val header = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        mListener.bindHeaderData(header, position)

        mCurrentHeader = header
        mCurrentHeaderPos = position
        return header
    }

    private fun fixLayoutSize(parent: ViewGroup, view: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        val childWidthSpec = ViewGroup.getChildMeasureSpec(
            widthSpec,
            parent.paddingLeft + parent.paddingRight, view.layoutParams.width
        )
        val childHeightSpec = ViewGroup.getChildMeasureSpec(
            heightSpec,
            parent.paddingTop + parent.paddingBottom, view.layoutParams.height
        )

        view.measure(childWidthSpec, childHeightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int, currentHeaderPos: Int): View? {
        var childInContact: View? = null
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.bottom > contactPoint) {
                if (child.top <= contactPoint) {
                    childInContact = child
                    break
                }
            }
        }
        return childInContact
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addItemDecoration(this)
        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                if (e.action == android.view.MotionEvent.ACTION_UP) {
                    if (mCurrentHeader != null) {
                        if (e.y <= mCurrentHeader!!.height) {
                            if (mCurrentHeaderPos != -1) {
                                mListener.onHeaderClicked(mCurrentHeaderPos)
                                return true
                            }
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }
}
