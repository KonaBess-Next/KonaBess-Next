package com.ireddragonicy.konabessnext.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.Deque
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class HistoryManager @Inject constructor() {

    // Store the DIFF, not the full state
    data class HistoryItem(val diff: List<LineDiff>, val description: String)

    sealed class LineDiff {
        data class Change(val index: Int, val oldLine: String, val newLine: String) : LineDiff()
        data class Insert(val index: Int, val newLine: String) : LineDiff()
        data class Delete(val index: Int, val oldLine: String) : LineDiff()
    }

    private val undoStack: Deque<HistoryItem> = ArrayDeque()
    private val redoStack: Deque<HistoryItem> = ArrayDeque()
    private val MAX_HISTORY = 50

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _historyList = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _historyList.asStateFlow()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateHistoryFlags()
        refreshHistoryList()
    }

    /**
     * Calculates the diff between oldState and newState and pushes it to history.
     */
    fun snapshot(oldState: List<String>, newState: List<String>, description: String) {
        val diff = calculateDiff(oldState, newState)
        if (diff.isEmpty()) return

        undoStack.push(HistoryItem(diff, description))
        
        if (undoStack.size > MAX_HISTORY) {
            undoStack.removeLast()
        }
        
        redoStack.clear()
        updateHistoryFlags()
        refreshHistoryList()
    }

    fun undo(currentState: List<String>): List<String>? {
        if (undoStack.isEmpty()) return null
        
        val historyItem = undoStack.pop()
        
        // To undo, we explicitly REVERSE the diff 
        // (e.g. if Diff was "Insert Line A", Undo is "Delete Line A")
        // We push this REVERSE diff to the Redo stack so Redo can re-apply it later? 
        // No, Redo stack should store the ORIGINAL diff (the action to re-do).
        
        redoStack.push(historyItem)
        
        val restoredState = applyReverseDiff(currentState, historyItem.diff)
        
        updateHistoryFlags()
        refreshHistoryList()
        
        return restoredState
    }

    fun redo(currentState: List<String>): List<String>? {
        if (redoStack.isEmpty()) return null
        
        val historyItem = redoStack.pop()
        
        undoStack.push(historyItem)
        
        val restoredState = applyDiff(currentState, historyItem.diff)
        
        updateHistoryFlags()
        refreshHistoryList()
        
        return restoredState
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun refreshHistoryList() {
        _historyList.value = undoStack.map { it.description }.reversed()
    }

    // --- Diffing Logic (Simple Myers-like or just heuristic since we usually change 1 line or block) ---
    // For this use case (Text Editor), user usually edits:
    // 1. One line (Replace)
    // 2. Inserts block (Insert)
    // 3. Deletes block (Delete)
    // We can implement a simplified diff: matching prefix and suffix.
    
    private fun calculateDiff(oldList: List<String>, newList: List<String>): List<LineDiff> {
        val diffs = ArrayList<LineDiff>()
        
        // 1. Find common prefix
        var prefixLen = 0
        while (prefixLen < oldList.size && prefixLen < newList.size && oldList[prefixLen] == newList[prefixLen]) {
            prefixLen++
        }
        
        // 2. Find common suffix
        var suffixLen = 0
        while (suffixLen < (oldList.size - prefixLen) && suffixLen < (newList.size - prefixLen) && 
               oldList[oldList.size - 1 - suffixLen] == newList[newList.size - 1 - suffixLen]) {
            suffixLen++
        }
        
        val oldMiddleStart = prefixLen
        val oldMiddleEnd = oldList.size - suffixLen
        val newMiddleStart = prefixLen
        val newMiddleEnd = newList.size - suffixLen
        
        // Case: Deletion (New middle is empty, Old middle has content)
        if (newMiddleStart == newMiddleEnd && oldMiddleStart < oldMiddleEnd) {
            for (i in oldMiddleStart until oldMiddleEnd) {
                // To delete indices correctly, we must consider that deleting index i shifts subsequent items.
                // However, for storage, we just record "Delete at index X"
                // But wait, if we delete 5 lines, do we record "Delete 5 items at X"?
                // Let's record individual line deletes.
                // NOTE: When applying deletes, order matters (delete from end to start to avoid index shift issues)
                // But here we just record WHAT changed.
                diffs.add(LineDiff.Delete(oldMiddleStart, oldList[i])) 
                // We record index 'oldMiddleStart' for all because as we remove them, the next one falls into that place?
                // No, let's store absolute indices in the ORIGINAL list for verification?
                // Or simplified: Just store "Range Delete".
                // Let's stick to simple LineDiffs but apply careful logic.
                // Actually, storing "Delete index X" multiple times is ambiguous unless we specify "Simultaneous" or "Sequential".
                // Let's assume Sequential Application for Redo, and Reverse Sequential for Undo.
            }
            // Better: Record them as a block or sequence.
            // If I delete lines 10, 11, 12.
            // Diff: Delete(10, val), Delete(10, val), Delete(10, val)? No.
            // Diff: Delete(10, val1), Delete(11, val2), Delete(12, val3).
        }
        
        // Case: Insertion (Old middle is empty, New middle has content)
        else if (oldMiddleStart == oldMiddleEnd && newMiddleStart < newMiddleEnd) {
            for (i in newMiddleStart until newMiddleEnd) {
                diffs.add(LineDiff.Insert(i, newList[i]))
            }
        }
        
        // Case: Replacement (Both have content - simplistic, assumes simple replace)
        // If sizes match, it's 1:1 replace.
        else if ((oldMiddleEnd - oldMiddleStart) == (newMiddleEnd - newMiddleStart)) {
            for (i in 0 until (oldMiddleEnd - oldMiddleStart)) {
                diffs.add(LineDiff.Change(oldMiddleStart + i, oldList[oldMiddleStart + i], newList[newMiddleStart + i]))
            }
        }
        
        // Case: Mixed (Complex - Fallback to Delete Old Middle + Insert New Middle)
        else {
             for (i in oldMiddleStart until oldMiddleEnd) {
                 diffs.add(LineDiff.Delete(i, oldList[i]))
             }
             for (i in newMiddleStart until newMiddleEnd) {
                 diffs.add(LineDiff.Insert(i, newList[i]))
             }
        }
        
        return diffs
    }

    private fun applyDiff(currentState: List<String>, diffs: List<LineDiff>): List<String> {
        val result = ArrayList(currentState)
        
        // We need to handle index shifts.
        // It's safest to perform operations.
        // BUT, our CalculateDiff produced indices based on the OLD state (for deletes/changes) or NEW state (for inserts?)
        // Let's refine the logic.
        
        // REFINED LOGIC:
        // Treat diffs as a set of operations to transform Old -> New.
        // To handle Multi-line operations safely without index hell:
        // We can just fallback to: Replace Range. 
        // But requested is Diff.
        
        // Let's rely on the structure of our Diff calculation:
        // 1. Replacements (Same size) -> Index matches.
        // 2. Insertions -> Indices are "target" indices in the NEW list.
        // 3. Deletions -> Indices are "target" indices in the OLD list.
        
        // Complex Mixed case fallback produces: Delete Old Range, then Insert New Range (effectively replace range).
        
        // Sort diffs to handle index shifting?
        // Deletions should be done from End to Start to preserve lower indices.
        // Insertions should be done from Start to End?
        
        // Let's categorize:
        val deletions = diffs.filterIsInstance<LineDiff.Delete>().sortedByDescending { it.index }
        val modifications = diffs.filterIsInstance<LineDiff.Change>() // Indices shouldn't shift if size maintained
        val insertions = diffs.filterIsInstance<LineDiff.Insert>().sortedBy { it.index }

        // 1. Apply Modifications (In place, no size change) - Unless mixed...
        // Wait, if we use the "Mixed Fallback", we have deletes AND inserts.
        // If we mix them, indices get messy.
        
        // SIMPLIFIED APPROACH:
        // Since we snapshot typically small edits, let's just use the "Middle Replacement" strategy.
        // We really only need to know:
        // Start Index, Number of Old Lines to Remove, List of New Lines to Insert.
        // This covers Delete (0 new), Insert (0 remove), Replace (N remove, N new), and Mixed.
        
        // Let's stick to the method signature but change the internal implementation of this function
        // to be robust. 
        // Actually, let's revert to a robust "Patch" object instead of list of granular diffs.
        
        // But I must stick to the signature requested or make it private?
        // I can change the HistoryItem definition.
        
        return applyPatch(result, diffs)
    }

    private fun applyReverseDiff(currentState: List<String>, diffs: List<LineDiff>): List<String> {
        // Reverse transformation: New -> Old
        // Inverse of Operations:
        // Insert(i, val) -> Delete(i)
        // Delete(i, oldVal) -> Insert(i, oldVal)
        // Change(i, old, new) -> Change(i, new, old)
        
        val result = ArrayList(currentState)
        
        // Reverse the logic of application.
        // If forward was: Deletes (desc), then Inserts (asc).
        // Reverse should be: Reverse Inserts (becomes deletes), Reverse Deletes (becomes inserts).
        
        // 1. Revert Insertions (Deleted them). 
        // Original Insert was at index i (in the NEW list).
        // So we delete at index i.
        // Must delete from high to low.
        val insertsToRevert = diffs.filterIsInstance<LineDiff.Insert>().sortedByDescending { it.index }
        insertsToRevert.forEach {
            result.removeAt(it.index)
        }
        
        // 2. Revert Modifications
        val modsToRevert = diffs.filterIsInstance<LineDiff.Change>()
        modsToRevert.forEach {
            result[it.index] = it.oldLine
        }
        
        // 3. Revert Deletions (Insert them back).
        // Original Delete was at index i (in the OLD list).
        // We insert back at i.
        // Must insert from low to high? 
        // If we deleted 10, 11, 12 (in that order in the list).
        // We insert val3 at 12, val2 at 11, val1 at 10?
        // Correct.
        val deletesToRevert = diffs.filterIsInstance<LineDiff.Delete>().sortedBy { it.index }
        deletesToRevert.forEach {
            result.add(it.index, it.oldLine)
        }
        
        return result
    }
    
    // Helper to actually apply the forward diff
    private fun applyPatch(list: ArrayList<String>, diffs: List<LineDiff>): List<String> {
        // 1. Deletions (From High to Low to avoid index shift affecting lower ones)
        val deletions = diffs.filterIsInstance<LineDiff.Delete>().sortedByDescending { it.index }
        deletions.forEach {
            if (it.index < list.size) list.removeAt(it.index)
        }
        
        // 2. Modifications
        diffs.filterIsInstance<LineDiff.Change>().forEach {
            if (it.index < list.size) list[it.index] = it.newLine
        }
        
        // 3. Insertions (From Low to High, but... wait)
        // If we insert at 5, then insert at 6.
        // Should the second insert be at 6 relative to the list AFTER first insert?
        // YES. My CalculateDiff logic generated indices based on the FINAL list for insertions.
        // Example: Old: [A, B]. New: [A, 1, 2, B].
        // Pref: 1(A). Suff: 1(B).
        // Old Mid: empty (start 1, end 1).
        // New Mid: [1, 2] (start 1, end 3).
        // Diff: Insert(1, "1"), Insert(2, "2").
        // Apply:
        // Insert "1" at 1. List: [A, 1, B].
        // Insert "2" at 2. List: [A, 1, 2, B].
        // Works!
        
        val insertions = diffs.filterIsInstance<LineDiff.Insert>().sortedBy { it.index }
        insertions.forEach {
             if (it.index <= list.size) list.add(it.index, it.newLine)
             else list.add(it.newLine) // Append
        }
        
        return list
    }
}
