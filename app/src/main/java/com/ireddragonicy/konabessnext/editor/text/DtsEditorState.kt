package com.ireddragonicy.konabessnext.editor.text

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

@Immutable
data class DtsEditorSnapshot(
    val bufferSnapshot: TextBufferSnapshot,
    val selection: TextSelection?
)

/**
 * Compose-facing editor state wrapper around [TextBuffer].
 *
 * The underlying [GapBufferState] keeps edits local to the current line, while
 * this class tracks selection + revision for undo/redo and persistence.
 */
@Stable
class DtsEditorState(
    initialText: String = "",
    private val bufferImpl: TextBuffer = GapBufferState(initialText)
) {
    val buffer: TextBuffer
        get() = bufferImpl

    val lines: SnapshotStateList<BufferLine>
        get() = bufferImpl.lines

    val cursor: TextCursor
        get() = bufferImpl.cursor

    var selection: TextSelection? by mutableStateOf(null)
        private set

    // Incremented on every text mutation. Useful as a debounce key.
    var revision: Long by mutableLongStateOf(0L)
        private set

    fun replaceText(text: String) {
        bufferImpl.setText(text)
        selection = null
        revision++
    }

    fun replaceLines(lines: List<String>) {
        bufferImpl.setLines(lines)
        selection = null
        revision++
    }

    fun moveCursor(line: Int, column: Int, clearSelection: Boolean = true) {
        bufferImpl.moveCursor(line, column)
        if (clearSelection) {
            selection = null
        }
    }

    fun setSelection(start: TextCursor, end: TextCursor) {
        selection = TextSelection(start = start, end = end)
        bufferImpl.moveCursor(end.line, end.column)
    }

    fun clearSelection() {
        selection = null
    }

    fun insert(char: Char) {
        val deletedSelection = deleteSelectionIfAny(trackRevision = false)
        bufferImpl.insert(char)
        selection = null
        if (deletedSelection || char != '\u0000') {
            revision++
        }
    }

    fun insertText(text: String) {
        if (text.isEmpty()) return
        deleteSelectionIfAny(trackRevision = false)
        bufferImpl.insertText(text)
        selection = null
        revision++
    }

    fun deleteBackward(): Boolean {
        if (deleteSelectionIfAny(trackRevision = true)) {
            return true
        }
        val didDelete = bufferImpl.delete()
        if (didDelete) {
            selection = null
            revision++
        }
        return didDelete
    }

    fun getText(): String = bufferImpl.getText()

    fun copyLines(): List<String> = bufferImpl.copyLines()

    fun snapshot(): DtsEditorSnapshot {
        return DtsEditorSnapshot(
            bufferSnapshot = bufferImpl.snapshot(),
            selection = selection
        )
    }

    fun restore(snapshot: DtsEditorSnapshot) {
        bufferImpl.restore(snapshot.bufferSnapshot)
        selection = snapshot.selection
        revision++
    }

    /**
     * Deletes current selection by moving to end and backspacing to start.
     * Complexity is O(K), where K is selected characters.
     */
    private fun deleteSelectionIfAny(trackRevision: Boolean): Boolean {
        val selected = selection ?: return false
        if (selected.isCollapsed) {
            selection = null
            return false
        }

        val start = selected.normalizedStart
        val end = selected.normalizedEnd
        bufferImpl.moveCursor(end.line, end.column)

        // K deletions, but each deletion is local to line gap operations.
        while (bufferImpl.cursor != start) {
            if (!bufferImpl.delete()) break
        }

        bufferImpl.moveCursor(start.line, start.column)
        selection = null
        if (trackRevision) {
            revision++
        }
        return true
    }
}
