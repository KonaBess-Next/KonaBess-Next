package com.ireddragonicy.konabessnext.core;

import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.ui.MainActivity;
import com.ireddragonicy.konabessnext.ui.ExportHistoryActivity;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Environment;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ireddragonicy.konabessnext.ui.adapters.ActionCardAdapter;
import com.ireddragonicy.konabessnext.utils.DialogUtil;
import com.ireddragonicy.konabessnext.utils.ExportHistoryManager;
import com.ireddragonicy.konabessnext.utils.FileUtil;
import com.ireddragonicy.konabessnext.utils.GzipUtils;
import com.ireddragonicy.konabessnext.utils.RootHelper;
import com.ireddragonicy.konabessnext.utils.ThreadUtil;

public class TableIO {

    @SuppressWarnings("unused")
    private static class json_keys {
        public static final String MODEL = "model";
        public static final String BRAND = "brand";
        public static final String ID = "id";
        public static final String VERSION = "version";
        public static final String FINGERPRINT = "fingerprint";
        public static final String MANUFACTURER = "manufacturer";
        public static final String DEVICE = "device";
        public static final String NAME = "name";
        public static final String BOARD = "board";
        public static final String CHIP = "chip";
        public static final String DESCRIPTION = "desc";
        public static final String FREQ = "freq";
        public static final String VOLT = "volt";
    }

    private static AlertDialog waiting_import;

    private static synchronized void prepareTables() throws Exception {
        GpuTableEditor.init();
        GpuTableEditor.decode();
        if (!ChipInfo.which.ignoreVoltTable) {
            GpuVoltEditor.init();
            GpuVoltEditor.decode();
        }
    }

    private static boolean decodeAndWriteData(JSONObject jsonObject) throws Exception {
        if (!ChipInfo.which.isEquivalentTo(ChipInfo.type.valueOf(jsonObject.getString(json_keys.CHIP))))
            return true;
        prepareTables();
        ArrayList<String> freq = new ArrayList<>(Arrays.asList(jsonObject.getString(json_keys.FREQ).split("\n")));
        GpuTableEditor.writeOut(GpuTableEditor.genBack(freq));
        if (!ChipInfo.which.ignoreVoltTable) {
            ArrayList<String> volt = new ArrayList<>(Arrays.asList(jsonObject.getString(json_keys.VOLT).split("\n")));
            // Init again because the dts file has been updated
            GpuVoltEditor.init();
            GpuVoltEditor.decode();
            GpuVoltEditor.writeOut(GpuVoltEditor.genBack(volt));
        }

        return false;
    }

    private static String getFreqData() {
        StringBuilder data = new StringBuilder();
        for (String line : GpuTableEditor.genTable())
            data.append(line).append("\n");
        return data.toString();
    }

    private static String getVoltData() {
        StringBuilder data = new StringBuilder();
        for (String line : GpuVoltEditor.genTable())
            data.append(line).append("\n");
        return data.toString();
    }

    private static String getConfig(String desc) throws IOException {
        JSONObject jsonObject = new JSONObject();
        try {
            prepareTables();
            /*
             * jsonObject.put(json_keys.MODEL, getCurrent("model"));
             * jsonObject.put(json_keys.BRAND, getCurrent("brand"));
             * jsonObject.put(json_keys.ID, getCurrent("id"));
             * jsonObject.put(json_keys.VERSION, getCurrent("version"));
             * jsonObject.put(json_keys.FINGERPRINT, getCurrent("fingerprint"));
             * jsonObject.put(json_keys.MANUFACTURER, getCurrent("manufacturer"));
             * jsonObject.put(json_keys.DEVICE, getCurrent("device"));
             * jsonObject.put(json_keys.NAME, getCurrent("name"));
             * jsonObject.put(json_keys.BOARD, getCurrent("board"));
             */
            jsonObject.put(json_keys.CHIP, ChipInfo.which.name());
            jsonObject.put(json_keys.DESCRIPTION, desc);
            jsonObject.put(json_keys.FREQ, getFreqData());
            if (!ChipInfo.which.ignoreVoltTable)
                jsonObject.put(json_keys.VOLT, getVoltData());
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new IOException("Failed to prepare configuration", e);
        }
        return GzipUtils.compress(jsonObject.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void import_edittext(Activity activity) {
        DialogUtil.showEditDialog(activity, 
            activity.getString(R.string.import_data), 
            null, 
            "", 
            InputType.TYPE_CLASS_TEXT, 
            (text) -> new showDecodeDialog(activity, text).start());
    }

    private static abstract class ConfirmExportCallback {
        public abstract void onConfirm(String desc, String filename);
    }

    private static void showExportDialog(Activity activity,
            ConfirmExportCallback confirmExportCallback) {
        // Create layout with two EditTexts
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        // Description field
        EditText descEditText = new EditText(activity);
        descEditText.setHint(R.string.input_introduction_here);
        layout.addView(descEditText);

        // Filename field
        EditText filenameEditText = new EditText(activity);
        filenameEditText.setHint(activity.getResources().getString(R.string.export_filename_hint));
        layout.addView(filenameEditText);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.export_data)
                .setMessage(R.string.export_data_msg)
                .setView(layout)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        dialog.dismiss();
                        String filename = filenameEditText.getText().toString().trim();
                        confirmExportCallback.onConfirm(descEditText.getText().toString(), filename);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create().show();
    }

    private static void export_cpy(Activity activity, String desc) {
        AlertDialog waiting = DialogUtil.getWaitDialog(activity, R.string.prepare_import_export);
        waiting.show();
        
        ThreadUtil.runInBackground(() -> {
            try {
                String data = "konabess://" + getConfig(desc);
                ThreadUtil.runOnMain(() -> {
                    waiting.dismiss();
                    DialogUtil.showDetailedInfo(activity, R.string.export_done, R.string.export_done_msg, data);
                });
            } catch (Exception e) {
                ThreadUtil.runOnMain(() -> {
                    waiting.dismiss();
                    DialogUtil.showError(activity, R.string.error_occur);
                });
            }
        });
    }

    private static class exportToFile extends Thread {
        Activity activity;
        boolean error;
        String desc;
        String filename;
        AlertDialog waiting;

        public exportToFile(Activity activity, String desc, String filename) {
            this.activity = activity;
            this.desc = desc;
            this.filename = filename;
        }

        public void run() {
            ThreadUtil.runInBackground(() -> {
                error = false;
                ThreadUtil.runOnMain(() -> {
                    waiting = DialogUtil.getWaitDialog(activity, R.string.prepare_import_export);
                    waiting.show();
                });

                // Generate filename
                String finalFilename;
                if (filename != null && !filename.isEmpty()) {
                    finalFilename = filename.endsWith(".txt") ? filename : filename + ".txt";
                } else {
                    finalFilename = "konabess-" + new SimpleDateFormat("MMddHHmmss").format(new Date()) + ".txt";
                }

                String destPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + finalFilename;

                try {
                    String data = "konabess://" + getConfig(desc);
                    File tempFile = new File(activity.getFilesDir(), "temp_export.txt");
                    FileUtil.writeString(tempFile.getAbsolutePath(), data);

                    if (!RootHelper.copyFile(tempFile.getAbsolutePath(), destPath, "644")) {
                        error = true;
                    }
                    tempFile.delete();
                } catch (Exception e) {
                    error = true;
                    e.printStackTrace();
                }

                final String finalPath = destPath;
                final String savedFilename = finalFilename;
                ThreadUtil.runOnMain(() -> {
                    if (waiting != null && waiting.isShowing()) {
                        waiting.dismiss();
                    }
                    if (!error) {
                        ExportHistoryManager historyManager = new ExportHistoryManager(activity);
                        historyManager.addExport(savedFilename, desc, finalPath,
                                ChipInfo.which.getDescription(activity));

                        Toast.makeText(activity,
                                activity.getResources().getString(R.string.success_export_to) + " " + finalPath,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(activity, R.string.failed_export, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }
    }

    private static class showDecodeDialog extends Thread {
        Activity activity;
        String data;
        boolean error;
        JSONObject jsonObject;

        public showDecodeDialog(Activity activity, String data) {
            this.activity = activity;
            this.data = data;
        }

        public void run() {
            error = !data.startsWith("konabess://");
            if (!error) {
                try {
                    data = data.replace("konabess://", "");
                    String decoded_data = GzipUtils.uncompress(data);
                    jsonObject = new JSONObject(decoded_data);
                    activity.runOnUiThread(() -> {
                        waiting_import.dismiss();
                        try {
                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                                    .setTitle(R.string.going_import)
                                    .setMessage(jsonObject.getString(json_keys.DESCRIPTION) + "\n"
                                            + activity.getResources().getString(R.string.compatible_chip)
                                            + ChipInfo.type.valueOf(jsonObject.getString(json_keys.CHIP))
                                                    .getDescription(activity))
                                    .setPositiveButton(R.string.confirm, (dialog, which) -> {
                                        if (which == DialogInterface.BUTTON_POSITIVE) {
                                            dialog.dismiss();
                                            waiting_import.show();
                                            new Thread(() -> {
                                                try {
                                                    error = decodeAndWriteData(jsonObject);
                                                } catch (Exception e) {
                                                    error = true;
                                                }
                                                activity.runOnUiThread(() -> {
                                                    waiting_import.dismiss();
                                                    if (!error) {
                                                        Toast.makeText(activity,
                                                                R.string.success_import,
                                                                Toast.LENGTH_SHORT).show();
                                                        if (activity instanceof MainActivity) {
                                                            ((MainActivity) activity).notifyGpuTableChanged();
                                                        }
                                                    } else
                                                        Toast.makeText(activity,
                                                                R.string.failed_incompatible,
                                                                Toast.LENGTH_LONG).show();
                                                });
                                            }).start();
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, null)
                                    .create().show();
                        } catch (Exception e) {
                            error = true;
                        }
                    });
                } catch (Exception e) {
                    error = true;
                }
            }
            if (error)
                activity.runOnUiThread(() -> {
                    waiting_import.dismiss();
                    Toast.makeText(activity, R.string.failed_decoding, Toast.LENGTH_LONG).show();
                });
        }
    }

    // Public method for importing from data string (used by history)
    public static void importFromData(Activity activity, String data) {
        new showDecodeDialog(activity, data).start();
    }

    private static class importFromFile extends MainActivity.fileWorker {
        Activity activity;

        public importFromFile(Activity activity) {
            this.activity = activity;
        }

        public void run() {
            if (uri == null)
                return;
            activity.runOnUiThread(() -> {
                waiting_import.show();
            });
            try {
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(activity.getContentResolver().openInputStream(uri)));
                new showDecodeDialog(activity, bufferedReader.readLine()).start();
                bufferedReader.close();
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        R.string.unable_get_target_file, Toast.LENGTH_SHORT).show());
            }
        }
    }

    private static void generateView(Activity activity, LinearLayout page) {
        // Back navigation handled by OnBackPressedDispatcher in MainActivity
        // No custom callback needed - system back works correctly

        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        // Fix: Increase bottom padding to 88dp for navbar and set clipToPadding false
        int bottomPadding = (int) (activity.getResources().getDisplayMetrics().density * 88); 
        recyclerView.setPadding(0, 12, 0, bottomPadding);
        recyclerView.setClipToPadding(false);

        ArrayList<ActionCardAdapter.ActionItem> items = new ArrayList<>();
        boolean canExport = KonaBessCore.isPrepared();

        items.add(new ActionCardAdapter.ActionItem(
                R.drawable.ic_history_modern,
                activity.getResources().getString(R.string.export_history),
                activity.getResources().getString(R.string.export_history_desc),
                canExport));
        items.add(new ActionCardAdapter.ActionItem(
                R.drawable.ic_import_modern,
                activity.getResources().getString(R.string.import_from_file),
                activity.getResources().getString(R.string.import_from_file_msg),
                canExport));
        items.add(new ActionCardAdapter.ActionItem(
                R.drawable.ic_export_modern,
                activity.getResources().getString(R.string.export_to_file),
                activity.getResources().getString(R.string.export_to_file_msg),
                canExport));
        items.add(new ActionCardAdapter.ActionItem(
                R.drawable.ic_clipboard_import,
                activity.getResources().getString(R.string.import_from_clipboard),
                activity.getResources().getString(R.string.import_from_clipboard_msg),
                canExport));
        items.add(new ActionCardAdapter.ActionItem(
                R.drawable.ic_clipboard_export,
                activity.getResources().getString(R.string.export_to_clipboard),
                activity.getResources().getString(R.string.export_to_clipboard_msg),
                canExport));
        items.add(new ActionCardAdapter.ActionItem(
                R.drawable.ic_description,
                activity.getResources().getString(R.string.export_raw_dts),
                activity.getResources().getString(R.string.export_raw_dts_msg),
                canExport));
        items.add(new ActionCardAdapter.ActionItem(
                R.drawable.ic_backup,
                activity.getResources().getString(R.string.backup_image),
                activity.getResources().getString(R.string.backup_image_desc),
                canExport));

        ActionCardAdapter adapter = new ActionCardAdapter(items);
        adapter.setOnItemClickListener(new ActionCardAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                ActionCardAdapter.ActionItem selectedItem = items.get(position);
                if (!selectedItem.enabled) {
                    Toast.makeText(activity, R.string.export_requires_chipset,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (position == 0) {
                    // Open history activity
                    Intent intent = new Intent(activity, ExportHistoryActivity.class);
                    activity.startActivity(intent);
                } else if (position == 1) {
                    MainActivity.runWithFilePath(activity, new importFromFile(activity));
                } else if (position == 2) {
                    showExportDialog(activity, new ConfirmExportCallback() {
                        @Override
                        public void onConfirm(String desc, String filename) {
                            MainActivity.runWithStoragePermission(activity, new exportToFile(activity, desc, filename));
                        }
                    });
                } else if (position == 3) {
                    import_edittext(activity);
                } else if (position == 4) {
                    showExportDialog(activity, new ConfirmExportCallback() {
                        @Override
                        public void onConfirm(String desc, String filename) {
                            export_cpy(activity, desc);
                        }
                    });
                } else if (position == 5) {
                    MainActivity.runWithStoragePermission(activity, new exportRawDts(activity));
                } else if (position == 6) {
                    MainActivity mainActivity = (MainActivity) activity;

                    // Backup path is now always in internal storage root
                    String backupPath = "/sdcard/" + KonaBessCore.boot_name + ".img";

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(mainActivity)
                            .setTitle(R.string.backup_old_image)
                            .setMessage(activity.getResources().getString(R.string.will_backup_to) + " " + backupPath)
                            .setPositiveButton(R.string.ok, (dialog, which) -> {
                                if (which == DialogInterface.BUTTON_POSITIVE) {
                                    dialog.dismiss();
                                    MainActivity.runWithStoragePermission(mainActivity,
                                            mainActivity.new backupBoot(mainActivity));
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .create().show();
                }
            }
        });

        recyclerView.setAdapter(adapter);

        page.removeAllViews();
        if (!canExport) {
            TextView hintView = new TextView(activity);
            hintView.setText(R.string.export_requires_chipset);
            hintView.setPadding(0, 0, 0, 24);
            page.addView(hintView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        
        // Use weight 1.0f to fill remaining vertical space, ensuring scrolling works
        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f);
        page.addView(recyclerView, recyclerParams);
    }

    private static class exportRawDts extends Thread {
        Activity activity;
        boolean error;
        String destPath;

        public exportRawDts(Activity activity) {
            this.activity = activity;
        }

        public void run() {
            ThreadUtil.runInBackground(() -> {
                error = false;
                String timestamp = new SimpleDateFormat("MMddHHmmss").format(new Date());
                String filename = "konabess_export_" + timestamp + ".dts";
                destPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + filename;

                File srcFile = new File(KonaBessCore.dts_path);

                try {
                    // Use centralized root copy utility
                    if (!RootHelper.copyFile(srcFile.getAbsolutePath(), destPath)) {
                        // Fallback to java IO if shell fails
                        FileUtil.copyFile(srcFile, new File(destPath));
                    }
                } catch (Exception e) {
                    error = true;
                    e.printStackTrace();
                }

                ThreadUtil.runOnMain(() -> {
                    if (!error) {
                        // Add to history
                        com.ireddragonicy.konabessnext.utils.ExportHistoryManager historyManager = new com.ireddragonicy.konabessnext.utils.ExportHistoryManager(
                                activity);
                        String chipType = "Unknown";
                        if (com.ireddragonicy.konabessnext.core.ChipInfo.which != com.ireddragonicy.konabessnext.core.ChipInfo.type.unknown) {
                            chipType = com.ireddragonicy.konabessnext.core.ChipInfo.which.name();
                        }
                        historyManager.addExport(filename, "Raw DTS Export (Main Menu)", destPath, chipType);

                        Toast.makeText(activity,
                                activity.getResources().getString(R.string.success_export_to) + " " + destPath,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(activity, R.string.failed_export, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }
    }

    public static class TableIOLogic extends Thread {
        Activity activity;
        LinearLayout showedView;
        LinearLayout page;

        public TableIOLogic(Activity activity, LinearLayout showedView) {
            this.activity = activity;
            this.showedView = showedView;
        }

        public void run() {
            activity.runOnUiThread(() -> {
                if (waiting_import == null || waiting_import.getContext() != activity) {
                    if (waiting_import != null && waiting_import.isShowing()) {
                        waiting_import.dismiss();
                    }
                    waiting_import = DialogUtil.getWaitDialog(activity, R.string.wait_importing);
                }
                showedView.removeAllViews();
                page = new LinearLayout(activity);
                page.setOrientation(LinearLayout.VERTICAL);
                try {
                    generateView(activity, page);
                } catch (Exception e) {
                    DialogUtil.showError(activity, R.string.error_occur);
                }
                showedView.addView(page);
            });

        }
    }
}
