package com.ireddragonicy.konabessnext.ui.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.core.ChipInfo;
import com.ireddragonicy.konabessnext.core.GpuTableEditor;
import com.ireddragonicy.konabessnext.core.GpuVoltEditor;
import com.ireddragonicy.konabessnext.ui.MainActivity;
import com.ireddragonicy.konabessnext.ui.RawDtsEditorActivity;

public class GpuActionToolbar extends LinearLayout implements GpuTableEditor.OnHistoryStateChangedListener {

    private MaterialButton btnSave, btnUndo, btnRedo, btnHistory, btnVolt, btnDtsEditor, btnRepack;
    private View parentViewForVolt;
    private boolean showVolt = false;
    private boolean showRepack = false;

    public GpuActionToolbar(Context context) {
        super(context);
        init(context);
    }

    public GpuActionToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        if (context instanceof MainActivity) {
            showRepack = true;
            showVolt = !ChipInfo.which.ignoreVoltTable;
        }
    }

    public void setParentViewForVolt(View view) {
        this.parentViewForVolt = view;
    }

    public void build(Activity activity) {
        removeAllViews();

        int chipSpacing = (int) (activity.getResources().getDisplayMetrics().density * 8);
        int rowSpacing = (int) (activity.getResources().getDisplayMetrics().density * 12);

        // Row 1: Save, Undo, Redo, History
        LinearLayout firstRow = new LinearLayout(activity);
        firstRow.setOrientation(HORIZONTAL);
        firstRow.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        btnSave = createMaterialButton(activity, "Save", R.drawable.ic_save);
        btnSave.setOnClickListener(v -> GpuTableEditor.saveFrequencyTable(activity, true, "Saved manually"));

        btnUndo = createMaterialButton(activity, null, R.drawable.ic_undo);
        btnUndo.setOnClickListener(v -> GpuTableEditor.handleUndo());

        btnRedo = createMaterialButton(activity, null, R.drawable.ic_redo);
        btnRedo.setOnClickListener(v -> GpuTableEditor.handleRedo());

        btnHistory = createMaterialButton(activity, null, R.drawable.ic_history);
        btnHistory.setOnClickListener(v -> GpuTableEditor.showHistoryDialog(activity));

        // Layout Params Configuration
        LinearLayout.LayoutParams mainActionParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        mainActionParams.setMarginEnd(chipSpacing);
        btnSave.setLayoutParams(mainActionParams);

        LinearLayout.LayoutParams iconActionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        iconActionParams.setMarginEnd(chipSpacing);
        btnUndo.setLayoutParams(iconActionParams);
        btnRedo.setLayoutParams(iconActionParams);

        LinearLayout.LayoutParams lastActionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        btnHistory.setLayoutParams(lastActionParams);

        firstRow.addView(btnSave);
        firstRow.addView(btnUndo);
        firstRow.addView(btnRedo);
        firstRow.addView(btnHistory);

        GpuTableEditor.registerToolbarButtons(btnSave, btnUndo, btnRedo, btnHistory);
        addView(firstRow);

        // Row 2: DTS Editor, Volt, Repack
        if (showRepack && activity instanceof MainActivity) {
            LinearLayout secondRow = new LinearLayout(activity);
            secondRow.setOrientation(HORIZONTAL);
            LinearLayout.LayoutParams secondRowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            secondRowParams.topMargin = rowSpacing;
            secondRow.setLayoutParams(secondRowParams);

            btnDtsEditor = createMaterialButton(activity, null, R.drawable.ic_code);
            btnDtsEditor.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.raw_dts_editor_warning_title)
                    .setMessage(R.string.raw_dts_editor_warning_msg)
                    .setPositiveButton(R.string.confirm, (dialog, which) -> activity.startActivity(new Intent(activity, RawDtsEditorActivity.class)))
                    .setNegativeButton(R.string.cancel, null)
                    .show());

            LinearLayout.LayoutParams dtsParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            dtsParams.setMarginEnd(chipSpacing);
            btnDtsEditor.setLayoutParams(dtsParams);
            secondRow.addView(btnDtsEditor);

            if (showVolt) {
                btnVolt = createMaterialButton(activity, "Volt", R.drawable.ic_voltage);
                btnVolt.setText(R.string.edit_gpu_volt_table);
                btnVolt.setOnClickListener(v -> {
                    if (parentViewForVolt != null && activity instanceof MainActivity) {
                        new GpuVoltEditor.gpuVoltLogic((MainActivity) activity, (LinearLayout) parentViewForVolt).start();
                    } else {
                        Toast.makeText(activity, "Error: Parent view not set for Voltage Editor", Toast.LENGTH_SHORT).show();
                    }
                });

                LinearLayout.LayoutParams voltParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
                voltParams.setMarginEnd(chipSpacing);
                btnVolt.setLayoutParams(voltParams);
                secondRow.addView(btnVolt);
            }

            btnRepack = createMaterialButton(activity, "Repack & Flash", R.drawable.ic_flash);
            btnRepack.setOnClickListener(v -> {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).startRepack();
                }
            });

            LinearLayout.LayoutParams repackParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
            btnRepack.setLayoutParams(repackParams);
            secondRow.addView(btnRepack);

            addView(secondRow);
        }
    }

    private MaterialButton createMaterialButton(Context context, String text, int iconResId) {
        MaterialButton button = new MaterialButton(context);
        
        button.setText(text != null ? text : "");
        if (iconResId != 0) {
            button.setIconResource(iconResId);
            button.setIconSize((int) (context.getResources().getDisplayMetrics().density * 20));
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setIconPadding(text != null && !text.isEmpty() ? 
                (int) (context.getResources().getDisplayMetrics().density * 8) : 0);
        }

        // Tonal Button Style
        int bg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorSecondaryContainer);
        int fg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSecondaryContainer);

        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setTextColor(fg);
        button.setIconTint(ColorStateList.valueOf(fg));
        button.setRippleColor(ColorStateList.valueOf(MaterialColors.getColor(button, com.google.android.material.R.attr.colorSecondary)));

        button.setCornerRadius((int) (context.getResources().getDisplayMetrics().density * 24));
        int hPad = (int) (context.getResources().getDisplayMetrics().density * (text == null || text.isEmpty() ? 12 : 16));
        int vPad = (int) (context.getResources().getDisplayMetrics().density * 12);
        button.setPadding(hPad, vPad, hPad, vPad);
        button.setInsetTop(0);
        button.setInsetBottom(0);

        return button;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        GpuTableEditor.addHistoryListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        GpuTableEditor.removeHistoryListener(this);
    }

    @Override
    public void onHistoryStateChanged(boolean canUndo, boolean canRedo) {
        if (getContext() instanceof Activity) {
            ((Activity) getContext()).runOnUiThread(() -> {
                updateButtonState(btnUndo, canUndo);
                updateButtonState(btnRedo, canRedo);
            });
        }
    }

    private void updateButtonState(MaterialButton btn, boolean enabled) {
        if (btn != null) {
            btn.setEnabled(enabled);
            btn.setAlpha(enabled ? 1f : 0.5f);
        }
    }
}
