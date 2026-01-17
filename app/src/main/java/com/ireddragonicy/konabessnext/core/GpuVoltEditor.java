package com.ireddragonicy.konabessnext.core;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.ui.adapters.ParamAdapter;
import com.ireddragonicy.konabessnext.utils.DialogUtil;
import com.ireddragonicy.konabessnext.utils.DtsHelper;
import com.ireddragonicy.konabessnext.utils.FileUtil;
import com.ireddragonicy.konabessnext.utils.ThreadUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GpuVoltEditor {

    private static class Opp {
        long frequency;
        long volt;
    }

    public static int levelint2int(long level) throws Exception {
        int[] levels = ChipInfo.rpmh_levels.levels();
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == level) return i;
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
        opps.clear();
        oppPosition = -1;
        linesInDts = FileUtil.readLines(KonaBessCore.dts_path);
    }

    public static void decode() throws Exception {
        String pattern = ChipInfo.which.voltTablePattern;
        if (pattern == null) return;

        int i = -1;
        boolean isInGpuTable = false;
        int bracket = 0;
        int start = -1;

        while (++i < linesInDts.size()) {
            String line = linesInDts.get(i).trim();
            if (line.isEmpty()) continue;

            if (line.contains(pattern) && line.contains("{")) {
                isInGpuTable = true;
                bracket++;
                continue;
            }

            if (!isInGpuTable) continue;

            if (line.contains("opp-") && line.contains("{")) {
                start = i;
                if (oppPosition < 0) oppPosition = i;
                bracket++;
                continue;
            }

            if (line.contains("}")) {
                bracket--;
                if (bracket == 0) break;
                if (bracket != 1) throw new Exception("Structure error");

                opps.add(decodeOpp(linesInDts.subList(start, i + 1)));
                linesInDts.subList(start, i + 1).clear();
                i = start - 1;
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
        ArrayList<String> result = new ArrayList<>(linesInDts);
        if (oppPosition >= 0 && oppPosition <= result.size()) {
            result.addAll(oppPosition, table);
        }
        return result;
    }

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
            ThreadUtil.runOnMain(() -> {
                waiting = DialogUtil.getWaitDialog(activity, R.string.getting_volt);
                waiting.show();
            });

            ThreadUtil.runInBackground(() -> {
                try {
                    init();
                    decode();
                    ThreadUtil.runOnMain(() -> {
                        waiting.dismiss();
                        showedView.removeAllViews();
                        showedView.addView(generateToolBar(activity));
                        LinearLayout page = new LinearLayout(activity);
                        page.setOrientation(LinearLayout.VERTICAL);
                        if (!opps.isEmpty()) {
                            generateVolts(activity, page);
                            showedView.addView(page);
                        } else {
                            DialogUtil.showError(activity, R.string.not_support);
                        }
                    });
                } catch (Exception e) {
                    ThreadUtil.runOnMain(() -> {
                        if (waiting != null && waiting.isShowing()) waiting.dismiss();
                        DialogUtil.showError(activity, R.string.getting_volt_failed);
                    });
                }
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
                FileUtil.writeLines(KonaBessCore.dts_path, genBack(genTable()));
                Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                DialogUtil.showError(activity, R.string.save_failed);
            }
        });
        toolbar.addView(saveParamsBtn);
        return scrollView;
    }

    private static void generateVolts(Activity activity, LinearLayout page) {
        page.removeAllViews();
        ListView listView = new ListView(activity);
        ArrayList<ParamAdapter.item> items = new ArrayList<>();
        
        for (Opp opp : opps) {
            items.add(createItem(opp.frequency + " MHz", getLevelStr(opp.volt)));
        }

        listView.setAdapter(new ParamAdapter(items, activity));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            generateAVolt(activity, page, position);
        });
        
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
                DialogUtil.showEditDialog(activity, 
                    activity.getString(R.string.edit), 
                    activity.getString(R.string.volt_freq_msg), 
                    String.valueOf(opp.frequency), 
                    InputType.TYPE_CLASS_NUMBER, 
                    (text) -> {
                        try {
                            opp.frequency = Long.parseLong(text);
                            generateAVolt(activity, page, index);
                        } catch (Exception e) {
                            DialogUtil.showError(activity, R.string.save_failed);
                        }
                    });
            } else if (position == 2) { // Edit Volt
                DialogUtil.showSingleChoiceDialog(activity, 
                    activity.getString(R.string.edit), 
                    ChipInfo.rpmh_levels.level_str(), 
                    getLevelIndex(opp.volt), 
                    (dialog, which) -> {
                        opp.volt = ChipInfo.rpmh_levels.levels()[which];
                        dialog.dismiss();
                        generateAVolt(activity, page, index);
                    });
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
            if (levels[i] == level) return i;
        }
        return 0;
    }

    private static String getLevelStr(long level) {
        int idx = getLevelIndex(level);
        return ChipInfo.rpmh_levels.level_str()[idx];
    }
}
