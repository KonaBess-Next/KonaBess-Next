package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Raw DTS Editor.
 * Manages DTS content state, search, and modification tracking.
 */
class RawDtsEditorViewModel : ViewModel() {

    // Content state
    private val _contentLines = MutableStateFlow<List<String>>(emptyList())
    val contentLines: StateFlow<List<String>> = _contentLines.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Search state
    data class SearchState(
        val query: String = "",
        val results: List<LineSearchResult> = emptyList(),
        val currentIndex: Int = -1
    )
    
    data class LineSearchResult(val lineIndex: Int, val startIndex: Int, val length: Int)

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // Navigation and cursor state
    private val _cursorPosition = MutableStateFlow(0)
    val cursorPosition: StateFlow<Int> = _cursorPosition.asStateFlow()

    private val _scrollPosition = MutableStateFlow(0)
    val scrollPosition: StateFlow<Int> = _scrollPosition.asStateFlow()

    // Events
    private val _saveEvent = MutableSharedFlow<String>()
    val saveEvent = _saveEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    private var originalLines: List<String> = emptyList()

    // ========================================================================
    // Content Operations
    // ========================================================================

    /**
     * Load content from DTS string.
     */
    fun loadContent(dtsContent: String?) {
        val lines = dtsContent?.split("\n") ?: emptyList()
        originalLines = ArrayList(lines)
        _contentLines.value = lines
        _isDirty.value = false
    }

    /**
     * Update content from editor (debounced by editor logic or this).
     */
    fun updateContent(newLines: List<String>) {
        _contentLines.value = newLines
        _isDirty.value = newLines != originalLines
    }

    /**
     * Mark content as saved.
     */
    fun markAsSaved() {
        originalLines = ArrayList(_contentLines.value)
        _isDirty.value = false
    }

    /**
     * Check if content has unsaved changes.
     */
    fun hasUnsavedChanges(): Boolean {
        return _isDirty.value
    }

    // ========================================================================
    // Search Operations
    // ========================================================================

    /**
     * Perform search in content lines.
     */
    fun search(query: String?) {
        if (query.isNullOrEmpty()) {
            _searchState.value = SearchState()
            return
        }

        val results = mutableListOf<LineSearchResult>()
        val lines = _contentLines.value
        
        lines.forEachIndexed { lineIdx, line ->
            var startIdx = 0
            while (line.indexOf(query, startIdx).also { startIdx = it } != -1) {
                results.add(LineSearchResult(lineIdx, startIdx, query.length))
                startIdx += query.length
            }
        }

        _searchState.value = SearchState(
            query = query,
            results = results,
            currentIndex = if (results.isNotEmpty()) 0 else -1
        )
    }

    /**
     * Navigate to next search result.
     */
    fun nextSearchResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return

        val next = (state.currentIndex + 1) % state.results.size
        _searchState.value = state.copy(currentIndex = next)
    }

    /**
     * Navigate to previous search result.
     */
    fun previousSearchResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return

        val prev = (state.currentIndex - 1 + state.results.size) % state.results.size
        _searchState.value = state.copy(currentIndex = prev)
    }

    /**
     * Clear search.
     */
    fun clearSearch() {
        _searchState.value = SearchState()
    }

    // ========================================================================
    // Editor State
    // ========================================================================

    fun setCursorPosition(position: Int) {
        _cursorPosition.value = position
    }

    fun setScrollPosition(position: Int) {
        _scrollPosition.value = position
    }
}
