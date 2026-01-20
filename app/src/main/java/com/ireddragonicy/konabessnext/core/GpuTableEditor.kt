package com.ireddragonicy.konabessnext.core

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.editor.ChipsetManager
import com.ireddragonicy.konabessnext.core.editor.EditorStateManager
import com.ireddragonicy.konabessnext.core.editor.EditorUIBuilder
import com.ireddragonicy.konabessnext.core.editor.LevelOperations
import com.ireddragonicy.konabessnext.core.editor.ParameterEditHandler
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.EditorState
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.adapters.GpuFreqAdapter
import com.ireddragonicy.konabessnext.ui.fragments.GpuCurveEditorFragment
import com.ireddragonicy.konabessnext.utils.DialogUtil
import com.ireddragonicy.konabessnext.utils.FileUtil
import com.ireddragonicy.konabessnext.viewmodel.GpuFrequencyViewModel
import com.google.android.material.button.MaterialButton
import java.io.IOException
import java.util.Deque

/**
 * Main entry point for GPU Frequency Table Editor.
 * Refactored into a Facade pattern to delegate responsibilities to specialized components.
 */
class GpuTableEditor : EditorUIBuilder.UIActionListener, ChipsetManager.OnChipsetSwitchedListener {

    // ===== UIActionListener Implementation (Logic Wiring) =====

    @Throws(Exception::class)
    override fun onOpenLevels(binIndex: Int) {
        onOpenLevels(binIndex, scrollToPosition = -1)
    }

    @Throws(Exception::class)
    fun onOpenLevels(binIndex: Int, scrollToPosition: Int) {
        currentBinIndex = binIndex
        currentLevelIndex = null
        viewModelRef?.selectedBinIndex?.value = binIndex
        viewModelRef?.selectedLevelIndex?.value = -1
        currentActivity?.let { restoreBackListener(it) }
        currentActivity?.let {
            if (currentPage != null && bins != null) {
                EditorUIBuilder.generateLevels(it, currentPage!!, bins!!, binIndex, this, scrollToPosition)
            }
        }
    }

    @Throws(Exception::class)
    override fun onOpenParamDetails(binIndex: Int, levelIndex: Int) {
        currentBinIndex = binIndex
        currentLevelIndex = levelIndex
        viewModelRef?.selectedLevelIndex?.value = levelIndex
        currentActivity?.let { restoreBackListener(it) }
        currentActivity?.let {
            if (currentPage != null && bins != null) {
                EditorUIBuilder.generateALevel(it, currentPage!!, bins!!, binIndex, levelIndex, this)
            }
        }
    }

    @Throws(Exception::class)
    override fun onBack() {
        if (currentLevelIndex != null) {
            // Back from Param Details -> Levels
            if (currentBinIndex != null) onOpenLevels(currentBinIndex!!)
        } else if (currentBinIndex != null) {
            // Back from Levels -> Bins
            currentBinIndex = null
            currentLevelIndex = null
            viewModelRef?.selectedBinIndex?.value = -1
            viewModelRef?.selectedLevelIndex?.value = -1
            if (currentActivity is MainActivity) {
                (currentActivity as MainActivity).updateGpuToolbarTitle(
                    currentActivity!!.getString(R.string.edit_freq_table)
                )
            }
            disableBackCallback()
            currentActivity?.let {
                if (currentPage != null && bins != null) {
                    EditorUIBuilder.generateBins(it, currentPage!!, bins!!, this, this)
                }
            }
        }
    }

    @Throws(Exception::class)
    override fun onAddLevelTop(binIndex: Int) {
        // UI Check
        if (!LevelOperations.canAddNewLevel(bins, binIndex)) return
        val bin = bins?.getOrNull(binIndex) ?: return
        
        // Delegate to ViewModel
        val activity = currentActivity ?: return
        viewModelRef?.addFrequency(binIndex, true /* top */)
        
        // Refresh UI logic (ViewModel update will trigger reactive refresh via Fragment, 
        // but explicit scroll is requested here)
        // Wait, if we rely on reactive flow, the list updates. 
        // But scrolling to specific position needs a trigger.
        // For now, let's assume the reactive refresh happens, and we might lose scroll context unless we handle it.
        // But this legacy method calls onOpenLevels.
        // Let's call onOpenLevels just to set the index and force refresh/scroll.
        onOpenLevels(binIndex, scrollToPosition = 3)
    }

    @Throws(Exception::class)
    override fun onAddLevelBottom(binIndex: Int) {
        if (!LevelOperations.canAddNewLevel(bins, binIndex)) return
        val bin = bins?.getOrNull(binIndex) ?: return
        
        viewModelRef?.addFrequency(binIndex, false /* bottom */)

        val offset = ChipInfo.which?.minLevelOffset ?: 0
        val insertIndex = (bin.levels.size - offset).coerceAtLeast(0)
        val scrollPos = 3 + insertIndex + 1
        
        onOpenLevels(binIndex, scrollToPosition = scrollPos)
    }

    @Throws(Exception::class)
    override fun onDuplicateLevel(binIndex: Int, levelIndex: Int) {
        if (!LevelOperations.canAddNewLevel(bins, binIndex)) return
        viewModelRef?.duplicateFrequency(binIndex, levelIndex)
        onOpenLevels(binIndex)
    }

    @Throws(Exception::class)
    override fun onRemoveLevel(binIndex: Int, levelIndex: Int) {
        viewModelRef?.removeFrequency(binIndex, levelIndex)
        onOpenLevels(binIndex)
    }

    @Throws(Exception::class)
    override fun onReorderLevels(binIndex: Int, items: List<GpuFreqAdapter.FreqItem>) {
        // Since drag & drop provides a list, we might need a batch update or specific move.
        // LevelOperations.updateBinsFromAdapter modifies in place.
        // We really want to replicate the move or just batch set the new levels.
        
        // GpuFrequencyViewModel does not have batch 'setLevels'.
        // It has 'reorderFrequency(from, to)'.
        // Adapting Drag&Drop list result to a single 'reorder' is hard if multiple moves happened.
        // Best approach: "Replace all levels for this bin".
        
        // Let's add 'updateLevels(binIndex, newLevels)' to ViewModel?
        // Or 'updateBin(binIndex, newBin)'.
        // Or we can use 'performBatchEdit'.
        
        viewModelRef?.performBatchEdit { mutableBins ->
            // Use LevelOperations to apply change to the mutable copy
             LevelOperations.updateBinsFromAdapter(mutableBins as ArrayList<Bin>, binIndex, items)
        }
    }

    @Throws(Exception::class)
    override fun onCurveEditor(binIndex: Int) {
        disableBackCallback()
        val fragment = GpuCurveEditorFragment()
        val args = android.os.Bundle()
        args.putInt("binId", binIndex)
        fragment.arguments = args

        val mainActivity = currentActivity as MainActivity
        val currentBackStackCount = mainActivity.supportFragmentManager.backStackEntryCount

        mainActivity.supportFragmentManager.addOnBackStackChangedListener(
            object : androidx.fragment.app.FragmentManager.OnBackStackChangedListener {
                override fun onBackStackChanged() {
                    val newCount = mainActivity.supportFragmentManager.backStackEntryCount
                    if (newCount < currentBackStackCount + 1) {
                        mainActivity.supportFragmentManager.removeOnBackStackChangedListener(this)
                        try {
                            // Curve editor popped, refresh view
                            onOpenLevels(binIndex)
                        } catch (e: Exception) {
                        }
                    }
                }
            })

        mainActivity.supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack("curve_editor")
            .commit()
    }

    @Throws(Exception::class)
    override fun onParamEdit(
        binIndex: Int,
        levelIndex: Int,
        lineIndex: Int,
        rawName: String,
        rawValue: String,
        paramTitle: String
    ) {
        val act = currentActivity ?: return
        
        ParameterEditHandler.handleParameterEdit(
            act, bins ?: ArrayList(), binIndex, levelIndex, lineIndex,
            rawName, rawValue, paramTitle,
            object : ParameterEditHandler.OnParameterEditedListener {
                override fun onEdited(lineIndex: Int, encodedLine: String, historyMessage: String) {
                    // Extract value from encoded line if possible, or pass whole line?
                    // ViewModel 'updateParameter' takes 'newValue'.
                    // But 'encodedLine' is the full line e.g. "qcom,freq = <300000>;"
                    // ParameterEditHandler logic constructs the line. 
                    // To follow ViewModel pattern, we should preferably just update the parameter value.
                    // But for now, we can use 'performBatchEdit' or just manually update if logic is complex.
                    
                    // Actually, ViewModel.updateParameter expects just the value inside <>.
                    // But ParameterEditHandler returns the FULL line.
                    // Let's use batch edit to just set the line.
                    
                    viewModelRef?.performBatchEdit { mutableBins ->
                         mutableBins.getOrNull(binIndex)?.levels?.getOrNull(levelIndex)?.lines?.set(lineIndex, encodedLine)
                    }
                    Toast.makeText(act, R.string.save_success, Toast.LENGTH_SHORT).show()
                }

                override fun refreshView() {
                    onOpenParamDetails(binIndex, levelIndex)
                }
            })
    }

    @Throws(Exception::class)
    override fun onFrequencyAdjust(
        binIndex: Int,
        levelIndex: Int,
        lineIndex: Int,
        rawName: String,
        deltaMHz: Int
    ) {
        val act = currentActivity ?: return
        
        ParameterEditHandler.handleFrequencyAdjust(
            act, bins ?: ArrayList(), binIndex, levelIndex, lineIndex,
            rawName, deltaMHz,
            object : ParameterEditHandler.OnParameterEditedListener {
                override fun onEdited(lineIndex: Int, encodedLine: String, historyMessage: String) {
                    viewModelRef?.performBatchEdit { mutableBins ->
                         mutableBins.getOrNull(binIndex)?.levels?.getOrNull(levelIndex)?.lines?.set(lineIndex, encodedLine)
                    }
                }

                override fun refreshView() {
                    onOpenParamDetails(binIndex, levelIndex)
                }
            })
    }

    @Throws(Exception::class)
    override fun onChangeChipset() {
        // Handled by view click listener directly invoking ChipsetManager
    }

    // ===== OnChipsetSwitchedListener Implementation =====

    override fun onInitAndDecode() {
        // Delegated to ViewModel
        viewModelRef?.loadData()
    }

    // ... (unchanged methods handled by previous refactor) ...

    @Throws(Exception::class)
    override fun onPatchThrottleLevel() {
        // Handled by Repository/ViewModel during load
    }

    override fun onResetEditorState() {
        // Handled by Repository state clearing
    }

    @Throws(Exception::class)
    override fun onRefreshView(restoredSession: Boolean, targetBinIndex: Int?, targetLevelIndex: Int?) {
        // View refresh is reactive in Fragment via Flow collection
        // But we might need to manually trigger generating views if Fragment relies on this callback
        // for specific restoration navigation.
        
        // This callback is called by ChipsetManager "onUiThread".
        // In the reactive world, the Fragment observes 'bins'.
        
        // However, restoring navigation state (which bin was open) is UI logic.
        if (targetBinIndex == null) {
            currentActivity?.let { 
                 if (currentPage != null && bins != null)
                     EditorUIBuilder.generateBins(it, currentPage!!, bins!!, this, this) 
            }
        } else {
            currentBinIndex = targetBinIndex
            currentActivity?.let { act ->
                if (targetLevelIndex != null) {
                    currentLevelIndex = targetLevelIndex
                    EditorUIBuilder.generateALevel(
                        act, currentPage!!, bins!!, targetBinIndex, targetLevelIndex, this
                    )
                } else {
                    EditorUIBuilder.generateLevels(act, currentPage!!, bins!!, targetBinIndex, this)
                }
                restoreBackListener(act)
            }
        }
    }

    override fun saveCurrentSession() {
        // TODO: Move session state to Repository/ViewModel
        stateManager.saveCurrentSession(lines_in_dts, bins, bin_position, currentBinIndex, currentLevelIndex)
    }

    override fun restoreSession(dtbId: Int): EditorStateManager.EditorSession? {
        return stateManager.restoreSession(dtbId)
    }

    override fun getLinesInDts(): ArrayList<String> {
        return lines_in_dts ?: ArrayList()
    }

    override fun getBins(): ArrayList<Bin> {
        return bins ?: ArrayList()
    }

    override fun getBinPosition(): Int {
        return bin_position
    }

    override fun setCurrentBinIndex(index: Int?) {
        currentBinIndex = index
    }

    override fun setCurrentLevelIndex(index: Int?) {
        currentLevelIndex = index
    }

    override fun getCurrentBinIndex(): Int? {
        return currentBinIndex
    }

    override fun getCurrentLevelIndex(): Int? {
        return currentLevelIndex
    }

    // ===== Entry Point (gpuTableLogic) =====
    // DELETED: Logic moved to GpuFrequencyViewModel using Coroutines.

    companion object {
        // Core Data - Populated by GpuRepository
        @JvmField
        var bin_position: Int = 0
        @JvmField
        var bins: ArrayList<Bin>? = null
        @JvmStatic
        var lines_in_dts: ArrayList<String>? = null

        // State Management
        private val stateManager = EditorStateManager.getInstance()

        // UI State
        @JvmField
        var currentActivity: Activity? = null
        @JvmField
        var currentPage: LinearLayout? = null
        private var currentBinIndex: Int? = null
        private var currentLevelIndex: Int? = null

        // ViewModel
        private var viewModelRef: GpuFrequencyViewModel? = null

        // Backward Compatibility Fields
        @JvmField
        val undoStack: Deque<EditorState>? = stateManager.getUndoStack()
        @JvmField
        val redoStack: Deque<EditorState>? = stateManager.getRedoStack()

        // ===== Initialization & Core I/O =====

        @JvmStatic
        @Throws(IOException::class)
        fun init() {
            // Legacy no-op or delegate if strictly needed. 
            // Repository handles loading.
        }

        @JvmStatic
        @Throws(Exception::class)
        fun decode() {
            // Legacy no-op. Repository handles decoding.
        }

        @JvmStatic
        fun genTable(): List<String> {
            return bins?.let { ChipInfo.which!!.architecture.generateTable(it) } ?: emptyList()
        }

        @JvmStatic
        fun genBack(table: List<String>?): List<String> {
            // Simple reconstruction if needed primarily by legacy save
            val new_dts = (lines_in_dts?.toMutableList() ?: mutableListOf())
            val insertPos = if (bin_position >= 0) bin_position.coerceAtMost(new_dts.size) else new_dts.size
            new_dts.addAll(insertPos, table.orEmpty())
            return new_dts
        }

        fun writeOut(new_dts: List<String>?) {
            // Delegate saving to ViewModel
             viewModelRef?.save(false) 
        }

        // ===== Public API =====

        @JvmStatic
        fun setViewModel(vm: GpuFrequencyViewModel?) {
            viewModelRef = vm
        }

        @JvmStatic
        fun getViewModel(): GpuFrequencyViewModel? {
            return viewModelRef
        }

        @JvmStatic
        fun getSelectedBinIndex(): Int? {
            return currentBinIndex
        }

        @JvmStatic
        fun registerToolbarButtons(
            save: MaterialButton?, undo: MaterialButton?,
            redo: MaterialButton?, history: MaterialButton?
        ) {
            stateManager.registerToolbarButtons(save, undo, redo, history)
        }

        @JvmStatic
        fun addHistoryListener(listener: EditorStateManager.OnHistoryStateChangedListener?) {
            stateManager.addHistoryListener(listener)
        }

        @JvmStatic
        fun removeHistoryListener(listener: EditorStateManager.OnHistoryStateChangedListener?) {
            stateManager.removeHistoryListener(listener)
        }

        @JvmStatic
        fun setDirty(isDirty: Boolean) {
            stateManager.setDirty(isDirty)
        }

        @JvmStatic
        fun updateUndoRedoButtons() {
             val canUndo = viewModelRef?.canUndo?.value ?: false
             val canRedo = viewModelRef?.canRedo?.value ?: false
             stateManager.updateUndoRedoButtons(canUndo, canRedo)
        }

        @JvmStatic
        fun updateHistoryButtonLabel() {
             val count = viewModelRef?.history?.value?.size ?: 0
             stateManager.updateHistoryButtonLabel(count)
        }
        
        @JvmStatic
        fun showHistoryDialog(activity: Activity?) {
             val history = viewModelRef?.history?.value ?: emptyList()
             stateManager.showHistoryDialog(activity, history)
        }

        /**
         * Entry point to generate the Bins UI on the main page.
         * Compatible with GpuFrequencyFragment usage.
         */
        @JvmStatic
        fun generateBins(activity: Activity, page: LinearLayout) {
            currentActivity = activity
            currentPage = page
            stateManager.currentActivity = activity
            
            // If bins are missing, try to load via ViewModel
            if (bins == null) {
                viewModelRef?.loadData()
                return 
            }

            try {
                val listener = GpuTableEditor()
                EditorUIBuilder.generateBins(activity, page, bins!!, listener, listener)
            } catch (e: Exception) {
                DialogUtil.showError(activity, R.string.error_occur)
            }
        }

        // ===== Facade Methods (Delegating to Components) =====

        @JvmStatic
        fun saveFrequencyTable(context: Context?, showToast: Boolean, historyMessage: String?) {
            // Delegate to ViewModel
            viewModelRef?.save(showToast)
        }

        @JvmStatic
        fun handleUndo() {
            viewModelRef?.undo()
            // UI refresh now handled reactively via stateVersion observer
        }

        @JvmStatic
        fun handleRedo() {
            viewModelRef?.redo()
            // UI refresh now handled reactively via stateVersion observer
        }

        @JvmStatic
        fun refreshCurrentView() {
            if (currentActivity == null || currentPage == null) return
            try {
                // Sync Legacy State from ViewModel (SSoT)
                viewModelRef?.let { vm ->
                    currentBinIndex = vm.selectedBinIndex.value
                    currentLevelIndex = vm.selectedLevelIndex.value
                }

                val instance = GpuTableEditor() // Helper for callbacks
                if (currentBinIndex == null || currentBinIndex!! < 0) {
                     if (bins != null) EditorUIBuilder.generateBins(currentActivity!!, currentPage!!, bins!!, instance, instance)
                } else if (currentLevelIndex == null || currentLevelIndex!! < 0) { // Fix: Allow 0 if it's a valid index? No, levelIndex use -1 for "none"? Need to check logic. Usually index >= 0 is valid.
                     // The logic above says "currentLevelIndex == null" -> generateLevels.
                     // If selectedLevelIndex is -1, it means "No Level Selected".
                     // So we should check if it's < 0.
                     
                     if (bins != null) EditorUIBuilder.generateLevels(
                        currentActivity!!,
                        currentPage!!,
                        bins!!,
                        currentBinIndex!!,
                        instance
                    )
                } else {
                    if (bins != null) EditorUIBuilder.generateALevel(
                        currentActivity!!,
                        currentPage!!,
                        bins!!,
                        currentBinIndex!!,
                        currentLevelIndex!!,
                        instance
                    )
                }
            } catch (e: Exception) {
                currentActivity?.let { DialogUtil.showError(it, R.string.error_occur) }
            }
        }

        @JvmStatic
        fun restoreBackListener(activity: Activity) {
            if (activity !is MainActivity) return
            if (currentPage == null) return

            if (currentLevelIndex != null && currentBinIndex != null) {
                enableBackCallback()
            } else if (currentBinIndex != null) {
                enableBackCallback()
            } else {
                disableBackCallback()
            }
        }

        @JvmStatic
        fun handleBackNavigation() {
            if (currentActivity == null || currentPage == null) return
            try {
                val instance = GpuTableEditor()
                if (currentLevelIndex != null && currentBinIndex != null) {
                    instance.onBack() // Go back to levels
                } else if (currentBinIndex != null) {
                    instance.onBack() // Go back to bins
                } else {
                    disableBackCallback()
                    if (currentActivity is MainActivity) {
                        (currentActivity as MainActivity).onBackPressedDispatcher.onBackPressed()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun enableBackCallback() {
            if (currentActivity is MainActivity) {
                val callback = (currentActivity as MainActivity).gpuTableEditorBackCallback
                callback?.isEnabled = true
            }
        }

        private fun disableBackCallback() {
            if (currentActivity is MainActivity) {
                val callback = (currentActivity as MainActivity).gpuTableEditorBackCallback
                callback?.isEnabled = false
            }
        }

        // Legacy support methods
        @JvmStatic
        fun createCompactChip(activity: Activity, textRes: Int, iconRes: Int): MaterialButton {
            return EditorUIBuilder.createCompactChip(activity, textRes, iconRes)
        }
    }
}
