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
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import com.ireddragonicy.konabessnext.viewmodel.GpuFrequencyViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.core.editor.ChipsetManager
import com.ireddragonicy.konabessnext.core.KonaBessCore

import com.google.android.material.button.MaterialButtonToggleGroup

class GpuActionToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var btnSave: MaterialButton? = null
    private var btnUndo: MaterialButton? = null
    private var btnRedo: MaterialButton? = null
    private var btnHistory: MaterialButton? = null
    
    // Toggle Group for View Modes
    private var viewModeToggleGroup: MaterialButtonToggleGroup? = null
    private var btnModeGui: MaterialButton? = null
    private var btnModeText: MaterialButton? = null
    private var btnModeVisual: MaterialButton? = null
    
    private var btnFlash: MaterialButton? = null
    private var btnChipset: MaterialButton? = null
    
    private var parentViewForVolt: View? = null
    private var showVolt = false
    private var showRepack = false
    
    private var onModeSelectedListener: ((SharedGpuViewModel.ViewMode) -> Unit)? = null
    private var chipsetListener: ChipsetManager.OnChipsetSwitchedListener? = null

    init {
        init(context)
    }
    
    fun setOnModeSelectedListener(listener: (SharedGpuViewModel.ViewMode) -> Unit) {
        this.onModeSelectedListener = listener
    }

    private fun init(context: Context) {
        orientation = VERTICAL
        if (context is MainActivity) {
            showRepack = true
            showVolt = ChipInfo.current != null && !ChipInfo.current!!.ignoreVoltTable
        }
    }

    fun setParentViewForVolt(view: View?) {
        this.parentViewForVolt = view
    }

    fun build(activity: Activity, viewModel: GpuFrequencyViewModel, lifecycleOwner: LifecycleOwner) {
        removeAllViews()

        val density = activity.resources.displayMetrics.density
        val chipSpacing = (density * 8).toInt()
        val rowSpacing = (density * 12).toInt()

        // Row 1: Save, Undo, Redo, History (Standard Actions)
        val firstRow = LinearLayout(activity)
        firstRow.orientation = HORIZONTAL
        val firstRowParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        firstRow.layoutParams = firstRowParams

        // Build Row 1 contents
        btnSave = createMaterialButton(activity, "Save", R.drawable.ic_save)
        btnSave!!.setOnClickListener {
            viewModel.save(true)
        }

        btnUndo = createMaterialButton(activity, null, R.drawable.ic_undo)
        btnUndo!!.setOnClickListener { viewModel.undo() }

        btnRedo = createMaterialButton(activity, null, R.drawable.ic_redo)
        btnRedo!!.setOnClickListener { viewModel.redo() }

        btnHistory = createMaterialButton(activity, null, R.drawable.ic_history)
        btnHistory!!.setOnClickListener { 
            val history = viewModel.history.value
            MaterialAlertDialogBuilder(activity)
                .setTitle("History")
                .setItems(history.toTypedArray(), null)
                .setPositiveButton("OK", null)
                .show()
        }

        // Layout Params
        val mainActionParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
        mainActionParams.marginEnd = chipSpacing
        btnSave!!.layoutParams = mainActionParams

        val iconActionParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        iconActionParams.marginEnd = chipSpacing
        btnUndo!!.layoutParams = iconActionParams
        btnRedo!!.layoutParams = iconActionParams
        btnHistory!!.layoutParams = iconActionParams

        firstRow.addView(btnSave)
        firstRow.addView(btnUndo)
        firstRow.addView(btnRedo)
        firstRow.addView(btnHistory)

        // Observe ViewModel state
        viewModel.isDirtyLiveData.observe(lifecycleOwner) { isDirty ->
             // Update Save button color/state if needed
             // btnSave?.isEnabled = isDirty // Or keep enabled to force save?
             // Legacy behavior: button changes color. 
             // We can implement color change logic here if needed.
             updateSaveButtonColor(btnSave, isDirty)
        }

        viewModel.canUndoLiveData.observe(lifecycleOwner) { canUndo ->
             updateButtonState(btnUndo, canUndo)
        }

        viewModel.canRedoLiveData.observe(lifecycleOwner) { canRedo ->
             updateButtonState(btnRedo, canRedo)
        }
        
        // Add Row 1 First (As requested)
        addView(firstRow)

        // Row 2: View Mode Switcher + Flash
        if (showRepack && activity is MainActivity) {
            val secondRow = LinearLayout(activity)
            secondRow.orientation = HORIZONTAL
            val secondRowParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            secondRowParams.topMargin = rowSpacing
            secondRow.layoutParams = secondRowParams
            
            // Toggle Group
            viewModeToggleGroup = MaterialButtonToggleGroup(activity, null, com.google.android.material.R.attr.materialButtonToggleGroupStyle)
            viewModeToggleGroup!!.isSingleSelection = true
            viewModeToggleGroup!!.isSelectionRequired = true
            
            btnModeGui = createOutlinedButton(activity, "GUI", R.drawable.ic_edit)
            btnModeText = createOutlinedButton(activity, "Text", R.drawable.ic_code)
            btnModeVisual = createOutlinedButton(activity, "Tree", R.drawable.ic_developer_board) 
            
            btnModeGui!!.id = View.generateViewId()
            btnModeText!!.id = View.generateViewId()
            btnModeVisual!!.id = View.generateViewId()
            
            viewModeToggleGroup!!.addView(btnModeGui)
            viewModeToggleGroup!!.addView(btnModeText)
            viewModeToggleGroup!!.addView(btnModeVisual)
            
            viewModeToggleGroup!!.check(btnModeGui!!.id)
            
            viewModeToggleGroup!!.addOnButtonCheckedListener { group, checkedId, isChecked ->
                if (isChecked) {
                    when (checkedId) {
                        btnModeGui!!.id -> onModeSelectedListener?.invoke(SharedGpuViewModel.ViewMode.MAIN_EDITOR)
                        btnModeText!!.id -> onModeSelectedListener?.invoke(SharedGpuViewModel.ViewMode.TEXT_ADVANCED)
                        btnModeVisual!!.id -> onModeSelectedListener?.invoke(SharedGpuViewModel.ViewMode.VISUAL_TREE)
                    }
                }
            }
            
            val toggleParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
            toggleParams.marginEnd = chipSpacing
            viewModeToggleGroup!!.layoutParams = toggleParams
            
            secondRow.addView(viewModeToggleGroup)
            // Chipset Selector
            // Show button if we have a listener. Data loading is handled lazily by ChipsetManager.
            if (chipsetListener != null) {
                // User requested icon-only button
                btnChipset = createMaterialButton(activity, null, R.drawable.ic_developer_board)
                btnChipset!!.setOnClickListener {
                     if (parentViewForVolt is LinearLayout) {
                         // Create a dummy text view to pass to manager (manager expects one to update text)
                         // We just ignore the text updates since the button is icon-only.
                         val dummyTextView = android.widget.TextView(activity)
                         
                         chipsetListener?.let { listener ->
                             ChipsetManager.showChipsetSelectorDialog(
                                 activity, 
                                 parentViewForVolt as LinearLayout, 
                                 dummyTextView, 
                                 listener
                             )
                         }
                     }
                }
                
                val chipsetParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                chipsetParams.marginEnd = chipSpacing
                btnChipset!!.layoutParams = chipsetParams
                secondRow.addView(btnChipset)
            }
            
            // Flash Button
            btnFlash = createMaterialButton(activity, "Flash", R.drawable.ic_flash)
            btnFlash!!.setOnClickListener {
                (activity as MainActivity).startRepack()
            }
            btnFlash!!.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            
            secondRow.addView(btnFlash)
            addView(secondRow)
        }
    }

    fun setChipsetListener(listener: ChipsetManager.OnChipsetSwitchedListener?) {
        this.chipsetListener = listener
    }
    
    private fun createOutlinedButton(context: Context, text: String, iconResId: Int): MaterialButton {
        val button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        button.text = text
        button.setIconResource(iconResId)
        button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        button.setPadding(
            (context.resources.displayMetrics.density * 12).toInt(), 
            0, 
            (context.resources.displayMetrics.density * 12).toInt(), 
            0
        )
        button.minWidth = 0
        button.minHeight = (context.resources.displayMetrics.density * 48).toInt()
        return button
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
        // GpuTableEditor.addHistoryListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // GpuTableEditor.removeHistoryListener(this)
    }

    /*
    override fun onHistoryStateChanged(canUndo: Boolean, canRedo: Boolean) {
       // Removed
    }
    */

    private fun updateButtonState(btn: MaterialButton?, enabled: Boolean) {
        if (btn != null) {
            btn.isEnabled = enabled
            btn.alpha = if (enabled) 1f else 0.5f
        }
    }

    private fun updateSaveButtonColor(btn: MaterialButton?, isDirty: Boolean) {
        if (btn == null) return
        val backgroundAttr = if (isDirty)
            com.google.android.material.R.attr.colorErrorContainer
        else
            com.google.android.material.R.attr.colorSecondaryContainer
        val foregroundAttr = if (isDirty)
            com.google.android.material.R.attr.colorOnErrorContainer
        else
            com.google.android.material.R.attr.colorOnSecondaryContainer
            
        val background = MaterialColors.getColor(btn, backgroundAttr)
        val foreground = MaterialColors.getColor(btn, foregroundAttr)
        
        btn.backgroundTintList = ColorStateList.valueOf(background)
        btn.setTextColor(foreground)
        btn.iconTint = ColorStateList.valueOf(foreground)
    }
}
