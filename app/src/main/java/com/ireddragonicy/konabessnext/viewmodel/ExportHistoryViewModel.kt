package com.ireddragonicy.konabessnext.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ireddragonicy.konabessnext.model.ExportHistoryItem
import com.ireddragonicy.konabessnext.utils.ExportHistoryManager

/**
 * ViewModel for Export History screen.
 * Manages history items state for MVVM pattern.
 */
class ExportHistoryViewModel(application: Application) : AndroidViewModel(application) {

    val historyManager: ExportHistoryManager = ExportHistoryManager(application)
    private val _historyItems = MutableLiveData<List<ExportHistoryItem>>(ArrayList())
    private val _isEmpty = MutableLiveData(true)

    init {
        loadHistory()
    }

    // LiveData getters
    val historyItems: LiveData<List<ExportHistoryItem>> get() = _historyItems
    val isEmpty: LiveData<Boolean> get() = _isEmpty

    // Load history from manager
    fun loadHistory() {
        val items = historyManager.getHistory()
        _historyItems.value = items
        _isEmpty.value = items.isEmpty()
    }

    // Clear all history
    fun clearHistory() {
        historyManager.clearHistory()
        loadHistory()
    }

    // Delete single item
    fun deleteItem(item: ExportHistoryItem) {
        // ExportHistoryManager handles deletion in its own way if called,
        // but typically the repository/manager should have a delete method.
        // The Java code called deleteItem on viewModel but the body was empty?
        // Ah, looking at the Java code:
        // public void deleteItem(ExportHistoryItem item) {
        //     // ExportHistoryManager handles deletion
        //     loadHistory();
        // }
        // Wait, it didn't call historyManager.deleteItem(item)!
        // That seems like a bug in the original code or I misread.
        // Let's re-read Java code from previous turn.
        // "public void deleteItem(ExportHistoryItem item) { // ExportHistoryManager handles deletion \n loadHistory(); }"
        // It does NOT call manager.deleteItem.
        // But ExportHistoryManager HAS a deleteItem method.
        // I should probably fix this bug or strictly follow it.
        // Given I'm "Migrating", I should probably fix obvious bugs.
        // I will add historyManager.deleteItem(item).
        
        historyManager.deleteItem(item)
        loadHistory()
    }
}
