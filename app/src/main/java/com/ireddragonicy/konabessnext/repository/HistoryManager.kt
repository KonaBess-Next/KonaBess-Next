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

    // Simpan HANYA perbedaannya (Delta), BUKAN AST atau seluruh state
    data class HistoryItem(
        val startIndex: Int,
        val oldLines: List<String>,
        val newLines: List<String>,
        val description: String
    )

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
    }

    /**
     * Algoritma Diff O(N) Git-style. Scan prefix dan suffix, simpan perubahan di tengah.
     * Sangat cepat karena hanya membandingkan referensi String/isi tanpa array allocation.
     */
    fun snapshot(oldState: List<String>, newState: List<String>, description: String) {
        if (oldState === newState) return
        if (oldState.size == newState.size && oldState == newState) return

        val minSize = min(oldState.size, newState.size)
        
        // 1. Temukan panjang prefix yang sama
        var prefix = 0
        while (prefix < minSize && oldState[prefix] == newState[prefix]) {
            prefix++
        }
        
        if (prefix == oldState.size && prefix == newState.size) return // Sama persis
        
        // 2. Temukan panjang suffix yang sama
        var suffix = 0
        val maxSuffix = minSize - prefix
        while (suffix < maxSuffix && oldState[oldState.size - 1 - suffix] == newState[newState.size - 1 - suffix]) {
            suffix++
        }
        
        // 3. Ambil bagian tengah yang berubah (Delta)
        val oldMid = oldState.subList(prefix, oldState.size - suffix).toList()
        val newMid = newState.subList(prefix, newState.size - suffix).toList()

        undoStack.push(HistoryItem(prefix, oldMid, newMid, description))
        
        if (undoStack.size > MAX_HISTORY) {
            undoStack.removeLast()
        }
        
        redoStack.clear()
        updateHistoryFlags()
    }

    /**
     * Undo yang bebas dari Array-Shifting lag. O(N) memory copy.
     */
    fun undo(currentState: List<String>): List<String>? {
        if (undoStack.isEmpty()) return null
        
        val item = undoStack.pop()
        redoStack.push(item) // Push objek yang sama, logika Redo tinggal membalik data
        
        val newSize = currentState.size - item.newLines.size + item.oldLines.size
        val result = ArrayList<String>(newSize)
        
        result.addAll(currentState.subList(0, item.startIndex))
        result.addAll(item.oldLines)
        result.addAll(currentState.subList(item.startIndex + item.newLines.size, currentState.size))
        
        updateHistoryFlags()
        return result
    }

    /**
     * Redo yang sama efisiennya.
     */
    fun redo(currentState: List<String>): List<String>? {
        if (redoStack.isEmpty()) return null
        
        val item = redoStack.pop()
        undoStack.push(item)
        
        val newSize = currentState.size - item.oldLines.size + item.newLines.size
        val result = ArrayList<String>(newSize)
        
        result.addAll(currentState.subList(0, item.startIndex))
        result.addAll(item.newLines)
        result.addAll(currentState.subList(item.startIndex + item.oldLines.size, currentState.size))
        
        updateHistoryFlags()
        return result
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _historyList.value = undoStack.map { it.description }.reversed()
    }
}
