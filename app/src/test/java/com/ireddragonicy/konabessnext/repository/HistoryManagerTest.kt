package com.ireddragonicy.konabessnext.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class HistoryManagerTest {

    private lateinit var historyManager: HistoryManager

    @Before
    fun setup() {
        historyManager = HistoryManager()
    }

    @Test
    fun testInsert() {
        val initial = listOf("A", "C")
        val new = listOf("A", "B", "C") // Insert B at index 1
        historyManager.snapshot(initial, new, "Insert B")
        
        // Verify Undo
        val undo = historyManager.undo(new)
        assertNotNull("Undo result should not be null", undo)
        assertEquals("Undo should return to initial state", initial, undo)
        
        // Verify Redo
        val redo = historyManager.redo(undo!!)
        assertNotNull("Redo result should not be null", redo)
        assertEquals("Redo should return to new state", new, redo)
    }

    @Test
    fun testDelete() {
        val initial = listOf("A", "B", "C")
        val new = listOf("A", "C") // Delete B at index 1
        historyManager.snapshot(initial, new, "Delete B")
        
        val undo = historyManager.undo(new)
        assertEquals(initial, undo)
        
        val redo = historyManager.redo(undo!!)
        assertEquals(new, redo)
    }

    @Test
    fun testModify() {
        val initial = listOf("A", "B", "C")
        val new = listOf("A", "X", "C") // Change B to X
        historyManager.snapshot(initial, new, "Modify B->X")
        
        val undo = historyManager.undo(new)
        assertEquals(initial, undo)
        
        val redo = historyManager.redo(undo!!)
        assertEquals(new, redo)
    }

    @Test
    fun testComplexMix() {
        // Change B->X, Insert Y after X, Delete A
        // Old: A, B, C
        // New: X, Y, C
        val initial = listOf("A", "B", "C")
        val new = listOf("X", "Y", "C")
        
        historyManager.snapshot(initial, new, "Complex")
        
        val undo = historyManager.undo(new)
        assertEquals("Undo match check", initial, undo)
        
        val redo = historyManager.redo(undo!!)
        assertEquals("Redo match check", new, redo)
    }
}
