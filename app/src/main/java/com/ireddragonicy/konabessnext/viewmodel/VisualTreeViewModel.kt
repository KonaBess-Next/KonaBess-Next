package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel responsible for the visual tree view:
 * - Parsed DTS tree (from repository SSOT)
 * - Node expansion state
 * - Tree scroll position persistence
 * - Tree-to-text synchronization
 *
 * Observes [GpuRepository.parsedTree] as the Single Source of Truth (SSOT).
 */
@HiltViewModel
class VisualTreeViewModel @Inject constructor(
    private val repository: GpuRepository
) : ViewModel() {

    // --- Parsed Tree (SSOT from Repository) ---
    val parsedTree: StateFlow<DtsNode?> = repository.parsedTree

    // --- DTS Content (for copy-all in tree view) ---
    val dtsContent = repository.dtsContent

    // --- Scroll Persistence ---
    val treeScrollIndex = MutableStateFlow(0)
    val treeScrollOffset = MutableStateFlow(0)

    // --- Expansion State ---
    private val _expandedNodePaths = MutableStateFlow<Set<String>>(setOf("root"))
    val expandedNodePaths: StateFlow<Set<String>> = _expandedNodePaths.asStateFlow()

    // --- Actions ---

    fun toggleNodeExpansion(path: String, expanded: Boolean) {
        val set = _expandedNodePaths.value.toMutableSet()
        if (expanded) set.add(path) else set.remove(path)
        _expandedNodePaths.value = set

        // Update the transient DtsNode object if it exists to reflect UI state immediately
        val root = parsedTree.value
        if (root != null) {
            findNode(root, path)?.isExpanded = expanded
        }
    }

    fun syncTreeToText() {
        repository.syncTreeToText("Property Edit")
    }

    // --- Reset (called on new DTS load) ---
    fun resetTreeState() {
        treeScrollIndex.value = 0
        treeScrollOffset.value = 0
        _expandedNodePaths.value = setOf("root")
    }

    // --- Private Helpers ---

    private fun findNode(node: DtsNode, path: String): DtsNode? {
        if (node.getFullPath() == path) return node
        for (child in node.children) {
            val found = findNode(child, path)
            if (found != null) return found
        }
        return null
    }
}
