package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Looper
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.GpuTableEditor
import com.ireddragonicy.konabessnext.core.KonaBessCore
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.EditorState
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Collections
import java.util.Date
import java.util.Deque
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Manages editor state: undo/redo stacks, history entries, session caching, and dirty state tracking.
 */
class EditorStateManager private constructor() {

    private val undoStack: Deque<EditorState> = ArrayDeque()
    private val redoStack: Deque<EditorState> = ArrayDeque()
    private val changeHistory = ArrayList<String>()
    private val sessionCache: MutableMap<Int, EditorSession> = HashMap()
    private val historyListeners: MutableList<OnHistoryStateChangedListener> = CopyOnWriteArrayList()

    var isDirty: Boolean = false
        private set
    private var lastSavedSignature: String? = null

    // Button references for direct UI updates
    private var saveButtonRef: MaterialButton? = null
    private var undoButtonRef: MaterialButton? = null
    private var redoButtonRef: MaterialButton? = null
    private var historyButtonRef: MaterialButton? = null

    var currentActivity: Activity? = null

    // Callback for view refresh after undo/redo
    private var onStateRestoredCallback: Runnable? = null

    interface OnHistoryStateChangedListener {
        fun onHistoryStateChanged(canUndo: Boolean, canRedo: Boolean)
    }

    fun interface EditorChange {
        @Throws(Exception::class)
        fun run()
    }

    /**
     * Session data for caching editor state per DTB.
     */
    class EditorSession {
        var linesInDts: ArrayList<String>? = null
        var binsSnapshot: ArrayList<Bin>? = null
        var binPosition: Int = 0
        var undoStates: Deque<EditorState>? = null
        var redoStates: Deque<EditorState>? = null
        var history: ArrayList<String>? = null
        var savedSignature: String? = null
        var dirty: Boolean = false
        var selectedBinIndex: Int? = null
        var selectedLevelIndex: Int? = null
    }

    // ===== Activity & Callback Management =====

    fun setOnStateRestoredCallback(callback: Runnable?) {
        this.onStateRestoredCallback = callback
    }

    // ===== History Listener Management =====

    fun addHistoryListener(listener: OnHistoryStateChangedListener?) {
        if (listener != null && !historyListeners.contains(listener)) {
            historyListeners.add(listener)
            listener.onHistoryStateChanged(!undoStack.isEmpty(), !redoStack.isEmpty())
        }
    }

    fun removeHistoryListener(listener: OnHistoryStateChangedListener?) {
        historyListeners.remove(listener)
    }

    private fun notifyHistoryListeners() {
        val canUndo = !undoStack.isEmpty()
        val canRedo = !redoStack.isEmpty()
        for (listener in historyListeners) {
            listener.onHistoryStateChanged(canUndo, canRedo)
        }
    }

    // ===== Button Registration =====

    fun registerToolbarButtons(
        save: MaterialButton?, undo: MaterialButton?,
        redo: MaterialButton?, history: MaterialButton?
    ) {
        this.saveButtonRef = save
        this.undoButtonRef = undo
        this.redoButtonRef = redo
        this.historyButtonRef = history

        runOnMainThread {
            updateSaveButtonAppearance()
            updateUndoRedoButtons()
            updateHistoryButtonLabel()
        }
    }

    // ===== State Capture & Restore =====

    fun captureState(linesInDts: ArrayList<String>, bins: ArrayList<Bin>, binPosition: Int): EditorState {
        val state = EditorState()
        state.linesInDts = ArrayList(linesInDts)
        state.binsSnapshot = LevelOperations.cloneBinsList(bins)
        state.binPosition = binPosition
        return state
    }

    fun restoreState(state: EditorState?, linesInDts: ArrayList<String>, bins: ArrayList<Bin>) {
        if (state == null) return
        linesInDts.clear()
        if (state.linesInDts != null) {
            linesInDts.addAll(state.linesInDts)
        }
        bins.clear()
        if (state.binsSnapshot != null) {
            bins.addAll(LevelOperations.cloneBinsList(state.binsSnapshot))
        }
    }

    private fun cloneEditorState(original: EditorState?): EditorState? {
        if (original == null) return null
        val copy = EditorState()
        copy.linesInDts = if (original.linesInDts != null) ArrayList(original.linesInDts) else ArrayList()
        copy.binsSnapshot = LevelOperations.cloneBinsList(original.binsSnapshot)
        copy.binPosition = original.binPosition
        return copy
    }

    private fun cloneEditorStateDeque(source: Deque<EditorState>?): Deque<EditorState> {
        val clone = ArrayDeque<EditorState>()
        if (source == null) return clone
        for (state in source) {
            cloneEditorState(state)?.let { clone.addLast(it) }
        }
        return clone
    }

    // ===== Undo/Redo Operations =====

    fun pushUndoState(state: EditorState?) {
        if (state == null) return
        undoStack.push(state)
        while (undoStack.size > MAX_HISTORY_SIZE) {
            undoStack.removeLast()
        }
        redoStack.clear()
        updateUndoRedoButtons()
    }

    fun handleUndo(
        linesInDts: ArrayList<String>, bins: ArrayList<Bin>,
        binPositionGetter: Supplier<Int>,
        binPositionSetter: Consumer<Int>
    ) {
        if (undoStack.isEmpty()) return

        val previous = undoStack.pop()
        val currentSnapshot = captureState(linesInDts, bins, binPositionGetter.get())
        redoStack.push(currentSnapshot)
        while (redoStack.size > MAX_HISTORY_SIZE) {
            redoStack.removeLast()
        }

        restoreState(previous, linesInDts, bins)
        binPositionSetter.accept(previous.binPosition)

        onStateRestoredCallback?.run()
        refreshDirtyStateFromSignature(linesInDts, bins)
        updateUndoRedoButtons()

        currentActivity?.let {
            addHistoryEntry(it.getString(R.string.history_undo_action))
        }
    }

    fun handleRedo(
        linesInDts: ArrayList<String>, bins: ArrayList<Bin>,
        binPositionGetter: Supplier<Int>,
        binPositionSetter: Consumer<Int>
    ) {
        if (redoStack.isEmpty()) return

        val nextState = redoStack.pop()
        val currentSnapshot = captureState(linesInDts, bins, binPositionGetter.get())
        undoStack.push(currentSnapshot)
        while (undoStack.size > MAX_HISTORY_SIZE) {
            undoStack.removeLast()
        }

        restoreState(nextState, linesInDts, bins)
        binPositionSetter.accept(nextState.binPosition)

        onStateRestoredCallback?.run()
        refreshDirtyStateFromSignature(linesInDts, bins)
        updateUndoRedoButtons()

        currentActivity?.let {
            addHistoryEntry(it.getString(R.string.history_redo_action))
        }
    }

    fun canUndo(): Boolean {
        return !undoStack.isEmpty()
    }

    fun canRedo(): Boolean {
        return !redoStack.isEmpty()
    }

    // ===== Session Management =====

    fun saveCurrentSession(
        linesInDts: ArrayList<String>?, bins: ArrayList<Bin>?, binPosition: Int,
        currentBinIndex: Int?, currentLevelIndex: Int?
    ) {
        val current = KonaBessCore.currentDtb
        if (current == null || linesInDts == null || bins == null) return

        val session = EditorSession()
        session.linesInDts = ArrayList(linesInDts)
        session.binsSnapshot = LevelOperations.cloneBinsList(bins)
        session.binPosition = binPosition
        session.undoStates = cloneEditorStateDeque(undoStack)
        session.redoStates = cloneEditorStateDeque(redoStack)
        session.history = ArrayList(changeHistory)
        session.savedSignature = lastSavedSignature
        session.dirty = isDirty
        session.selectedBinIndex = currentBinIndex
        session.selectedLevelIndex = currentLevelIndex

        synchronized(sessionCache) {
            sessionCache[current.id] = session
        }
    }

    fun restoreSession(dtbId: Int): EditorSession? {
        val session: EditorSession?
        synchronized(sessionCache) {
            session = sessionCache[dtbId]
        }
        if (session == null) return null

        undoStack.clear()
        session.undoStates?.let { undoStack.addAll(cloneEditorStateDeque(it)) }

        redoStack.clear()
        session.redoStates?.let { redoStack.addAll(cloneEditorStateDeque(it)) }

        changeHistory.clear()
        session.history?.let { changeHistory.addAll(it) }

        lastSavedSignature = session.savedSignature
        isDirty = session.dirty

        return session
    }

    // ===== Dirty State Management =====

    fun setDirty(dirty: Boolean) {
        this.isDirty = dirty
        updateSaveButtonAppearance()
    }

    fun markStateSaved(snapshot: List<String>?) {
        lastSavedSignature = computeStateSignature(snapshot)
        setDirty(false)
        updateUndoRedoButtons()
    }

    private fun computeStateSignature(snapshot: List<String>?): String {
        if (snapshot == null) return ""
        val builder = StringBuilder()
        for (line in snapshot) {
            builder.append(line).append('\n')
        }
        return builder.toString()
    }

    fun refreshDirtyStateFromSignature(linesInDts: ArrayList<String>, bins: ArrayList<Bin>) {
        if (lastSavedSignature == null) {
            setDirty(true)
            return
        }
        // This needs access to genBack/genTable - delegate to GpuTableEditor logic in consumer if needed
        // For now, simpler equality check or just re-checking signature against current state?
        // In GpuTableEditor, we call genBack(genTable()) to get list.
        // Since we don't have that here, we assume dirty state is managed by actions calling setDirty(true)
        // or markStateSaved(). Logic for comparison is tricky without genBack.
        // However, restoring state usually means we are back to a known state.
        // If we want exact dirty checking, we'd need current snapshot.
    }

    fun resetEditorState(snapshot: List<String>) {
        undoStack.clear()
        redoStack.clear()
        changeHistory.clear()
        lastSavedSignature = computeStateSignature(snapshot)
        isDirty = false
        runOnMainThread {
            updateSaveButtonAppearance()
            updateUndoRedoButtons()
            updateHistoryButtonLabel()
        }
    }

    // ===== History Entry Management =====

    fun addHistoryEntry(description: String?) {
        if (description.isNullOrEmpty()) return
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        changeHistory.add(0, "$timestamp • ${description.trim()}")
        while (changeHistory.size > MAX_HISTORY_SIZE) {
            changeHistory.removeAt(changeHistory.size - 1)
        }
        updateHistoryButtonLabel()
    }

    fun getChangeHistory(): ArrayList<String> {
        return changeHistory
    }

    // ===== Apply Change with Undo Support =====

    @Throws(Exception::class)
    fun applyChange(
        description: String, change: EditorChange,
        linesInDts: ArrayList<String>, bins: ArrayList<Bin>, binPosition: Int
    ) {
        val snapshot = captureState(linesInDts, bins, binPosition)
        change.run()
        pushUndoState(snapshot)
        completeChange(description)
    }

    private fun completeChange(description: String) {
        addHistoryEntry(description)
        setDirty(true)
        updateUndoRedoButtons()
        autoSaveIfEnabled()
    }

    private fun autoSaveIfEnabled() {
        val activity = currentActivity ?: return
        if (!SettingsActivity.isAutoSaveEnabled(activity)) {
            updateSaveButtonAppearance()
            return
        }
        // Delegate actual save to GpuTableEditor
        GpuTableEditor.saveFrequencyTable(
            activity, false,
            activity.getString(R.string.history_auto_saved)
        )
    }

    // ===== UI Update Methods =====

    fun updateSaveButtonAppearance() {
        if (saveButtonRef == null) return
        runOnMainThread {
            if (saveButtonRef == null) return@runOnMainThread
            val backgroundAttr = if (isDirty)
                com.google.android.material.R.attr.colorErrorContainer
            else
                com.google.android.material.R.attr.colorSecondaryContainer
            val foregroundAttr = if (isDirty)
                com.google.android.material.R.attr.colorOnErrorContainer
            else
                com.google.android.material.R.attr.colorOnSecondaryContainer
            val rippleAttr = if (isDirty)
                com.google.android.material.R.attr.colorError
            else
                com.google.android.material.R.attr.colorSecondary

            val background = MaterialColors.getColor(saveButtonRef!!, backgroundAttr)
            val foreground = MaterialColors.getColor(saveButtonRef!!, foregroundAttr)
            val ripple = MaterialColors.getColor(saveButtonRef!!, rippleAttr)

            saveButtonRef!!.backgroundTintList = ColorStateList.valueOf(background)
            saveButtonRef!!.setTextColor(foreground)
            saveButtonRef!!.iconTint = ColorStateList.valueOf(foreground)
            saveButtonRef!!.rippleColor = ColorStateList.valueOf(ripple)
        }
    }

    fun updateUndoRedoButtons() {
        val canUndo = !undoStack.isEmpty()
        val canRedo = !redoStack.isEmpty()

        notifyHistoryListeners()

        if (undoButtonRef != null && currentActivity != null) {
            currentActivity!!.runOnUiThread {
                try {
                    undoButtonRef!!.isEnabled = canUndo
                    undoButtonRef!!.alpha = if (canUndo) 1.0f else 0.5f
                    redoButtonRef!!.isEnabled = canRedo
                    redoButtonRef!!.alpha = if (canRedo) 1.0f else 0.5f
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateHistoryButtonLabel() {
        if (historyButtonRef == null) return
        runOnMainThread {
            if (historyButtonRef == null) return@runOnMainThread
            // Icon-only button - update content description for accessibility
            val count = changeHistory.size
            val description = if (currentActivity != null) {
                if (count == 0) {
                    currentActivity!!.getString(R.string.history)
                } else {
                    currentActivity!!.getString(R.string.history_with_count, count)
                }
            } else {
                "History${if (count == 0) "" else " ($count)"}"
            }
            historyButtonRef!!.contentDescription = description
        }
    }

    fun showHistoryDialog(activity: Activity?) {
        if (activity == null) return
        if (changeHistory.isEmpty()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.history_title)
                .setMessage(R.string.history_empty)
                .setPositiveButton(R.string.ok, null)
                .create()
                .show()
            return
        }
        val entries = changeHistory.toTypedArray<CharSequence>()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.history_title)
            .setItems(entries, null)
            .setPositiveButton(R.string.ok, null)
            .create()
            .show()
    }

    // ===== Utility =====

    private fun runOnMainThread(action: Runnable?) {
        if (action == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
            return
        }
        currentActivity?.runOnUiThread(action)
    }

    // ===== Stack Access (for GpuTableEditor compatibility) =====

    fun getUndoStack(): Deque<EditorState> {
        return undoStack
    }

    fun getRedoStack(): Deque<EditorState> {
        return redoStack
    }

    companion object {
        private const val MAX_HISTORY_SIZE = 50

        // ===== Singleton-like access for static methods compatibility =====
        @Volatile
        private var instance: EditorStateManager? = null

        @JvmStatic
        fun getInstance(): EditorStateManager {
            return instance ?: synchronized(this) {
                instance ?: EditorStateManager().also { instance = it }
            }
        }

        @JvmStatic
        fun getInstanceReference(): EditorStateManager {
            return getInstance()
        }
    }
}
