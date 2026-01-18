package com.ireddragonicy.konabessnext.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.ireddragonicy.konabessnext.R

class ActionCardAdapter(private val items: List<ActionItem>) : RecyclerView.Adapter<ActionCardAdapter.ViewHolder>() {

    class ActionItem(
        val iconResId: Int,
        val title: String,
        val description: String,
        @JvmField val enabled: Boolean = true
    )

    fun interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    private var clickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.clickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.action_item_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageResource(item.iconResId)
        holder.title.text = item.title
        holder.description.text = item.description
        holder.card.isEnabled = item.enabled
        holder.card.isClickable = item.enabled
        holder.card.isFocusable = item.enabled

        // Simple disabled visual state
        holder.card.alpha = if (item.enabled) 1f else 0.5f

        holder.card.setOnClickListener {
            if (clickListener != null) {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val actionItem = items[adapterPosition]
                    if (actionItem.enabled) {
                        clickListener!!.onItemClick(adapterPosition)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val title: TextView = itemView.findViewById(R.id.title)
        val description: TextView = itemView.findViewById(R.id.description)
    }
}
