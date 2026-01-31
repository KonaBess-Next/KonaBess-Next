package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.KonaBessCore
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.adapters.ChipsetSelectorAdapter
import com.ireddragonicy.konabessnext.ui.compose.ManualChipsetSetupScreen
import com.ireddragonicy.konabessnext.utils.DialogUtil
import com.ireddragonicy.konabessnext.viewmodel.DeviceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ChipsetManager {

    interface OnChipsetSwitchedListener {
        fun onChipsetSwitched(dtb: Dtb)
    }

    @JvmStatic
    fun createChipsetSelectorCard(
        activity: Activity, page: LinearLayout,
        chipsetNameView: TextView,
        listener: OnChipsetSwitchedListener
    ): View {
        val density = activity.resources.displayMetrics.density
        val padding = (density * 16).toInt()

        val card = MaterialCardView(activity)
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(padding, padding, padding, (density * 8).toInt())
        card.layoutParams = cardParams
        card.cardElevation = density * 2
        card.radius = density * 12

        val innerLayout = LinearLayout(activity)
        innerLayout.orientation = LinearLayout.VERTICAL
        innerLayout.setPadding(padding, padding, padding, padding)

        val titleView = TextView(activity)
        titleView.text = "Target Chipset"
        titleView.textSize = 12f
        titleView.alpha = 0.6f
        titleView.setPadding(0, 0, 0, (density * 8).toInt())
        innerLayout.addView(titleView)

        val chipsetRow = LinearLayout(activity)
        chipsetRow.orientation = LinearLayout.HORIZONTAL
        chipsetRow.gravity = android.view.Gravity.CENTER_VERTICAL
        chipsetRow.isClickable = true
        chipsetRow.isFocusable = true

        val typedArray = activity.theme.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val selectableItemBackground = typedArray.getResourceId(0, 0)
        typedArray.recycle()
        chipsetRow.setBackgroundResource(selectableItemBackground)
        chipsetRow.setPadding(
            (density * 12).toInt(), (density * 12).toInt(),
            (density * 12).toInt(), (density * 12).toInt()
        )

        val chipIcon = ImageView(activity)
        chipIcon.setImageResource(R.drawable.ic_developer_board)
        val iconSize = (density * 24).toInt()
        val iconParams = LinearLayout.LayoutParams(iconSize, iconSize)
        iconParams.marginEnd = (density * 12).toInt()
        chipIcon.layoutParams = iconParams
        chipIcon.setColorFilter(MaterialColors.getColor(chipIcon, com.google.android.material.R.attr.colorOnSurface))
        chipsetRow.addView(chipIcon)

        val currentDtb = KonaBessCore.currentDtb
        if (currentDtb != null) {
            chipsetNameView.text = "${currentDtb.id} ${currentDtb.type.name}"
        } else {
            chipsetNameView.text = "Unknown"
        }
        chipsetNameView.textSize = 16f
        val nameParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        chipsetNameView.layoutParams = nameParams
        chipsetRow.addView(chipsetNameView)

        val changeIcon = ImageView(activity)
        changeIcon.setImageResource(R.drawable.ic_tune)
        val changeIconParams = LinearLayout.LayoutParams(iconSize, iconSize)
        changeIcon.layoutParams = changeIconParams
        changeIcon.alpha = 0.7f
        changeIcon.setColorFilter(MaterialColors.getColor(changeIcon, com.google.android.material.R.attr.colorOnSurfaceVariant))
        chipsetRow.addView(changeIcon)

        chipsetRow.setOnClickListener {
            // NOTE: In legacy card use, active ID is hard to get synchronously from here without viewModel.
            // We pass -1 or try to get it if activity is MainActivity.
            var activeId = -1
            if (activity is MainActivity) {
                activeId = activity.deviceViewModel.activeDtbId.value
            }
            showChipsetSelectorDialog(activity, page, chipsetNameView, activeId, listener)
        }

        innerLayout.addView(chipsetRow)
        card.addView(innerLayout)

        return card
    }

    @JvmStatic
    fun showChipsetSelectorDialog(
        activity: Activity, page: LinearLayout,
        chipsetNameView: TextView,
        activeDtbId: Int, // Explicit pass
        listener: OnChipsetSwitchedListener
    ) {
        val lifecycleOwner = activity as? ComponentActivity ?: return

        if (KonaBessCore.dtbs == null) {
            val waiting = DialogUtil.getWaitDialog(activity, R.string.processing)
            waiting.show()
            lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try { KonaBessCore.checkDevice(activity) } catch (e: Exception) { e.printStackTrace() }
                withContext(Dispatchers.Main) {
                    waiting.dismiss()
                    showChipsetSelectorDialog(activity, page, chipsetNameView, activeDtbId, listener)
                }
            }
            return
        }

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_chipset_selector, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.chipset_list)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        
        val list = KonaBessCore.dtbs ?: emptyList()
        
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()

        val adapter = ChipsetSelectorAdapter(
            list,
            activity,
            activeDtbId,
            KonaBessCore.currentDtb?.id,
            object : ChipsetSelectorAdapter.OnChipsetSelectedListener {
                override fun onChipsetSelected(dtb: Dtb) {
                    dialog.dismiss()
                    if (dtb.id == ChipsetSelectorAdapter.MANUAL_SETUP_ID) {
                        showManualSetupDialog(activity, chipsetNameView, listener)
                    } else {
                        switchChipset(activity, page, dtb, chipsetNameView, listener)
                    }
                }
            })

        recyclerView.adapter = adapter
        dialog.show()
    }

    private fun showManualSetupDialog(
        activity: Activity,
        chipsetNameView: TextView,
        listener: OnChipsetSwitchedListener
    ) {
        if (activity !is MainActivity) {
            Toast.makeText(activity, "Error: Manual setup requires MainActivity context", Toast.LENGTH_SHORT).show()
            return
        }
        
        val deviceViewModel = activity.deviceViewModel
        
        val composeDialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        composeDialog.setContentView(ComposeView(activity).apply {
            setContent {
                com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
                    ManualChipsetSetupScreen(
                        dtbIndex = 0,
                        onDeepScan = { deviceViewModel.performManualScan(0) },
                        onSave = { def -> 
                            deviceViewModel.saveManualDefinition(def, 0)
                            chipsetNameView.text = "${def.id} ${def.name}"
                            listener.onChipsetSwitched(Dtb(0, def))
                            composeDialog.dismiss()
                        },
                        onCancel = { composeDialog.dismiss() }
                    )
                }
            }
        })
        composeDialog.show()
    }

    fun switchChipset(
        activity: Activity, page: LinearLayout,
        newDtb: Dtb, chipsetNameView: TextView,
        listener: OnChipsetSwitchedListener
    ) {
         chipsetNameView.text = "${newDtb.id} ${newDtb.type.name}"
         listener.onChipsetSwitched(newDtb)
    }
}