package com.ireddragonicy.konabessnext.editor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EditorSession incremental highlighting.
 * Tests cache management, line insertion/deletion, and incremental updates.
 */
class EditorSessionTest {

    @Before
    fun setup() {
        EditorSession.clear()
    }

    @Test
    fun `clear resets all cache state`() {
        // Prepare some state
        EditorSession.highlightCache[0] = androidx.compose.ui.text.AnnotatedString("test")
        EditorSession.highlightCache[1] = androidx.compose.ui.text.AnnotatedString("test2")
        
        // Clear
        EditorSession.clear()
        
        // Verify
        assertTrue("Cache should be empty after clear", EditorSession.highlightCache.isEmpty())
    }

    @Test
    fun `deleteLine shifts cache indices correctly`() {
        // Setup: 5 lines, manually populate cache
        for (i in 0 until 5) {
            EditorSession.highlightCache[i] = androidx.compose.ui.text.AnnotatedString("Line $i")
        }
        
        // Delete line at index 2 (Line 2)
        // Before: [Line 0, Line 1, Line 2, Line 3, Line 4]
        // After:  [Line 0, Line 1, Line 3, Line 4]
        EditorSession.deleteLine(atIndex = 2, totalLines = 4)
        
        // Verify shift
        assertEquals("Line 0", EditorSession.highlightCache[0]?.text)
        assertEquals("Line 1", EditorSession.highlightCache[1]?.text)
        assertEquals("Line 3", EditorSession.highlightCache[2]?.text) // Was at index 3
        assertEquals("Line 4", EditorSession.highlightCache[3]?.text) // Was at index 4
        assertNull("Index 4 should be removed", EditorSession.highlightCache[4])
    }

    @Test
    fun `deleteLine at start shifts all indices`() {
        for (i in 0 until 3) {
            EditorSession.highlightCache[i] = androidx.compose.ui.text.AnnotatedString("Line $i")
        }
        
        // Delete first line
        EditorSession.deleteLine(atIndex = 0, totalLines = 2)
        
        assertEquals("Line 1", EditorSession.highlightCache[0]?.text)
        assertEquals("Line 2", EditorSession.highlightCache[1]?.text)
        assertNull(EditorSession.highlightCache[2])
    }

    @Test
    fun `deleteLine at end only removes last entry`() {
        for (i in 0 until 3) {
            EditorSession.highlightCache[i] = androidx.compose.ui.text.AnnotatedString("Line $i")
        }
        
        // Delete last line (index 2)
        EditorSession.deleteLine(atIndex = 2, totalLines = 2)
        
        assertEquals("Line 0", EditorSession.highlightCache[0]?.text)
        assertEquals("Line 1", EditorSession.highlightCache[1]?.text)
        assertNull(EditorSession.highlightCache[2])
    }

    @Test
    fun `successive deletes maintain correct indices`() {
        // Simulate rapid backspacing: delete lines 4, 3, 2, 1
        for (i in 0 until 5) {
            EditorSession.highlightCache[i] = androidx.compose.ui.text.AnnotatedString("Line $i")
        }
        
        // Delete line 4 (total becomes 4)
        EditorSession.deleteLine(atIndex = 4, totalLines = 4)
        // Delete line 3 (total becomes 3)
        EditorSession.deleteLine(atIndex = 3, totalLines = 3)
        // Delete line 2 (total becomes 2)
        EditorSession.deleteLine(atIndex = 2, totalLines = 2)
        
        // Should have: [Line 0, Line 1]
        assertEquals("Line 0", EditorSession.highlightCache[0]?.text)
        assertEquals("Line 1", EditorSession.highlightCache[1]?.text)
        assertNull(EditorSession.highlightCache[2])
        assertNull(EditorSession.highlightCache[3])
        assertNull(EditorSession.highlightCache[4])
    }

    @Test
    fun `cache handles empty state gracefully`() {
        // Delete from empty cache - should not crash
        EditorSession.deleteLine(atIndex = 0, totalLines = 0)
        assertTrue(EditorSession.highlightCache.isEmpty())
    }
}
