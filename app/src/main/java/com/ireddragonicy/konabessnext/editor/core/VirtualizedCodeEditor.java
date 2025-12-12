package com.ireddragonicy.konabessnext.editor.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.OverScroller;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.content.ClipboardManager;
import android.content.ClipData;

import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import com.ireddragonicy.konabessnext.editor.highlight.DtsHighlighter;
import com.ireddragonicy.konabessnext.editor.highlight.EditorColorScheme;
import com.ireddragonicy.konabessnext.editor.highlight.Span;

/**
 * High-performance virtualized code editor.
 * Only renders visible lines for smooth scrolling with large files.
 * Professional-grade with text selection, proper layer ordering, and IME
 * support.
 */
public class VirtualizedCodeEditor extends View {

    // Content storage
    private final TextDocument document = new TextDocument();

    // Undo/Redo Manager
    private final com.ireddragonicy.konabessnext.utils.TextEditorStateManager stateManager = new com.ireddragonicy.konabessnext.utils.TextEditorStateManager();

    // Undo/Redo Batching
    private enum ActionType {
        NONE, INSERT, DELETE, COMPLEX
    }

    private ActionType lastUndoAction = ActionType.NONE;
    private long lastUndoTime = 0;
    private static final long UNDO_BATCH_WINDOW_MS = 1000;

    // Path for handle drawing reuse
    private final Path handlePath = new Path();
    private final Path pointerPath = new Path();

    // Paint objects
    private Paint textPaint;
    private Paint lineNumberPaint;
    private Paint lineNumberBgPaint;
    private Paint cursorPaint;
    private Paint selectionPaint;
    private Paint currentLinePaint;
    private Paint separatorPaint;

    // Syntax highlighter
    private DtsHighlighter highlighter;
    private EditorColorScheme colorScheme;

    // Cached values for performance
    private float textBaseline;
    private final int[] tempPosition = new int[2]; // Reusable array for getPositionFromPoint

    // Performance optimization flags
    private boolean isDraggingSelection = false;
    private long lastActionModeUpdate = 0;
    private static final long ACTION_MODE_UPDATE_THROTTLE_MS = 100; // Throttle ActionMode updates

    // Cached normalized selection for fast drawing
    private int nStartLine = -1;
    private int nStartCol = -1;
    private int nEndLine = -1;
    private int nEndCol = -1;

    // Dimensions
    private int lineHeight;
    private int lineNumberWidth;
    private float charWidth;
    private static final int LINE_NUMBER_PADDING = 16;
    private static final int TEXT_PADDING_LEFT = 12;
    private static final float TEXT_SIZE_SP = 13f;

    // Scroll
    private OverScroller scroller;
    private GestureDetector gestureDetector;
    private int scrollX = 0;
    private int scrollY = 0;

    // Cursor
    private int cursorLine = 0;
    private int cursorColumn = 0;
    private boolean cursorVisible = true;
    private final Handler blinkHandler = new Handler(Looper.getMainLooper());
    private static final long CURSOR_BLINK_MS = 530;

    // Selection
    private boolean hasSelection = false;
    private int selStartLine = -1;
    private int selStartCol = -1;
    private int selEndLine = -1;
    private int selEndCol = -1;
    private boolean isSelecting = false;
    private boolean longPressTriggered = false;

    // Selection Handles
    private static final int HANDLE_SIZE = 40; // dp
    private float handleRadius;
    private int draggingHandle = 0; // 0=none, 1=start, 2=end
    private RectF startHandleRect = new RectF();
    private RectF endHandleRect = new RectF();
    private Paint handlePaint;

    // Context Menu
    private android.view.ActionMode actionMode;

    // Listener
    private Runnable onTextChangedListener;

    // Auto-scroll
    private static final int AUTO_SCROLL_ZONE_DP = 40;
    private static final int AUTO_SCROLL_SPEED_DP = 14;
    private int autoScrollZonePx;
    private int autoScrollSpeedPx;
    private boolean isAutoScrolling = false;
    private int autoScrollDirection = 0; // -1 up, 1 down
    private float lastTouchX, lastTouchY;

    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoScrolling)
                return;

            int deltaY = autoScrollDirection * autoScrollSpeedPx;
            int initialScrollY = scrollY;
            scrollBy(0, deltaY);

            // If we didn't actually scroll (hit boundary), stop
            if (scrollY == initialScrollY) {
                // stopAutoScroll(); // Optional: keep trying? keeping it makes it feel "stuck"
                // against wall which is fine
            } else {
                // Update selection based on new position
                if (draggingHandle != 0) {
                    updateSelectionHandleFast(lastTouchX, lastTouchY);
                } else if (isSelecting) {
                    calculateSelectionFast(lastTouchX, lastTouchY);
                }
                invalidate();
            }

            postOnAnimation(this);
        }
    };

    // Search
    private int lastSearchIndex = -1;

    public VirtualizedCodeEditor(Context context) {
        super(context);
        init(context);
    }

    public VirtualizedCodeEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VirtualizedCodeEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setLongClickable(true);

        float density = context.getResources().getDisplayMetrics().density;
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;

        handleRadius = 12 * density;
        autoScrollZonePx = (int) (AUTO_SCROLL_ZONE_DP * density);
        autoScrollSpeedPx = (int) (AUTO_SCROLL_SPEED_DP * density);

        // Text paint
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(TEXT_SIZE_SP * scaledDensity);
        textPaint.setColor(0xFFE0E0E0);

        // Line number paint
        lineNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineNumberPaint.setTypeface(Typeface.MONOSPACE);
        lineNumberPaint.setTextSize(TEXT_SIZE_SP * scaledDensity * 0.9f);
        lineNumberPaint.setColor(0xFF6A6A6A);
        lineNumberPaint.setTextAlign(Paint.Align.RIGHT);

        // Line number background
        lineNumberBgPaint = new Paint();
        lineNumberBgPaint.setColor(0xFF1A1A1A);

        // Cursor paint
        cursorPaint = new Paint();
        cursorPaint.setColor(0xFF448AFF);
        cursorPaint.setStrokeWidth(2.5f * density);

        // Selection paint
        selectionPaint = new Paint();
        selectionPaint.setColor(0x50448AFF);

        // Current line highlight
        currentLinePaint = new Paint();
        currentLinePaint.setColor(0x15FFFFFF);

        // Handle paint
        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(0xFF448AFF);

        // Calculate dimensions
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        lineHeight = (int) Math.ceil(fm.descent - fm.ascent + fm.leading) + (int) (6 * density);
        charWidth = textPaint.measureText("M");
        textBaseline = -fm.ascent; // Cache this value

        // Separator paint
        separatorPaint = new Paint();
        separatorPaint.setColor(0xFF2A2A2A);

        // Initialize syntax highlighter
        colorScheme = EditorColorScheme.getDefault();
        highlighter = new DtsHighlighter(colorScheme);
        separatorPaint.setStrokeWidth(1);

        // Scroller for fling
        scroller = new OverScroller(context);

        // Gesture detector with long press
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (!isSelecting && draggingHandle == 0) {
                    scrollBy((int) distanceX, (int) distanceY);
                    return true;
                }
                // Return false so onTouchEvent handles the drag
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (!isSelecting && draggingHandle == 0) {
                    int maxScrollX = getMaxScrollX();
                    int maxScrollY = getMaxScrollY();
                    scroller.fling(scrollX, scrollY, (int) -velocityX, (int) -velocityY,
                            0, maxScrollX, 0, maxScrollY);
                    postInvalidateOnAnimation();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (draggingHandle != 0)
                    return true;

                // Check if tapped near handles
                if (hasSelection) {
                    if (isNearHandle(e.getX(), e.getY(), startHandleRect)) {
                        draggingHandle = 1;
                        return true;
                    }
                    if (isNearHandle(e.getX(), e.getY(), endHandleRect)) {
                        draggingHandle = 2;
                        return true;
                    }
                }

                clearSelection();
                handleTap(e.getX(), e.getY());
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                longPressTriggered = true;
                if (!hasSelection) {
                    selectWordAt(e.getX(), e.getY());
                } else {
                    // Start dragging appropriate handle
                    // Logic to determine closest handle
                    // For now simplest is re-selecting word
                    selectWordAt(e.getX(), e.getY());
                }
            }

            @Override
            public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                longPressTriggered = false;

                // Check handle hit test immediately on down
                if (hasSelection) {
                    if (isNearHandle(e.getX(), e.getY(), startHandleRect)) {
                        draggingHandle = 1;
                        return true;
                    }
                    if (isNearHandle(e.getX(), e.getY(), endHandleRect)) {
                        draggingHandle = 2;
                        return true;
                    }
                }

                draggingHandle = 0;
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                selectWordAt(e.getX(), e.getY());
                return true;
            }
        });
        gestureDetector.setIsLongpressEnabled(true);

        // Initialize
        updateLineNumberWidth();
        startCursorBlink();
    }

    private void startCursorBlink() {
        blinkHandler.removeCallbacksAndMessages(null);
        blinkHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFocused() && !hasSelection) {
                    cursorVisible = !cursorVisible;
                    invalidate();
                }
                blinkHandler.postDelayed(this, CURSOR_BLINK_MS);
            }
        }, CURSOR_BLINK_MS);
    }

    private void updateLineNumberWidth() {
        String maxLineNum = String.valueOf(Math.max(document.getLineCount(), 1));
        lineNumberWidth = (int) (lineNumberPaint.measureText(maxLineNum) + LINE_NUMBER_PADDING * 2 + 8);
    }

    private int getMaxScrollX() {
        int maxLineWidth = 0;
        int count = document.getLineCount();
        for (int i = 0; i < count; i++) {
            StringBuilder line = document.getLine(i);
            int width = (int) (line.length() * charWidth);
            if (width > maxLineWidth)
                maxLineWidth = width;
        }
        return Math.max(0, maxLineWidth + 100);
    }

    private int getMaxScrollY() {
        return Math.max(0, document.getLineCount() * lineHeight - getHeight() + lineHeight);
    }

    @Override
    public void scrollBy(int x, int y) {
        scrollX = Math.max(0, Math.min(scrollX + x, getMaxScrollX()));
        scrollY = Math.max(0, Math.min(scrollY + y, getMaxScrollY()));

        // Update ActionMode position during scroll
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null && hasSelection) {
            actionMode.invalidateContentRect();
        }

        invalidate();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.getCurrX();
            scrollY = scroller.getCurrY();

            // Update ActionMode position during scroll
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null && hasSelection) {
                actionMode.invalidateContentRect();
            }

            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Calculate visible line range
        int lineCount = document.getLineCount();
        int firstVisibleLine = Math.max(0, scrollY / lineHeight);
        int lastVisibleLine = Math.min(lineCount - 1, (scrollY + height) / lineHeight + 1);

        // Use cached textBaseline instead of recalculating every frame

        // === LAYER 1: Draw text content area (clipped) ===
        canvas.save();
        canvas.clipRect(lineNumberWidth, 0, width, height);

        // Draw current line highlight
        if (cursorLine >= firstVisibleLine && cursorLine <= lastVisibleLine) {
            int cursorY = cursorLine * lineHeight - scrollY;
            canvas.drawRect(lineNumberWidth, cursorY, width, cursorY + lineHeight, currentLinePaint);
        }

        // Draw selection background
        if (hasSelection) {
            drawSelection(canvas, firstVisibleLine, lastVisibleLine);
        }

        // Draw Handles
        if (hasSelection) {
            drawHandles(canvas, firstVisibleLine, lastVisibleLine);
        }

        // Draw visible text lines with syntax highlighting
        for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
            if (i < lineCount) {
                int y = i * lineHeight - scrollY;
                StringBuilder line = document.getLine(i);
                float baseX = lineNumberWidth + TEXT_PADDING_LEFT - scrollX;
                float textY = y + textBaseline;

                // Get syntax highlighting spans for this line
                List<Span> spans = highlighter.tokenize(line);

                if (spans.isEmpty()) {
                    // No spans - draw plain text
                    textPaint.setColor(colorScheme.textColor);
                    canvas.drawText(line, 0, line.length(), baseX, textY, textPaint);
                } else {
                    // Draw each span with its color
                    for (Span span : spans) {
                        if (span.end > span.start && span.end <= line.length()) {
                            textPaint.setColor(span.color);
                            float spanX = baseX + span.start * charWidth;
                            canvas.drawText(line, span.start, span.end, spanX, textY, textPaint);
                        }
                    }
                }

                // Draw Cursor
                if (cursorVisible && !hasSelection) {
                    if (i == cursorLine) {
                        float cursorX = baseX + (cursorColumn * charWidth);
                        canvas.drawLine(cursorX, y, cursorX, y + lineHeight, cursorPaint);
                    }
                }
            }
        }

        canvas.restore();

        // === LAYER 2: Draw line numbers (always on top, fixed position) ===
        canvas.drawRect(0, 0, lineNumberWidth, height, lineNumberBgPaint);

        for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
            int y = i * lineHeight - scrollY;
            float baseline = y + textBaseline + 2;
            canvas.drawText(
                    String.valueOf(i + 1),
                    lineNumberWidth - LINE_NUMBER_PADDING,
                    baseline,
                    lineNumberPaint);
        }

        // Draw separator line - use cached paint
        canvas.drawLine(lineNumberWidth, 0, lineNumberWidth, height, separatorPaint);
    }

    /**
     * Expert-level virtualized selection highlight.
     * Optimizations applied:
     * - Pre-computed normalized selection bounds (inline)
     * - Local variable caching to avoid field lookups
     * - Direct rectangle drawing without intermediate object allocation
     * - Early exit for non-overlapping visible range
     * - Inlined calculations for maximum performance
     */
    private void drawSelection(Canvas canvas, int firstVisibleLine, int lastVisibleLine) {
        // Local copies to avoid repeated field access
        final int sStartLine = selStartLine;
        final int sStartCol = selStartCol;
        final int sEndLine = selEndLine;
        final int sEndCol = selEndCol;

        // Early exit if selection is invalid
        if (sStartLine < 0 || sEndLine < 0)
            return;

        // Use pre-computed normalized bounds
        final int normStartLine = nStartLine;
        final int normStartCol = nStartCol;
        final int normEndLine = nEndLine;
        final int normEndCol = nEndCol;

        // Early exit if selection doesn't overlap visible area
        if (normEndLine < firstVisibleLine || normStartLine > lastVisibleLine)
            return;

        // Pre-compute loop bounds - avoid Math.max/min in loop
        final int drawStart = normStartLine > firstVisibleLine ? normStartLine : firstVisibleLine;
        final int drawEnd = normEndLine < lastVisibleLine ? normEndLine : lastVisibleLine;

        // Pre-compute constant values outside loop
        final int localLineHeight = lineHeight;
        final int localScrollY = scrollY;
        final float localCharWidth = charWidth;
        final float baseX = lineNumberWidth + TEXT_PADDING_LEFT - scrollX;
        final int linesCount = document.getLineCount();
        final Paint paint = selectionPaint;
        final float minWidth = localCharWidth * 0.5f;

        // Batch drawing - iterate only visible selected lines
        for (int i = drawStart; i <= drawEnd; i++) {
            if (i >= linesCount)
                break; // Safety check

            final int lineLen = document.getLine(i).length();
            final int y = i * localLineHeight - localScrollY;

            float selLeft, selRight;

            // Inlined selection bounds calculation
            if (i == normStartLine) {
                if (i == normEndLine) {
                    // Single line selection - most common case
                    final int sc = normStartCol < 0 ? 0 : (normStartCol > lineLen ? lineLen : normStartCol);
                    final int ec = normEndCol < 0 ? 0 : (normEndCol > lineLen ? lineLen : normEndCol);
                    selLeft = baseX + sc * localCharWidth;
                    selRight = baseX + ec * localCharWidth;
                } else {
                    // First line of multi-line
                    final int sc = normStartCol < 0 ? 0 : (normStartCol > lineLen ? lineLen : normStartCol);
                    selLeft = baseX + sc * localCharWidth;
                    selRight = baseX + (lineLen + 1) * localCharWidth;
                }
            } else if (i == normEndLine) {
                // Last line of multi-line
                final int ec = normEndCol < 0 ? 0 : (normEndCol > lineLen ? lineLen : normEndCol);
                selLeft = baseX;
                selRight = baseX + ec * localCharWidth;
            } else {
                // Middle line - full width
                selLeft = baseX;
                selRight = baseX + (lineLen + 1) * localCharWidth;
            }

            // Inline minimum width check
            if (selRight <= selLeft) {
                selRight = selLeft + minWidth;
            }

            // Direct draw - no intermediate RectF allocation
            canvas.drawRect(selLeft, y, selRight, y + localLineHeight, paint);
        }
    }

    private void selectWordAt(float x, float y) {
        int[] pos = getPositionFromPoint(x, y);
        int line = pos[0];
        int col = pos[1];

        if (line >= document.getLineCount())
            return;
        StringBuilder lineContent = document.getLine(line);
        if (lineContent.length() == 0)
            return;

        // Find word boundaries
        int wordStart = col;
        int wordEnd = col;

        while (wordStart > 0 && isWordChar(lineContent.charAt(wordStart - 1))) {
            wordStart--;
        }
        while (wordEnd < lineContent.length() && isWordChar(lineContent.charAt(wordEnd))) {
            wordEnd++;
        }

        if (wordStart < wordEnd) {
            hasSelection = true;
            selStartLine = line;
            selStartCol = wordStart;
            selEndLine = line;
            selEndCol = wordEnd;
            cursorLine = line;
            cursorColumn = wordEnd;
            hasSelection = true;
            normalizeSelection();
            showKeyboard();
            showActionMode();
            invalidate();
        }
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private int[] getPositionFromPoint(float x, float y) {
        int line = (int) ((y + scrollY) / lineHeight);
        line = Math.max(0, Math.min(line, document.getLineCount() - 1));

        float textX = x - lineNumberWidth - TEXT_PADDING_LEFT + scrollX;

        // Fast calculation for Monospace font
        int col = 0;
        if (textX > 0) {
            col = Math.round(textX / charWidth);
        }

        // Clamp column to line length
        if (line < document.getLineCount()) {
            col = Math.min(col, document.getLine(line).length());
        }

        // Reuse cached array to avoid GC during drag operations
        tempPosition[0] = line;
        tempPosition[1] = col;
        return tempPosition;
    }

    private void handleTap(float x, float y) {
        int[] pos = getPositionFromPoint(x, y);
        cursorLine = pos[0];
        cursorColumn = pos[1];
        cursorVisible = true;
        showKeyboard();
        invalidate();
    }

    private void clearSelection() {
        hasSelection = false;
        selStartLine = -1;
        selStartCol = -1;
        selEndLine = -1;
        selEndCol = -1;
        isSelecting = false;
        nStartLine = -1; // Clear cached
        hideActionMode();
        invalidate();
    }

    private void showKeyboard() {
        requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled())
            return false;

        // Reset undo batch on touch (cursor move)
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            resetUndoBatch();
        }

        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }

        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Check if starting handle drag - enable hardware layer for smooth animation
                if (hasSelection) {
                    if (isNearHandle(event.getX(), event.getY(), startHandleRect)) {
                        draggingHandle = 1;
                        isDraggingSelection = true;
                        return true;
                    }
                    if (isNearHandle(event.getX(), event.getY(), endHandleRect)) {
                        draggingHandle = 2;
                        isDraggingSelection = true;
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                lastTouchX = event.getX();
                lastTouchY = event.getY();

                // Auto-scroll logic
                int height = getHeight();
                if (lastTouchY < autoScrollZonePx) {
                    autoScrollDirection = -1;
                    startAutoScroll();
                } else if (lastTouchY > height - autoScrollZonePx) {
                    autoScrollDirection = 1;
                    startAutoScroll();
                } else {
                    stopAutoScroll();
                }

                if (draggingHandle != 0) {
                    // Update specific handle - lightweight update
                    updateSelectionHandleFast(event.getX(), event.getY());
                    // Use invalidate() directly for immediate response
                    invalidate();
                    return true;
                } else if (isSelecting) {
                    // Original drag selection logic - also use fast path
                    calculateSelectionFast(event.getX(), event.getY());
                    invalidate();
                    return true;
                } else if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stopAutoScroll();
                if (draggingHandle != 0 || isDraggingSelection) {
                    draggingHandle = 0;
                }
                if (hasSelection) {
                    showActionMode(); // Only update ActionMode on release
                }
                isSelecting = false;
                break;
        }

        return super.onTouchEvent(event);
    }

    // Fast version without ActionMode updates during drag
    private void updateSelectionHandleFast(float x, float y) {
        int[] pos = getPositionFromPoint(x, y);
        int line = pos[0];
        int col = pos[1];

        if (draggingHandle == 1) { // Start handle
            selStartLine = line;
            selStartCol = col;
        } else if (draggingHandle == 2) { // End handle
            selEndLine = line;
            selEndCol = col;
        }

        // Auto-swap handles if start crosses end
        boolean startBeforeEnd = selStartLine < selEndLine ||
                (selStartLine == selEndLine && selStartCol <= selEndCol);

        if (!startBeforeEnd) {
            // Swap the selection positions
            int tempLine = selStartLine;
            int tempCol = selStartCol;
            selStartLine = selEndLine;
            selStartCol = selEndCol;
            selEndLine = tempLine;
            selEndCol = tempCol;

            // Also swap which handle we're dragging
            draggingHandle = (draggingHandle == 1) ? 2 : 1;
        }
        // Note: NO ActionMode update here - only update on release for smooth
        // performance
        normalizeSelection();
    }

    // Fast version without ActionMode updates during drag
    private void calculateSelectionFast(float x, float y) {
        int[] pos = getPositionFromPoint(x, y);
        selEndLine = pos[0];
        selEndCol = pos[1];
        cursorLine = pos[0];
        cursorColumn = pos[1];
        // Note: NO ActionMode update here - only ensureCursorVisible on release
        normalizeSelection();
    }

    private boolean isNearHandle(float x, float y, RectF handleRect) {
        float touchSize = handleRadius * 4; // Larger touch area
        return x >= handleRect.left - touchSize && x <= handleRect.right + touchSize &&
                y >= handleRect.top - touchSize && y <= handleRect.bottom + touchSize;
    }

    private void updateSelectionHandle(float x, float y) {
        int[] pos = getPositionFromPoint(x, y);
        int line = pos[0];
        int col = pos[1];

        if (draggingHandle == 1) { // Start handle
            selStartLine = line;
            selStartCol = col;
        } else if (draggingHandle == 2) { // End handle
            selEndLine = line;
            selEndCol = col;
        }

        // Auto-swap handles if start crosses end (or vice versa)
        // This ensures the dragged handle continues to follow the finger correctly
        boolean startBeforeEnd = selStartLine < selEndLine ||
                (selStartLine == selEndLine && selStartCol <= selEndCol);

        if (!startBeforeEnd) {
            // Swap the selection positions
            int tempLine = selStartLine;
            int tempCol = selStartCol;
            selStartLine = selEndLine;
            selStartCol = selEndCol;
            selEndLine = tempLine;
            selEndCol = tempCol;

            // Also swap which handle we're dragging so the finger continues
            // to control the correct handle
            draggingHandle = (draggingHandle == 1) ? 2 : 1;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null) {
            actionMode.invalidateContentRect();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null) {
            actionMode.invalidateContentRect();
        }
        normalizeSelection();
    }

    private void startAutoScroll() {
        if (!isAutoScrolling) {
            isAutoScrolling = true;
            postOnAnimation(autoScrollRunnable);
        }
    }

    private void stopAutoScroll() {
        if (isAutoScrolling) {
            isAutoScrolling = false;
            removeCallbacks(autoScrollRunnable);
        }
    }

    private void drawHandles(Canvas canvas, int firstVisibleLine, int lastVisibleLine) {
        if (!hasSelection)
            return;

        // Normalize first
        int startL = selStartLine, startC = selStartCol;
        int endL = selEndLine, endC = selEndCol;

        if (startL > endL || (startL == endL && startC > endC)) {
            int tL = startL;
            int tC = startC;
            startL = endL;
            startC = endC;
            endL = tL;
            endC = tC;
        }

        // Draw Start Handle
        if (startL >= firstVisibleLine && startL <= lastVisibleLine) {
            float x = lineNumberWidth + TEXT_PADDING_LEFT - scrollX;
            if (startL < document.getLineCount()) {
                StringBuilder line = document.getLine(startL);
                int safeCol = Math.min(startC, line.length());
                x += safeCol * charWidth;
            }
            int y = startL * lineHeight - scrollY + lineHeight;

            drawHandle(canvas, x, y, true, startHandleRect);
        }

        // Draw End Handle
        if (endL >= firstVisibleLine && endL <= lastVisibleLine) {
            float x = lineNumberWidth + TEXT_PADDING_LEFT - scrollX;
            if (endL < document.getLineCount()) {
                StringBuilder line = document.getLine(endL);
                int safeCol = Math.min(endC, line.length());
                x += safeCol * charWidth;
            }
            int y = endL * lineHeight - scrollY + lineHeight;

            drawHandle(canvas, x, y, false, endHandleRect);
        }
    }

    // Add drawHandles call to onDraw
    // NOTE: This will require editing onDraw separately or relying on the user to
    // insert it.
    // Since I'm using replace_file_content, I'll update onDraw in next step or use
    // multi_replace if possible.
    // For this tool call, I'm replacing the bottom part of file handling input.

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
        return new EditorInputConnection(this, true);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    // Input connection for IME
    private class EditorInputConnection extends BaseInputConnection {
        public EditorInputConnection(View targetView, boolean fullEditor) {
            super(targetView, fullEditor);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (hasSelection) {
                deleteSelection();
            }
            insertText(text.toString());
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            if (hasSelection) {
                deleteSelection();
            } else {
                for (int i = 0; i < beforeLength; i++) {
                    deleteCharBeforeCursor();
                }
            }
            return true;
        }

        @Override
        public CharSequence getTextBeforeCursor(int length, int flags) {
            if (cursorLine >= document.getLineCount())
                return "";
            StringBuilder line = document.getLine(cursorLine);
            int start = Math.max(0, cursorColumn - length);
            int end = Math.min(cursorColumn, line.length());
            if (start >= end)
                return "";
            return line.substring(start, end);
        }

        @Override
        public CharSequence getTextAfterCursor(int length, int flags) {
            if (cursorLine >= document.getLineCount())
                return "";
            StringBuilder line = document.getLine(cursorLine);
            int start = Math.min(cursorColumn, line.length());
            int end = Math.min(start + length, line.length());
            if (start >= end)
                return "";
            return line.substring(start, end);
        }

        @Override
        public CharSequence getSelectedText(int flags) {
            if (!hasSelection)
                return null;
            return getSelectionText();
        }
    }

    private void deleteSelection() {
        if (!hasSelection)
            return;

        int startLine = Math.min(selStartLine, selEndLine);
        int endLine = Math.max(selStartLine, selEndLine);
        int startCol = (startLine == selStartLine) ? selStartCol : selEndCol;
        int endCol = (endLine == selEndLine) ? selEndCol : selStartCol;

        if (selStartLine > selEndLine || (selStartLine == selEndLine && selStartCol > selEndCol)) {
            int temp = startCol;
            startCol = endCol;
            endCol = temp;
        }

        // Complex delete always snapshots
        snapshot(ActionType.COMPLEX);

        document.delete(startLine, startCol, endLine, endCol);

        cursorLine = startLine;
        cursorColumn = startCol;
        clearSelection();
        updateLineNumberWidth();
        notifyTextChanged();
        invalidate();
    }

    // Helper to snapshot current state with batching logic
    private void snapshot(ActionType type) {
        long now = System.currentTimeMillis();
        boolean shouldSnapshot = false;

        if (type == ActionType.COMPLEX) {
            shouldSnapshot = true;
        } else if (type != lastUndoAction) {
            shouldSnapshot = true;
        } else if (now - lastUndoTime > UNDO_BATCH_WINDOW_MS) {
            shouldSnapshot = true;
        }

        if (shouldSnapshot) {
            stateManager.snapshot(document.getLines());
            lastUndoAction = type;
        }

        lastUndoTime = now;
    }

    private void resetUndoBatch() {
        lastUndoAction = ActionType.NONE;
    }

    private void snapshot() {
        snapshot(ActionType.COMPLEX);
    }

    private void insertText(String text) {
        // Determine type
        ActionType type = ActionType.INSERT;
        if (text.length() > 1) {
            type = ActionType.COMPLEX;
        } else if (text.equals("\n")) {
            type = ActionType.COMPLEX; // Newline always breaks batch
        }

        snapshot(type);

        document.insert(cursorLine, cursorColumn, text);

        // Update cursor position
        String[] parts = text.split("\n", -1);
        if (parts.length == 1) {
            cursorColumn += text.length();
        } else {
            cursorLine += parts.length - 1;
            cursorColumn = parts[parts.length - 1].length();
        }

        updateLineNumberWidth();
        ensureCursorVisible();
        notifyTextChanged();
        invalidate();
    }

    private void deleteCharBeforeCursor() {
        if (cursorLine >= document.getLineCount())
            return;

        // Snapshot with DELETE type
        snapshot(ActionType.DELETE);

        if (cursorColumn > 0) {
            document.deleteChar(cursorLine, cursorColumn);
            cursorColumn--;
        } else if (cursorLine > 0) {
            StringBuilder prevLine = document.getLine(cursorLine - 1);
            int prevLen = prevLine.length();
            document.deleteChar(cursorLine, 0); // Handles merge
            cursorLine--;
            cursorColumn = prevLen;
            updateLineNumberWidth();
        }

        notifyTextChanged();
        invalidate();
    }

    private void ensureCursorVisible() {
        int cursorY = cursorLine * lineHeight;
        if (cursorY < scrollY) {
            scrollY = cursorY;
        } else if (cursorY + lineHeight > scrollY + getHeight()) {
            scrollY = cursorY + lineHeight - getHeight();
        }

        float cursorX = 0;
        if (cursorLine < document.getLineCount() && cursorColumn > 0) {
            StringBuilder line = document.getLine(cursorLine);
            int col = Math.min(cursorColumn, line.length());
            cursorX = textPaint.measureText(line.toString(), 0, col);
        }

        if (cursorX < scrollX) {
            scrollX = (int) cursorX;
        } else if (cursorX > scrollX + getWidth() - lineNumberWidth - TEXT_PADDING_LEFT - 50) {
            scrollX = (int) (cursorX - getWidth() + lineNumberWidth + TEXT_PADDING_LEFT + 50);
        }
        scrollX = Math.max(0, scrollX);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (hasSelection) {
                    deleteSelection();
                } else {
                    deleteCharBeforeCursor();
                }
                return true;
            case KeyEvent.KEYCODE_ENTER:
                if (hasSelection)
                    deleteSelection();
                insertText("\n");
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                clearSelection();
                moveCursorLeft();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                clearSelection();
                moveCursorRight();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                clearSelection();
                moveCursorUp();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                clearSelection();
                moveCursorDown();
                return true;
            default:
                if (event.isPrintingKey()) {
                    if (hasSelection)
                        deleteSelection();
                    char c = (char) event.getUnicodeChar();
                    insertText(String.valueOf(c));
                    return true;
                }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void moveCursorLeft() {
        if (cursorColumn > 0) {
            cursorColumn--;
        } else if (cursorLine > 0) {
            cursorLine--;
            cursorColumn = document.getLine(cursorLine).length();
        }
        cursorVisible = true;
        ensureCursorVisible();
        invalidate();
    }

    private void moveCursorRight() {
        if (cursorLine < document.getLineCount()) {
            StringBuilder line = document.getLine(cursorLine);
            if (cursorColumn < line.length()) {
                cursorColumn++;
            } else if (cursorLine < document.getLineCount() - 1) {
                cursorLine++;
                cursorColumn = 0;
            }
        }
        cursorVisible = true;
        ensureCursorVisible();
        invalidate();
    }

    private void moveCursorUp() {
        if (cursorLine > 0) {
            cursorLine--;
            cursorColumn = Math.min(cursorColumn, document.getLine(cursorLine).length());
        }
        cursorVisible = true;
        ensureCursorVisible();
        invalidate();
    }

    private void moveCursorDown() {
        if (cursorLine < document.getLineCount() - 1) {
            cursorLine++;
            cursorColumn = Math.min(cursorColumn, document.getLine(cursorLine).length());
        }
        cursorVisible = true;
        ensureCursorVisible();
        invalidate();
    }

    private void notifyTextChanged() {
        if (onTextChangedListener != null) {
            onTextChangedListener.run();
        }
    }

    // ===== Public API (compatible with RawDtsEditorActivity) =====

    public void setText(CharSequence text) {
        document.setText(text);
        cursorLine = 0;
        cursorColumn = 0;
        scrollX = 0;
        scrollY = 0;
        clearSelection();
        updateLineNumberWidth();
        invalidate();
    }

    private void drawHandle(Canvas canvas, float x, float y, boolean isLeft, RectF outRect) {
        float size = handleRadius * 2;

        // Update Hit Rect
        float radius = handleRadius;
        float cy = y + radius;

        // Handle is centered at x, and hangs below y
        // Hit box should be generous
        if (outRect != null) {
            outRect.set(x - radius * 1.5f, y, x + radius * 1.5f, y + radius * 2.5f);
        }

        handlePath.reset();

        // Teardrop shape
        // Circle center at (x, y + radius)
        // Triangle connects (x, y) to tangents

        // Simpler native look:
        handlePath.moveTo(x, y);

        // Right side curve (Top-Right quadrant)
        // Control points:
        // 1. (x + radius/1.5f, y) - starts going out but slightly down?
        // Actually, let's use a nice bulbous drop shape.
        // Start from tip (x,y), curve to right edge of circle (x+r, cy)
        handlePath.cubicTo(x + radius / 1.5f, y, x + radius, cy - radius / 2, x + radius, cy);

        // Bottom circle (Right to Left)
        // We can approximate a semi-circle with cubicTo or just use arcTo if we had a
        // rect,
        // but cubicTo is faster if hardcoded.
        // Control points for semi-circle from (x+r, cy) to (x-r, cy) going down:
        // (x+r, cy+k*r) and (x-r, cy+k*r) where k = 1.33 for circle approx?
        // Let's just use a simpler curve for the bottom that is slightly squashed or
        // round.
        handlePath.cubicTo(x + radius, cy + radius, x - radius, cy + radius, x - radius, cy);

        // Left side curve (Top-Left quadrant) back to tip
        handlePath.cubicTo(x - radius, cy - radius / 2, x - radius / 1.5f, y, x, y);

        handlePath.close();

        canvas.drawPath(handlePath, handlePaint);
    }

    public CharSequence getText() {
        return document.getText();
    }

    public List<String> getLines() {
        return document.getLines();
    }

    public void setLines(List<String> newLines) {
        document.setLines(newLines);
        cursorLine = Math.min(cursorLine, document.getLineCount() - 1);
        cursorColumn = Math.min(cursorColumn, document.getLine(cursorLine).length());
        clearSelection();
        updateLineNumberWidth();
        invalidate();
    }

    public void setOnTextChangedListener(Runnable listener) {
        this.onTextChangedListener = listener;
    }

    public boolean searchAndSelect(String query) {
        if (query == null || query.isEmpty())
            return false;

        String fullText = getText().toString();
        int startPos = lastSearchIndex + 1;
        if (startPos >= fullText.length())
            startPos = 0;

        int index = fullText.indexOf(query, startPos);
        if (index == -1 && startPos > 0) {
            index = fullText.indexOf(query, 0);
        }

        if (index != -1) {
            lastSearchIndex = index;

            int line = 0, col = 0, pos = 0;
            int count = document.getLineCount();
            for (int i = 0; i < count && pos <= index; i++) {
                int lineLen = document.getLine(i).length() + 1;
                if (pos + lineLen > index) {
                    line = i;
                    col = index - pos;
                    break;
                }
                pos += lineLen;
            }

            // Select the found text
            hasSelection = true;
            selStartLine = line;
            selStartCol = col;
            selEndLine = line;
            selEndCol = col + query.length();
            cursorLine = line;
            cursorColumn = col + query.length();

            ensureCursorVisible();
            normalizeSelection();
            showActionMode(); // Show menu
            invalidate();
            return true;
        }

        lastSearchIndex = -1;
        return false;
    }

    /**
     * Search for the previous occurrence of the query and select it.
     * Wraps around to the end when reaching the beginning.
     */
    public boolean searchPrevious(String query) {
        if (query == null || query.isEmpty())
            return false;

        String fullText = getText().toString();
        int searchEnd = lastSearchIndex - 1;
        if (searchEnd < 0)
            searchEnd = fullText.length() - 1;

        int index = fullText.lastIndexOf(query, searchEnd);
        if (index == -1 && searchEnd < fullText.length() - 1) {
            // Wrap around to end
            index = fullText.lastIndexOf(query, fullText.length() - 1);
        }

        if (index != -1) {
            lastSearchIndex = index;

            int line = 0, col = 0, pos = 0;
            int count = document.getLineCount();
            for (int i = 0; i < count && pos <= index; i++) {
                int lineLen = document.getLine(i).length() + 1;
                if (pos + lineLen > index) {
                    line = i;
                    col = index - pos;
                    break;
                }
                pos += lineLen;
            }

            // Select the found text
            hasSelection = true;
            selStartLine = line;
            selStartCol = col;
            selEndLine = line;
            selEndCol = col + query.length();
            cursorLine = line;
            cursorColumn = col + query.length();

            ensureCursorVisible();
            normalizeSelection();
            invalidate();
            return true;
        }

        return false;
    }

    /**
     * Clear any search highlighting.
     */
    public void clearSearch() {
        lastSearchIndex = -1;
        clearSelection();
    }

    private void calculateSelection(float x, float y) {
        int[] pos = getPositionFromPoint(x, y);
        selEndLine = pos[0];
        selEndCol = pos[1];
        cursorLine = pos[0];
        cursorColumn = pos[1];
        ensureCursorVisible();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null) {
            actionMode.invalidateContentRect();
        }
        invalidate();
    }

    // Action Mode
    private void showActionMode() {
        if (actionMode != null) {
            // Already showing, just update position
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                actionMode.invalidateContentRect();
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            actionMode = startActionMode(new FloatingActionModeCallback(), ActionMode.TYPE_FLOATING);
        } else {
            actionMode = startActionMode(new StandardActionModeCallback());
        }
    }

    private void hideActionMode() {
        if (actionMode != null) {
            actionMode.finish();
            actionMode = null;
        }
    }

    // Helper methods for Action Mode logic
    private boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.copy) {
            copySelection();
            mode.finish();
            return true;
        } else if (id == android.R.id.cut) {
            cutSelection();
            mode.finish();
            return true;
        } else if (id == android.R.id.paste) {
            pasteText();
            mode.finish();
            return true;
        } else if (id == android.R.id.selectAll) {
            selectAll();
            return true;
        }
        return false;
    }

    private boolean onCreateActionMode(ActionMode mode, Menu menu) {
        menu.add(0, android.R.id.copy, 0, android.R.string.copy);
        menu.add(0, android.R.id.cut, 0, android.R.string.cut);
        menu.add(0, android.R.id.paste, 0, android.R.string.paste);
        menu.add(0, android.R.id.selectAll, 0, android.R.string.selectAll);
        return true;
    }

    private void onDestroyActionMode(ActionMode mode) {
        actionMode = null;
        if (!isSelecting && draggingHandle == 0 && hasSelection) {
            clearSelection();
        }
    }

    public void undo() {
        resetUndoBatch(); // Reset batch on explicit undo
        List<String> oldState = stateManager.undo(document.getLines());
        if (oldState != null) {
            document.setLines(oldState);
            // safe cursor bounds
            cursorLine = Math.min(cursorLine, document.getLineCount() - 1);
            cursorColumn = Math.min(cursorColumn, document.getLine(cursorLine).length());
            invalidate();
            notifyTextChanged();
        }
    }

    public void redo() {
        resetUndoBatch(); // Reset batch on explicit redo
        List<String> newState = stateManager.redo(document.getLines());
        if (newState != null) {
            document.setLines(newState);
            // safe cursor bounds
            cursorLine = Math.min(cursorLine, document.getLineCount() - 1);
            cursorColumn = Math.min(cursorColumn, document.getLine(cursorLine).length());
            invalidate();
            notifyTextChanged();
        }
    }

    public boolean canUndo() {
        return stateManager.canUndo();
    }

    public boolean canRedo() {
        return stateManager.canRedo();
    }

    private class StandardActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return VirtualizedCodeEditor.this.onCreateActionMode(mode, menu);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return VirtualizedCodeEditor.this.onActionItemClicked(mode, item);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            VirtualizedCodeEditor.this.onDestroyActionMode(mode);
        }
    }

    private class FloatingActionModeCallback extends ActionMode.Callback2 {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return VirtualizedCodeEditor.this.onCreateActionMode(mode, menu);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return VirtualizedCodeEditor.this.onActionItemClicked(mode, item);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            VirtualizedCodeEditor.this.onDestroyActionMode(mode);
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            if (!hasSelection) {
                super.onGetContentRect(mode, view, outRect);
                return;
            }

            int startL = Math.min(selStartLine, selEndLine);
            int endL = Math.max(selStartLine, selEndLine);

            // Calculate actual Y positions relative to view
            int top = startL * lineHeight - scrollY;
            int bottom = (endL + 1) * lineHeight - scrollY;

            // For X: If single line, use precise width. If multiline, use full width for
            // simplicity
            int left = lineNumberWidth + TEXT_PADDING_LEFT;
            int right = getWidth();

            if (startL == endL) {
                int startC = Math.min(selStartCol, selEndCol);
                int endC = Math.max(selStartCol, selEndCol);
                // Need actual X coordinates...
                // Since we use Monospace charWidth:
                left += (startC * charWidth) - scrollX;
                right = (int) (lineNumberWidth + TEXT_PADDING_LEFT + (endC * charWidth) - scrollX);
            }

            outRect.set(left, top, right, bottom);
        }
    }

    private void copySelection() {
        if (!hasSelection)
            return;
        String text = getSelectionText();
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Code", text);
        clipboard.setPrimaryClip(clip);
    }

    private void cutSelection() {
        if (!hasSelection)
            return;
        copySelection();
        deleteSelection();
    }

    private void pasteText() {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (text != null) {
                insertText(text.toString());
            }
        }
    }

    private void selectAll() {
        if (document.getLineCount() == 0)
            return;
        selStartLine = 0;
        selStartCol = 0;
        selEndLine = document.getLineCount() - 1;
        selEndCol = document.getLine(selEndLine).length();
        selEndCol = document.getLine(selEndLine).length();
        hasSelection = true;
        normalizeSelection();
        showActionMode();
        invalidate();
    }

    private void normalizeSelection() {
        if (!hasSelection) {
            nStartLine = -1;
            return;
        }
        if (selStartLine < selEndLine || (selStartLine == selEndLine && selStartCol <= selEndCol)) {
            nStartLine = selStartLine;
            nStartCol = selStartCol;
            nEndLine = selEndLine;
            nEndCol = selEndCol;
        } else {
            nStartLine = selEndLine;
            nStartCol = selEndCol;
            nEndLine = selStartLine;
            nEndCol = selStartCol;
        }
    }

    private String getSelectionText() {
        if (!hasSelection)
            return "";

        // Use cached normalized values
        int startL = nStartLine, startC = nStartCol;
        int endL = nEndLine, endC = nEndCol;

        StringBuilder sb = new StringBuilder();
        for (int i = startL; i <= endL; i++) {
            StringBuilder line = document.getLine(i);
            int s = (i == startL) ? Math.min(startC, line.length()) : 0;
            int e = (i == endL) ? Math.min(endC, line.length()) : line.length();
            if (s < e)
                sb.append(line.substring(s, e));
            if (i < endL)
                sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        blinkHandler.removeCallbacksAndMessages(null);
        hideActionMode();
    }
}
