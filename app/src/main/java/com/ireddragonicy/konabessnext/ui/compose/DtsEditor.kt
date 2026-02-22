package com.ireddragonicy.konabessnext.ui.compose

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewParent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ireddragonicy.konabessnext.editor.sora.DtsSoraLanguage
import com.ireddragonicy.konabessnext.editor.sora.toSoraColorScheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

/**
 * Holds a reference to the Sora [CodeEditor] for external access
 * (e.g. search commands from the toolbar).
 */
class SoraEditorState {
    internal var editorRef: WeakReference<CodeEditor> = WeakReference(null)

    /** Current search match count, updated by Sora's [PublishSearchResultEvent]. */
    var matchCount by mutableIntStateOf(0)
        internal set

    /** Current search match index, updated after gotoNext/gotoPrevious. */
    var currentMatchIndex by mutableIntStateOf(-1)
        internal set

    /** Active search query. */
    var searchQuery by mutableStateOf("")
        internal set

    /** Whether word-wrap is enabled. */
    var isWordWrapEnabled by mutableStateOf(false)
        internal set

    val editor: CodeEditor? get() = editorRef.get()

    fun search(query: String) {
        searchQuery = query
        val editor = editorRef.get() ?: return
        if (query.isEmpty()) {
            if (editor.searcher.hasQuery()) {
                editor.searcher.stopSearch()
            }
            matchCount = 0
            currentMatchIndex = -1
            return
        }
        try {
            editor.searcher.search(
                query,
                EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true)
            )
        } catch (_: IllegalArgumentException) {
            // Empty or invalid pattern — ignore
        }
    }

    fun gotoNext() {
        val editor = editorRef.get() ?: return
        if (!editor.searcher.hasQuery()) return
        editor.searcher.gotoNext()
        currentMatchIndex = editor.searcher.currentMatchedPositionIndex
    }

    fun gotoPrevious() {
        val editor = editorRef.get() ?: return
        if (!editor.searcher.hasQuery()) return
        editor.searcher.gotoPrevious()
        currentMatchIndex = editor.searcher.currentMatchedPositionIndex
    }

    fun toggleWordWrap() {
        isWordWrapEnabled = !isWordWrapEnabled
        editorRef.get()?.isWordwrap = isWordWrapEnabled
    }

    fun copyAllText(): String {
        return editorRef.get()?.text?.toString() ?: ""
    }
}

@Composable
fun rememberSoraEditorState(): SoraEditorState {
    return remember { SoraEditorState() }
}

/**
 * DTS code editor backed by Sora Editor's [CodeEditor], embedded via [AndroidView].
 *
 * @param content The DTS content string to display. Changes trigger editor text update.
 * @param soraEditorState Holder for the editor reference, used for search commands.
 * @param onContentChanged Called when the user edits text (debounced 500ms).
 * @param modifier Compose modifier for the editor container.
 */
@Composable
fun DtsEditor(
    content: String,
    soraEditorState: SoraEditorState,
    onContentChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    class SyncState {
        var lastPushedContent: String = ""
    }
    val syncState = remember { SyncState() }
    
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Track the onContentChanged callback in a ref to avoid factory re-creation
    val onContentChangedRef = rememberUpdatedState(onContentChanged)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Set DTS language for syntax highlighting
                setEditorLanguage(DtsSoraLanguage())

                // Apply M3 theme
                colorScheme.applyToEditor(this)

                // Editor appearance
                typefaceText = Typeface.MONOSPACE
                typefaceLineNumber = Typeface.MONOSPACE
                setTextSize(14f)
                isLineNumberEnabled = true
                isHighlightCurrentLine = true
                tabWidth = 4

                // Word wrap (default off, user can toggle)
                isWordwrap = soraEditorState.isWordWrapEnabled

                // --- Fix: Prevent horizontal swipe from being intercepted by parent HorizontalPager ---
                // When the editor needs to scroll horizontally (non-wrap mode),
                // request the parent to not intercept touch events.
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Always request parent to not intercept when touch starts in editor
                            disallowParentIntercept(v.parent, true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Release parent after touch ends
                            disallowParentIntercept(v.parent, false)
                        }
                    }
                    // Return false to let the editor handle the touch normally
                    false
                }

                // Store reference for external access
                soraEditorState.editorRef = WeakReference(this)

                // Set initial content
                if (content.isNotEmpty()) {
                    setText(content)
                    syncState.lastPushedContent = content
                }

                // Listen for search result updates
                subscribeAlways(PublishSearchResultEvent::class.java) { _ ->
                    if (searcher.hasQuery()) {
                        soraEditorState.matchCount = searcher.matchedPositionCount
                        soraEditorState.currentMatchIndex = searcher.currentMatchedPositionIndex
                    } else {
                        soraEditorState.matchCount = 0
                        soraEditorState.currentMatchIndex = -1
                    }
                }

                // Listen for content changes with debounce
                subscribeAlways(ContentChangeEvent::class.java) { _ ->
                    debounceJob?.cancel()
                    debounceJob = scope.launch(Dispatchers.Main) {
                        delay(500)
                        val newText = text.toString()
                        if (newText != syncState.lastPushedContent) {
                            syncState.lastPushedContent = newText
                            onContentChangedRef.value(newText)
                        }
                    }
                }
            }
        },
        update = { editor ->
            // Update theme on recomposition (handles dark/light mode changes)
            colorScheme.applyToEditor(editor)

            // Sync word wrap setting
            if (editor.isWordwrap != soraEditorState.isWordWrapEnabled) {
                editor.isWordwrap = soraEditorState.isWordWrapEnabled
            }

            // Sync text content and ignore echoes from viewmodel
            if (content != syncState.lastPushedContent) {
                syncState.lastPushedContent = content
                
                // Only actually replace the editor buffer if it differs
                if (content != editor.text.toString()) {
                    val oldText = editor.text.toString()
                    val oldLine = editor.cursor.leftLine
                    val oldCol = editor.cursor.leftColumn
                    val oldScrollX = editor.scrollX
                    val oldScrollY = editor.scrollY

                    var prefixLen = 0
                    val minLen = minOf(oldText.length, content.length)
                    while (prefixLen < minLen && oldText[prefixLen] == content[prefixLen]) {
                        prefixLen++
                    }

                    var suffixLen = 0
                    val maxSuffix = minLen - prefixLen
                    while (suffixLen < maxSuffix && oldText[oldText.length - 1 - suffixLen] == content[content.length - 1 - suffixLen]) {
                        suffixLen++
                    }

                    val oldMiddleStart = prefixLen
                    val oldMiddleEnd = oldText.length - suffixLen
                    val newMiddleStart = prefixLen
                    val newMiddleEnd = content.length - suffixLen

                    val replacement = content.substring(newMiddleStart, newMiddleEnd)

                    // Calculate positions
                    var startLine = 0
                    var startCol = 0
                    var endLine = 0
                    var endCol = 0

                    // Custom index calculation since we don't know Sora's getCharPosition exactly
                    for (i in 0 until oldMiddleEnd) {
                        if (i == oldMiddleStart) {
                            startLine = endLine
                            startCol = endCol
                        }
                        if (oldText[i] == '\n') {
                            endLine++
                            endCol = 0
                        } else {
                            endCol++
                        }
                    }
                    if (oldMiddleStart == oldMiddleEnd) {
                        startLine = endLine
                        startCol = endCol
                    }

                    try {
                        // Delete old text segment
                        if (oldMiddleEnd > oldMiddleStart) {
                            editor.text.delete(startLine, startCol, endLine, endCol)
                        }

                        // Insert new text segment
                        if (replacement.isNotEmpty()) {
                            editor.text.insert(startLine, startCol, replacement)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback
                        editor.setText(content)
                    }

                    // Restore cursor, clamped to new text bounds
                    val maxLine = editor.text.lineCount - 1
                    val line = oldLine.coerceIn(0, maxLine)
                    val maxCol = editor.text.getColumnCount(line)
                    val col = oldCol.coerceIn(0, maxCol)
                    editor.setSelection(line, col)
                    
                    // Restore scroll position
                    editor.post {
                        editor.scrollTo(oldScrollX, oldScrollY)
                    }
                }
            }
        },
        onRelease = { editor ->
            debounceJob?.cancel()
            soraEditorState.editorRef = WeakReference(null)
            editor.release()
        }
    )
}

/**
 * Recursively request all ancestor [ViewParent]s to not intercept touch events.
 * This prevents the [HorizontalPager] from stealing horizontal swipe gestures
 * that belong to the code editor's horizontal scroll.
 */
private fun disallowParentIntercept(parent: ViewParent?, disallow: Boolean) {
    var p = parent
    while (p != null) {
        p.requestDisallowInterceptTouchEvent(disallow)
        p = p.parent
    }
}

/**
 * Apply the M3 color scheme to a Sora [CodeEditor].
 */
private fun ColorScheme.applyToEditor(editor: CodeEditor) {
    editor.colorScheme = toSoraColorScheme()
}
