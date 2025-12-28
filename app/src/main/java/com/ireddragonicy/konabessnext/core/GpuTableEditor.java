package com.ireddragonicy.konabessnext.core;

import android.app.Activity;
import java.util.concurrent.CopyOnWriteArrayList;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.os.Looper;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.ui.MainActivity;
import com.ireddragonicy.konabessnext.ui.SettingsActivity;
import com.ireddragonicy.konabessnext.data.KonaBessStr;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.ireddragonicy.konabessnext.ui.adapters.ChipsetSelectorAdapter;
import com.ireddragonicy.konabessnext.ui.adapters.GpuBinAdapter;
import com.ireddragonicy.konabessnext.ui.adapters.GpuFreqAdapter;
import com.ireddragonicy.konabessnext.ui.adapters.GpuParamDetailAdapter;
import com.ireddragonicy.konabessnext.ui.adapters.ParamAdapter;
import com.ireddragonicy.konabessnext.utils.DialogUtil;
import com.ireddragonicy.konabessnext.utils.DtsHelper;
import com.ireddragonicy.konabessnext.utils.ItemTouchHelperCallback;
import com.ireddragonicy.konabessnext.core.strategy.ChipArchitecture;

// MVVM Architecture imports
import com.ireddragonicy.konabessnext.viewmodel.GpuFrequencyViewModel;
import com.ireddragonicy.konabessnext.model.Bin;
import com.ireddragonicy.konabessnext.model.Level;
import com.ireddragonicy.konabessnext.model.EditorState;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

public class GpuTableEditor {
    public static int bin_position;

    // Using model.Bin and model.Level for MVVM architecture
    public static ArrayList<Bin> bins;

    private static ArrayList<String> lines_in_dts;

    private static final int MAX_HISTORY_SIZE = 50;
    public static final Deque<EditorState> undoStack = new ArrayDeque<>();
    public static final Deque<EditorState> redoStack = new ArrayDeque<>();
    private static final ArrayList<String> changeHistory = new ArrayList<>();
    private static final Map<Integer, EditorSession> sessionCache = new HashMap<>();

    private static Activity currentActivity;
    private static LinearLayout currentPage;
    private static Integer currentBinIndex = null;
    private static Integer currentLevelIndex = null;

    public interface OnHistoryStateChangedListener {
        void onHistoryStateChanged(boolean canUndo, boolean canRedo);
    }

    private static final java.util.List<OnHistoryStateChangedListener> historyListeners = new CopyOnWriteArrayList<>();

    public static void addHistoryListener(OnHistoryStateChangedListener listener) {
        if (listener != null && !historyListeners.contains(listener)) {
            historyListeners.add(listener);
            // Immediate update
            listener.onHistoryStateChanged(!undoStack.isEmpty(), !redoStack.isEmpty());
        }
    }

    public static void removeHistoryListener(OnHistoryStateChangedListener listener) {
        historyListeners.remove(listener);
    }

    // Maintain old refs for a moment or remove?
    // User wants "Top Professional". Obsolete static refs are bad.
    // But I must ensure I don't break existing code if I don't refactor everything
    // at once.
    // I will keep them but Deprecate usage.
    private static MaterialButton saveButtonRef;
    private static MaterialButton undoButtonRef;
    private static MaterialButton redoButtonRef;
    private static MaterialButton historyButtonRef;

    private static boolean isDirty = false;
    private static String lastSavedSignature;

    // MVVM ViewModel reference - bridge for gradual migration
    private static GpuFrequencyViewModel viewModelRef;

    /**
     * Initialize ViewModel reference for MVVM integration.
     * Should be called from Activity/Fragment before using GpuTableEditor.
     */
    public static void setViewModel(GpuFrequencyViewModel vm) {
        viewModelRef = vm;
    }

    /**
     * Get the current ViewModel reference.
     */
    public static GpuFrequencyViewModel getViewModel() {
        return viewModelRef;
    }

    /**
     * Registers toolbar buttons to allow GpuTableEditor to update their state
     * directly.
     * This fixes issues where the buttons wouldn't update (e.g. Save button color).
     */
    public static void registerToolbarButtons(MaterialButton save, MaterialButton undo,
            MaterialButton redo, MaterialButton history) {
        saveButtonRef = save;
        undoButtonRef = undo;
        redoButtonRef = redo;
        historyButtonRef = history;

        // Immediate update to reflect current state
        runOnMainThread(() -> {
            updateSaveButtonAppearance();
            updateUndoRedoButtons();
            updateHistoryButtonLabel();
        });
    }

    private static class EditorSession {
        ArrayList<String> linesInDts;
        ArrayList<Bin> binsSnapshot;
        int binPosition;
        Deque<EditorState> undoStates;
        Deque<EditorState> redoStates;
        ArrayList<String> history;
        String savedSignature;
        boolean dirty;
        Integer selectedBinIndex;
        Integer selectedLevelIndex;
    }

    private static boolean isOnMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private static void runOnMainThread(Runnable action) {
        if (action == null) {
            return;
        }
        if (isOnMainThread()) {
            action.run();
            return;
        }
        Activity activity = currentActivity;
        if (activity != null) {
            activity.runOnUiThread(action);
        }
    }

    // EditorState is now in com.ireddragonicy.konabessnext.model.EditorState

    public static void init() throws IOException {
        lines_in_dts = new ArrayList<>();
        bins = new ArrayList<>();
        bin_position = -1;
        BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(KonaBessCore.dts_path)));
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            lines_in_dts.add(s);
        }
    }

    public static void decode() throws Exception {
        int i = -1;
        while (++i < lines_in_dts.size()) {
            String this_line = lines_in_dts.get(i).trim();
            try {
                if (ChipInfo.which.architecture.isStartLine(this_line)) {
                    if (bin_position < 0)
                        bin_position = i;
                    ChipInfo.which.architecture.decode(lines_in_dts, bins, i);
                    // Since decode removes lines from lines_in_dts, we need to adjust index
                    // decode removes lines [i, end]. The next line is now at index i.
                    // The loop does ++i, so we need to decrement i to process the current index
                    // again.
                    i--;
                }
            } catch (Exception e) {
                // Ignore parsing errors for blocks we don't care about, or rethrow if critical
                // But GpuTableEditor logic mostly ignored "bracket!=0" exceptions in some
                // paths.
                // However, BaseChipArchitecture throws Exception on failure.
                // We should probably allow the loop to continue or fail?
                // Original code threw Exception in some cases.
                throw e;
            }
        }
    }

    public static List<String> genTable() {
        return ChipInfo.which.architecture.generateTable(bins);
    }

    public static List<String> genBack(List<String> table) {
        ArrayList<String> new_dts = new ArrayList<>(lines_in_dts);
        new_dts.addAll(bin_position, table);
        return new_dts;
    }

    public static void writeOut(List<String> new_dts) throws IOException {
        File file = new File(KonaBessCore.dts_path);

        // If file exists, delete it first to avoid permission issues
        if (file.exists()) {
            if (!file.delete()) {
                // If can't delete, try to set writable first
                file.setWritable(true);
                if (!file.delete()) {
                    throw new IOException("Cannot delete existing file: " + file.getAbsolutePath());
                }
            }
        }

        // Create new file
        if (!file.createNewFile()) {
            throw new IOException("Failed to create file: " + file.getAbsolutePath());
        }

        // Set proper permissions
        file.setReadable(true, false);
        file.setWritable(true, false);

        BufferedWriter bufferedWriter = null;
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(file));
            for (String s : new_dts) {
                bufferedWriter.write(s);
                bufferedWriter.newLine();
            }
        } finally {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
            }
        }
    }

    public static EditorState captureState() {
        EditorState state = new EditorState();
        state.linesInDts = new ArrayList<>(lines_in_dts);
        state.binsSnapshot = cloneBinsList(bins);
        state.binPosition = bin_position;
        return state;
    }

    private static ArrayList<Bin> cloneBinsList(List<Bin> source) {
        ArrayList<Bin> clone = new ArrayList<>();
        if (source == null) {
            return clone;
        }
        for (Bin original : source) {
            clone.add(cloneBin(original));
        }
        return clone;
    }

    private static Bin cloneBin(Bin original) {
        Bin copy = new Bin();
        copy.id = original.id;
        copy.header = new ArrayList<>(original.header);
        copy.levels = new ArrayList<>();
        for (Level lvl : original.levels) {
            copy.levels.add(cloneLevel(lvl));
        }
        return copy;
    }

    private static Level cloneLevel(Level original) {
        Level copy = new Level();
        copy.lines = new ArrayList<>(original.lines);
        return copy;
    }

    private static void restoreState(EditorState state) {
        if (state == null) {
            return;
        }
        lines_in_dts = new ArrayList<>(state.linesInDts);
        bins = cloneBinsList(state.binsSnapshot);
        bin_position = state.binPosition;
    }

    private static EditorState cloneEditorState(EditorState original) {
        if (original == null) {
            return null;
        }
        EditorState copy = new EditorState();
        copy.linesInDts = original.linesInDts != null
                ? new ArrayList<>(original.linesInDts)
                : new ArrayList<>();
        copy.binsSnapshot = cloneBinsList(original.binsSnapshot);
        copy.binPosition = original.binPosition;
        return copy;
    }

    private static Deque<EditorState> cloneEditorStateDeque(Deque<EditorState> source) {
        ArrayDeque<EditorState> clone = new ArrayDeque<>();
        if (source == null) {
            return clone;
        }
        for (EditorState state : source) {
            clone.addLast(cloneEditorState(state));
        }
        return clone;
    }

    private static void saveCurrentSession() {
        KonaBessCore.Dtb current = KonaBessCore.getCurrentDtb();
        if (current == null || lines_in_dts == null || bins == null) {
            return;
        }
        EditorSession session = new EditorSession();
        session.linesInDts = new ArrayList<>(lines_in_dts);
        session.binsSnapshot = cloneBinsList(bins);
        session.binPosition = bin_position;
        session.undoStates = cloneEditorStateDeque(undoStack);
        session.redoStates = cloneEditorStateDeque(redoStack);
        session.history = new ArrayList<>(changeHistory);
        session.savedSignature = lastSavedSignature;
        session.dirty = isDirty;
        session.selectedBinIndex = currentBinIndex;
        session.selectedLevelIndex = currentLevelIndex;
        synchronized (sessionCache) {
            sessionCache.put(current.id, session);
        }
    }

    private static boolean restoreSession(int dtbId) {
        EditorSession session;
        synchronized (sessionCache) {
            session = sessionCache.get(dtbId);
        }
        if (session == null) {
            return false;
        }
        lines_in_dts = session.linesInDts != null ? new ArrayList<>(session.linesInDts) : new ArrayList<>();
        bins = cloneBinsList(session.binsSnapshot);
        bin_position = session.binPosition;

        undoStack.clear();
        undoStack.addAll(cloneEditorStateDeque(session.undoStates));

        redoStack.clear();
        redoStack.addAll(cloneEditorStateDeque(session.redoStates));

        changeHistory.clear();
        if (session.history != null) {
            changeHistory.addAll(session.history);
        }

        lastSavedSignature = session.savedSignature;
        isDirty = session.dirty;
        currentBinIndex = session.selectedBinIndex;
        currentLevelIndex = session.selectedLevelIndex;
        return true;
    }

    private static void pushUndoState(EditorState state) {
        if (state == null) {
            return;
        }
        undoStack.push(state);
        while (undoStack.size() > MAX_HISTORY_SIZE) {
            undoStack.removeLast();
        }
        redoStack.clear();
        updateUndoRedoButtons();
    }

    private static void setDirty(boolean dirty) {
        isDirty = dirty;
        updateSaveButtonAppearance();
    }

    private static void updateSaveButtonAppearance() {
        if (saveButtonRef == null) {
            return;
        }
        runOnMainThread(() -> {
            if (saveButtonRef == null) {
                return;
            }
            int backgroundAttr = isDirty
                    ? com.google.android.material.R.attr.colorErrorContainer
                    : com.google.android.material.R.attr.colorSecondaryContainer;
            int foregroundAttr = isDirty
                    ? com.google.android.material.R.attr.colorOnErrorContainer
                    : com.google.android.material.R.attr.colorOnSecondaryContainer;
            int rippleAttr = isDirty
                    ? com.google.android.material.R.attr.colorError
                    : com.google.android.material.R.attr.colorSecondary;

            int background = MaterialColors.getColor(saveButtonRef, backgroundAttr);
            int foreground = MaterialColors.getColor(saveButtonRef, foregroundAttr);
            int ripple = MaterialColors.getColor(saveButtonRef, rippleAttr);

            saveButtonRef.setBackgroundTintList(ColorStateList.valueOf(background));
            saveButtonRef.setTextColor(foreground);
            saveButtonRef.setIconTint(ColorStateList.valueOf(foreground));
            saveButtonRef.setRippleColor(ColorStateList.valueOf(ripple));
        });
    }

    public static void updateUndoRedoButtons() {
        boolean canUndo = !undoStack.isEmpty();
        boolean canRedo = !redoStack.isEmpty();

        // Notify all listeners (Modular approach)
        for (OnHistoryStateChangedListener listener : historyListeners) {
            listener.onHistoryStateChanged(canUndo, canRedo);
        }

        // Legacy support (Direct View manipulation) - Deprecate but keep for
        // safety/transitions
        if (undoButtonRef != null && currentActivity != null) {
            currentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        undoButtonRef.setEnabled(canUndo);
                        undoButtonRef.setAlpha(canUndo ? 1.0f : 0.5f);

                        redoButtonRef.setEnabled(canRedo);
                        redoButtonRef.setAlpha(canRedo ? 1.0f : 0.5f);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private static void updateHistoryButtonLabel() {
        if (historyButtonRef == null) {
            return;
        }
        runOnMainThread(() -> {
            if (historyButtonRef == null) {
                return;
            }
            Activity activity = currentActivity;
            if (activity == null) {
                historyButtonRef
                        .setText("History" + (changeHistory.isEmpty() ? "" : " (" + changeHistory.size() + ")"));
                return;
            }
            if (changeHistory.isEmpty()) {
                historyButtonRef.setText(activity.getString(R.string.history));
            } else {
                historyButtonRef.setText(activity.getString(R.string.history_with_count, changeHistory.size()));
            }
        });
    }

    private static void addHistoryEntry(String description) {
        if (description == null || description.trim().isEmpty()) {
            return;
        }
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        changeHistory.add(0, timestamp + " • " + description.trim());
        while (changeHistory.size() > MAX_HISTORY_SIZE) {
            changeHistory.remove(changeHistory.size() - 1);
        }
        updateHistoryButtonLabel();
    }

    @FunctionalInterface
    public interface EditorChange {
        void run() throws Exception;
    }

    public static void applyChange(String description, EditorChange change) throws Exception {
        EditorState snapshot = captureState();
        change.run();
        pushUndoState(snapshot);
        completeChange(description);
    }

    private static void completeChange(String description) {
        addHistoryEntry(description);
        setDirty(true);
        updateUndoRedoButtons();
        autoSaveIfEnabled();
    }

    private static void autoSaveIfEnabled() {
        if (currentActivity == null) {
            return;
        }
        if (!SettingsActivity.isAutoSaveEnabled(currentActivity)) {
            updateSaveButtonAppearance();
            return;
        }
        boolean success = saveFrequencyTable(currentActivity, false,
                currentActivity.getString(R.string.history_auto_saved));
        if (!success) {
            setDirty(true);
        }
    }

    public static boolean saveFrequencyTable(Context context, boolean showToast, String historyMessage) {
        try {
            writeOut(genBack(genTable()));
            markStateSaved();
            if (historyMessage != null) {
                addHistoryEntry(historyMessage);
            }
            if (showToast) {
                Toast.makeText(context, R.string.save_success, Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (Exception e) {
            if (context instanceof Activity) {
                DialogUtil.showError((Activity) context, R.string.save_failed);
            }
            return false;
        }
    }

    private static void markStateSaved() {
        lastSavedSignature = computeStateSignature();
        setDirty(false);
        updateUndoRedoButtons();
    }

    private static String computeStateSignature() {
        try {
            List<String> snapshot = genBack(genTable());
            StringBuilder builder = new StringBuilder();
            for (String line : snapshot) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toString(bins.hashCode());
        }
    }

    private static void refreshDirtyStateFromSignature() {
        if (lastSavedSignature == null) {
            setDirty(true);
            return;
        }
        boolean dirty = !lastSavedSignature.equals(computeStateSignature());
        setDirty(dirty);
    }

    private static void resetEditorState() {
        undoStack.clear();
        redoStack.clear();
        changeHistory.clear();
        lastSavedSignature = computeStateSignature();
        isDirty = false;
        runOnMainThread(() -> {
            updateSaveButtonAppearance();
            updateUndoRedoButtons();
            updateHistoryButtonLabel();
        });
    }

    private static void refreshCurrentView() {
        if (currentActivity == null || currentPage == null) {
            return;
        }
        try {
            if (currentBinIndex == null || currentBinIndex < 0) {
                generateBins(currentActivity, currentPage);
            } else if (currentLevelIndex == null) {
                generateLevels(currentActivity, currentBinIndex, currentPage);
            } else {
                generateALevel(currentActivity, currentBinIndex, currentLevelIndex, currentPage);
            }
        } catch (Exception e) {
            DialogUtil.showError(currentActivity, R.string.error_occur);
        }
    }

    public static void handleUndo() {
        if (undoStack.isEmpty()) {
            return;
        }
        EditorState previous = undoStack.pop();
        EditorState currentSnapshot = captureState();
        redoStack.push(currentSnapshot);
        while (redoStack.size() > MAX_HISTORY_SIZE) {
            redoStack.removeLast();
        }
        restoreState(previous);
        refreshCurrentView();
        refreshDirtyStateFromSignature();
        updateUndoRedoButtons();
        if (currentActivity != null) {
            addHistoryEntry(currentActivity.getString(R.string.history_undo_action));
        }
    }

    public static void handleRedo() {
        if (redoStack.isEmpty()) {
            return;
        }
        EditorState nextState = redoStack.pop();
        EditorState currentSnapshot = captureState();
        undoStack.push(currentSnapshot);
        while (undoStack.size() > MAX_HISTORY_SIZE) {
            undoStack.removeLast();
        }
        restoreState(nextState);
        refreshCurrentView();
        refreshDirtyStateFromSignature();
        updateUndoRedoButtons();
        if (currentActivity != null) {
            addHistoryEntry(currentActivity.getString(R.string.history_redo_action));
        }
    }

    /**
     * Handles back navigation from the OnBackPressedDispatcher callback.
     * Navigates up the hierarchy: Param Detail → Levels → Bins → (disable callback,
     * let system handle)
     */
    public static void handleBackNavigation() {
        if (currentActivity == null || currentPage == null) {
            return;
        }
        try {
            if (currentLevelIndex != null && currentBinIndex != null) {
                // In Param Detail level → go back to Levels list
                generateLevels(currentActivity, currentBinIndex, currentPage);
            } else if (currentBinIndex != null) {
                // In Levels list → go back to Bins list
                generateBins(currentActivity, currentPage);
                ((MainActivity) currentActivity).updateGpuToolbarTitle(
                        currentActivity.getString(R.string.edit_freq_table));
            } else {
                // At root Bins level → disable callback, let system handle back
                disableBackCallback();
                // Trigger actual back navigation (will exit fragment/activity)
                if (currentActivity instanceof MainActivity) {
                    ((MainActivity) currentActivity).getOnBackPressedDispatcher().onBackPressed();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Enables the GpuTableEditor back callback in MainActivity.
     * Called when navigating into sub-levels (Levels, Param Details).
     */
    private static void enableBackCallback() {
        if (currentActivity instanceof MainActivity) {
            OnBackPressedCallback callback = ((MainActivity) currentActivity).getGpuTableEditorBackCallback();
            if (callback != null) {
                callback.setEnabled(true);
            }
        }
    }

    /**
     * Disables the GpuTableEditor back callback in MainActivity.
     * Called when at root level or when fragment should handle back.
     */
    private static void disableBackCallback() {
        if (currentActivity instanceof MainActivity) {
            OnBackPressedCallback callback = ((MainActivity) currentActivity).getGpuTableEditorBackCallback();
            if (callback != null) {
                callback.setEnabled(false);
            }
        }
    }

    public static void showHistoryDialog(Activity activity) {
        if (activity == null) {
            return;
        }
        if (changeHistory.isEmpty()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.history_title)
                    .setMessage(R.string.history_empty)
                    .setPositiveButton(R.string.ok, null)
                    .create()
                    .show();
            return;
        }
        CharSequence[] entries = changeHistory.toArray(new CharSequence[0]);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.history_title)
                .setItems(entries, null)
                .setPositiveButton(R.string.ok, null)
                .create()
                .show();
    }

    private static String generateSubtitle(String line) throws Exception {
        String raw_name = DtsHelper.decode_hex_line(line).name;
        if ("qcom,level".equals(raw_name) || "qcom,cx-level".equals(raw_name)) {
            return GpuVoltEditor.levelint2str(DtsHelper.decode_int_line(line).value);
        }
        return DtsHelper.shouldUseHex(line) ? DtsHelper.decode_hex_line(line).value
                : DtsHelper.decode_int_line(line).value + "";
    }

    private static void generateALevel(Activity activity, int last, int levelid,
            LinearLayout page) throws Exception {
        // Enable callback for back navigation (Param Detail → Levels)
        enableBackCallback();

        currentActivity = activity;
        currentPage = page;
        currentBinIndex = last;
        currentLevelIndex = levelid;
        updateUndoRedoButtons();
        updateHistoryButtonLabel();
        updateSaveButtonAppearance();

        // Create RecyclerView with Material You card design
        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));

        ArrayList<GpuParamDetailAdapter.ParamDetailItem> items = new ArrayList<>();

        // Add back button as first item
        items.add(new GpuParamDetailAdapter.ParamDetailItem(
                activity.getResources().getString(R.string.back),
                R.drawable.ic_back,
                true));

        // Group bus-max, bus-min, and bus-freq into modern stat cards
        ArrayList<GpuParamDetailAdapter.StatItem> statsGroup = new ArrayList<>();
        ArrayList<GpuParamDetailAdapter.ParamDetailItem> otherParams = new ArrayList<>();

        int lineIndex = 0;
        for (String line : bins.get(last).levels.get(levelid).lines) {
            String paramName = DtsHelper.decode_hex_line(line).name;
            String paramTitle = KonaBessStr.convert_level_params(paramName, activity);
            String paramValue = generateSubtitle(line);
            int iconRes = GpuParamDetailAdapter.getIconForParam(paramName);

            // Group bus-freq, bus-min, and bus-max into stat cards (NOT gpu-freq)
            if (paramName.contains("bus-freq") || paramName.contains("bus-min") || paramName.contains("bus-max")) {

                // Simplify label for stat cards
                String statLabel = paramTitle;
                if (paramName.contains("bus-freq")) {
                    statLabel = "Bus Freq";
                } else if (paramName.contains("bus-min")) {
                    statLabel = "Bus Min";
                } else if (paramName.contains("bus-max")) {
                    statLabel = "Bus Max";
                }

                statsGroup.add(new GpuParamDetailAdapter.StatItem(
                        statLabel,
                        paramValue,
                        paramName,
                        iconRes,
                        lineIndex));
            } else {
                // Other parameters use regular card layout
                // Special handling for GPU frequency to make it clearer
                String displayTitle = paramTitle;
                if (paramName.contains("gpu-freq") || (paramName.contains("frequency") && !paramName.contains("bus"))) {
                    displayTitle = "GPU Frequency";
                }

                GpuParamDetailAdapter.ParamDetailItem paramItem = new GpuParamDetailAdapter.ParamDetailItem(
                        displayTitle,
                        paramValue,
                        paramName,
                        iconRes);
                if (displayTitle.equals("GPU Frequency")) {
                    paramItem.isFrequencyControl = true;
                }
                paramItem.lineIndex = lineIndex;
                otherParams.add(paramItem);
            }
            lineIndex++;
        }

        // Add stats group first if we have any
        if (!statsGroup.isEmpty()) {
            items.add(new GpuParamDetailAdapter.ParamDetailItem(statsGroup));
        }

        // Add other parameters
        items.addAll(otherParams);

        GpuParamDetailAdapter adapter = new GpuParamDetailAdapter(items, activity);
        adapter.setOnItemClickListener(new GpuParamDetailAdapter.OnItemClickListener() {
            @Override
            public void onBackClicked() {
                try {
                    generateLevels(activity, last, page);
                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.error_occur);
                }
            }

            @Override
            public void onStatItemClicked(GpuParamDetailAdapter.StatItem statItem) {
                if (statItem == null) {
                    return;
                }
                try {
                    int actualLineIndex = statItem.lineIndex;
                    String line = bins.get(last).levels.get(levelid).lines.get(actualLineIndex);
                    String raw_name = statItem.paramName;
                    String raw_value = DtsHelper.shouldUseHex(line)
                            ? DtsHelper.decode_hex_line(line).value
                            : DtsHelper.decode_int_line(line).value + "";
                    handleParameterEdit(activity, last, levelid, page, actualLineIndex, raw_name, raw_value,
                            statItem.label);
                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.error_occur);
                }
            }

            @Override
            public void onParamClicked(GpuParamDetailAdapter.ParamDetailItem item) {
                if (item == null || item.isStatsGroup) {
                    return;
                }
                try {
                    int actualLineIndex = item.lineIndex;
                    if (actualLineIndex < 0 || actualLineIndex >= bins.get(last).levels.get(levelid).lines.size()) {
                        return;
                    }
                    String line = bins.get(last).levels.get(levelid).lines.get(actualLineIndex);
                    String raw_name = item.paramName;
                    String raw_value = DtsHelper.shouldUseHex(line)
                            ? DtsHelper.decode_hex_line(line).value
                            : DtsHelper.decode_int_line(line).value + "";
                    String paramTitle = item.title;
                    handleParameterEdit(activity, last, levelid, page, actualLineIndex, raw_name, raw_value,
                            paramTitle);
                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.error_occur);
                }
            }

            @Override
            public void onFrequencyAdjust(GpuParamDetailAdapter.ParamDetailItem item, int deltaMHz) {
                try {
                    int actualLineIndex = item.lineIndex;
                    String line = bins.get(last).levels.get(levelid).lines.get(actualLineIndex);
                    String raw_name = item.paramName;

                    long currentVal = 0;
                    if (DtsHelper.shouldUseHex(line)) {
                        String hexVal = DtsHelper.decode_hex_line(line).value;
                        currentVal = Long.parseLong(hexVal.replace("0x", ""), 16);
                    } else {
                        currentVal = DtsHelper.decode_int_line(line).value;
                    }

                    long newVal = currentVal + (deltaMHz * 1000000L);
                    if (newVal < 0)
                        newVal = 0;

                    final String newValueStr = String.valueOf(newVal);
                    final String encodedLine = DtsHelper.encodeIntOrHexLine(raw_name, newValueStr);

                    final String freqLabel = SettingsActivity.formatFrequency(newVal, activity);

                    applyChange(activity.getString(R.string.history_edit_parameter, "GPU Frequency", freqLabel),
                            () -> bins.get(last).levels.get(levelid).lines.set(actualLineIndex, encodedLine));

                    generateALevel(activity, last, levelid, page);

                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.save_failed);
                    e.printStackTrace();
                }
            }
        });

        recyclerView.setAdapter(adapter);

        page.removeAllViews();
        page.addView(recyclerView);
    }

    private static void handleParameterEdit(Activity activity, int binIndex, int levelIndex,
            LinearLayout page, int lineIndex, String raw_name,
            String raw_value, String paramTitle) throws Exception {
        // Handle voltage level editing with spinner
        if (raw_name.equals("qcom,level") || raw_name.equals("qcom,cx-level")) {
            try {
                Spinner spinner = new Spinner(activity);
                ArrayAdapter<String> levelAdapter = new ArrayAdapter<>(activity,
                        android.R.layout.simple_dropdown_item_1line,
                        ChipInfo.rpmh_levels.level_str());
                spinner.setAdapter(levelAdapter);
                spinner.setSelection(GpuVoltEditor.levelint2int(Integer.parseInt(raw_value)));

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.edit)
                        .setView(spinner)
                        .setMessage(R.string.editvolt_msg)
                        .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    final String newValue = String.valueOf(
                                            ChipInfo.rpmh_levels.levels()[spinner.getSelectedItemPosition()]);
                                    final String encodedLine = DtsHelper.encodeIntOrHexLine(raw_name, newValue);
                                    final String existingLine = bins.get(binIndex).levels.get(levelIndex).lines
                                            .get(lineIndex);
                                    if (Objects.equals(existingLine, encodedLine)) {
                                        return;
                                    }
                                    final String freqLabel = SettingsActivity.formatFrequency(
                                            getFrequencyFromLevel(bins.get(binIndex).levels.get(levelIndex)), activity);
                                    applyChange(activity.getString(R.string.history_update_voltage_level, freqLabel),
                                            new EditorChange() {
                                                @Override
                                                public void run() {
                                                    bins.get(binIndex).levels.get(levelIndex).lines.set(lineIndex,
                                                            encodedLine);
                                                }
                                            });
                                    generateALevel(activity, binIndex, levelIndex, page);
                                    Toast.makeText(activity, R.string.save_success,
                                            Toast.LENGTH_SHORT).show();
                                } catch (Exception exception) {
                                    DialogUtil.showError(activity, R.string.save_failed);
                                    exception.printStackTrace();
                                }
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .create().show();

            } catch (Exception e) {
                DialogUtil.showError(activity, R.string.error_occur);
            }
        } else if (raw_name.equals("qcom,gpu-freq")) {
            // Handle GPU frequency with unit converter
            showFrequencyEditDialog(activity, binIndex, levelIndex, page, lineIndex, raw_name, raw_value, paramTitle);
        } else {
            // Handle other parameters with text input
            EditText editText = new EditText(activity);
            editText.setInputType(
                    DtsHelper.shouldUseHex(raw_name) ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_CLASS_NUMBER);
            editText.setText(raw_value);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getResources().getString(R.string.edit) + " \"" + paramTitle + "\"")
                    .setView(editText)
                    .setMessage(KonaBessStr.help(raw_name, activity))
                    .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                final String newValue = editText.getText().toString();
                                final String encodedLine = DtsHelper.encodeIntOrHexLine(raw_name, newValue);
                                final String existingLine = bins.get(binIndex).levels.get(levelIndex).lines
                                        .get(lineIndex);
                                if (Objects.equals(existingLine, encodedLine)) {
                                    return;
                                }
                                final String freqLabel = SettingsActivity.formatFrequency(
                                        getFrequencyFromLevel(bins.get(binIndex).levels.get(levelIndex)), activity);
                                applyChange(activity.getString(R.string.history_edit_parameter, paramTitle, freqLabel),
                                        new EditorChange() {
                                            @Override
                                            public void run() {
                                                bins.get(binIndex).levels.get(levelIndex).lines.set(lineIndex,
                                                        encodedLine);
                                            }
                                        });
                                generateALevel(activity, binIndex, levelIndex, page);
                                Toast.makeText(activity, R.string.save_success,
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                DialogUtil.showError(activity, R.string.save_failed);
                            }
                        }

                    }).setNegativeButton(R.string.cancel, null).create().show();
        }
    }

    /**
     * Show frequency edit dialog with unit selector (Hz, MHz, GHz).
     * Allows users to input frequency in their preferred unit.
     */
    private static void showFrequencyEditDialog(Activity activity, int binIndex, int levelIndex,
            LinearLayout page, int lineIndex, String raw_name, String raw_value, String paramTitle) {
        // Create container layout
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, 0);

        // Create horizontal layout for input and unit
        LinearLayout inputRow = new LinearLayout(activity);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);

        // EditText for value
        EditText editText = new EditText(activity);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        editText.setLayoutParams(editParams);
        editText.setHint("Enter frequency");

        // Spinner for unit selection
        Spinner unitSpinner = new Spinner(activity);
        String[] units = { "Hz", "MHz", "GHz" };
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);

        // Get current frequency in Hz
        long currentHz = 0;
        try {
            currentHz = Long.parseLong(raw_value);
        } catch (NumberFormatException ignored) {
        }

        // Get user's preferred unit from settings
        android.content.SharedPreferences prefs = activity.getSharedPreferences(
                SettingsActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        int preferredUnit = prefs.getInt(SettingsActivity.KEY_FREQ_UNIT, SettingsActivity.FREQ_UNIT_MHZ);

        // Set initial value based on preferred unit
        final long finalCurrentHz = currentHz;
        switch (preferredUnit) {
            case SettingsActivity.FREQ_UNIT_HZ:
                editText.setText(String.valueOf(currentHz));
                unitSpinner.setSelection(0);
                break;
            case SettingsActivity.FREQ_UNIT_MHZ:
                editText.setText(String.valueOf(currentHz / 1000000));
                unitSpinner.setSelection(1);
                break;
            case SettingsActivity.FREQ_UNIT_GHZ:
                editText.setText(String.format(java.util.Locale.US, "%.3f", currentHz / 1000000000.0));
                unitSpinner.setSelection(2);
                break;
            default:
                editText.setText(String.valueOf(currentHz / 1000000));
                unitSpinner.setSelection(1);
        }

        // Add views to row
        inputRow.addView(editText);
        inputRow.addView(unitSpinner);

        // Preview text showing Hz equivalent
        TextView previewText = new TextView(activity);
        previewText.setPadding(0, padding / 2, 0, 0);
        previewText.setTextSize(12);
        previewText.setText("= " + finalCurrentHz + " Hz");

        // Update preview when input changes
        android.text.TextWatcher textWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateFrequencyPreview(editText, unitSpinner, previewText);
            }
        };
        editText.addTextChangedListener(textWatcher);

        // Track previous unit for conversion
        final int[] previousUnit = { preferredUnit == SettingsActivity.FREQ_UNIT_HZ ? 0
                : preferredUnit == SettingsActivity.FREQ_UNIT_GHZ ? 2 : 1 };

        // Update preview AND convert textbox value when unit changes
        unitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position,
                    long id) {
                int oldUnit = previousUnit[0];
                int newUnit = position;

                if (oldUnit != newUnit) {
                    // Convert current value to Hz first, then to new unit
                    try {
                        String currentText = editText.getText().toString().trim();
                        if (!currentText.isEmpty()) {
                            long hzValue = parseFrequencyToHz(currentText, oldUnit);
                            if (hzValue > 0) {
                                String newText;
                                switch (newUnit) {
                                    case 0: // Hz
                                        newText = String.valueOf(hzValue);
                                        break;
                                    case 1: // MHz
                                        newText = String.valueOf(hzValue / 1000000);
                                        break;
                                    case 2: // GHz
                                        newText = String.format(java.util.Locale.US, "%.3f", hzValue / 1000000000.0);
                                        break;
                                    default:
                                        newText = currentText;
                                }
                                editText.setText(newText);
                                editText.setSelection(newText.length());
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    previousUnit[0] = newUnit;
                }
                updateFrequencyPreview(editText, unitSpinner, previewText);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        container.addView(inputRow);
        container.addView(previewText);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.edit) + " \"" + paramTitle + "\"")
                .setView(container)
                .setMessage(KonaBessStr.help(raw_name, activity))
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    try {
                        long hzValue = parseFrequencyToHz(editText.getText().toString(),
                                unitSpinner.getSelectedItemPosition());
                        if (hzValue <= 0) {
                            DialogUtil.showError(activity, R.string.invalid_value);
                            return;
                        }
                        final String newValue = String.valueOf(hzValue);
                        final String encodedLine = DtsHelper.encodeIntOrHexLine(raw_name, newValue);
                        final String existingLine = bins.get(binIndex).levels.get(levelIndex).lines.get(lineIndex);
                        if (Objects.equals(existingLine, encodedLine)) {
                            return;
                        }
                        final String freqLabel = SettingsActivity.formatFrequency(hzValue, activity);
                        applyChange(activity.getString(R.string.history_edit_parameter, paramTitle, freqLabel),
                                () -> bins.get(binIndex).levels.get(levelIndex).lines.set(lineIndex, encodedLine));
                        generateALevel(activity, binIndex, levelIndex, page);
                        Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        DialogUtil.showError(activity, R.string.save_failed);
                        e.printStackTrace();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create().show();
    }

    /**
     * Update the preview text showing Hz equivalent.
     */
    private static void updateFrequencyPreview(EditText editText, Spinner unitSpinner, TextView previewText) {
        try {
            long hz = parseFrequencyToHz(editText.getText().toString(), unitSpinner.getSelectedItemPosition());
            if (hz > 0) {
                previewText.setText("= " + String.format(java.util.Locale.US, "%,d", hz) + " Hz");
            } else {
                previewText.setText("= ? Hz");
            }
        } catch (Exception e) {
            previewText.setText("= ? Hz");
        }
    }

    /**
     * Parse frequency input to Hz based on selected unit.
     * 
     * @param value     Input value as string
     * @param unitIndex 0=Hz, 1=MHz, 2=GHz
     * @return Frequency in Hz, or -1 if invalid
     */
    private static long parseFrequencyToHz(String value, int unitIndex) {
        try {
            double inputValue = Double.parseDouble(value.trim());
            switch (unitIndex) {
                case 0: // Hz
                    return (long) inputValue;
                case 1: // MHz
                    return (long) (inputValue * 1000000);
                case 2: // GHz
                    return (long) (inputValue * 1000000000);
                default:
                    return (long) inputValue;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Level level_clone(Level from) {
        Level next = new Level();
        next.lines = new ArrayList<>(from.lines);
        return next;
    }

    private static void offset_initial_level(int bin_id, int offset) throws Exception {
        if (bins == null || bins.isEmpty())
            return;
        // Safety: ensure bin_id is valid. For single bin chips, we use bin 0.
        int targetBinId = (bin_id >= 0 && bin_id < bins.size()) ? bin_id : 0;
        Bin bin = bins.get(targetBinId);

        for (int i = 0; i < bin.header.size(); i++) {
            String line = bin.header.get(i);
            if (line.contains("qcom,initial-pwrlevel")) {
                bin.header.set(i,
                        DtsHelper.encodeIntOrHexLine(
                                DtsHelper.decode_int_line(line).name,
                                DtsHelper.decode_int_line(line).value + offset + ""));
                break;
            }
        }
    }

    private static void offset_ca_target_level(int bin_id, int offset) throws Exception {
        for (int i = 0; i < bins.get(bin_id).header.size(); i++) {
            String line = bins.get(bin_id).header.get(i);
            if (line.contains("qcom,ca-target-pwrlevel")) {
                bins.get(bin_id).header.set(i,
                        DtsHelper.encodeIntOrHexLine(
                                DtsHelper.decode_int_line(line).name,
                                DtsHelper.decode_int_line(line).value + offset + ""));
                break;
            }
        }
    }

    private static void patch_throttle_level() throws Exception {
        if (bins == null)
            return;

        for (Bin bin : bins) {
            for (int i = 0; i < bin.header.size(); i++) {
                String line = bin.header.get(i);
                if (line.contains("qcom,throttle-pwrlevel")) {
                    bin.header.set(i,
                            DtsHelper.encodeIntOrHexLine(
                                    DtsHelper.decode_int_line(line).name, "0"));
                    break;
                }
            }
        }
    }

    public static boolean canAddNewLevel(int binID, Context context) throws Exception {
        // Limit removed as per user request
        return true;
    }

    private static void generateLevels(Activity activity, int id, LinearLayout page) throws Exception {
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.updateGpuToolbarTitle(activity.getString(R.string.edit_freq_table)
                + " - " + KonaBessStr.convert_bins(bins.get(id).id, activity));

        // Enable callback for back navigation (Levels → Bins)
        enableBackCallback();

        currentActivity = activity;
        currentPage = page;
        currentBinIndex = id;
        currentLevelIndex = null;
        updateUndoRedoButtons();
        updateHistoryButtonLabel();
        updateSaveButtonAppearance();

        ArrayList<GpuFreqAdapter.FreqItem> items = new ArrayList<>();

        // Back button (header)
        items.add(new GpuFreqAdapter.FreqItem(
                activity.getResources().getString(R.string.back),
                "",
                GpuFreqAdapter.FreqItem.ActionType.BACK));

        // Add new at top button (header)
        items.add(new GpuFreqAdapter.FreqItem(
                activity.getResources().getString(R.string.add_freq_top),
                activity.getResources().getString(R.string.add_freq_top_desc),
                GpuFreqAdapter.FreqItem.ActionType.ADD_TOP));

        // Curve Editor button (header)
        items.add(new GpuFreqAdapter.FreqItem(
                activity.getResources().getString(R.string.gpu_curve_editor_title),
                "Edit frequency curve for this bin",
                GpuFreqAdapter.FreqItem.ActionType.CURVE_EDITOR));

        // Add all frequency levels
        for (int i = 0; i < bins.get(id).levels.size(); i++) {
            Level lvl = bins.get(id).levels.get(i);
            long freq = getFrequencyFromLevel(lvl);
            if (freq == 0)
                continue;

            GpuFreqAdapter.FreqItem item = new GpuFreqAdapter.FreqItem(
                    SettingsActivity.formatFrequency(freq, activity),
                    "");
            item.originalPosition = i;
            item.frequencyHz = freq;

            // Extract spec details from DTS lines
            try {
                for (String line : lvl.lines) {
                    String paramName = DtsHelper.decode_hex_line(line).name;

                    if ("qcom,bus-max".equals(paramName)) {
                        long busMax = DtsHelper.decode_int_line(line).value;
                        item.busMax = String.valueOf(busMax);
                    } else if ("qcom,bus-min".equals(paramName)) {
                        long busMin = DtsHelper.decode_int_line(line).value;
                        item.busMin = String.valueOf(busMin);
                    } else if ("qcom,bus-freq".equals(paramName)) {
                        long busFreq = DtsHelper.decode_int_line(line).value;
                        // Bus-freq is a level/index, not frequency in MHz
                        item.busFreq = String.valueOf(busFreq);
                    } else if ("qcom,level".equals(paramName) || "qcom,cx-level".equals(paramName)) {
                        long voltLevel = DtsHelper.decode_int_line(line).value;
                        item.voltageLevel = GpuVoltEditor.levelint2str(voltLevel);
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors for individual specs
            }

            items.add(item);
        }

        // Add new at bottom button (footer)
        items.add(new GpuFreqAdapter.FreqItem(
                activity.getResources().getString(R.string.add_freq_bottom),
                activity.getResources().getString(R.string.add_freq_bottom_desc),
                GpuFreqAdapter.FreqItem.ActionType.ADD_BOTTOM));

        final RecyclerView recyclerView;
        final GpuFreqAdapter adapter;

        if (page.getChildCount() > 0 && page.getChildAt(0) instanceof RecyclerView
                && ((RecyclerView) page.getChildAt(0)).getAdapter() instanceof GpuFreqAdapter) {
            recyclerView = (RecyclerView) page.getChildAt(0);
            adapter = (GpuFreqAdapter) recyclerView.getAdapter();
            recyclerView.clearOnScrollListeners(); // Remove old listener capturing old ID
            adapter.updateData(items);
        } else {
            recyclerView = new RecyclerView(activity);
            recyclerView.setLayoutManager(new LinearLayoutManager(activity));

            // Set padding to prevent content from being hidden behind bottom toolbar
            float density = activity.getResources().getDisplayMetrics().density;
            int bottomPadding = (int) (density * 80); // Toolbar height + extra space
            recyclerView.setClipToPadding(false);
            recyclerView.setPadding(0, 0, 0, bottomPadding);

            adapter = new GpuFreqAdapter(items, activity);

            // Setup drag and drop
            ItemTouchHelperCallback callback = new ItemTouchHelperCallback(adapter);
            ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(recyclerView);

            adapter.setOnStartDragListener(viewHolder -> {
                touchHelper.startDrag(viewHolder);
            });

            recyclerView.setAdapter(adapter);

            page.removeAllViews();
            page.addView(recyclerView);
        }

        // Item click listener
        adapter.setOnItemClickListener(position -> {
            GpuFreqAdapter.FreqItem item = items.get(position);

            switch (item.actionType) {
                case ADD_BOTTOM:
                    try {
                        if (!canAddNewLevel(id, activity))
                            return;
                        final int offset = ChipInfo.which.minLevelOffset;
                        final int insertIndex = bins.get(id).levels.size() - offset;
                        final Level template = bins.get(id).levels.get(insertIndex);
                        final String freqLabel = SettingsActivity.formatFrequency(
                                getFrequencyFromLevel(template), activity);
                        applyChange(activity.getString(R.string.history_add_frequency, freqLabel), () -> {
                            bins.get(id).levels.add(insertIndex, level_clone(template));
                            offset_initial_level(id, 1);
                            if (ChipInfo.which == ChipInfo.type.lito_v1
                                    || ChipInfo.which == ChipInfo.type.lito_v2
                                    || ChipInfo.which == ChipInfo.type.lagoon) {
                                offset_ca_target_level(id, 1);
                            }
                        });
                        generateLevels(activity, id, page);
                    } catch (Exception e) {
                        DialogUtil.showError(activity, R.string.error_occur);
                    }
                    return;
                case CURVE_EDITOR:
                    // Disable callback so fragment back stack handles navigation
                    disableBackCallback();

                    com.ireddragonicy.konabessnext.ui.fragments.GpuCurveEditorFragment fragment = new com.ireddragonicy.konabessnext.ui.fragments.GpuCurveEditorFragment();
                    android.os.Bundle args = new android.os.Bundle();
                    args.putInt("binId", id);
                    fragment.setArguments(args);

                    // Add back stack listener to refresh list when returning from Curve Editor
                    final int currentBackStackCount = mainActivity.getSupportFragmentManager().getBackStackEntryCount();
                    mainActivity.getSupportFragmentManager().addOnBackStackChangedListener(
                            new androidx.fragment.app.FragmentManager.OnBackStackChangedListener() {
                                @Override
                                public void onBackStackChanged() {
                                    int newCount = mainActivity.getSupportFragmentManager().getBackStackEntryCount();
                                    if (newCount < currentBackStackCount + 1) {
                                        // Curve Editor was popped, refresh the frequency list
                                        mainActivity.getSupportFragmentManager().removeOnBackStackChangedListener(this);
                                        try {
                                            generateLevels(activity, id, page);
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                            });

                    mainActivity.getSupportFragmentManager()
                            .beginTransaction()
                            .replace(android.R.id.content, fragment)
                            .addToBackStack("curve_editor")
                            .commit();
                    return;
                case BACK:
                    try {
                        generateBins(activity, page);
                        mainActivity.updateGpuToolbarTitle(activity.getString(R.string.edit_freq_table));
                    } catch (Exception ignored) {
                    }
                    return;
                case ADD_TOP:
                    try {
                        if (!canAddNewLevel(id, activity))
                            return;
                        final Level template = bins.get(id).levels.get(0);
                        final String freqLabel = SettingsActivity.formatFrequency(
                                getFrequencyFromLevel(template), activity);
                        applyChange(activity.getString(R.string.history_add_frequency, freqLabel), () -> {
                            bins.get(id).levels.add(0, level_clone(template));
                            offset_initial_level(id, 1);
                            if (ChipInfo.which == ChipInfo.type.lito_v1
                                    || ChipInfo.which == ChipInfo.type.lito_v2
                                    || ChipInfo.which == ChipInfo.type.lagoon) {
                                offset_ca_target_level(id, 1);
                            }
                        });
                        generateLevels(activity, id, page);
                    } catch (Exception e) {
                        DialogUtil.showError(activity, R.string.error_occur);
                    }
                    return;
                case NONE:
                default:
                    if (item.isLevelItem()) {
                        int levelIndex = 0;
                        for (int i = 0; i < position; i++) {
                            if (items.get(i).isLevelItem()) {
                                levelIndex++;
                            }
                        }
                        try {
                            generateALevel(activity, id, levelIndex, page);
                        } catch (Exception e) {
                            DialogUtil.showError(activity, R.string.error_occur);
                        }
                    }
                    break;
            }
        });

        // Delete button click listener
        adapter.setOnDeleteClickListener(position -> {
            if (bins.get(id).levels.size() == 1) {
                Toast.makeText(activity, R.string.unable_add_more, Toast.LENGTH_SHORT).show();
                return;
            }

            final int levelPosition = position - 3; // Adjust for header items (BACK, ADD_TOP, CURVE_EDITOR)
            try {
                final long freqValue = getFrequencyFromLevel(bins.get(id).levels.get(levelPosition));
                final String freqLabel = SettingsActivity.formatFrequency(freqValue, activity);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.remove)
                        .setMessage(activity.getString(R.string.remove_frequency_message, freqLabel))
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            try {
                                applyChange(activity.getString(R.string.history_remove_frequency, freqLabel), () -> {
                                    bins.get(id).levels.remove(levelPosition);
                                    offset_initial_level(id, -1);
                                    if (ChipInfo.which == ChipInfo.type.lito_v1
                                            || ChipInfo.which == ChipInfo.type.lito_v2
                                            || ChipInfo.which == ChipInfo.type.lagoon) {
                                        offset_ca_target_level(id, -1);
                                    }
                                });
                                generateLevels(activity, id, page);
                            } catch (Exception e) {
                                DialogUtil.showError(activity, R.string.error_occur);
                            }
                        })
                        .setNegativeButton(R.string.no, null)
                        .create().show();
            } catch (Exception e) {
                DialogUtil.showError(activity, R.string.error_occur);
            }
        });

        // Apply any reordering changes when drag completes
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Update the bins order based on adapter items
                    try {
                        updateBinsFromAdapter(activity, id, adapter.getItems());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private static void updateBinsFromAdapter(Activity activity, int binId, List<GpuFreqAdapter.FreqItem> items)
            throws Exception {
        ArrayList<Level> currentLevels = bins.get(binId).levels;
        ArrayList<Level> newLevels = new ArrayList<>();
        boolean[] used = new boolean[currentLevels.size()];

        for (GpuFreqAdapter.FreqItem item : items) {
            if (!item.isHeader && !item.isFooter && item.hasFrequencyValue()) {
                for (int index = 0; index < currentLevels.size(); index++) {
                    if (used[index])
                        continue;
                    Level levelRef = currentLevels.get(index);
                    long freq = getFrequencyFromLevel(levelRef);
                    if (freq == item.frequencyHz) {
                        newLevels.add(levelRef);
                        used[index] = true;
                        break;
                    }
                }
            }
        }

        if (newLevels.size() == currentLevels.size() && !newLevels.equals(currentLevels)) {
            final ArrayList<Level> replacement = newLevels;
            applyChange(activity.getString(R.string.history_reorder_frequency),
                    () -> bins.get(binId).levels = replacement);
        }
    }

    private static long getFrequencyFromLevel(Level lvl) throws Exception {
        for (String line : lvl.lines) {
            if (line.contains("qcom,gpu-freq")) {
                return DtsHelper.decode_int_line(line).value;
            }
        }
        throw new Exception();
    }

    private static View createChipsetSelectorCard(Activity activity, LinearLayout page) {
        float density = activity.getResources().getDisplayMetrics().density;
        int padding = (int) (density * 16);

        // Main card container
        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(
                activity);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(padding, padding, padding, (int) (density * 8));
        card.setLayoutParams(cardParams);
        card.setCardElevation(density * 2);
        card.setRadius(density * 12);

        // Inner layout
        LinearLayout innerLayout = new LinearLayout(activity);
        innerLayout.setOrientation(LinearLayout.VERTICAL);
        innerLayout.setPadding(padding, padding, padding, padding);

        // Title
        TextView titleView = new TextView(activity);
        titleView.setText("Target Chipset");
        titleView.setTextSize(12);
        titleView.setAlpha(0.6f);
        titleView.setPadding(0, 0, 0, (int) (density * 8));
        innerLayout.addView(titleView);

        // Current chipset display with click to change
        LinearLayout chipsetRow = new LinearLayout(activity);
        chipsetRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsetRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chipsetRow.setClickable(true);
        chipsetRow.setFocusable(true);

        // Set ripple effect
        android.content.res.TypedArray typedArray = activity.getTheme().obtainStyledAttributes(
                new int[] { android.R.attr.selectableItemBackground });
        int selectableItemBackground = typedArray.getResourceId(0, 0);
        typedArray.recycle();
        chipsetRow.setBackgroundResource(selectableItemBackground);
        chipsetRow.setPadding((int) (density * 12), (int) (density * 12),
                (int) (density * 12), (int) (density * 12));

        // Chipset icon (Material Design)
        ImageView chipIcon = new ImageView(activity);
        chipIcon.setImageResource(R.drawable.ic_developer_board);
        int iconSize = (int) (density * 24);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd((int) (density * 12));
        chipIcon.setLayoutParams(iconParams);
        chipIcon.setColorFilter(MaterialColors.getColor(chipIcon,
                com.google.android.material.R.attr.colorOnSurface));
        chipsetRow.addView(chipIcon);

        // Chipset name
        TextView chipsetName = new TextView(activity);
        KonaBessCore.Dtb currentDtb = KonaBessCore.getCurrentDtb();
        if (currentDtb != null) {
            chipsetName.setText(currentDtb.id + " " +
                    currentDtb.type.getDescription(activity));
        } else {
            chipsetName.setText("Unknown");
        }
        chipsetName.setTextSize(16);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        chipsetName.setLayoutParams(nameParams);
        chipsetRow.addView(chipsetName);

        // Settings/Change icon (Material Design)
        ImageView changeIcon = new ImageView(activity);
        changeIcon.setImageResource(R.drawable.ic_tune);
        LinearLayout.LayoutParams changeIconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        changeIcon.setLayoutParams(changeIconParams);
        changeIcon.setAlpha(0.7f);
        changeIcon.setColorFilter(MaterialColors.getColor(changeIcon,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        chipsetRow.addView(changeIcon);

        // Click listener to show chipset selector dialog
        chipsetRow.setOnClickListener(v -> showChipsetSelectorDialog(activity, page, chipsetName));

        innerLayout.addView(chipsetRow);
        card.addView(innerLayout);

        return card;
    }

    private static void showChipsetSelectorDialog(Activity activity, LinearLayout page,
            TextView chipsetNameView) {
        if (KonaBessCore.dtbs == null || KonaBessCore.dtbs.isEmpty()) {
            Toast.makeText(activity, "No chipsets available", Toast.LENGTH_SHORT).show();
            return;
        }

        KonaBessCore.Dtb currentDtb = KonaBessCore.getCurrentDtb();
        int currentDtbIndex = KonaBessCore.getDtbIndex();

        // Inflate custom modern dialog layout
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_chipset_selector, null);

        // Setup RecyclerView
        RecyclerView recyclerView = dialogView.findViewById(R.id.chipset_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setHasFixedSize(true);

        // Create dialog
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .create();

        // Setup adapter with click listener
        ChipsetSelectorAdapter adapter = new ChipsetSelectorAdapter(
                KonaBessCore.dtbs,
                activity,
                currentDtbIndex,
                currentDtb != null ? currentDtb.id : null,
                selectedDtb -> {
                    // Show confirmation if switching chipset
                    if (currentDtb != null && selectedDtb.id != currentDtb.id) {
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(activity.getString(R.string.switch_chipset_title))
                                .setMessage(activity.getString(R.string.switch_chipset_msg))
                                .setPositiveButton(activity.getString(R.string.yes), (d, w) -> {
                                    dialog.dismiss();
                                    switchChipset(activity, page, selectedDtb, chipsetNameView);
                                })
                                .setNegativeButton(activity.getString(R.string.no), null)
                                .create().show();
                    } else {
                        // Same chipset, just close dialog
                        dialog.dismiss();
                    }
                });

        recyclerView.setAdapter(adapter);

        // Show dialog with rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }

        dialog.show();
    }

    private static void switchChipset(Activity activity, LinearLayout page,
            KonaBessCore.Dtb newDtb, TextView chipsetNameView) {
        AlertDialog waiting = DialogUtil.getWaitDialog(activity, R.string.getting_freq_table);
        waiting.show();

        new Thread(() -> {
            try {
                KonaBessCore.Dtb previous = KonaBessCore.getCurrentDtb();
                if (previous != null && previous.id != newDtb.id) {
                    saveCurrentSession();
                }

                // Switch to new chipset
                KonaBessCore.chooseTarget(newDtb, activity);

                boolean restored = restoreSession(newDtb.id);
                Integer targetBinIndex = restored ? currentBinIndex : null;
                Integer targetLevelIndex = restored ? currentLevelIndex : null;
                if (!restored) {
                    // Reload GPU table for new chipset
                    init();
                    decode();
                    patch_throttle_level();
                    resetEditorState();
                    saveCurrentSession();
                }

                final boolean restoredSession = restored;
                activity.runOnUiThread(() -> {
                    waiting.dismiss();

                    // Update chipset name in card
                    chipsetNameView.setText(newDtb.id + " " +
                            newDtb.type.getDescription(activity));

                    // Regenerate bins view
                    try {
                        refreshDirtyStateFromSignature();
                        generateBins(activity, page);
                        if (restoredSession && targetBinIndex != null) {
                            try {
                                if (targetLevelIndex != null) {
                                    generateALevel(activity, targetBinIndex, targetLevelIndex, page);
                                } else {
                                    generateLevels(activity, targetBinIndex, page);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        Toast.makeText(activity, "Switched to chipset " + newDtb.id,
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        DialogUtil.showError(activity, R.string.error_occur);
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    waiting.dismiss();
                    DialogUtil.showError(activity, R.string.getting_freq_table_failed);
                });
            }
        }).start();
    }

    public static void restoreBackListener(Activity activity) {
        if (!(activity instanceof MainActivity))
            return;

        if (currentPage == null) {
            // Editor not active
            return;
        }

        // Simply enable/disable callback based on current navigation depth
        if (currentLevelIndex != null && currentBinIndex != null) {
            // In a Frequency/Level Detail level
            enableBackCallback();
        } else if (currentBinIndex != null) {
            // In a Bin level (Frequency Table)
            enableBackCallback();
        } else {
            // Top level (Bin List) - disable so system handles back
            disableBackCallback();
        }
    }

    private static void generateBins(Activity activity, LinearLayout page) throws Exception {
        // At root level, disable callback so system handles back (exit/home)
        disableBackCallback();

        currentActivity = activity;
        currentPage = page;
        currentBinIndex = null;
        currentLevelIndex = null;
        updateUndoRedoButtons();
        updateHistoryButtonLabel();
        updateSaveButtonAppearance();

        // Build the bin items list
        ArrayList<GpuBinAdapter.BinItem> items = new ArrayList<>();
        for (int i = 0; i < bins.size(); i++) {
            items.add(new GpuBinAdapter.BinItem(
                    KonaBessStr.convert_bins(bins.get(i).id, activity),
                    ""));
        }

        float density = activity.getResources().getDisplayMetrics().density;

        // Check if we can reuse existing view hierarchy
        // The structure is: page > LinearLayout (mainLayout) > [ChipsetSelector,
        // RecyclerView]
        // or page > LinearLayout (mainLayout) > RecyclerView (when single chipset)
        RecyclerView existingRecyclerView = findBinRecyclerView(page);

        if (existingRecyclerView != null
                && existingRecyclerView.getAdapter() instanceof GpuBinAdapter) {
            // Hot path: Reuse existing views
            GpuBinAdapter adapter = (GpuBinAdapter) existingRecyclerView.getAdapter();
            adapter.updateData(items);
        } else {
            // Cold path: Create new view hierarchy
            LinearLayout mainLayout = new LinearLayout(activity);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));

            // Add chipset selector card if multiple chipsets are available
            if (KonaBessCore.dtbs != null && KonaBessCore.dtbs.size() > 1) {
                mainLayout.addView(createChipsetSelectorCard(activity, page));
            }

            RecyclerView recyclerView = new RecyclerView(activity);
            recyclerView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            recyclerView.setLayoutManager(new LinearLayoutManager(activity));
            recyclerView.setClipToPadding(false);
            recyclerView.setPadding(0, (int) (density * 8), 0, (int) (density * 16));

            GpuBinAdapter adapter = new GpuBinAdapter(items);
            adapter.setOnItemClickListener(new GpuBinAdapter.OnItemClickListener() {
                @Override
                public void onBinClick(int position) {
                    try {
                        generateLevels(activity, position, page);
                    } catch (Exception e) {
                        DialogUtil.showError(activity, R.string.error_occur);
                    }
                }
            });

            recyclerView.setAdapter(adapter);
            mainLayout.addView(recyclerView);

            page.removeAllViews();
            page.addView(mainLayout, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
        }
    }

    /**
     * Helper method to find the RecyclerView containing bin items in the view
     * hierarchy.
     * Handles the case where the structure is: page > LinearLayout > RecyclerView
     */
    private static RecyclerView findBinRecyclerView(LinearLayout page) {
        if (page.getChildCount() == 0) {
            return null;
        }

        View firstChild = page.getChildAt(0);

        // Check if first child is a LinearLayout (mainLayout wrapper)
        if (firstChild instanceof LinearLayout) {
            LinearLayout mainLayout = (LinearLayout) firstChild;
            // Look for RecyclerView in the mainLayout
            for (int i = 0; i < mainLayout.getChildCount(); i++) {
                View child = mainLayout.getChildAt(i);
                if (child instanceof RecyclerView) {
                    RecyclerView rv = (RecyclerView) child;
                    // Verify it's a GpuBinAdapter (not GpuFreqAdapter)
                    if (rv.getAdapter() instanceof GpuBinAdapter) {
                        return rv;
                    }
                }
            }
        }

        return null;
    }

    public static View generateToolBar(Activity activity, LinearLayout showedView) {
        currentActivity = activity;
        com.ireddragonicy.konabessnext.ui.widget.GpuActionToolbar toolbar = new com.ireddragonicy.konabessnext.ui.widget.GpuActionToolbar(
                activity);
        toolbar.setParentViewForVolt(showedView);
        toolbar.build(activity);
        return toolbar;
    }

    public static MaterialButton createCompactChip(Activity activity, int textRes, int iconRes) {
        MaterialButton chip = new MaterialButton(activity);
        chip.setAllCaps(false);
        chip.setText(textRes);
        chip.setIconResource(iconRes);
        chip.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);

        float density = activity.getResources().getDisplayMetrics().density;

        // Compact sizing for modern chip design
        int iconSize = (int) (density * 18);
        int iconPadding = (int) (density * 4);
        int horizontalPadding = (int) (density * 12);
        int verticalPadding = (int) (density * 8);

        chip.setIconSize(iconSize);
        chip.setIconPadding(iconPadding);
        chip.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        // Smaller text size for compact design
        chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);

        // Material You colors
        int backgroundColor = MaterialColors.getColor(chip,
                com.google.android.material.R.attr.colorSecondaryContainer);
        int foregroundColor = MaterialColors.getColor(chip,
                com.google.android.material.R.attr.colorOnSecondaryContainer);
        int rippleColor = MaterialColors.getColor(chip,
                com.google.android.material.R.attr.colorSecondary);

        chip.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        chip.setTextColor(foregroundColor);
        chip.setIconTint(ColorStateList.valueOf(foregroundColor));
        chip.setRippleColor(ColorStateList.valueOf(rippleColor));
        chip.setStrokeWidth(0);

        // Rounded corners for modern chip look
        chip.setCornerRadius((int) (density * 20));

        return chip;
    }

    // Keep old method for backward compatibility
    private static MaterialButton createActionButton(Activity activity, int textRes, int iconRes) {
        return createCompactChip(activity, textRes, iconRes);
    }

    public static class gpuTableLogic extends Thread {
        Activity activity;
        AlertDialog waiting;
        LinearLayout showedView;
        LinearLayout page;

        public gpuTableLogic(Activity activity, LinearLayout showedView) {
            this.activity = activity;
            this.showedView = showedView;
        }

        public void run() {
            activity.runOnUiThread(() -> {
                waiting = DialogUtil.getWaitDialog(activity, R.string.getting_freq_table);
                waiting.show();
            });

            try {
                init();
                decode();
                patch_throttle_level();
                resetEditorState();
            } catch (Exception e) {
                activity.runOnUiThread(() -> DialogUtil.showError(activity,
                        R.string.getting_freq_table_failed));
            }

            activity.runOnUiThread(() -> {
                currentActivity = activity;
                waiting.dismiss();
                showedView.removeAllViews();
                showedView.addView(generateToolBar(activity, showedView));
                page = new LinearLayout(activity);
                page.setOrientation(LinearLayout.VERTICAL);
                currentPage = page;
                currentBinIndex = null;
                currentLevelIndex = null;
                try {
                    generateBins(activity, page);
                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.getting_freq_table_failed);
                }
                updateUndoRedoButtons();
                updateHistoryButtonLabel();
                updateSaveButtonAppearance();
                showedView.addView(page);
            });

        }
    }
}
