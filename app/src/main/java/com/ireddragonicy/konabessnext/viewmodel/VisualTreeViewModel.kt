package com.ireddragonicy.konabessnext.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.model.TargetPartition
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.repository.DtboRepository
import com.ireddragonicy.konabessnext.repository.DtsDataProvider
import com.ireddragonicy.konabessnext.repository.GpuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel responsible for the visual tree view:
 * - Parsed DTS tree (from repository SSOT)
 * - Node expansion state
 * - Tree scroll position persistence
 * - Tree-to-text synchronization
 *
 * Partition-aware: switches between [GpuRepository] and [DtboRepository]
 * based on the active partition set via [setActivePartition].
 */
@HiltViewModel
class VisualTreeViewModel @Inject constructor(
    private val gpuRepository: GpuRepository,
    private val dtboRepository: DtboRepository
) : ViewModel() {

    // --- Partition-aware provider switching ---
    private val _activePartition = MutableStateFlow(TargetPartition.VENDOR_BOOT)

    private val activeProvider: DtsDataProvider
        get() = if (_activePartition.value == TargetPartition.DTBO) dtboRepository else gpuRepository

    fun setActivePartition(partition: TargetPartition) {
        if (_activePartition.value == partition) return
        _activePartition.value = partition
        resetTreeState()
    }

    // --- Parsed Tree (SSOT from active partition's repository) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val parsedTree: StateFlow<DtsNode?> = _activePartition
        .flatMapLatest { partition ->
            if (partition == TargetPartition.DTBO) dtboRepository.parsedTree
            else gpuRepository.parsedTree
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- DTS Content (for copy-all in tree view) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val dtsContent = _activePartition
        .flatMapLatest { partition ->
            if (partition == TargetPartition.DTBO) dtboRepository.dtsContent
            else gpuRepository.dtsContent
        }

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
        activeProvider.syncTreeToText("Property Edit")
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
