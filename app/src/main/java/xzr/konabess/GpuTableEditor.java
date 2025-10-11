package xzr.konabess;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import xzr.konabess.R;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import xzr.konabess.adapters.GpuBinAdapter;
import xzr.konabess.adapters.GpuFreqAdapter;
import xzr.konabess.adapters.GpuParamDetailAdapter;
import xzr.konabess.adapters.ParamAdapter;
import xzr.konabess.utils.DialogUtil;
import xzr.konabess.utils.DtsHelper;
import xzr.konabess.utils.ItemTouchHelperCallback;

public class GpuTableEditor {
    private static int bin_position;
    private static ArrayList<bin> bins;

    private static class bin {
        int id;
        ArrayList<String> header;
        ArrayList<level> levels;
    }

    private static class level {
        ArrayList<String> lines;
    }

    private static ArrayList<String> lines_in_dts;

    private static final int MAX_HISTORY_SIZE = 50;
    private static final Deque<EditorState> undoStack = new ArrayDeque<>();
    private static final Deque<EditorState> redoStack = new ArrayDeque<>();
    private static final ArrayList<String> changeHistory = new ArrayList<>();

    private static Activity currentActivity;
    private static LinearLayout currentPage;
    private static Integer currentBinIndex = null;
    private static Integer currentLevelIndex = null;

    private static MaterialButton saveButtonRef;
    private static MaterialButton undoButtonRef;
    private static MaterialButton redoButtonRef;
    private static MaterialButton historyButtonRef;

    private static boolean isDirty = false;
    private static String lastSavedSignature;

    private static class EditorState {
        ArrayList<String> linesInDts;
        ArrayList<bin> binsSnapshot;
        int binPosition;
    }

    public static void init() throws IOException {
        lines_in_dts = new ArrayList<>();
        bins = new ArrayList<>();
        bin_position = -1;
        BufferedReader bufferedReader =
                new BufferedReader(new FileReader(new File(KonaBessCore.dts_path)));
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            lines_in_dts.add(s);
        }
    }

    public static void decode() throws Exception {
        int i = -1;
        String this_line;
        int start = -1;
        int end;
        int bracket = 0;
        while (++i < lines_in_dts.size()) {
            this_line = lines_in_dts.get(i).trim();

            if ((ChipInfo.which == ChipInfo.type.kona_singleBin
                    || ChipInfo.which == ChipInfo.type.msmnile_singleBin
                    || ChipInfo.which == ChipInfo.type.lahaina_singleBin
                    || ChipInfo.which == ChipInfo.type.waipio_singleBin
                    || ChipInfo.which == ChipInfo.type.cape_singleBin
                    || ChipInfo.which == ChipInfo.type.ukee_singleBin
                    || ChipInfo.which == ChipInfo.type.cliffs_singleBin
                    || ChipInfo.which == ChipInfo.type.cliffs_7_singleBin
                    || ChipInfo.which == ChipInfo.type.kalama_sg_singleBin)
                    && this_line.equals("qcom,gpu-pwrlevels {")) {
                start = i;
                if (bin_position < 0)
                    bin_position = i;
                bracket++;
                continue;
            }
            if (ChipInfo.which == ChipInfo.type.tuna
                    && this_line.contains("qcom,gpu-pwrlevels-")
                    && !this_line.contains("compatible = ")
                    && !this_line.contains("qcom,gpu-pwrlevel-bins")) {
                start = i;
                if (bin_position < 0)
                    bin_position = i;
                if (bracket != 0)
                    throw new Exception();
                bracket++;
                continue;
            }
            if ((ChipInfo.which == ChipInfo.type.kona
                    || ChipInfo.which == ChipInfo.type.msmnile
                    || ChipInfo.which == ChipInfo.type.lahaina
                    || ChipInfo.which == ChipInfo.type.lito_v1 || ChipInfo.which == ChipInfo.type.lito_v2
                    || ChipInfo.which == ChipInfo.type.lagoon
                    || ChipInfo.which == ChipInfo.type.shima
                    || ChipInfo.which == ChipInfo.type.yupik
                    || ChipInfo.which == ChipInfo.type.kalama
                    || ChipInfo.which == ChipInfo.type.diwali
                    || ChipInfo.which == ChipInfo.type.pineapple
                    || ChipInfo.which == ChipInfo.type.sun
                    || ChipInfo.which == ChipInfo.type.canoe)
                    && this_line.contains("qcom,gpu-pwrlevels-")
                    && !this_line.contains("compatible = ")) {
                start = i;
                if (bin_position < 0)
                    bin_position = i;
                if (bracket != 0)
                    throw new Exception();
                bracket++;
                continue;
            }

            if (this_line.contains("{") && start >= 0)
                bracket++;
            if (this_line.contains("}") && start >= 0)
                bracket--;

            if (bracket == 0 && start >= 0
                    && (ChipInfo.which == ChipInfo.type.kona
                    || ChipInfo.which == ChipInfo.type.msmnile
                    || ChipInfo.which == ChipInfo.type.lahaina
                    || ChipInfo.which == ChipInfo.type.lito_v1 || ChipInfo.which == ChipInfo.type.lito_v2
                    || ChipInfo.which == ChipInfo.type.lagoon
                    || ChipInfo.which == ChipInfo.type.shima
                    || ChipInfo.which == ChipInfo.type.yupik
                    || ChipInfo.which == ChipInfo.type.kalama
                    || ChipInfo.which == ChipInfo.type.diwali
                    || ChipInfo.which == ChipInfo.type.pineapple
                    || ChipInfo.which == ChipInfo.type.sun
                    || ChipInfo.which == ChipInfo.type.canoe
                    || ChipInfo.which == ChipInfo.type.tuna)) {
                end = i;
                if (end >= start) {
                    try {
                        decode_bin(lines_in_dts.subList(start, end + 1));
                        int removedLines = end - start + 1;
                        lines_in_dts.subList(start, end + 1).clear();
                        i = i - removedLines; // Adjust index after removing lines
                    } catch (Exception e) {
                        throw e;
                    }
                } else {
                    throw new Exception();
                }
                start = -1;
                continue;
            }

            if (bracket == 0 && start >= 0 && (ChipInfo.which == ChipInfo.type.kona_singleBin
                    || ChipInfo.which == ChipInfo.type.msmnile_singleBin
                    || ChipInfo.which == ChipInfo.type.lahaina_singleBin
                    || ChipInfo.which == ChipInfo.type.waipio_singleBin
                    || ChipInfo.which == ChipInfo.type.cape_singleBin
                    || ChipInfo.which == ChipInfo.type.ukee_singleBin
                    || ChipInfo.which == ChipInfo.type.cliffs_singleBin
                    || ChipInfo.which == ChipInfo.type.cliffs_7_singleBin
                    || ChipInfo.which == ChipInfo.type.kalama_sg_singleBin)) {
                end = i;
                if (end >= start) {
                    decode_bin(lines_in_dts.subList(start, end + 1));
                    lines_in_dts.subList(start, end + 1).clear();
                } else {
                    throw new Exception();
                }
                break;
            }
        }
    }

    private static int getBinID(String line, int prev_id) {
        line = line.trim();
        line = line.replace(" {", "")
                .replace("-", "");
        try {
            for (int i = line.length() - 1; i >= 0; i--) {
                prev_id = Integer.parseInt(line.substring(i));
            }
        } catch (Exception ignored) {
        }
        return prev_id;
    }

    private static void decode_bin(List<String> lines) throws Exception {
        bin bin = new bin();
        bin.header = new ArrayList<>();
        bin.levels = new ArrayList<>();
        bin.id = bins.size();
        int i = 0;
        int bracket = 0;
        int start = 0;
        int end;
        bin.id = getBinID(lines.get(0), bin.id);
        while (++i < lines.size() && bracket >= 0) {
            String line = lines.get(i);

            line = line.trim();
            if (line.equals(""))
                continue;

            if (line.contains("{")) {
                if (bracket != 0)
                    throw new Exception();
                start = i;
                bracket++;
                continue;
            }

            if (line.contains("}")) {
                if (--bracket < 0)
                    continue;
                end = i;
                if (end >= start)
                    bin.levels.add(decode_level(lines.subList(start, end + 1)));
                continue;
            }

            if (bracket == 0) {
                bin.header.add(line);
            }
        }
        bins.add(bin);
    }

    private static level decode_level(List<String> lines) {
        level level = new level();
        level.lines = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.contains("{") || line.contains("}"))
                continue;
            if (line.contains("reg"))
                continue;
            level.lines.add(line);
        }

        return level;
    }

    public static List<String> genTable() {
        ArrayList<String> lines = new ArrayList<>();
        if (ChipInfo.which == ChipInfo.type.kona
                || ChipInfo.which == ChipInfo.type.msmnile
                || ChipInfo.which == ChipInfo.type.lahaina
                || ChipInfo.which == ChipInfo.type.lito_v1 || ChipInfo.which == ChipInfo.type.lito_v2
                || ChipInfo.which == ChipInfo.type.lagoon
                || ChipInfo.which == ChipInfo.type.shima
                || ChipInfo.which == ChipInfo.type.yupik
                || ChipInfo.which == ChipInfo.type.kalama
                || ChipInfo.which == ChipInfo.type.diwali
                || ChipInfo.which == ChipInfo.type.pineapple
                || ChipInfo.which == ChipInfo.type.sun
                || ChipInfo.which == ChipInfo.type.canoe
                || ChipInfo.which == ChipInfo.type.tuna) {
            for (int bin_id = 0; bin_id < bins.size(); bin_id++) {
                lines.add("qcom,gpu-pwrlevels-" + bins.get(bin_id).id + " {");
                lines.addAll(bins.get(bin_id).header);
                for (int pwr_level_id = 0; pwr_level_id < bins.get(bin_id).levels.size(); pwr_level_id++) {
                    lines.add("qcom,gpu-pwrlevel@" + pwr_level_id + " {");
                    lines.add("reg = <" + pwr_level_id + ">;");
                    lines.addAll(bins.get(bin_id).levels.get(pwr_level_id).lines);
                    lines.add("};");
                }
                lines.add("};");
            }
        } else if (ChipInfo.which == ChipInfo.type.kona_singleBin
                || ChipInfo.which == ChipInfo.type.msmnile_singleBin
                || ChipInfo.which == ChipInfo.type.lahaina_singleBin
                || ChipInfo.which == ChipInfo.type.waipio_singleBin
                || ChipInfo.which == ChipInfo.type.cape_singleBin
                || ChipInfo.which == ChipInfo.type.ukee_singleBin
                || ChipInfo.which == ChipInfo.type.cliffs_singleBin
                || ChipInfo.which == ChipInfo.type.cliffs_7_singleBin
                || ChipInfo.which == ChipInfo.type.kalama_sg_singleBin) {
            lines.add("qcom,gpu-pwrlevels {");
            lines.addAll(bins.get(0).header);
            for (int pwr_level_id = 0; pwr_level_id < bins.get(0).levels.size(); pwr_level_id++) {
                lines.add("qcom,gpu-pwrlevel@" + pwr_level_id + " {");
                lines.add("reg = <" + pwr_level_id + ">;");
                lines.addAll(bins.get(0).levels.get(pwr_level_id).lines);
                lines.add("};");
            }
            lines.add("};");
        }
        return lines;
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

    private static EditorState captureState() {
        EditorState state = new EditorState();
        state.linesInDts = new ArrayList<>(lines_in_dts);
        state.binsSnapshot = cloneBinsList(bins);
        state.binPosition = bin_position;
        return state;
    }

    private static ArrayList<bin> cloneBinsList(List<bin> source) {
        ArrayList<bin> clone = new ArrayList<>();
        if (source == null) {
            return clone;
        }
        for (bin original : source) {
            clone.add(cloneBin(original));
        }
        return clone;
    }

    private static bin cloneBin(bin original) {
        bin copy = new bin();
        copy.id = original.id;
        copy.header = new ArrayList<>(original.header);
        copy.levels = new ArrayList<>();
        for (level lvl : original.levels) {
            copy.levels.add(cloneLevel(lvl));
        }
        return copy;
    }

    private static level cloneLevel(level original) {
        level copy = new level();
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
    }

    private static void updateUndoRedoButtons() {
        if (undoButtonRef != null) {
            boolean enabled = !undoStack.isEmpty();
            undoButtonRef.setEnabled(enabled);
            undoButtonRef.setAlpha(enabled ? 1f : 0.5f);
        }
        if (redoButtonRef != null) {
            boolean enabled = !redoStack.isEmpty();
            redoButtonRef.setEnabled(enabled);
            redoButtonRef.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private static void updateHistoryButtonLabel() {
        if (historyButtonRef == null) {
            return;
        }
        Activity activity = currentActivity;
        if (activity == null) {
            historyButtonRef.setText("History" + (changeHistory.isEmpty() ? "" : " (" + changeHistory.size() + ")"));
            return;
        }
        if (changeHistory.isEmpty()) {
            historyButtonRef.setText(activity.getString(R.string.history));
        } else {
            historyButtonRef.setText(activity.getString(R.string.history_with_count, changeHistory.size()));
        }
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
    private interface EditorChange {
        void run() throws Exception;
    }

    private static void applyChange(String description, EditorChange change) throws Exception {
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

    private static boolean saveFrequencyTable(Context context, boolean showToast, String historyMessage) {
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
        updateUndoRedoButtons();
        updateHistoryButtonLabel();
        markStateSaved();
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

    private static void handleUndo() {
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

    private static void handleRedo() {
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

    private static void showHistoryDialog(Activity activity) {
        if (activity == null) {
            return;
        }
        if (changeHistory.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.history_title)
                    .setMessage(R.string.history_empty)
                    .setPositiveButton(R.string.ok, null)
                    .create()
                    .show();
            return;
        }
        CharSequence[] entries = changeHistory.toArray(new CharSequence[0]);
        new AlertDialog.Builder(activity)
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
        return DtsHelper.shouldUseHex(line) ? DtsHelper.decode_hex_line(line).value :
                DtsHelper.decode_int_line(line).value + "";
    }

    private static void generateALevel(Activity activity, int last, int levelid,
                                       LinearLayout page) throws Exception {
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                try {
                    generateLevels(activity, last, page);
                } catch (Exception ignored) {
                }
            }
        };

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
                true
        ));

        // Group bus-max, bus-min, and bus-freq into modern stat cards
        ArrayList<GpuParamDetailAdapter.StatItem> statsGroup = new ArrayList<>();
        ArrayList<Integer> statsPositions = new ArrayList<>(); // Track positions for editing
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
                    lineIndex // Use lineIndex directly, not +1
                ));
                statsPositions.add(lineIndex);
            } else {
                // Other parameters use regular card layout
                // Special handling for GPU frequency to make it clearer
                String displayTitle = paramTitle;
                if (paramName.contains("gpu-freq") || (paramName.contains("frequency") && !paramName.contains("bus"))) {
                    displayTitle = "GPU Frequency";
                }
                
                otherParams.add(new GpuParamDetailAdapter.ParamDetailItem(
                    displayTitle,
                    paramValue,
                    paramName,
                    iconRes
                ));
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
            public void onItemClick(int position) {
                try {
                    // Handle back button
                    if (position == 0) {
                        generateLevels(activity, last, page);
                        return;
                    }

                    // For stat cards, position is already the correct lineIndex
                    // For regular params after stats group, we need to calculate offset
                    int actualLineIndex;
                    
                    // Check if this position is from a stat card (position < statsPositions.size())
                    boolean isStatCard = false;
                    for (int statPos : statsPositions) {
                        if (position == statPos) {
                            isStatCard = true;
                            actualLineIndex = position;
                            
                            // Get parameter details
                            String raw_name = DtsHelper.decode_hex_line(bins.get(last).levels.get(levelid).lines.get(actualLineIndex)).name;
                            String raw_value = DtsHelper.shouldUseHex(bins.get(last).levels.get(levelid).lines.get(actualLineIndex))
                                    ? DtsHelper.decode_hex_line(bins.get(last).levels.get(levelid).lines.get(actualLineIndex)).value
                                    : DtsHelper.decode_int_line(bins.get(last).levels.get(levelid).lines.get(actualLineIndex)).value + "";
                            
                            String paramTitle = KonaBessStr.convert_level_params(raw_name, activity);
                            handleParameterEdit(activity, last, levelid, page, actualLineIndex, raw_name, raw_value, paramTitle);
                            return;
                        }
                    }
                    
                    // If not a stat card, handle as regular parameter
                    if (!isStatCard) {
                        // Position 1 is the stats group, positions after are regular params
                        int offsetPosition = statsGroup.isEmpty() ? position - 1 : position - 2;
                        if (offsetPosition < 0 || offsetPosition >= otherParams.size()) {
                            return;
                        }
                        
                        // Find the line index for this parameter
                        actualLineIndex = statsPositions.size() + offsetPosition;

                        // Get parameter details
                        String raw_name = DtsHelper.decode_hex_line(bins.get(last).levels.get(levelid).lines.get(actualLineIndex)).name;
                        String raw_value = DtsHelper.shouldUseHex(bins.get(last).levels.get(levelid).lines.get(actualLineIndex))
                                ? DtsHelper.decode_hex_line(bins.get(last).levels.get(levelid).lines.get(actualLineIndex)).value
                                : DtsHelper.decode_int_line(bins.get(last).levels.get(levelid).lines.get(actualLineIndex)).value + "";

                        handleParameterEdit(activity, last, levelid, page, actualLineIndex, raw_name, raw_value, 
                                          items.get(position).title);
                    }
                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.error_occur);
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

                new AlertDialog.Builder(activity)
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
                                    final String existingLine = bins.get(binIndex).levels.get(levelIndex).lines.get(lineIndex);
                                    if (Objects.equals(existingLine, encodedLine)) {
                                        return;
                                    }
                                    final String freqLabel = SettingsActivity.formatFrequency(
                                            getFrequencyFromLevel(bins.get(binIndex).levels.get(levelIndex)), activity);
                                    applyChange(activity.getString(R.string.history_update_voltage_level, freqLabel), new EditorChange() {
                                        @Override
                                        public void run() {
                                            bins.get(binIndex).levels.get(levelIndex).lines.set(lineIndex, encodedLine);
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
        } else {
            // Handle other parameters with text input
            EditText editText = new EditText(activity);
            editText.setInputType(DtsHelper.shouldUseHex(raw_name) ?
                    InputType.TYPE_CLASS_TEXT : InputType.TYPE_CLASS_NUMBER);
            editText.setText(raw_value);
            new AlertDialog.Builder(activity)
                    .setTitle(activity.getResources().getString(R.string.edit) + " \"" + paramTitle + "\"")
                    .setView(editText)
                    .setMessage(KonaBessStr.help(raw_name, activity))
                    .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                final String newValue = editText.getText().toString();
                                final String encodedLine = DtsHelper.encodeIntOrHexLine(raw_name, newValue);
                                final String existingLine = bins.get(binIndex).levels.get(levelIndex).lines.get(lineIndex);
                                if (Objects.equals(existingLine, encodedLine)) {
                                    return;
                                }
                                final String freqLabel = SettingsActivity.formatFrequency(
                                        getFrequencyFromLevel(bins.get(binIndex).levels.get(levelIndex)), activity);
                                applyChange(activity.getString(R.string.history_edit_parameter, paramTitle, freqLabel), new EditorChange() {
                                    @Override
                                    public void run() {
                                        bins.get(binIndex).levels.get(levelIndex).lines.set(lineIndex, encodedLine);
                                    }
                                });
                                generateALevel(activity, binIndex, levelIndex, page);
                                Toast.makeText(activity, R.string.save_success,
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                DialogUtil.showError(activity, R.string.save_failed);
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create().show();
        }
    }

    private static level level_clone(level from) {
        level next = new level();
        next.lines = new ArrayList<>(from.lines);
        return next;
    }

    private static void offset_initial_level_old(int offset) throws Exception {
        boolean started = false;
        int bracket = 0;
        for (int i = 0; i < lines_in_dts.size(); i++) {
            String line = lines_in_dts.get(i);

            if (line.contains("qcom,kgsl-3d0") && line.contains("{")) {
                started = true;
                bracket++;
                continue;
            }

            if (line.contains("{")) {
                bracket++;
                continue;
            }

            if (line.contains("}")) {
                bracket--;
                if (bracket == 0)
                    break;
                continue;
            }

            if (!started)
                continue;

            if (line.contains("qcom,initial-pwrlevel")) {
                lines_in_dts.set(i,
                        DtsHelper.encodeIntOrHexLine(DtsHelper.decode_int_line(line).name,
                                DtsHelper.decode_int_line(line).value + offset + ""));
            }

        }
    }

    private static void offset_initial_level(int bin_id, int offset) throws Exception {
        if (ChipInfo.which == ChipInfo.type.kona_singleBin
                || ChipInfo.which == ChipInfo.type.msmnile_singleBin
                || ChipInfo.which == ChipInfo.type.lahaina_singleBin
                || ChipInfo.which == ChipInfo.type.waipio_singleBin
                || ChipInfo.which == ChipInfo.type.cape_singleBin
                || ChipInfo.which == ChipInfo.type.ukee_singleBin
                || ChipInfo.which == ChipInfo.type.cliffs_singleBin
                || ChipInfo.which == ChipInfo.type.cliffs_7_singleBin
                || ChipInfo.which == ChipInfo.type.kalama_sg_singleBin) {
            offset_initial_level_old(offset);
            return;
        }
        for (int i = 0; i < bins.get(bin_id).header.size(); i++) {
            String line = bins.get(bin_id).header.get(i);
            if (line.contains("qcom,initial-pwrlevel")) {
                bins.get(bin_id).header.set(i,
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

    private static void patch_throttle_level_old() throws Exception {
        boolean started = false;
        int bracket = 0;
        for (int i = 0; i < lines_in_dts.size(); i++) {
            String line = lines_in_dts.get(i);

            if (line.contains("qcom,kgsl-3d0") && line.contains("{")) {
                started = true;
                bracket++;
                continue;
            }

            if (line.contains("{")) {
                bracket++;
                continue;
            }

            if (line.contains("}")) {
                bracket--;
                if (bracket == 0)
                    break;
                continue;
            }

            if (!started)
                continue;

            if (line.contains("qcom,throttle-pwrlevel")) {
                lines_in_dts.set(i,
                        DtsHelper.encodeIntOrHexLine(DtsHelper.decode_int_line(line).name,
                                "0"));
            }

        }
    }

    private static void patch_throttle_level() throws Exception {
        if (ChipInfo.which == ChipInfo.type.kona_singleBin
                || ChipInfo.which == ChipInfo.type.msmnile_singleBin
                || ChipInfo.which == ChipInfo.type.lahaina_singleBin
                || ChipInfo.which == ChipInfo.type.waipio_singleBin
                || ChipInfo.which == ChipInfo.type.cape_singleBin
                || ChipInfo.which == ChipInfo.type.ukee_singleBin
                || ChipInfo.which == ChipInfo.type.cliffs_singleBin
                || ChipInfo.which == ChipInfo.type.cliffs_7_singleBin
                || ChipInfo.which == ChipInfo.type.kalama_sg_singleBin) {
            patch_throttle_level_old();
            return;
        }
        for (int bin_id = 0; bin_id < bins.size(); bin_id++) {
            for (int i = 0; i < bins.get(bin_id).header.size(); i++) {
                String line = bins.get(bin_id).header.get(i);
                if (line.contains("qcom,throttle-pwrlevel")) {
                    bins.get(bin_id).header.set(i,
                            DtsHelper.encodeIntOrHexLine(
                                    DtsHelper.decode_int_line(line).name, "0"));
                    break;
                }
            }
        }
    }

    public static boolean canAddNewLevel(int binID, Context context) throws Exception {
        int max_levels = ChipInfo.getMaxTableLevels(ChipInfo.which) - min_level_chip_offset();
        if (bins.get(binID).levels.size() <= max_levels)
            return true;
        Toast.makeText(context, R.string.unable_add_more, Toast.LENGTH_SHORT).show();
        return false;
    }

    public static int min_level_chip_offset() throws Exception {
        if (ChipInfo.which == ChipInfo.type.lahaina || ChipInfo.which == ChipInfo.type.lahaina_singleBin
                || ChipInfo.which == ChipInfo.type.shima || ChipInfo.which == ChipInfo.type.yupik
                || ChipInfo.which == ChipInfo.type.waipio_singleBin
                || ChipInfo.which == ChipInfo.type.cape_singleBin
                || ChipInfo.which == ChipInfo.type.kalama
                || ChipInfo.which == ChipInfo.type.diwali
                || ChipInfo.which == ChipInfo.type.ukee_singleBin
                || ChipInfo.which == ChipInfo.type.pineapple
                || ChipInfo.which == ChipInfo.type.cliffs_singleBin
                || ChipInfo.which == ChipInfo.type.cliffs_7_singleBin
                || ChipInfo.which == ChipInfo.type.kalama_sg_singleBin
                || ChipInfo.which == ChipInfo.type.sun
                || ChipInfo.which == ChipInfo.type.canoe
                || ChipInfo.which == ChipInfo.type.tuna)
            return 1;
        if (ChipInfo.which == ChipInfo.type.kona || ChipInfo.which == ChipInfo.type.kona_singleBin
                || ChipInfo.which == ChipInfo.type.msmnile || ChipInfo.which == ChipInfo.type.msmnile_singleBin
                || ChipInfo.which == ChipInfo.type.lito_v1 || ChipInfo.which == ChipInfo.type.lito_v2
                || ChipInfo.which == ChipInfo.type.lagoon)
            return 2;
        throw new Exception();
    }

    private static void generateLevels(Activity activity, int id, LinearLayout page) throws Exception {
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.updateGpuToolbarTitle(activity.getString(R.string.edit_freq_table)
                + " - " + KonaBessStr.convert_bins(bins.get(id).id, activity));

        mainActivity.onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                try {
                    generateBins(activity, page);
                    mainActivity.updateGpuToolbarTitle(activity.getString(R.string.edit_freq_table));
                } catch (Exception ignored) {
                }
            }
        };

        currentActivity = activity;
        currentPage = page;
        currentBinIndex = id;
        currentLevelIndex = null;
        updateUndoRedoButtons();
        updateHistoryButtonLabel();
        updateSaveButtonAppearance();

        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        
        // Set padding to prevent content from being hidden behind bottom toolbar
        float density = activity.getResources().getDisplayMetrics().density;
        int bottomPadding = (int) (density * 80); // Toolbar height + extra space
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, 0, 0, bottomPadding);
        
        ArrayList<GpuFreqAdapter.FreqItem> items = new ArrayList<>();

        // Back button (header)
        items.add(new GpuFreqAdapter.FreqItem(
            activity.getResources().getString(R.string.back),
            "",
            GpuFreqAdapter.FreqItem.ActionType.BACK
        ));

        // Add new at top button (header)
        items.add(new GpuFreqAdapter.FreqItem(
            activity.getResources().getString(R.string.add_freq_top),
            activity.getResources().getString(R.string.add_freq_top_desc),
            GpuFreqAdapter.FreqItem.ActionType.ADD_TOP
        ));

        // Add all frequency levels
        for (int i = 0; i < bins.get(id).levels.size(); i++) {
            level level = bins.get(id).levels.get(i);
            long freq = getFrequencyFromLevel(level);
            if (freq == 0)
                continue;
            
            GpuFreqAdapter.FreqItem item = new GpuFreqAdapter.FreqItem(
                SettingsActivity.formatFrequency(freq, activity),
                ""
            );
            item.originalPosition = i;
            item.frequencyHz = freq;
            
            // Extract spec details from DTS lines
            try {
                for (String line : level.lines) {
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
            GpuFreqAdapter.FreqItem.ActionType.ADD_BOTTOM
        ));

        GpuFreqAdapter adapter = new GpuFreqAdapter(items, activity);
        
        // Item click listener
        adapter.setOnItemClickListener(position -> {
            GpuFreqAdapter.FreqItem item = items.get(position);

            switch (item.actionType) {
                case ADD_BOTTOM:
                    try {
                        if (!canAddNewLevel(id, activity))
                            return;
                        final int offset = min_level_chip_offset();
                        final int insertIndex = bins.get(id).levels.size() - offset;
                        final level template = bins.get(id).levels.get(insertIndex);
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
                        final level template = bins.get(id).levels.get(0);
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
                case DUPLICATE:
                    try {
                        if (!canAddNewLevel(id, activity))
                            return;
                        final int targetPosition = item.targetPosition;
                        final level template = bins.get(id).levels.get(targetPosition);
                        final String freqLabel = SettingsActivity.formatFrequency(
                                getFrequencyFromLevel(template), activity);
                        applyChange(activity.getString(R.string.history_duplicate_frequency, freqLabel), () -> {
                            bins.get(id).levels.add(targetPosition + 1, level_clone(template));
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
        
        // Long-press listener for inline duplicate
        adapter.setOnItemLongClickListener(position -> {
            GpuFreqAdapter.FreqItem item = items.get(position);
            
            // Only allow duplicate for regular frequency items
            if (!item.isLevelItem()) {
                return;
            }
            
            // Clear all highlights and duplicates
            for (int i = items.size() - 1; i >= 0; i--) {
                GpuFreqAdapter.FreqItem currentItem = items.get(i);
                
                // Remove duplicate items
                if (currentItem.isDuplicateItem()) {
                    items.remove(i);
                    adapter.notifyItemRemoved(i);
                    continue;
                }
                
                // Clear highlights
                if (currentItem.isHighlighted) {
                    currentItem.isHighlighted = false;
                    adapter.notifyItemChanged(i);
                }
            }
            
            // Recalculate position after removal
            int adjustedPosition = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == item) {
                    adjustedPosition = i;
                    break;
                }
            }
            
            if (adjustedPosition == -1) return;
            
            // Highlight the selected item
            item.isHighlighted = true;
            adapter.notifyItemChanged(adjustedPosition);
            
            // Calculate level index
            int levelIndex = 0;
            for (int i = 0; i < adjustedPosition; i++) {
                if (items.get(i).isLevelItem()) {
                    levelIndex++;
                }
            }
            
            // Create duplicate card
            GpuFreqAdapter.FreqItem duplicate = new GpuFreqAdapter.FreqItem(
                activity.getString(R.string.duplicate_frequency),
                "",
                GpuFreqAdapter.FreqItem.ActionType.DUPLICATE
            );
            duplicate.targetPosition = levelIndex;
            
            // Insert card below the selected item
            items.add(adjustedPosition + 1, duplicate);
            adapter.notifyItemInserted(adjustedPosition + 1);
            recyclerView.smoothScrollToPosition(adjustedPosition);
        });
        
        // Delete button click listener
        adapter.setOnDeleteClickListener(position -> {
            if (bins.get(id).levels.size() == 1) {
                Toast.makeText(activity, R.string.unable_add_more, Toast.LENGTH_SHORT).show();
                return;
            }

            final int levelPosition = position - 2; // Adjust for header items
            try {
                final long freqValue = getFrequencyFromLevel(bins.get(id).levels.get(levelPosition));
                final String freqLabel = SettingsActivity.formatFrequency(freqValue, activity);
                new AlertDialog.Builder(activity)
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
    
    private static void updateBinsFromAdapter(Activity activity, int binId, List<GpuFreqAdapter.FreqItem> items) throws Exception {
        ArrayList<level> currentLevels = bins.get(binId).levels;
        ArrayList<level> newLevels = new ArrayList<>();
        boolean[] used = new boolean[currentLevels.size()];

        for (GpuFreqAdapter.FreqItem item : items) {
            if (!item.isHeader && !item.isFooter && item.hasFrequencyValue()) {
                for (int index = 0; index < currentLevels.size(); index++) {
                    if (used[index]) continue;
                    level levelRef = currentLevels.get(index);
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
            final ArrayList<level> replacement = newLevels;
            applyChange(activity.getString(R.string.history_reorder_frequency), () ->
                    bins.get(binId).levels = replacement);
        }
    }

    private static long getFrequencyFromLevel(level level) throws Exception {
        for (String line : level.lines) {
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
        com.google.android.material.card.MaterialCardView card = 
                new com.google.android.material.card.MaterialCardView(activity);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(padding, padding, padding, (int)(density * 8));
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
        titleView.setPadding(0, 0, 0, (int)(density * 8));
        innerLayout.addView(titleView);
        
        // Current chipset display with click to change
        LinearLayout chipsetRow = new LinearLayout(activity);
        chipsetRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsetRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chipsetRow.setClickable(true);
        chipsetRow.setFocusable(true);
        
        // Set ripple effect
        android.content.res.TypedArray typedArray = activity.getTheme().obtainStyledAttributes(
                new int[]{android.R.attr.selectableItemBackground});
        int selectableItemBackground = typedArray.getResourceId(0, 0);
        typedArray.recycle();
        chipsetRow.setBackgroundResource(selectableItemBackground);
        chipsetRow.setPadding((int)(density * 12), (int)(density * 12), 
                (int)(density * 12), (int)(density * 12));
        
        // Chipset icon (Material Design)
        ImageView chipIcon = new ImageView(activity);
        chipIcon.setImageResource(R.drawable.ic_developer_board);
        int iconSize = (int)(density * 24);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd((int)(density * 12));
        chipIcon.setLayoutParams(iconParams);
        chipIcon.setColorFilter(MaterialColors.getColor(chipIcon,
                com.google.android.material.R.attr.colorOnSurface));
        chipsetRow.addView(chipIcon);
        
        // Chipset name
        TextView chipsetName = new TextView(activity);
        KonaBessCore.dtb currentDtb = KonaBessCore.getCurrentDtb();
        if (currentDtb != null) {
            chipsetName.setText(currentDtb.id + " " + 
                    ChipInfo.name2chipdesc(currentDtb.type, activity));
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
        
        ListView listView = new ListView(activity);
        ArrayList<ParamAdapter.item> items = new ArrayList<>();
        
        KonaBessCore.dtb currentDtb = KonaBessCore.getCurrentDtb();
        int currentDtbIndex = KonaBessCore.getDtbIndex();
        
        for (KonaBessCore.dtb dtb : KonaBessCore.dtbs) {
            items.add(new ParamAdapter.item() {{
                title = dtb.id + " " + ChipInfo.name2chipdesc(dtb.type, activity);
                // Highlight current selected
                subtitle = (currentDtb != null && dtb.id == currentDtb.id) ? "Currently Selected" :
                        (dtb.id == currentDtbIndex ? "Possible DTB" : "");
            }});
        }
        
        listView.setAdapter(new ParamAdapter(items, activity));
        
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Select Target Chipset")
                .setMessage("Choose the chipset configuration you want to edit")
                .setView(listView)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        
        listView.setOnItemClickListener((parent, view, position, id) -> {
            KonaBessCore.dtb selectedDtb = KonaBessCore.dtbs.get(position);
            
            // Show confirmation if switching chipset
            if (currentDtb != null && selectedDtb.id != currentDtb.id) {
                new AlertDialog.Builder(activity)
                        .setTitle("Switch Chipset?")
                        .setMessage("Switching chipset will reload the GPU frequency table. Continue?")
                        .setPositiveButton("Yes", (d, w) -> {
                            dialog.dismiss();
                            switchChipset(activity, page, selectedDtb, chipsetNameView);
                        })
                        .setNegativeButton("No", null)
                        .create().show();
            } else {
                dialog.dismiss();
            }
        });
    }
    
    private static void switchChipset(Activity activity, LinearLayout page, 
                                      KonaBessCore.dtb newDtb, TextView chipsetNameView) {
        AlertDialog waiting = DialogUtil.getWaitDialog(activity, R.string.getting_freq_table);
        waiting.show();
        
        new Thread(() -> {
            try {
                // Switch to new chipset
                KonaBessCore.chooseTarget(newDtb, activity);
                
                // Reload GPU table for new chipset
                init();
                decode();
                patch_throttle_level();
                resetEditorState();
                
                activity.runOnUiThread(() -> {
                    waiting.dismiss();
                    
                    // Update chipset name in card
                    chipsetNameView.setText(newDtb.id + " " + 
                            ChipInfo.name2chipdesc(newDtb.type, activity));
                    
                    // Regenerate bins view
                    try {
                        generateBins(activity, page);
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

    private static void generateBins(Activity activity, LinearLayout page) throws Exception {
        ((MainActivity) activity).onBackPressedListener = new MainActivity.onBackPressedListener() {
            @Override
            public void onBackPressed() {
                ((MainActivity) activity).showMainView();
            }
        };

        currentActivity = activity;
        currentPage = page;
        currentBinIndex = null;
        currentLevelIndex = null;
        updateUndoRedoButtons();
        updateHistoryButtonLabel();
        updateSaveButtonAppearance();

        // Create main vertical layout
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        float density = activity.getResources().getDisplayMetrics().density;
        
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

        ArrayList<GpuBinAdapter.BinItem> items = new ArrayList<>();
        for (int i = 0; i < bins.size(); i++) {
            items.add(new GpuBinAdapter.BinItem(
                    KonaBessStr.convert_bins(bins.get(i).id, activity),
                    ""));
        }

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

    private static View generateToolBar(Activity activity, LinearLayout showedView) {
        currentActivity = activity;
        
        // Use non-scrollable LinearLayout with wrap to prevent horizontal scroll
        LinearLayout mainContainer = new LinearLayout(activity);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        float density = activity.getResources().getDisplayMetrics().density;
        int padding = (int) (density * 12);
        int chipSpacing = (int) (density * 8);
        mainContainer.setPadding(padding, padding, padding, padding / 2);

        // First row: Save, Undo, Redo, History
        LinearLayout firstRow = new LinearLayout(activity);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Create compact chip buttons for first row
        MaterialButton saveButton = createCompactChip(activity, R.string.save_freq_table, R.drawable.ic_file_upload);
        MaterialButton undoButton = createCompactChip(activity, R.string.undo, R.drawable.ic_undo);
        MaterialButton redoButton = createCompactChip(activity, R.string.redo, R.drawable.ic_redo);
        MaterialButton historyButton = createCompactChip(activity, R.string.history, R.drawable.ic_history);

        // Save button reference and setup
        saveButtonRef = saveButton;
        updateSaveButtonAppearance();
        saveButton.setOnClickListener(v ->
            saveFrequencyTable(activity, true, activity.getString(R.string.history_manual_save)));

        // Undo button setup
        undoButtonRef = undoButton;
        undoButton.setOnClickListener(v -> handleUndo());

        // Redo button setup
        redoButtonRef = redoButton;
        redoButton.setOnClickListener(v -> handleRedo());

        // History button setup
        historyButtonRef = historyButton;
        historyButton.setOnClickListener(v -> showHistoryDialog(activity));
        updateHistoryButtonLabel();
        updateUndoRedoButtons();

        // Add buttons to first row with equal weight
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        chipParams.setMargins(0, 0, chipSpacing, 0);

        saveButton.setLayoutParams(chipParams);
        firstRow.addView(saveButton);

        undoButton.setLayoutParams(chipParams);
        firstRow.addView(undoButton);

        redoButton.setLayoutParams(chipParams);
        firstRow.addView(redoButton);

        LinearLayout.LayoutParams lastChipParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        historyButton.setLayoutParams(lastChipParams);
        firstRow.addView(historyButton);

        mainContainer.addView(firstRow);

        // Second row: Volt and Repack (if applicable)
        boolean hasSecondRow = false;
        LinearLayout secondRow = new LinearLayout(activity);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        secondRowParams.setMargins(0, chipSpacing, 0, 0);
        secondRow.setLayoutParams(secondRowParams);

        if (activity instanceof MainActivity && !ChipInfo.shouldIgnoreVoltTable(ChipInfo.which)) {
            MaterialButton voltButton = createCompactChip(activity, R.string.edit_gpu_volt_table, R.drawable.ic_voltage);
            voltButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new GpuVoltEditor.gpuVoltLogic((MainActivity) activity, showedView).start();
                }
            });

            LinearLayout.LayoutParams voltParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            voltParams.setMargins(0, 0, chipSpacing, 0);
            voltButton.setLayoutParams(voltParams);
            secondRow.addView(voltButton);
            hasSecondRow = true;
        }

        if (activity instanceof MainActivity) {
            MaterialButton repackButton = createCompactChip(activity, R.string.repack_flash, R.drawable.ic_flash);
            repackButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((MainActivity) activity).new repackLogic().start();
                }
            });

            LinearLayout.LayoutParams repackParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            if (!hasSecondRow) {
                // If this is the first button in second row
                repackParams.setMargins(0, 0, chipSpacing, 0);
            }
            repackButton.setLayoutParams(repackParams);
            secondRow.addView(repackButton);
            hasSecondRow = true;
        }

        if (hasSecondRow) {
            mainContainer.addView(secondRow);
        }

        return mainContainer;
    }

    private static MaterialButton createCompactChip(Activity activity, int textRes, int iconRes) {
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
