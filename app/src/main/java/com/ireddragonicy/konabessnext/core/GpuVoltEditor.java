package com.ireddragonicy.konabessnext.core;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.ui.SettingsActivity;
import com.ireddragonicy.konabessnext.ui.adapters.ParamAdapter;
import com.ireddragonicy.konabessnext.utils.DialogUtil;
import com.ireddragonicy.konabessnext.utils.DtsHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GpuVoltEditor {

    private static class Opp {
        long frequency;
        long volt;
    }

    public static int levelint2int(long level) throws Exception {
        int[] levels = ChipInfo.rpmh_levels.levels();
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == level)
                return i;
        }
        throw new Exception("Level not found");
    }

    public static String levelint2str(long level) {
        try {
            return ChipInfo.rpmh_levels.level_str()[levelint2int(level)];
        } catch (Exception e) {
            return String.valueOf(level);
        }
    }

    private static List<Opp> opps = new ArrayList<>();
    private static List<String> linesInDts = new ArrayList<>();
    private static int oppPosition = -1;

    public static void init() throws IOException {
        linesInDts.clear();
        opps.clear();
        oppPosition = -1;

        File file = new File(KonaBessCore.dts_path);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                linesInDts.add(line);
            }
        }
    }

    public static void decode() throws Exception {
        String pattern = ChipInfo.which.voltTablePattern;
        if (pattern == null)
            return; // Should not happen if ignoreVoltTable is false

        int i = -1;
        boolean isInGpuTable = false;
        int bracket = 0;
        int start = -1;

        while (++i < linesInDts.size()) {
            String line = linesInDts.get(i).trim();
            if (line.isEmpty())
                continue;

            if (line.contains(pattern) && line.contains("{")) {
                isInGpuTable = true;
                bracket++;
                continue;
            }

            if (!isInGpuTable)
                continue;

            if (line.contains("opp-") && line.contains("{")) {
                start = i;
                if (oppPosition < 0)
                    oppPosition = i;
                bracket++;
                continue;
            }

            if (line.contains("}")) {
                bracket--;
                if (bracket == 0)
                    break; // End of table
                if (bracket != 1)
                    throw new Exception("Structure error");

                // End of an opp block
                opps.add(decodeOpp(linesInDts.subList(start, i + 1)));
                linesInDts.subList(start, i + 1).clear();
                i = start - 1; // logical rollback since we removed lines
            }
        }
    }

    private static Opp decodeOpp(List<String> lines) throws Exception {
        Opp opp = new Opp();
        for (String line : lines) {
            if (line.contains("opp-hz"))
                opp.frequency = DtsHelper.decode_int_line_hz(line).value;
            if (line.contains("opp-microvolt"))
                opp.volt = DtsHelper.decode_int_line(line).value;
        }
        return opp;
    }

    public static List<String> genTable() {
        List<String> table = new ArrayList<>();
        for (Opp opp : opps) {
            table.add("opp-" + opp.frequency + " {");
            table.add("opp-hz = <0x0 " + opp.frequency + ">;");
            table.add("opp-microvolt = <" + opp.volt + ">;");
            table.add("};");
        }
        return table;
    }

    public static List<String> genBack(List<String> table) {
        List<String> result = new ArrayList<>(linesInDts);
        if (oppPosition >= 0 && oppPosition <= result.size()) {
            result.addAll(oppPosition, table);
        }
        return result;
    }

    public static void writeOut(List<String> newDts) throws IOException {
        File file = new File(KonaBessCore.dts_path);
        if (file.exists()) {
            if (!file.delete() && !file.setWritable(true) && !file.delete()) {
                // Try best effort
            }
        }
        if (!file.createNewFile())
            throw new IOException("Failed to create file: " + file.getAbsolutePath());

        file.setReadable(true, false);
        file.setWritable(true, false);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String s : newDts) {
                writer.write(s);
                writer.newLine();
            }
        }
    }

    // UI Logic ----------------------------------------------------------------

    public static class gpuVoltLogic extends Thread {
        private final Activity activity;
        private final LinearLayout showedView;
        private AlertDialog waiting;

        public gpuVoltLogic(Activity activity, LinearLayout showedView) {
            this.activity = activity;
            this.showedView = showedView;
        }

        @Override
        public void run() {
            activity.runOnUiThread(() -> {
                waiting = DialogUtil.getWaitDialog(activity, R.string.getting_volt);
                waiting.show();
            });

            try {
                init();
                decode();
            } catch (Exception e) {
                activity.runOnUiThread(() -> DialogUtil.showError(activity, R.string.getting_volt_failed));
                return;
            }

            activity.runOnUiThread(() -> {
                waiting.dismiss();
                showedView.removeAllViews();
                showedView.addView(generateToolBar(activity));
                LinearLayout page = new LinearLayout(activity);
                page.setOrientation(LinearLayout.VERTICAL);
                generateVolts(activity, page);
                showedView.addView(page);
            });
        }
    }

    private static View generateToolBar(Activity activity) {
        LinearLayout toolbar = new LinearLayout(activity);
        HorizontalScrollView scrollView = new HorizontalScrollView(activity);
        scrollView.addView(toolbar);

        Button saveParamsBtn = new Button(activity);
        saveParamsBtn.setText(R.string.save_volt_table);
        saveParamsBtn.setOnClickListener(v -> {
            try {
                writeOut(genBack(genTable()));
                Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                DialogUtil.showError(activity, R.string.save_failed);
            }
        });
        toolbar.addView(saveParamsBtn);

        return scrollView;
    }

    private static void generateVolts(Activity activity, LinearLayout page) {
        ListView listView = new ListView(activity);
        ArrayList<ParamAdapter.item> items = new ArrayList<>();

        items.add(createItem(activity.getString(R.string.new_item), activity.getString(R.string.new_desc_volt)));

        for (Opp opp : opps) {
            items.add(createItem(SettingsActivity.formatFrequency(opp.frequency, activity), ""));
        }

        items.add(createItem(activity.getString(R.string.new_item), activity.getString(R.string.new_desc_volt))); // Add
                                                                                                                  // at
                                                                                                                  // bottom
                                                                                                                  // too

        listView.setAdapter(new ParamAdapter(items, activity));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0 || position == items.size() - 1) { // New Item (Top or Bottom)
                if (opps.isEmpty()) {
                    Opp newOpp = new Opp();
                    newOpp.frequency = 300000000;
                    newOpp.volt = ChipInfo.rpmh_levels.levels()[0];
                    opps.add(newOpp);
                } else {
                    // Clone nearby item
                    int baseIdx = (position == 0) ? 0 : opps.size() - 1;
                    Opp base = opps.get(baseIdx);
                    Opp newOpp = new Opp();
                    newOpp.frequency = base.frequency;
                    newOpp.volt = base.volt;
                    opps.add((position == 0) ? 0 : opps.size(), newOpp);
                }
                generateVolts(activity, page);
                return;
            }

            // Edit existing
            try {
                generateAVolt(activity, page, position - 1);
            } catch (Exception e) {
                DialogUtil.showError(activity, R.string.error_occur);
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position == 0 || position == items.size() - 1)
                return true;

            int oppIdx = position - 1;
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.remove)
                    .setMessage("Remove voltage level "
                            + SettingsActivity.formatFrequency(opps.get(oppIdx).frequency, activity) + "?")
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        opps.remove(oppIdx);
                        generateVolts(activity, page);
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
            return true;
        });

        page.removeAllViews();
        page.addView(listView);
    }

    private static void generateAVolt(Activity activity, LinearLayout page, int index) {
        ListView listView = new ListView(activity);
        ArrayList<ParamAdapter.item> items = new ArrayList<>();
        Opp opp = opps.get(index);

        items.add(createItem(activity.getString(R.string.back), ""));
        items.add(createItem(activity.getString(R.string.freq), opp.frequency + ""));
        items.add(createItem(activity.getString(R.string.volt), getLevelStr(opp.volt)));

        listView.setAdapter(new ParamAdapter(items, activity));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                generateVolts(activity, page);
            } else if (position == 1) { // Edit Freq
                EditText input = new EditText(activity);
                input.setText(String.valueOf(opp.frequency));
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.edit)
                        .setMessage(R.string.volt_freq_msg)
                        .setView(input)
                        .setPositiveButton(R.string.save, (dialog, which) -> {
                            try {
                                opp.frequency = Long.parseLong(input.getText().toString());
                                generateAVolt(activity, page, index);
                            } catch (Exception e) {
                                DialogUtil.showError(activity, R.string.save_failed);
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else if (position == 2) { // Edit Volt
                Spinner spinner = new Spinner(activity);
                spinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line,
                        ChipInfo.rpmh_levels.level_str()));
                spinner.setSelection(getLevelIndex(opp.volt));

                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.edit)
                        .setView(spinner)
                        .setMessage(R.string.editvolt_msg)
                        .setPositiveButton(R.string.save, (dp, w) -> {
                            opp.volt = ChipInfo.rpmh_levels.levels()[spinner.getSelectedItemPosition()];
                            generateAVolt(activity, page, index);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });

        page.removeAllViews();
        page.addView(listView);
    }

    private static ParamAdapter.item createItem(String title, String subtitle) {
        ParamAdapter.item item = new ParamAdapter.item();
        item.title = title;
        item.subtitle = subtitle;
        return item;
    }

    private static int getLevelIndex(long level) {
        int[] levels = ChipInfo.rpmh_levels.levels();
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == level)
                return i;
        }
        return 0; // Default
    }

    private static String getLevelStr(long level) {
        int idx = getLevelIndex(level);
        return ChipInfo.rpmh_levels.level_str()[idx];
    }
}
