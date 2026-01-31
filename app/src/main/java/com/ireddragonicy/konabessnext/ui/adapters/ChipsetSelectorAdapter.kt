package com.ireddragonicy.konabessnext.ui.adapters

import android.app.Activity
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.color.MaterialColors
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.Dtb

class ChipsetSelectorAdapter(
    private val dtbList: List<Dtb>,
    private val activity: Activity,
    private val activeDtbId: Int, // Changed name for clarity, was recommendedIndex
    private val currentlySelectedId: Int?,
    private val listener: OnChipsetSelectedListener?
) : RecyclerView.Adapter<ChipsetSelectorAdapter.ViewHolder>() {

    constructor(
        dtbList: List<Dtb>,
        activity: Activity,
        activeDtbId: Int,
        listener: OnChipsetSelectedListener?
    ) : this(dtbList, activity, activeDtbId, null, listener)

    fun interface OnChipsetSelectedListener {
        fun onChipsetSelected(dtb: Dtb)
    }

    companion object {
        const val MANUAL_SETUP_ID = -999
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == dtbList.size) 1 else 0 // 1 for manual setup, 0 for item
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chipset_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (getItemViewType(position) == 1) {
            // Manual Setup Item
            holder.chipsetName.text = "Manual Setup / Deep Scan"
            holder.chipsetSubtitle.text = "Configure unsupported device manually"
            holder.chipsetIcon.setImageResource(R.drawable.ic_search) 
            holder.recommendedBadge.visibility = View.GONE
            holder.selectedBadge.visibility = View.GONE
            
            holder.cardView.setOnClickListener {
                listener?.onChipsetSelected(Dtb(MANUAL_SETUP_ID, com.ireddragonicy.konabessnext.model.ChipDefinition("manual", "Manual", 0, false, 0, null, "", 0, mapOf()))) 
            }
        } else {
            val dtb = dtbList[position]

            // Set chipset name
            val chipsetName = "${dtb.id} ${dtb.type.name}"
            holder.chipsetName.text = chipsetName

            // Set subtitle
            holder.chipsetSubtitle.text = "DTB Index: ${dtb.id}"

            // Show ACTIVE badge (System running)
            if (dtb.id == activeDtbId) {
                holder.recommendedBadge.visibility = View.VISIBLE
                holder.recommendedBadge.text = "ACTIVE"
                holder.recommendedBadge.setChipIconResource(R.drawable.ic_check) // Or power icon
                // Style as Green/Success
                val colorGreen = activity.getColor(android.R.color.holo_green_dark)
                // We'd ideally use dynamic colors, but for distinct "Active", green is standard.
                // Or use TertiaryContainer from theme.
            } else {
                holder.recommendedBadge.visibility = View.GONE
            }

            // Show currently selected badge (Editing)
            if (currentlySelectedId != null && dtb.id == currentlySelectedId) {
                holder.selectedBadge.visibility = View.VISIBLE
            } else {
                holder.selectedBadge.visibility = View.GONE
            }

            // Set click listener
            holder.cardView.setOnClickListener {
                listener?.onChipsetSelected(dtb)
            }
        }

        // Add subtle elevation on press
        holder.cardView.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> holder.cardView.cardElevation = 8f
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> holder.cardView.cardElevation = 0f
            }
            false
        }
    }

    override fun getItemCount(): Int {
        return dtbList.size + 1
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView as MaterialCardView
        val chipsetIcon: ImageView = itemView.findViewById(R.id.chipset_icon)
        val chipsetName: MaterialTextView = itemView.findViewById(R.id.chipset_name)
        val chipsetSubtitle: MaterialTextView = itemView.findViewById(R.id.chipset_subtitle)
        val recommendedBadge: Chip = itemView.findViewById(R.id.recommended_badge)
        val selectedBadge: Chip = itemView.findViewById(R.id.selected_badge)
    }
}