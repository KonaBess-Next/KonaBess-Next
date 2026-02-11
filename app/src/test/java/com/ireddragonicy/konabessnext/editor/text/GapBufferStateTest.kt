package com.ireddragonicy.konabessnext.editor.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GapBufferStateTest {

    @Test
    fun `insert and backspace mutate only current line`() {
        val buffer = GapBufferState("abc")
        buffer.moveCursor(line = 0, column = 1)

        buffer.insert('X')
        assertEquals("aXbc", buffer.copyLines()[0])
        assertEquals(TextCursor(0, 2), buffer.cursor)

        val deleted = buffer.delete()
        assertTrue(deleted)
        assertEquals("abc", buffer.copyLines()[0])
        assertEquals(TextCursor(0, 1), buffer.cursor)
    }

    @Test
    fun `newline splits line and backspace at start merges lines`() {
        val buffer = GapBufferState("abcdef")
        buffer.moveCursor(line = 0, column = 3)

        buffer.insert('\n')
        assertEquals(listOf("abc", "def"), buffer.copyLines())
        assertEquals(TextCursor(1, 0), buffer.cursor)

        val deleted = buffer.delete()
        assertTrue(deleted)
        assertEquals(listOf("abcdef"), buffer.copyLines())
        assertEquals(TextCursor(0, 3), buffer.cursor)
    }

    @Test
    fun `insertText handles multiline and CRLF`() {
        val buffer = GapBufferState("")
        buffer.insertText("a\r\nb\nc")

        assertEquals(listOf("a", "b", "c"), buffer.copyLines())
        assertEquals("a\nb\nc", buffer.getText())
        assertEquals(TextCursor(2, 1), buffer.cursor)
    }

    @Test
    fun `snapshot and restore keep cursor and content`() {
        val buffer = GapBufferState("one\ntwo")
        buffer.moveCursor(1, 3)
        val snapshot = buffer.snapshot()

        buffer.insertText("\nthree")
        assertEquals(listOf("one", "two", "three"), buffer.copyLines())

        buffer.restore(snapshot)
        assertEquals(listOf("one", "two"), buffer.copyLines())
        assertEquals(TextCursor(1, 3), buffer.cursor)
    }

    @Test
    fun `editor state replaces selection on insert`() {
        val state = DtsEditorState("hello world")
        state.setSelection(
            start = TextCursor(0, 6),
            end = TextCursor(0, 11)
        )

        state.insertText("gpu")
        assertEquals("hello gpu", state.getText())
        assertEquals(TextCursor(0, 9), state.cursor)
        assertFalse(state.selection != null)
    }
}
