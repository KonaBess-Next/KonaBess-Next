package com.ireddragonicy.konabessnext.editor

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.AnnotatedString
import com.ireddragonicy.konabessnext.editor.highlight.ComposeHighlighter
import kotlinx.coroutines.*
import com.ireddragonicy.konabessnext.utils.DtsEditorDebug

/**
 * EditorSession manages syntax highlight caching for the DTS editor.
 * 
 * Optimizations:
 * - Only rehighlights lines that have actually changed (incremental)
 * - Handles line insertions/deletions by shifting cache indices
 * - Uses debounced background processing to avoid blocking UI
 */
object EditorSession {
    // Global Cache: line index -> highlighted AnnotatedString
    val highlightCache = mutableStateMapOf<Int, AnnotatedString>()
    
    // Track content signature per line: "$lineContent||$searchQuery"
    private val cacheContentSignature = mutableStateMapOf<Int, String>()
    
    // Previous line count for detecting structural changes
    private var previousLineCount = 0
    
    // Viewport range for memoized highlighting — only visible + buffer lines are prioritized.
    // Written from UI thread (composable), read from background (Dispatchers.Default).
    @Volatile
    var visibleRange: IntRange = 0..30
    
    // Stored lines for on-demand viewport highlighting (set by update, read by onViewportChanged)
    @Volatile private var currentLines: List<String> = emptyList()
    @Volatile private var currentSearchQuery: String = ""
    
    // Job to cancel previous work
    private var job: Job? = null
    private var scrollJob: Job? = null

    /**
     * Viewport-aware update (TRUE LAZY MEMOIZATION).
     *
     * ONLY highlights visible lines + buffer. No background processing of all 24K lines.
     * When user scrolls, onViewportChanged() highlights the new viewport on-demand.
     *
     * Combined with debounce in ViewModel:
     * - Typing: only 1 line re-highlighted via updateLine(), instant
     * - Debounce fires: visible + buffer re-validated (< 5ms with cache hits)
     * - Scrolling: onViewportChanged() highlights new viewport on-demand
     */
    fun update(lines: List<String>, searchQuery: String, scope: CoroutineScope) {
        job?.cancel()
        currentLines = lines
        currentSearchQuery = searchQuery
        
        DtsEditorDebug.logEditorSessionUpdate(lines.size, searchQuery)
        
        job = scope.launch(Dispatchers.Default) {
            val newSize = lines.size
            val oldSize = previousLineCount
            
            // Handle size shrink — trim stale cache entries
            if (newSize < oldSize) {
                withContext(Dispatchers.Main) {
                    for (i in newSize until oldSize) {
                        highlightCache.remove(i)
                        cacheContentSignature.remove(i)
                    }
                }
            }
            previousLineCount = newSize
            
            // ONLY visible + buffer — no full-file background processing
            highlightVisibleRange(lines, searchQuery)
        }
    }
    
    /**
     * Called on scroll — highlights new viewport lines on-demand.
     * Cancels previous scroll job to avoid wasted work during fast scrolling.
     * Cache hits (already-highlighted lines) are O(1) per line.
     */
    fun onViewportChanged(range: IntRange, scope: CoroutineScope) {
        visibleRange = range
        val lines = currentLines
        val query = currentSearchQuery
        if (lines.isEmpty()) return
        
        scrollJob?.cancel()
        scrollJob = scope.launch(Dispatchers.Default) {
            highlightVisibleRange(lines, query)
        }
    }
    
    /**
     * Shared highlight logic — processes ONLY visible + buffer lines.
     * Lines already in cache (matching signature) are skipped in O(1).
     * Typical cost: < 5ms for ~60 visible lines with most being cache hits.
     */
    private suspend fun highlightVisibleRange(lines: List<String>, searchQuery: String) {
        val startTime = System.nanoTime()
        val visible = visibleRange
        val bufferSize = 50
        val priorityStart = (visible.first - bufferSize).coerceAtLeast(0)
        val priorityEnd = (visible.last + bufferSize).coerceAtMost(lines.size - 1)
        
        val pendingUpdates = ArrayList<Triple<Int, AnnotatedString, String>>(64)
        var linesHighlighted = 0
        
        for (i in priorityStart..priorityEnd) {
            val line = lines.getOrNull(i) ?: continue
            val signature = "$line||$searchQuery"
            if (cacheContentSignature[i] == signature) continue
            
            val result = ComposeHighlighter.highlight(line, searchQuery)
            pendingUpdates.add(Triple(i, result, signature))
            linesHighlighted++
        }
        
        if (pendingUpdates.isNotEmpty()) {
            DtsEditorDebug.logEditorSessionHighlightBatch(pendingUpdates.size, linesHighlighted, lines.size)
            withContext(Dispatchers.Main) {
                for ((idx, hl, sig) in pendingUpdates) {
                    highlightCache[idx] = hl
                    cacheContentSignature[idx] = sig
                }
            }
        }
        
        val durationMs = (System.nanoTime() - startTime) / 1_000_000
        DtsEditorDebug.logEditorSessionComplete(linesHighlighted, lines.size, durationMs)
    }
    
    /**
     * Update a single line - optimized for typing scenarios.
     * Only rehighlights the specific line that changed.
     */
    fun updateLine(lineIndex: Int, content: String, searchQuery: String, scope: CoroutineScope) {
        val signature = "$content||$searchQuery"
        
        // Skip if unchanged
        if (cacheContentSignature[lineIndex] == signature) {
            return
        }
        
        scope.launch(Dispatchers.Default) {
            val result = ComposeHighlighter.highlight(content, searchQuery)
            withContext(Dispatchers.Main) {
                highlightCache[lineIndex] = result
                cacheContentSignature[lineIndex] = signature
            }
        }
    }
    
    /**
     * Handle line insertion - shifts cache indices down and adds new entry.
     */
    fun insertLine(atIndex: Int, content: String, searchQuery: String, totalLines: Int, scope: CoroutineScope) {
        // Shift all entries at and after atIndex down by 1
        // We process from end to avoid overwriting
        for (i in (totalLines - 1) downTo atIndex) {
            highlightCache[i + 1] = highlightCache[i] ?: AnnotatedString("")
            cacheContentSignature[i + 1] = cacheContentSignature[i] ?: ""
        }
        previousLineCount = totalLines + 1
        
        // Now highlight the new line
        updateLine(atIndex, content, searchQuery, scope)
    }
    
    /**
     * Handle line deletion - removes entry and shifts cache indices up.
     */
    fun deleteLine(atIndex: Int, totalLines: Int) {
        // Shift all entries after atIndex up by 1
        for (i in atIndex until totalLines) {
            val nextHighlight = highlightCache[i + 1]
            val nextSignature = cacheContentSignature[i + 1]
            if (nextHighlight != null) {
                highlightCache[i] = nextHighlight
            } else {
                highlightCache.remove(i)
            }
            if (nextSignature != null) {
                cacheContentSignature[i] = nextSignature
            } else {
                cacheContentSignature.remove(i)
            }
        }
        // Remove the last entry (now orphan)
        highlightCache.remove(totalLines)
        cacheContentSignature.remove(totalLines)
        previousLineCount = totalLines
    }
    
    fun clear() {
        job?.cancel()
        scrollJob?.cancel()
        highlightCache.clear()
        cacheContentSignature.clear()
        previousLineCount = 0
        currentLines = emptyList()
        currentSearchQuery = ""
    }
}
