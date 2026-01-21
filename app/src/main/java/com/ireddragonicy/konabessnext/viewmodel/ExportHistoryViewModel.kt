package com.ireddragonicy.konabessnext.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ireddragonicy.konabessnext.model.ExportHistoryItem
import com.ireddragonicy.konabessnext.utils.ExportHistoryManager
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel for Export History screen.
 * Manages history items state for MVVM pattern.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class ExportHistoryViewModel @javax.inject.Inject constructor(
    application: Application,
    private val repository: com.ireddragonicy.konabessnext.repository.GpuRepository
) : AndroidViewModel(application) {

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
        historyManager.deleteItem(item)
        loadHistory()
    }

    fun applyConfig(content: String) {
        viewModelScope.launch {
            repository.parseContentPartial(content)
        }
    }
}
