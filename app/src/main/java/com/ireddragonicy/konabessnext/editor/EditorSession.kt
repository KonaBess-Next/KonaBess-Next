package com.ireddragonicy.konabessnext.editor

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.AnnotatedString
import com.ireddragonicy.konabessnext.editor.highlight.ComposeHighlighter
import kotlinx.coroutines.*

object EditorSession {
    // Global Cache
    val highlightCache = mutableStateMapOf<Int, AnnotatedString>()
    
    // Track content signature
    private val cacheContentSignature = mutableStateMapOf<Int, String>()
    
    // Job to cancel previous work
    private var job: Job? = null

    fun update(lines: List<String>, searchQuery: String, scope: CoroutineScope) {
        // Cancel previous
        job?.cancel()
        
        job = scope.launch(Dispatchers.Default) {
            val chunk = 50
            var processed = 0
            
            // If size mismatch (new file), clear
            if (highlightCache.size != lines.size && lines.isNotEmpty() && highlightCache.isNotEmpty()) {
                highlightCache.clear()
                cacheContentSignature.clear()
            }
            
            for (i in lines.indices) {
                if (!isActive) break
                
                val line = lines[i]
                val signature = "$line||$searchQuery"
                
                if (cacheContentSignature[i] != signature) {
                    val result = ComposeHighlighter.highlight(line, searchQuery)
                    
                    withContext(Dispatchers.Main) {
                        highlightCache[i] = result
                        cacheContentSignature[i] = signature
                    }
                }
                
                processed++
                if (processed >= chunk) {
                    processed = 0
                    yield()
                }
            }
        }
    }
    
    fun clear() {
        job?.cancel()
        highlightCache.clear()
        cacheContentSignature.clear()
    }
}
