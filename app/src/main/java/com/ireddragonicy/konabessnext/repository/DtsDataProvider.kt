package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.model.DomainResult
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for any repository that holds DTS lines as SSOT.
 *
 * Both [GpuRepository] (vendor_boot / dtb) and [DisplayRepository] (dtbo)
 * implement this interface so that partition-agnostic ViewModels
 * (TextEditorViewModel, VisualTreeViewModel) can switch data source
 * at runtime based on the selected partition.
 */
interface DtsDataProvider {
    val dtsLines: StateFlow<List<String>>
    val dtsContent: Flow<String>
    val parsedTree: StateFlow<DtsNode?>
    val isDirty: StateFlow<Boolean>
    val canUndo: StateFlow<Boolean>
    val canRedo: StateFlow<Boolean>
    val history: StateFlow<List<String>>

    fun currentDtsPath(): String?

    fun updateContent(
        newLines: List<String>,
        description: String = "Edit",
        addToHistory: Boolean = true,
        reparseTree: Boolean = true
    )

    fun syncTreeToText(description: String = "Tree Edit")
    fun undo()
    fun redo()

    suspend fun loadTable(): DomainResult<Unit>
    suspend fun saveTable(): DomainResult<Unit>
}
