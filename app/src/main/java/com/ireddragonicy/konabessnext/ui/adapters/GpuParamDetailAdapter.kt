package com.ireddragonicy.konabessnext.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ireddragonicy.konabessnext.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch

class GpuParamDetailAdapter(
    private val items: List<ParamDetailItem>,
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_BACK = 0
        private const val VIEW_TYPE_STATS_GROUP = 1
        private const val VIEW_TYPE_SINGLE_PARAM = 2

        @JvmStatic
        fun getIconForParam(paramName: String?): Int {
            if (paramName == null) {
                return R.drawable.ic_back
            }

            return when {
                paramName.contains("gpu-freq") || paramName.contains("frequency") -> R.drawable.ic_frequency
                paramName.contains("level") || paramName.contains("cx-level") -> R.drawable.ic_voltage
                paramName.contains("bus") -> R.drawable.ic_bus
                paramName.contains("acd") -> R.drawable.ic_acd
                paramName.contains("power") || paramName.contains("pwr") -> R.drawable.ic_power
                else -> R.drawable.ic_settings
            }
        }
    }

    class ParamDetailItem {
        @JvmField var title: String? = null
        @JvmField var value: String? = null
        @JvmField var paramName: String? = null // DTS parameter name
        @JvmField var iconRes: Int = 0
        @JvmField var isBackButton: Boolean = false
        @JvmField var isStatsGroup: Boolean = false
        @JvmField var isFrequencyControl: Boolean = false
        @JvmField var statItems: List<StatItem>? = null // For grouped stats
        @JvmField var lineIndex: Int = -1

        constructor(title: String, value: String, paramName: String, iconRes: Int) {
            this.title = title
            this.value = value
            this.paramName = paramName
            this.iconRes = iconRes
            this.isBackButton = false
            this.isStatsGroup = false
            this.isFrequencyControl = false
        }

        constructor(title: String, iconRes: Int, isBackButton: Boolean) {
            this.title = title
            this.value = ""
            this.paramName = ""
            this.iconRes = iconRes
            this.isBackButton = isBackButton
            this.isStatsGroup = false
        }

        // Constructor for stats group
        constructor(statItems: List<StatItem>) {
            this.statItems = statItems
            this.isStatsGroup = true
            this.isBackButton = false
        }
    }

    class StatItem(
        @JvmField var label: String,
        @JvmField var value: String,
        @JvmField var paramName: String,
        @JvmField var iconRes: Int,
        @JvmField var lineIndex: Int // Original line index in data
    )

    interface OnItemClickListener {
        fun onBackClicked()
        fun onStatItemClicked(statItem: StatItem?)
        fun onParamClicked(item: ParamDetailItem?)
        fun onFrequencyAdjust(item: ParamDetailItem?, deltaMHz: Int)
    }

    private var clickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.clickListener = listener
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return if (item.isBackButton) {
            VIEW_TYPE_BACK
        } else if (item.isStatsGroup) {
            VIEW_TYPE_STATS_GROUP
        } else {
            VIEW_TYPE_SINGLE_PARAM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_STATS_GROUP) {
            val view = LayoutInflater.from(context).inflate(R.layout.gpu_param_modern_group, parent, false)
            StatsGroupViewHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.gpu_param_detail_card, parent, false)
            ParamViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        if (holder is StatsGroupViewHolder) {
            bindStatsGroup(holder, item)
        } else if (holder is ParamViewHolder) {
            bindParam(holder, item, position)
        }
    }

    private fun bindStatsGroup(holder: StatsGroupViewHolder, item: ParamDetailItem) {
        // Clear previous stats
        holder.statsRow.removeAllViews()

        // Add each stat card
        item.statItems?.forEach { stat ->
            val statCard = LayoutInflater.from(context).inflate(R.layout.gpu_param_stat_card, holder.statsRow, false)

            val icon = statCard.findViewById<ImageView>(R.id.stat_icon)
            val label = statCard.findViewById<TextView>(R.id.stat_label)
            val value = statCard.findViewById<TextView>(R.id.stat_value)
            val card = statCard.findViewById<MaterialCardView>(R.id.stat_card)

            icon.setImageResource(stat.iconRes)
            label.text = stat.label
            value.text = stat.value

            // Apply primary color to icon
            val primary = MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimary)
            icon.setColorFilter(primary)

            // Set click listener
            statCard.findViewById<View>(R.id.stat_content).setOnClickListener {
                clickListener?.onStatItemClicked(stat)
            }

            holder.statsRow.addView(statCard)
        }

        // Hide back button in this view (it's shown separately)
        holder.backButtonCard.visibility = View.GONE
    }

    private fun bindParam(holder: ParamViewHolder, item: ParamDetailItem, position: Int) {
        holder.title.text = item.title
        holder.value.text = item.value
        holder.icon.setImageResource(item.iconRes)

        if (item.isBackButton) {
            // Style back button differently
            val surfaceVariant = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurfaceVariant = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOnSurfaceVariant)

            holder.card.setCardBackgroundColor(surfaceVariant)
            holder.title.setTextColor(onSurfaceVariant)
            holder.value.visibility = View.GONE
            holder.chevron.visibility = View.GONE
            holder.icon.setColorFilter(onSurfaceVariant)
        } else {
            // Style regular param items
            val surface = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorSurface)
            val onSurface = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariant = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val primary = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorPrimary)

            holder.card.setCardBackgroundColor(surface)
            holder.title.setTextColor(onSurface)
            holder.value.setTextColor(onSurfaceVariant)
            holder.value.visibility = View.VISIBLE
            holder.chevron.visibility = View.VISIBLE
            holder.chevron.setColorFilter(onSurfaceVariant)
            holder.icon.setColorFilter(primary)
        }

        holder.cardContent.setOnClickListener {
            if (clickListener != null) {
                if (item.isBackButton) {
                    clickListener!!.onBackClicked()
                } else {
                    clickListener!!.onParamClicked(item)
                }
            }
        }

        // Frequency Buttons Logic
        val buttonsScroll = holder.card.findViewById<View>(R.id.freq_buttons_scroll)
        if (buttonsScroll != null) {
            if (item.isFrequencyControl) {
                buttonsScroll.visibility = View.VISIBLE

                // Helper to set click listener
                val adjListener = View.OnClickListener { v ->
                    if (clickListener != null) {
                        var delta = 0
                        val id = v.id
                        if (id == R.id.btn_minus_20) delta = -20
                        else if (id == R.id.btn_minus_10) delta = -10
                        else if (id == R.id.btn_minus_5) delta = -5
                        else if (id == R.id.btn_plus_5) delta = 5
                        else if (id == R.id.btn_plus_10) delta = 10
                        else if (id == R.id.btn_plus_20) delta = 20

                        if (delta != 0) {
                            clickListener!!.onFrequencyAdjust(item, delta)
                        }
                    }
                }

                // Bind buttons
                val btnIds = intArrayOf(R.id.btn_minus_20, R.id.btn_minus_10, R.id.btn_minus_5, R.id.btn_plus_5, R.id.btn_plus_10, R.id.btn_plus_20)
                for (id in btnIds) {
                    val btn = holder.card.findViewById<View>(id)
                    btn?.setOnClickListener(adjListener)
                }

            } else {
                buttonsScroll.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class ParamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val cardContent: View = itemView.findViewById(R.id.card_content)
        val icon: ImageView = itemView.findViewById(R.id.param_icon)
        val title: TextView = itemView.findViewById(R.id.param_title)
        val value: TextView = itemView.findViewById(R.id.param_value)
        val chevron: ImageView = itemView.findViewById(R.id.chevron_icon)
    }

    class StatsGroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val statsRow: LinearLayout = itemView.findViewById(R.id.stats_row)
        val backButtonCard: MaterialCardView = itemView.findViewById(R.id.back_button_card)
    }
}
