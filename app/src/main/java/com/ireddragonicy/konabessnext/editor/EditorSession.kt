package com.ireddragonicy.konabessnext.editor

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.AnnotatedString
import com.ireddragonicy.konabessnext.editor.highlight.ComposeHighlighter
import kotlinx.coroutines.*

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
    
    // Job to cancel previous work
    private var job: Job? = null

    /**
     * Full update - used when loading a new file or after major structural change.
     * Only rehighlights lines that differ from cached version.
     */
    fun update(lines: List<String>, searchQuery: String, scope: CoroutineScope) {
        job?.cancel()
        
        job = scope.launch(Dispatchers.Default) {
            val newSize = lines.size
            val oldSize = previousLineCount
            
            // Handle size changes (structural edits)
            when {
                newSize > oldSize -> {
                    // Lines were inserted - we need to shift cache down
                    // But we don't know WHERE, so we'll just revalidate
                    // The signature check will catch changed lines
                }
                newSize < oldSize -> {
                    // Lines were deleted - trim cache and signatures
                    withContext(Dispatchers.Main) {
                        for (i in newSize until oldSize) {
                            highlightCache.remove(i)
                            cacheContentSignature.remove(i)
                        }
                    }
                }
            }
            previousLineCount = newSize
            
            // Incremental update: only process lines that changed
            val chunk = 100  // Process 100 lines before yielding
            var processed = 0
            
            for (i in lines.indices) {
                if (!isActive) break
                
                val line = lines[i]
                val signature = "$line||$searchQuery"
                
                // Skip if signature matches (line hasn't changed)
                if (cacheContentSignature[i] == signature) {
                    continue
                }
                
                // Line changed - rehighlight
                val result = ComposeHighlighter.highlight(line, searchQuery)
                
                withContext(Dispatchers.Main) {
                    highlightCache[i] = result
                    cacheContentSignature[i] = signature
                }
                
                processed++
                if (processed >= chunk) {
                    processed = 0
                    yield() // Give other coroutines a chance
                }
            }
        }
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
        highlightCache.clear()
        cacheContentSignature.clear()
        previousLineCount = 0
    }
}
