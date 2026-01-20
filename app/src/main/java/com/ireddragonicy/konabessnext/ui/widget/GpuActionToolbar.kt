package com.ireddragonicy.konabessnext.ui.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.core.GpuTableEditor
import com.ireddragonicy.konabessnext.core.GpuVoltEditor
import com.ireddragonicy.konabessnext.core.editor.EditorStateManager
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.RawDtsEditorActivity

class GpuActionToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs), EditorStateManager.OnHistoryStateChangedListener {

    private var btnSave: MaterialButton? = null
    private var btnUndo: MaterialButton? = null
    private var btnRedo: MaterialButton? = null
    private var btnHistory: MaterialButton? = null
    private var btnVolt: MaterialButton? = null
    private var btnDtsEditor: MaterialButton? = null
    private var btnRepack: MaterialButton? = null
    private var parentViewForVolt: View? = null
    private var showVolt = false
    private var showRepack = false

    init {
        init(context)
    }

    private fun init(context: Context) {
        orientation = VERTICAL
        if (context is MainActivity) {
            showRepack = true
            showVolt = ChipInfo.which != null && !ChipInfo.which!!.ignoreVoltTable
        }
    }

    fun setParentViewForVolt(view: View?) {
        this.parentViewForVolt = view
    }

    fun build(activity: Activity) {
        removeAllViews()

        val density = activity.resources.displayMetrics.density
        val chipSpacing = (density * 8).toInt()
        val rowSpacing = (density * 12).toInt()

        // Row 1: Save, Undo, Redo, History
        val firstRow = LinearLayout(activity)
        firstRow.orientation = HORIZONTAL
        firstRow.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        btnSave = createMaterialButton(activity, "Save", R.drawable.ic_save)
        btnSave!!.setOnClickListener {
            GpuTableEditor.saveFrequencyTable(
                activity,
                true,
                "Saved manually"
            )
        }

        btnUndo = createMaterialButton(activity, null, R.drawable.ic_undo)
        btnUndo!!.setOnClickListener { GpuTableEditor.handleUndo() }

        btnRedo = createMaterialButton(activity, null, R.drawable.ic_redo)
        btnRedo!!.setOnClickListener { GpuTableEditor.handleRedo() }

        btnHistory = createMaterialButton(activity, null, R.drawable.ic_history)
        btnHistory!!.setOnClickListener { GpuTableEditor.showHistoryDialog(activity) }

        // Layout Params Configuration
        val mainActionParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
        mainActionParams.marginEnd = chipSpacing
        btnSave!!.layoutParams = mainActionParams

        val iconActionParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        iconActionParams.marginEnd = chipSpacing
        btnUndo!!.layoutParams = iconActionParams
        btnRedo!!.layoutParams = iconActionParams

        val lastActionParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        btnHistory!!.layoutParams = lastActionParams

        firstRow.addView(btnSave)
        firstRow.addView(btnUndo)
        firstRow.addView(btnRedo)
        firstRow.addView(btnHistory)

        GpuTableEditor.registerToolbarButtons(btnSave, btnUndo, btnRedo, btnHistory)
        addView(firstRow)

        // Row 2: DTS Editor, Curve Editor, Volt, Repack
        if (showRepack && activity is MainActivity) {
            val secondRow = LinearLayout(activity)
            secondRow.orientation = HORIZONTAL
            val secondRowParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            secondRowParams.topMargin = rowSpacing
            secondRow.layoutParams = secondRowParams

            btnDtsEditor = createMaterialButton(activity, null, R.drawable.ic_code)
            btnDtsEditor!!.setOnClickListener {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.raw_dts_editor_warning_title)
                    .setMessage(R.string.raw_dts_editor_warning_msg)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        activity.startActivity(
                            Intent(
                                activity,
                                RawDtsEditorActivity::class.java
                            )
                        )
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }

            val dtsParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            dtsParams.marginEnd = chipSpacing
            btnDtsEditor!!.layoutParams = dtsParams
            secondRow.addView(btnDtsEditor)

            // Curve Editor button
            val btnCurveEditor = createMaterialButton(activity, null, R.drawable.ic_frequency)
            btnCurveEditor.setOnClickListener {
                // Get the current bin index from GpuTableEditor
                val binIndex = GpuTableEditor.getSelectedBinIndex()
                if (binIndex != null && binIndex >= 0) {
                    (activity as MainActivity).openCurveEditor(binIndex)
                } else {
                    Toast.makeText(activity, R.string.select_bin_first, Toast.LENGTH_SHORT).show()
                }
            }

            val curveParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            curveParams.marginEnd = chipSpacing
            btnCurveEditor.layoutParams = curveParams
            secondRow.addView(btnCurveEditor)

            if (showVolt) {
                btnVolt = createMaterialButton(activity, "Volt", R.drawable.ic_voltage)
                btnVolt!!.setText(R.string.edit_gpu_volt_table)
                btnVolt!!.setOnClickListener {
                    if (parentViewForVolt != null) {
                        GpuVoltEditor.gpuVoltLogic(activity as MainActivity, parentViewForVolt as LinearLayout).start()
                    } else {
                        Toast.makeText(
                            activity,
                            "Error: Parent view not set for Voltage Editor",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val voltParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
                voltParams.marginEnd = chipSpacing
                btnVolt!!.layoutParams = voltParams
                secondRow.addView(btnVolt)
            }

            btnRepack = createMaterialButton(activity, "Flash", R.drawable.ic_flash)
            btnRepack!!.setOnClickListener {
                (activity as MainActivity).startRepack()
            }

            val repackParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
            btnRepack!!.layoutParams = repackParams
            secondRow.addView(btnRepack)

            addView(secondRow)
        }
    }

    private fun createMaterialButton(context: Context, text: String?, iconResId: Int): MaterialButton {
        val button = MaterialButton(context)

        button.text = text ?: ""
        if (iconResId != 0) {
            button.setIconResource(iconResId)
            button.iconSize = (context.resources.displayMetrics.density * 20).toInt()
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            button.iconPadding =
                if (text != null && text.isNotEmpty()) (context.resources.displayMetrics.density * 8).toInt() else 0
        }

        // Tonal Button Style
        val bg =
            MaterialColors.getColor(button, com.google.android.material.R.attr.colorSecondaryContainer)
        val fg =
            MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSecondaryContainer)

        button.backgroundTintList = ColorStateList.valueOf(bg)
        button.setTextColor(fg)
        button.iconTint = ColorStateList.valueOf(fg)
        button.rippleColor = ColorStateList.valueOf(
            MaterialColors.getColor(
                button,
                com.google.android.material.R.attr.colorSecondary
            )
        )

        button.cornerRadius = (context.resources.displayMetrics.density * 24).toInt()
        val hPad =
            (context.resources.displayMetrics.density * if (text.isNullOrEmpty()) 12 else 16).toInt()
        val vPad = (context.resources.displayMetrics.density * 12).toInt()
        button.setPadding(hPad, vPad, hPad, vPad)
        button.insetTop = 0
        button.insetBottom = 0

        return button
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        GpuTableEditor.addHistoryListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        GpuTableEditor.removeHistoryListener(this)
    }

    override fun onHistoryStateChanged(canUndo: Boolean, canRedo: Boolean) {
        if (context is Activity) {
            (context as Activity).runOnUiThread {
                updateButtonState(btnUndo, canUndo)
                updateButtonState(btnRedo, canRedo)
            }
        }
    }

    private fun updateButtonState(btn: MaterialButton?, enabled: Boolean) {
        if (btn != null) {
            btn.isEnabled = enabled
            btn.alpha = if (enabled) 1f else 0.5f
        }
    }
}
