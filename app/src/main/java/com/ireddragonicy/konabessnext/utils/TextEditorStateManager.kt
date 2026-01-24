package com.ireddragonicy.konabessnext.utils

import java.util.Stack

/**
 * Manages undo/redo state for the text editor.
 * Uses snapshot-based approach where full state is captured on line focus
 * change.
 */
class TextEditorStateManager {

    companion object {
        private const val MAX_HISTORY = 50
    }

    private val undoStack: Stack<List<String>> = Stack()
    private val redoStack: Stack<List<String>> = Stack()

    /**
     * Take a snapshot of the current state.
     * Should be called when user changes focus to a different line.
     *
     * @param lines Current state of all lines (will be deep copied)
     */
    fun snapshot(lines: List<String>?) {
        if (lines == null) return

        // Don't add duplicate snapshots
        if (!undoStack.isEmpty()) {
            val lastSnapshot = undoStack.peek()
            if (areEqual(lastSnapshot, lines)) {
                return
            }
        }

        // Deep copy to prevent reference issues
        val copy = ArrayList(lines)
        undoStack.push(copy)

        // Limit stack size
        while (undoStack.size > MAX_HISTORY) {
            undoStack.removeAt(0)
        }

        // Clear redo stack when new snapshot is taken
        redoStack.clear()
    }

    /**
     * Undo to the previous state.
     *
     * @param currentLines Current state to push to redo stack
     * @return Previous state, or null if cannot undo
     */
    fun undo(currentLines: List<String>?): List<String>? {
        if (undoStack.isEmpty()) {
            return null
        }

        // Push current state to redo stack
        if (currentLines != null) {
            val copy = ArrayList(currentLines)
            redoStack.push(copy)
        }

        return undoStack.pop()
    }

    /**
     * Redo to the next state.
     *
     * @param currentLines Current state to push to undo stack
     * @return Next state, or null if cannot redo
     */
    fun redo(currentLines: List<String>?): List<String>? {
        if (redoStack.isEmpty()) {
            return null
        }

        // Push current state to undo stack
        if (currentLines != null) {
            val copy = ArrayList(currentLines)
            undoStack.push(copy)
        }

        return redoStack.pop()
    }

    /**
     * @return true if undo is available
     */
    fun canUndo(): Boolean {
        // Need at least 2 states: current + previous
        // Single snapshot is the current state, so no undo available
        return undoStack.size > 1
    }

    /**
     * @return true if redo is available
     */
    fun canRedo(): Boolean {
        return redoStack.isNotEmpty()
    }

    /**
     * Clear all history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * Compare two lists for equality.
     */
    private fun areEqual(a: List<String>?, b: List<String>?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        if (a.size != b.size) return false

        for (i in a.indices) {
            val lineA = a[i]
            val lineB = b[i]
            if (lineA != lineB) return false
        }

        return true
    }
}
