package com.ireddragonicy.konabessnext.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.OverScroller
import androidx.core.graphics.PathParser
import androidx.core.view.ViewCompat
import com.ireddragonicy.konabessnext.model.dts.DeepMatch
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import com.ireddragonicy.konabessnext.model.dts.ItemType
import com.ireddragonicy.konabessnext.model.dts.TreeItem
import com.ireddragonicy.konabessnext.ui.compose.DtsNodeIcon
import kotlin.math.max
import kotlin.math.min

class DtsTreeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        // Match CodeEditor: explicit layout params + clip to bounds
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        clipBounds = null // reset any inherited clip, will be set in onSizeChanged
    }

    // --- Data ---
    private var flattenedList: List<TreeItem> = emptyList()
    private var matchIdSet: Set<String> = emptySet()
    private var activeMatchId: String = ""

    // --- Callbacks ---
    var onNodeToggle: ((DtsNode) -> Unit)? = null
    var onPropertyEditReq: ((DtsProperty, String, RectF) -> Unit)? = null

    // --- Sizing ---
    private val density = context.resources.displayMetrics.density
    private val rowHeight = 36f * density
    private val indentWidthPx = 16f * density
    private val iconSize = 18f * density
    private val textPadding = 8f * density
    private val breadcrumbHeight = 40f * density
    private val iconSizePx = iconSize.toInt()

    // --- Scrolling ---
    private val scroller = OverScroller(context)
    private var currentScrollY = 0f
    private var maxScrollY = 0f

    // --- Colors & Paints ---
    private var primaryColor = Color.WHITE
    private var onSurfaceColor = Color.WHITE
    private var onSurfaceVariantColor = Color.GRAY
    private var surfaceColor = Color.BLACK
    private var activeHighlightColor = Color.DKGRAY
    private var passiveHighlightColor = Color.DKGRAY
    private var dividerColor = Color.DKGRAY

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * density
        typeface = Typeface.MONOSPACE
    }
    private val bgPaint = Paint()
    private val highlightPaint = Paint()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dividerPaint = Paint().apply { strokeWidth = 1f * density }
    
    private val tmpRectF = RectF()
    
    private val chevronPath = PathParser.createPathFromPathData("M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6-6-6z")

    // --- Icon Bitmaps (lazily cached) ---
    private var nodeIconCache = HashMap<String, Bitmap>()
    private var propertyIconBitmap: Bitmap? = null

    private fun getNodeIcon(nodeName: String): Bitmap {
        nodeIconCache[nodeName]?.let { return it }
        val icon = DtsNodeIcon.forNode(nodeName)
        val bmp = DtsCanvasIcons.getBitmap(icon, iconSizePx, primaryColor)
        nodeIconCache[nodeName] = bmp
        return bmp
    }

    private fun getPropertyIcon(): Bitmap {
        propertyIconBitmap?.let { return it }
        val bmp = DtsCanvasIcons.getBitmap(DtsNodeIcon.propertyIcon, iconSizePx, onSurfaceVariantColor)
        propertyIconBitmap = bmp
        return bmp
    }

    fun setColors(
        surface: Int, primary: Int, onSurface: Int, onSurfaceVariant: Int,
        divider: Int, activeHighlight: Int, passiveHighlight: Int
    ) {
        this.surfaceColor = surface
        this.primaryColor = primary
        this.onSurfaceColor = onSurface
        this.onSurfaceVariantColor = onSurfaceVariant
        this.dividerColor = divider
        this.activeHighlightColor = activeHighlight
        this.passiveHighlightColor = passiveHighlight

        setBackgroundColor(surface) // Use View's own background instead of canvas.drawColor()
        iconPaint.color = primary
        arrowPaint.color = primary
        dividerPaint.color = divider
        // Clear icon caches on color change
        nodeIconCache.clear()
        propertyIconBitmap = null
        DtsCanvasIcons.clearCache()
        invalidate()
    }

    fun setTreeData(list: List<TreeItem>, matches: List<DeepMatch>, activeId: String) {
        flattenedList = list
        matchIdSet = matches.map { it.flatId }.toSet()
        activeMatchId = activeId

        recalculateMaxScroll()
        invalidate()
    }

    private fun recalculateMaxScroll() {
        maxScrollY = max(0f, (flattenedList.size * rowHeight) - height + breadcrumbHeight)
        currentScrollY = currentScrollY.coerceIn(0f, maxScrollY)
    }

    fun scrollToMatch(matchId: String) {
        val index = flattenedList.indexOfFirst { it.id == matchId }
        if (index >= 0) {
            val targetY = index * rowHeight
            scroller.startScroll(0, currentScrollY.toInt(), 0, (targetY - currentScrollY).toInt(), 300)
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipBounds = Rect(0, 0, w, h) // Force clip to measured bounds
        recalculateMaxScroll()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always fill the exact space given by the parent (matches CodeEditor behavior)
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            if (!scroller.isFinished) scroller.abortAnimation()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            currentScrollY = (currentScrollY + distanceY).coerceIn(0f, maxScrollY)
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(
                0, currentScrollY.toInt(),
                0, -velocityY.toInt(),
                0, 0,
                0, maxScrollY.toInt(),
                0, (rowHeight * 2).toInt() // overscroll
            )
            ViewCompat.postInvalidateOnAnimation(this@DtsTreeView)
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Adjust y for breadcrumb header (which is sticky)
            if (e.y < breadcrumbHeight) return true // tapped sticky header

            val listY = e.y + currentScrollY - breadcrumbHeight
            val index = (listY / rowHeight).toInt()

            if (index in flattenedList.indices) {
                val item = flattenedList[index]
                if (item.type == ItemType.NODE) {
                    item.node?.let { onNodeToggle?.invoke(it) }
                } else if (item.type == ItemType.PROPERTY) {
                    val top = index * rowHeight - currentScrollY + breadcrumbHeight
                    val xOffset = textPadding + item.depth * indentWidthPx + iconSize + textPadding
                    
                    textPaint.typeface = Typeface.MONOSPACE
                    val propName = item.display
                    val nameWidth = textPaint.measureText(propName)
                    val equalsWidth = textPaint.measureText(" = ")
                    
                    val valueX = xOffset + iconSize + textPadding + nameWidth + equalsWidth
                    val valueWidth = textPaint.measureText(item.property?.getDisplayValue() ?: "")
                    
                    val rect = RectF(valueX, top, valueX + valueWidth + 24f * density, top + rowHeight)
                    onPropertyEditReq?.invoke(item.property!!, item.property.getDisplayValue(), rect)
                }
            }
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            currentScrollY = scroller.currY.toFloat()
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (flattenedList.isEmpty()) return

        // Background is handled by View.setBackgroundColor() — no canvas.drawColor() needed
        
        // --- Draw List ---
        canvas.save()
        // Content starts below breadcrumb
        canvas.translate(0f, breadcrumbHeight - currentScrollY)

        val firstVis = max(0, (currentScrollY / rowHeight).toInt())
        val lastVis = min(flattenedList.size - 1, ((currentScrollY + height) / rowHeight).toInt() + 1)

        val textBaselineOffset = (textPaint.descent() + textPaint.ascent()) / 2

        for (i in firstVis..lastVis) {
            val item = flattenedList[i]
            val top = i * rowHeight
            val bottom = top + rowHeight
            val centerY = top + rowHeight / 2f

            // Highlights
            if (item.id == activeMatchId) {
                highlightPaint.color = activeHighlightColor
                canvas.drawRect(0f, top, width.toFloat(), bottom, highlightPaint)
            } else if (matchIdSet.contains(item.id)) {
                highlightPaint.color = passiveHighlightColor
                canvas.drawRect(0f, top, width.toFloat(), bottom, highlightPaint)
            }

            val xOffset = textPadding + (item.depth * indentWidthPx)

            if (item.type == ItemType.NODE) {
                // Arrow
                canvas.save()
                canvas.translate(xOffset - 4f * density, centerY - iconSize / 2f)
                canvas.scale(iconSize / 24f, iconSize / 24f)
                if (item.isExpanded) {
                     canvas.rotate(90f, 12f, 12f)
                }
                arrowPaint.color = primaryColor
                canvas.drawPath(chevronPath, arrowPaint)
                canvas.restore()

                // Node Icon (from DtsNodeIcon mapping)
                val nodeBmp = getNodeIcon(item.display)
                val iconLeft = xOffset + iconSize
                canvas.drawBitmap(nodeBmp, iconLeft, centerY - iconSize / 2f, null)
                
                // Text
                textPaint.color = onSurfaceColor
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.isFakeBoldText = true
                val badgeXStart = iconLeft + iconSize + textPadding
                canvas.drawText(item.display, badgeXStart, centerY - textBaselineOffset, textPaint)

                // Child count badge
                if (item.childCount > 0) {
                    val nameWidth = textPaint.measureText(item.display)
                    val badgeX = badgeXStart + nameWidth + 8f * density
                    textPaint.color = onSurfaceVariantColor
                    textPaint.isFakeBoldText = false
                    canvas.drawText("{${item.childCount}}", badgeX, centerY - textBaselineOffset, textPaint)
                }
            } else {
                // Property Icon (from DtsNodeIcon.propertyIcon)
                val propBmp = getPropertyIcon()
                val propIconX = xOffset + iconSize
                canvas.drawBitmap(propBmp, propIconX, centerY - iconSize / 2f, null)

                // Text
                val propName = item.display
                val propVal = item.property?.getDisplayValue() ?: ""
                val fullValText = if (propVal.isNotEmpty()) " = $propVal" else ""

                val textX = propIconX + iconSize + textPadding
                
                textPaint.color = primaryColor
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.isFakeBoldText = false
                canvas.drawText(propName, textX, centerY - textBaselineOffset, textPaint)
                
                if (fullValText.isNotEmpty()) {
                    val nameWidth = textPaint.measureText(propName)
                    textPaint.color = onSurfaceColor
                    canvas.drawText(fullValText, textX + nameWidth, centerY - textBaselineOffset, textPaint)
                }
            }

            // Divider
            canvas.drawLine(xOffset + iconSize, bottom, width.toFloat(), bottom, dividerPaint)
        }
        canvas.restore()

        // --- Draw Sticky Breadcrumb ---
        drawBreadcrumb(canvas, max(0, ((currentScrollY + breadcrumbHeight) / rowHeight).toInt()))
    }

    private fun drawBreadcrumb(canvas: Canvas, firstVisIndex: Int) {
        bgPaint.color = surfaceColor
        canvas.drawRect(0f, 0f, width.toFloat(), breadcrumbHeight, bgPaint)
        
        canvas.drawLine(0f, breadcrumbHeight, width.toFloat(), breadcrumbHeight, dividerPaint)

        if (firstVisIndex in flattenedList.indices) {
            val item = flattenedList[firstVisIndex]
            val path = computeBreadcrumb(item)
            if (path.isEmpty()) return
            
            var x = textPadding
            val centerY = breadcrumbHeight / 2f
            val textBaselineOffset = (textPaint.descent() + textPaint.ascent()) / 2

            for (i in path.indices) {
                if (i > 0) {
                    textPaint.color = onSurfaceVariantColor
                    textPaint.isFakeBoldText = false
                    canvas.drawText(" ❯ ", x, centerY - textBaselineOffset, textPaint)
                    x += textPaint.measureText(" ❯ ")
                }
                
                val isLast = i == path.lastIndex
                textPaint.color = if (isLast) primaryColor else onSurfaceVariantColor
                textPaint.isFakeBoldText = isLast
                
                canvas.drawText(path[i], x, centerY - textBaselineOffset, textPaint)
                x += textPaint.measureText(path[i])
            }
        }
    }

    private fun computeBreadcrumb(item: TreeItem): List<String> {
        val node = item.node ?: return emptyList()
        val segments = mutableListOf<String>()
        var current: DtsNode? = node
        while (current != null) {
            when (current.name) {
                "root", "/" -> { }
                else -> segments.add(0, current.name)
            }
            current = current.parent
        }
        segments.add(0, "/")
        return segments
    }
}
