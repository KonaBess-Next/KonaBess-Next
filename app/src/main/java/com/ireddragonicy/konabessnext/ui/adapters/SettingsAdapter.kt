package com.ireddragonicy.konabessnext.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.ireddragonicy.konabessnext.R

class SettingsAdapter(private val items: List<SettingItem>, private val context: Context) :
    RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    class SettingItem {
        var iconResId: Int
        var title: String
        var description: String
        var value: String = ""
        var isSwitch: Boolean = false
        var isChecked: Boolean = false

        constructor(iconResId: Int, title: String, description: String, value: String) {
            this.iconResId = iconResId
            this.title = title
            this.description = description
            this.value = value
            this.isSwitch = false
        }

        // Constructor for Switch Item
        constructor(iconResId: Int, title: String, description: String, isChecked: Boolean) {
            this.iconResId = iconResId
            this.title = title
            this.description = description
            this.value = ""
            this.isSwitch = true
            this.isChecked = isChecked
        }
    }

    fun interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    private var clickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.clickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.setting_item_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.icon.setImageResource(item.iconResId)
        holder.title.text = item.title
        holder.description.text = item.description

        if (item.isSwitch) {
            holder.value.visibility = View.GONE
            holder.chevron.visibility = View.GONE
            holder.switchWidget.visibility = View.VISIBLE
            holder.switchWidget.isChecked = item.isChecked

            // Handle switch clicks strictly
            holder.switchWidget.setOnClickListener {
                if (clickListener != null) {
                    item.isChecked = holder.switchWidget.isChecked // Update model
                    clickListener!!.onItemClick(position)
                }
            }

            // Also handle card click to toggle switch
            holder.card.setOnClickListener {
                holder.switchWidget.toggle()
                if (clickListener != null) {
                    item.isChecked = holder.switchWidget.isChecked // Update model
                    clickListener!!.onItemClick(position)
                }
            }

        } else {
            holder.value.visibility = View.VISIBLE
            holder.value.text = item.value
            holder.chevron.visibility = View.VISIBLE
            holder.switchWidget.visibility = View.GONE

            holder.card.setOnClickListener {
                clickListener?.onItemClick(position)
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun updateItem(position: Int, newValue: String) {
        if (position >= 0 && position < items.size) {
            items[position].value = newValue
            notifyItemChanged(position)
        }
    }

    fun updateSwitch(position: Int, isChecked: Boolean) {
        if (position >= 0 && position < items.size) {
            items[position].isChecked = isChecked
            notifyItemChanged(position)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val title: TextView = itemView.findViewById(R.id.title)
        val description: TextView = itemView.findViewById(R.id.description)
        val value: TextView = itemView.findViewById(R.id.value)
        val chevron: ImageView = itemView.findViewById(R.id.chevron)
        val switchWidget: com.google.android.material.materialswitch.MaterialSwitch = itemView.findViewById(R.id.switch_widget)
    }
}
