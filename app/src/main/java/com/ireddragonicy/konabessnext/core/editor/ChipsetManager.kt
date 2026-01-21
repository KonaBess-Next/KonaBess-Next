package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.KonaBessCore
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.ui.adapters.ChipsetSelectorAdapter
import com.ireddragonicy.konabessnext.utils.DialogUtil
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages chipset switching and session persistence across chipsets.
 */
object ChipsetManager {

    interface OnChipsetSwitchedListener {
        fun onChipsetSwitched(dtb: Dtb)
    }

    /**
     * Create the chipset selector card for multi-DTB devices.
     */
    @JvmStatic
    fun createChipsetSelectorCard(
        activity: Activity, page: LinearLayout,
        chipsetNameView: TextView,
        listener: OnChipsetSwitchedListener
    ): View {
        val density = activity.resources.displayMetrics.density
        val padding = (density * 16).toInt()

        // Main card container
        val card = MaterialCardView(activity)
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(padding, padding, padding, (density * 8).toInt())
        card.layoutParams = cardParams
        card.cardElevation = density * 2
        card.radius = density * 12

        // Inner layout
        val innerLayout = LinearLayout(activity)
        innerLayout.orientation = LinearLayout.VERTICAL
        innerLayout.setPadding(padding, padding, padding, padding)

        // Title
        val titleView = TextView(activity)
        titleView.text = "Target Chipset"
        titleView.textSize = 12f
        titleView.alpha = 0.6f
        titleView.setPadding(0, 0, 0, (density * 8).toInt())
        innerLayout.addView(titleView)

        // Current chipset display with click to change
        val chipsetRow = LinearLayout(activity)
        chipsetRow.orientation = LinearLayout.HORIZONTAL
        chipsetRow.gravity = android.view.Gravity.CENTER_VERTICAL
        chipsetRow.isClickable = true
        chipsetRow.isFocusable = true

        // Set ripple effect
        val typedArray = activity.theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.selectableItemBackground)
        )
        val selectableItemBackground = typedArray.getResourceId(0, 0)
        typedArray.recycle()
        chipsetRow.setBackgroundResource(selectableItemBackground)
        chipsetRow.setPadding(
            (density * 12).toInt(), (density * 12).toInt(),
            (density * 12).toInt(), (density * 12).toInt()
        )

        // Chipset icon
        val chipIcon = ImageView(activity)
        chipIcon.setImageResource(R.drawable.ic_developer_board)
        val iconSize = (density * 24).toInt()
        val iconParams = LinearLayout.LayoutParams(iconSize, iconSize)
        iconParams.marginEnd = (density * 12).toInt()
        chipIcon.layoutParams = iconParams
        chipIcon.setColorFilter(
            MaterialColors.getColor(
                chipIcon,
                com.google.android.material.R.attr.colorOnSurface
            )
        )
        chipsetRow.addView(chipIcon)

        // Chipset name
        val currentDtb = KonaBessCore.currentDtb
        if (currentDtb != null) {
            chipsetNameView.text = "${currentDtb.id} ${currentDtb.type.name}"
        } else {
            chipsetNameView.text = "Unknown"
        }
        chipsetNameView.textSize = 16f
        val nameParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )
        chipsetNameView.layoutParams = nameParams
        chipsetRow.addView(chipsetNameView)

        // Change icon
        val changeIcon = ImageView(activity)
        changeIcon.setImageResource(R.drawable.ic_tune)
        val changeIconParams = LinearLayout.LayoutParams(iconSize, iconSize)
        changeIcon.layoutParams = changeIconParams
        changeIcon.alpha = 0.7f
        changeIcon.setColorFilter(
            MaterialColors.getColor(
                changeIcon,
                com.google.android.material.R.attr.colorOnSurfaceVariant
            )
        )
        chipsetRow.addView(changeIcon)

        // Click listener
        chipsetRow.setOnClickListener {
            showChipsetSelectorDialog(activity, page, chipsetNameView, listener)
        }

        innerLayout.addView(chipsetRow)
        card.addView(innerLayout)

        return card
    }

    /**
     * Show chipset selector dialog.
     */
    @JvmStatic
    fun showChipsetSelectorDialog(
        activity: Activity, page: LinearLayout,
        chipsetNameView: TextView,
        listener: OnChipsetSwitchedListener
    ) {
        val lifecycleOwner = activity as? androidx.activity.ComponentActivity
            ?: return // Safety check, should be ComponentActivity

        if (KonaBessCore.dtbs == null) {
            val waiting = DialogUtil.getWaitDialog(activity, R.string.processing)
            waiting.show()
            
            lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    KonaBessCore.checkDevice(activity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                withContext(Dispatchers.Main) {
                    waiting.dismiss()
                    showChipsetSelectorDialog(activity, page, chipsetNameView, listener)
                }
            }
            return
        }

        if (KonaBessCore.dtbs!!.isEmpty()) {
            Toast.makeText(activity, "No chipsets available", Toast.LENGTH_SHORT).show()
            return
        }

        val currentDtb = KonaBessCore.currentDtb
        
        // Find recommended index (first known chipset)
        val recommendedIndex = if (KonaBessCore.dtbs!!.isNotEmpty()) 0 else -1

        // Inflate custom dialog layout
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_chipset_selector, null)

        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.chipset_list)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setHasFixedSize(true)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()

        val adapter = ChipsetSelectorAdapter(
            KonaBessCore.dtbs!!,
            activity,
            recommendedIndex,
            currentDtb?.id,
            object : ChipsetSelectorAdapter.OnChipsetSelectedListener {
                override fun onChipsetSelected(dtb: Dtb) {
                    if (currentDtb != null && dtb.id != currentDtb.id) {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle(activity.getString(R.string.switch_chipset_title))
                            .setMessage(activity.getString(R.string.switch_chipset_msg))
                            .setPositiveButton(activity.getString(R.string.yes)) { _, _ ->
                                dialog.dismiss()
                                switchChipset(activity, page, dtb, chipsetNameView, listener)
                            }
                            .setNegativeButton(activity.getString(R.string.no), null)
                            .create().show()
                    } else {
                        dialog.dismiss()
                    }
                }
            })

        recyclerView.adapter = adapter

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)
        dialog.show()
    }

    /**
     * Switch to a different chipset.
     */
    @JvmStatic
    fun switchChipset(
        activity: Activity, page: LinearLayout,
        newDtb: Dtb, chipsetNameView: TextView,
        listener: OnChipsetSwitchedListener
    ) {
         // UI Update first
         chipsetNameView.text = "${newDtb.id} ${newDtb.type.name}"
         // Notify listener to handle logic (save, load, refresh)
         listener.onChipsetSwitched(newDtb)
    }
}

