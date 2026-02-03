package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Simplified VM for Raw Editor.
 * Now acts as a window into the GpuRepository.
 */
@HiltViewModel
class RawDtsEditorViewModel @Inject constructor(
    private val repository: GpuRepository
) : ViewModel() {

    // Direct Flow from Repository
    val contentLines = repository.dtsLines
    val isDirty = repository.isDirty

    // Search State (Local UI state)
    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

    fun updateContent(newLines: List<String>) {
        repository.updateContent(newLines)
    }

    fun search(query: String) {
        // ... (Same search logic as before, just filtering contentLines.value) ...
        if (query.isEmpty()) { _searchState.value = SearchState(); return }
        
        val lines = contentLines.value
        val results = mutableListOf<SearchResult>()
        lines.forEachIndexed { i, line ->
            // simple check
            if (line.contains(query, true)) results.add(SearchResult(i))
        }
        _searchState.value = SearchState(query, results, if (results.isNotEmpty()) 0 else -1)
    }
    
    fun nextSearchResult() { /* ... prev logic ... */ }
    fun previousSearchResult() { /* ... prev logic ... */ }
    fun clearSearch() { _searchState.value = SearchState() }

    data class SearchState(val query: String = "", val results: List<SearchResult> = emptyList(), val currentIndex: Int = -1)
    data class SearchResult(val lineIndex: Int)
}
