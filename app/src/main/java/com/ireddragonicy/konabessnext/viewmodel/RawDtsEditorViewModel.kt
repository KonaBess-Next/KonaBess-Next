package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel for the Raw DTS Editor.
 * Manages DTS content state, search, and modification tracking.
 */
class RawDtsEditorViewModel : ViewModel() {

    // Content state
    private val _content = MutableLiveData<String>()
    private val _isDirty = MutableLiveData(false)
    private val _isLoading = MutableLiveData(false)

    // Search state
    private val _searchQuery = MutableLiveData("")
    private val _searchResults = MutableLiveData<List<SearchResult>>(ArrayList())
    private val _currentSearchIndex = MutableLiveData(-1)

    // Editor state
    private val _cursorPosition = MutableLiveData(0)
    private val _scrollPosition = MutableLiveData(0)

    // Events
    private val _saveEvent = MutableLiveData<Event<String>>()
    private val _errorEvent = MutableLiveData<Event<String>>()

    private var originalContent: String = ""

    // ========================================================================
    // LiveData Getters
    // ========================================================================

    val content: LiveData<String> get() = _content
    val isDirty: LiveData<Boolean> get() = _isDirty
    val isLoading: LiveData<Boolean> get() = _isLoading

    val searchQuery: LiveData<String> get() = _searchQuery
    val searchResults: LiveData<List<SearchResult>> get() = _searchResults
    val currentSearchIndex: LiveData<Int> get() = _currentSearchIndex

    val saveEvent: LiveData<Event<String>> get() = _saveEvent
    val errorEvent: LiveData<Event<String>> get() = _errorEvent

    // ========================================================================
    // Content Operations
    // ========================================================================

    /**
     * Load content from DTS file.
     */
    fun loadContent(dtsContent: String?) {
        originalContent = dtsContent ?: ""
        _content.value = originalContent
        _isDirty.value = false
    }

    /**
     * Update content from editor.
     */
    fun updateContent(newContent: String) {
        _content.value = newContent
        _isDirty.value = newContent != originalContent
    }

    /**
     * Mark content as saved.
     */
    fun markAsSaved() {
        originalContent = _content.value ?: ""
        _isDirty.value = false
    }

    /**
     * Check if content has unsaved changes.
     */
    fun hasUnsavedChanges(): Boolean {
        return _isDirty.value == true
    }

    // ========================================================================
    // Search Operations
    // ========================================================================

    /**
     * Perform search in content.
     */
    fun search(query: String?) {
        _searchQuery.value = query

        if (query.isNullOrEmpty()) {
            _searchResults.value = ArrayList()
            _currentSearchIndex.value = -1
            return
        }

        val text = _content.value ?: return

        val results = ArrayList<SearchResult>()
        var index = 0
        while (text.indexOf(query, index).also { index = it } != -1) {
            results.add(SearchResult(index, query.length))
            index += query.length
        }

        _searchResults.value = results
        if (results.isNotEmpty()) {
            _currentSearchIndex.value = 0
        } else {
            _currentSearchIndex.value = -1
        }
    }

    /**
     * Navigate to next search result.
     */
    fun nextSearchResult() {
        val results = _searchResults.value
        val current = _currentSearchIndex.value

        if (results.isNullOrEmpty() || current == null) {
            return
        }

        val next = (current + 1) % results.size
        _currentSearchIndex.value = next
    }

    /**
     * Navigate to previous search result.
     */
    fun previousSearchResult() {
        val results = _searchResults.value
        val current = _currentSearchIndex.value

        if (results.isNullOrEmpty() || current == null) {
            return
        }

        val prev = (current - 1 + results.size) % results.size
        _currentSearchIndex.value = prev
    }

    /**
     * Clear search.
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = ArrayList()
        _currentSearchIndex.value = -1
    }

    /**
     * Get current search result for highlighting.
     */
    fun getCurrentResult(): SearchResult? {
        val results = _searchResults.value ?: return null
        val current = _currentSearchIndex.value ?: return null

        return if (results.isNotEmpty() && current >= 0 && current < results.size) {
            results[current]
        } else null
    }

    // ========================================================================
    // Editor State
    // ========================================================================

    fun setCursorPosition(position: Int) {
        _cursorPosition.value = position
    }

    val cursorPositionValue: Int
        get() = _cursorPosition.value ?: 0

    fun setScrollPosition(position: Int) {
        _scrollPosition.value = position
    }

    val scrollPositionValue: Int
        get() = _scrollPosition.value ?: 0

    // ========================================================================
    // Search Result Model
    // ========================================================================

    data class SearchResult(val startIndex: Int, val length: Int)
}
