package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.repository.HistoryManager.LineDiff
import org.junit.Assert.assertEquals
import org.junit.Test

class UndoRedoTest {

    @Test
    fun testBlockDeleteUndoOrder() {
        val historyManager = HistoryManager()
        
        // Initial: 5 lines
        val original = listOf("Line 1", "Line 2", "Line 3", "Line 4", "Line 5")
        
        // Delete block: 2, 3, 4 (indices 1, 2, 3)
        // Result: 1, 5
        val afterDelete = listOf("Line 1", "Line 5")
        
        // Snapshot
        historyManager.snapshot(original, afterDelete, "Delete Block")
        
        // Verify Undo
        val restored = historyManager.undo(afterDelete)
        
        println("Original: $original")
        println("Restored: $restored")
        
        // Check integrity
        assertEquals("Undo should restore exact string list", original, restored)
    }

    @Test
    fun testBlockInsertUndo() {
        // Validation for Insert Undo as well
        val historyManager = HistoryManager()
        val original = listOf("A", "E")
        val afterInsert = listOf("A", "B", "C", "D", "E")
        
        historyManager.snapshot(original, afterInsert, "Insert Block")
        
        val restored = historyManager.undo(afterInsert)
        assertEquals(original, restored)
    }
}
