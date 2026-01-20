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
        if (!LevelOperations.canAddNewLevel(bins, binIndex)) return
        val bin = bins?.getOrNull(binIndex) ?: return
        val activity = currentActivity ?: return

        val freqLabel = LevelOperations.getFrequencyLabel(bin.levels.firstOrNull() ?: return, activity)
        stateManager.applyChange(
            activity.getString(R.string.history_add_frequency, freqLabel),
            { LevelOperations.addLevelAtTop(bins, binIndex) },
            getLinesInDts(), bins!!, bin_position
        )
        onOpenLevels(binIndex, scrollToPosition = 3)
    }

    @Throws(Exception::class)
    override fun onAddLevelBottom(binIndex: Int) {
        if (!LevelOperations.canAddNewLevel(bins, binIndex)) return
        val bin = bins?.getOrNull(binIndex) ?: return
        val activity = currentActivity ?: return

        val offset = ChipInfo.which?.minLevelOffset ?: 0
        val insertIndex = (bin.levels.size - offset).coerceAtLeast(0)
        val freqLabel = bin.levels.getOrNull(insertIndex)?.let {
            LevelOperations.getFrequencyLabel(it, activity)
        } ?: "New Level"

        // Calculate scroll position: 3 headers + insertIndex + 1 (for newly added item)
        val scrollPos = 3 + insertIndex + 1
        
        stateManager.applyChange(
            activity.getString(R.string.history_add_frequency, freqLabel),
            { LevelOperations.addLevelAtBottom(bins, binIndex) },
            getLinesInDts(), bins!!, bin_position
        )
        onOpenLevels(binIndex, scrollToPosition = scrollPos)
    }

    @Throws(Exception::class)
    override fun onDuplicateLevel(binIndex: Int, levelIndex: Int) {
        if (!LevelOperations.canAddNewLevel(bins, binIndex)) return
        val bin = bins?.getOrNull(binIndex) ?: return
        val level = bin.levels.getOrNull(levelIndex) ?: return
        val activity = currentActivity ?: return

        val freqLabel = LevelOperations.getFrequencyLabel(level, activity)
        stateManager.applyChange(
            activity.getString(R.string.history_duplicate_frequency, freqLabel),
            { LevelOperations.duplicateLevel(bins, binIndex, levelIndex) },
            getLinesInDts(), bins!!, bin_position
        )
        onOpenLevels(binIndex)
    }

    @Throws(Exception::class)
    override fun onRemoveLevel(binIndex: Int, levelIndex: Int) {
        val bin = bins?.getOrNull(binIndex) ?: return
        val level = bin.levels.getOrNull(levelIndex) ?: return
        val activity = currentActivity ?: return

        val freqLabel = LevelOperations.getFrequencyLabel(level, activity)
        stateManager.applyChange(
            activity.getString(R.string.history_remove_frequency, freqLabel),
            { LevelOperations.removeLevel(bins, binIndex, levelIndex) },
            getLinesInDts(), bins!!, bin_position
        )

        onOpenLevels(binIndex)
    }

    @Throws(Exception::class)
    override fun onReorderLevels(binIndex: Int, items: List<GpuFreqAdapter.FreqItem>) {
        val binsSnapshot = LevelOperations.cloneBinsList(bins)
        val changed = LevelOperations.updateBinsFromAdapter(bins, binIndex, items)

        if (changed) {
            // Revert the change to capture state, then apply it properly via stateManager
            val newBins = LevelOperations.cloneBinsList(bins)
            bins!!.clear()
            bins!!.addAll(binsSnapshot) // Restore

            stateManager.applyChange(
                currentActivity!!.getString(R.string.history_reorder_frequency),
                {
                    bins!!.clear()
                    bins!!.addAll(newBins)
                },
                lines_in_dts!!, bins!!, bin_position
            )
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
        val currentBins = bins ?: return
        
        ParameterEditHandler.handleParameterEdit(
            act, currentBins, binIndex, levelIndex, lineIndex,
            rawName, rawValue, paramTitle,
            object : ParameterEditHandler.OnParameterEditedListener {
                override fun onEdited(lineIndex: Int, encodedLine: String, historyMessage: String) {
                    stateManager.applyChange(
                        historyMessage,
                        { currentBins[binIndex].levels[levelIndex].lines[lineIndex] = encodedLine },
                        getLinesInDts(), currentBins, bin_position
                    )
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
        val currentBins = bins ?: return
        
        ParameterEditHandler.handleFrequencyAdjust(
            act, currentBins, binIndex, levelIndex, lineIndex,
            rawName, deltaMHz,
            object : ParameterEditHandler.OnParameterEditedListener {
                override fun onEdited(lineIndex: Int, encodedLine: String, historyMessage: String) {
                    stateManager.applyChange(
                        historyMessage,
                        { bins!![binIndex].levels[levelIndex].lines[lineIndex] = encodedLine },
                        lines_in_dts!!, bins!!, bin_position
                    )
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

    @Throws(Exception::class)
    override fun onInitAndDecode() {
        init()
        decode()
    }

    @Throws(Exception::class)
    override fun onPatchThrottleLevel() {
        LevelOperations.patchThrottleLevel(bins)
    }

    override fun onResetEditorState() {
        stateManager.resetEditorState(genBack(genTable()))
    }

    @Throws(Exception::class)
    override fun onRefreshView(restoredSession: Boolean, targetBinIndex: Int?, targetLevelIndex: Int?) {
        stateManager.refreshDirtyStateFromSignature(lines_in_dts!!, bins!!)
        if (targetBinIndex == null) {
            EditorUIBuilder.generateBins(currentActivity!!, currentPage!!, bins!!, this, this)
        } else {
            currentBinIndex = targetBinIndex
            if (targetLevelIndex != null) {
                currentLevelIndex = targetLevelIndex
                EditorUIBuilder.generateALevel(
                    currentActivity!!,
                    currentPage!!,
                    bins!!,
                    targetBinIndex,
                    targetLevelIndex,
                    this
                )
            } else {
                EditorUIBuilder.generateLevels(currentActivity!!, currentPage!!, bins!!, targetBinIndex, this)
            }
            restoreBackListener(currentActivity!!)
        }
    }

    override fun saveCurrentSession() {
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

    class gpuTableLogic(activity: Activity, showedView: LinearLayout) : Thread() {
        var activity: Activity
        var waiting: AlertDialog? = null
        var showedView: LinearLayout
        var page: LinearLayout? = null

        init {
            this.activity = activity
            this.showedView = showedView
        }

        override fun run() {
            activity.runOnUiThread {
                waiting = DialogUtil.getWaitDialog(activity, R.string.getting_freq_table)
                waiting!!.show()
            }

            try {
                init()
                decode()
                LevelOperations.patchThrottleLevel(bins)
                stateManager.resetEditorState(genBack(genTable()))
            } catch (e: Exception) {
                activity.runOnUiThread {
                    DialogUtil.showError(
                        activity,
                        R.string.getting_freq_table_failed
                    )
                }
                return
            }

            activity.runOnUiThread {
                currentActivity = activity
                stateManager.currentActivity = activity
                stateManager.setOnStateRestoredCallback { refreshCurrentView() }

                waiting!!.dismiss()
                showedView.removeAllViews()

                val instance = GpuTableEditor()
                EditorUIBuilder.generateToolBar(activity, showedView, instance)

                page = LinearLayout(activity)
                page!!.orientation = LinearLayout.VERTICAL
                page!!.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                currentPage = page

                currentBinIndex = null
                currentLevelIndex = null

                try {
                    val listener = GpuTableEditor()
                    EditorUIBuilder.generateBins(activity, page!!, bins!!, listener, listener)
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.getting_freq_table_failed)
                }

                stateManager.updateUndoRedoButtons()
                stateManager.updateHistoryButtonLabel()
                stateManager.updateSaveButtonAppearance()

                showedView.addView(page)
            }
        }
    }

    companion object {
        // Core Data
        @JvmField
        var bin_position: Int = 0
        @JvmField
        var bins: ArrayList<Bin>? = null
        @JvmStatic
        private var lines_in_dts: ArrayList<String>? = null

        // State Management
        private val stateManager = EditorStateManager.getInstance()

        // UI State
        private var currentActivity: Activity? = null
        private var currentPage: LinearLayout? = null
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
            lines_in_dts = ArrayList()
            bins = ArrayList()
            bin_position = -1
            if (KonaBessCore.dts_path != null) {
                lines_in_dts!!.addAll(FileUtil.readLines(KonaBessCore.dts_path!!))
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun decode() {
            var i = -1
            while (++i < lines_in_dts!!.size) {
                val this_line = lines_in_dts!![i].trim { it <= ' ' }
                try {
                    if (ChipInfo.which!!.architecture.isStartLine(this_line)) {
                        if (bin_position < 0)
                            bin_position = i
                        ChipInfo.which!!.architecture.decode(lines_in_dts!!, bins!!, i)
                        i--
                    }
                } catch (e: Exception) {
                    throw e
                }
            }
        }

        @JvmStatic
        fun genTable(): List<String> {
            return ChipInfo.which!!.architecture.generateTable(bins!!)
        }

        @JvmStatic
        fun genBack(table: List<String>?): List<String> {
            val new_dts = (lines_in_dts?.toMutableList() ?: mutableListOf())
            val insertPos = if (bin_position >= 0) {
                 bin_position.coerceAtMost(new_dts.size)
            } else {
                 new_dts.size // Append if position invalid
            }
            new_dts.addAll(insertPos, table.orEmpty())
            return new_dts
        }

        fun writeOut(new_dts: List<String>?) {
            if (KonaBessCore.dts_path != null && new_dts != null) {
                FileUtil.writeLines(KonaBessCore.dts_path!!, new_dts)
            }
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
            stateManager.updateUndoRedoButtons()
        }

        @JvmStatic
        fun updateHistoryButtonLabel() {
            stateManager.updateHistoryButtonLabel()
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

            // Ensure state is initialized if accessed directly without gpuTableLogic
            if (bins == null) {
                try {
                    init()
                    decode()
                    LevelOperations.patchThrottleLevel(bins)
                    stateManager.resetEditorState(genBack(genTable()))
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.getting_freq_table_failed)
                    return
                }
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
            try {
                val table = genTable()
                val content = genBack(table)
                
                writeOut(content)
                stateManager.markStateSaved(content)
                if (historyMessage != null) {
                    stateManager.addHistoryEntry(historyMessage)
                }
                if (showToast) {
                    Toast.makeText(context, R.string.save_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (context is Activity) {
                    DialogUtil.showError(context, R.string.save_failed)
                }
            }
        }

        @JvmStatic
        fun handleUndo() {
            val act = currentActivity ?: return
            val currentBins = bins ?: return
            stateManager.handleUndo(
                lines_in_dts ?: ArrayList(), currentBins,
                { bin_position },
                { pos -> bin_position = pos })
        }

        @JvmStatic
        fun handleRedo() {
            val act = currentActivity ?: return
            val currentBins = bins ?: return
            stateManager.handleRedo(
                lines_in_dts ?: ArrayList(), currentBins,
                { bin_position },
                { pos -> bin_position = pos })
        }

        @JvmStatic
        fun showHistoryDialog(activity: Activity?) {
            stateManager.showHistoryDialog(activity)
        }

        private fun refreshCurrentView() {
            if (currentActivity == null || currentPage == null) return
            try {
                val instance = GpuTableEditor() // Helper for callbacks
                if (currentBinIndex == null || currentBinIndex!! < 0) {
                    EditorUIBuilder.generateBins(currentActivity!!, currentPage!!, bins!!, instance, instance)
                } else if (currentLevelIndex == null) {
                    EditorUIBuilder.generateLevels(
                        currentActivity!!,
                        currentPage!!,
                        bins!!,
                        currentBinIndex!!,
                        instance
                    )
                } else {
                    EditorUIBuilder.generateALevel(
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
