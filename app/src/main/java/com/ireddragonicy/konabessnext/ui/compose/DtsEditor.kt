package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ireddragonicy.konabessnext.editor.core.CodeEditor
import com.ireddragonicy.konabessnext.viewmodel.RawDtsEditorViewModel.LineSearchResult

@Composable
fun DtsEditor(
    content: String,
    onContentChanged: (String) -> Unit,
    searchQuery: String = "",
    searchResultIndex: Int = -1,
    searchResults: List<LineSearchResult> = emptyList(),
    modifier: Modifier = Modifier
) {
    // We use a side-effect to avoid resetting text cursor when 'content' changes due to our own typing
    // Actually CodeEditor handles this check internally usually, or we do it here.
    
    // Create the view
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                // Setup listener
                setOnTextChangedListener {
                    // Signal change
                    onContentChanged(this.text.toString())
                }
            }
        },
        update = { view ->
            // Update content only if different to avoid cursor jump/loop
            if (view.text.toString() != content) {
                // Determine if difference is significant or just typing?
                // For now, simple check.
                view.setText(content)
            }
            
            // Handle Search
            if (searchQuery.isNotEmpty()) {
                // This might spam search on every recomposition if query doesn't change?
                // No, 'update' block runs on recomposition.
                // We should check if query actually changed? 
                // Or just let CodeEditor handle "search same query" gracefully.
                // It does have logic to find next.
                // Ideally we only search if different.
                
                // For basic "Highlight all" behavior:
                // view.searchAndSelect(searchQuery) matches ONE.
                // We might need a "setSearchQuery" on CodeEditor if we expanded it.
                // But for now, let's map the ViewModel's "current index" navigation.
                
                // If the ViewModel manages navigation, we just highlight?
                // Legacy logic pushed navigation via function calls.
                // Compose 'update' is state-driven.
                
                 if (searchQuery != view.lastSearchQuery || searchResultIndex != view.lastSearchIndex) {
                     // Trigger logic
                     view.searchAndSelect(searchQuery)
                 }
            } else {
                view.clearSearch()
            }
        }
    )
}

// Extension to store state in View for diffing during Compose updates
private var CodeEditor.lastSearchQuery: String?
    get() = this.tag as? String // Using tag as simple storage
    set(value) { this.tag = value }

private var CodeEditor.lastSearchIndex: Int
    get() = 0 // Needs a better storage mechanism or field in CodeEditor
    set(value) {}

