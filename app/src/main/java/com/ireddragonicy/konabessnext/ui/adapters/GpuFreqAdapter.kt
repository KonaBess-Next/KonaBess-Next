package com.ireddragonicy.konabessnext.ui.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import java.util.Collections
import java.util.Objects

class GpuFreqAdapter(
    var items: MutableList<FreqItem>,
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class FreqItem {
        enum class ActionType {
            NONE, BACK, ADD_TOP, ADD_BOTTOM, DUPLICATE, CURVE_EDITOR
        }

        @JvmField var title: String?
        @JvmField var subtitle: String?
        @JvmField var isHeader: Boolean
        @JvmField var isFooter: Boolean
        @JvmField var originalPosition: Int
        @JvmField var actionType: ActionType
        @JvmField var targetPosition: Int // For duplicate action, stores the target frequency position
        @JvmField var isHighlighted: Boolean // For visual feedback on long-press

        // Spec details for frequency items
        @JvmField var busMax: String? = null
        @JvmField var busMin: String? = null
        @JvmField var busFreq: String? = null
        @JvmField var voltageLevel: String? = null
        @JvmField var frequencyHz: Long = -1L
        @JvmField var tag: Any? = null

        constructor(title: String?, subtitle: String?, actionType: ActionType) {
            this.title = title
            this.subtitle = subtitle
            this.actionType = actionType
            this.isHeader = actionType == ActionType.BACK || actionType == ActionType.ADD_TOP || actionType == ActionType.CURVE_EDITOR
            this.isFooter = actionType == ActionType.ADD_BOTTOM
            this.originalPosition = -1
            this.targetPosition = -1
            this.isHighlighted = false
        }

        constructor(title: String?, subtitle: String?) : this(title, subtitle, ActionType.NONE)

        val isLevelItem: Boolean get() = actionType == ActionType.NONE
        val isActionItem: Boolean get() = actionType == ActionType.ADD_TOP || actionType == ActionType.ADD_BOTTOM || actionType == ActionType.CURVE_EDITOR
        val isDuplicateItem: Boolean get() = actionType == ActionType.DUPLICATE
        val hasSpecs: Boolean get() = busMax != null || busMin != null || busFreq != null || voltageLevel != null
        
        fun hasFrequencyValue(): Boolean {
            return frequencyHz >= 0
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val freqItem = other as FreqItem
            return isHeader == freqItem.isHeader &&
                    isFooter == freqItem.isFooter &&
                    originalPosition == freqItem.originalPosition &&
                    targetPosition == freqItem.targetPosition &&
                    isHighlighted == freqItem.isHighlighted &&
                    frequencyHz == freqItem.frequencyHz &&
                    actionType == freqItem.actionType &&
                    Objects.equals(title, freqItem.title) &&
                    Objects.equals(subtitle, freqItem.subtitle) &&
                    Objects.equals(busMax, freqItem.busMax) &&
                    Objects.equals(busMin, freqItem.busMin) &&
                    Objects.equals(busFreq, freqItem.busFreq) &&
                    Objects.equals(voltageLevel, freqItem.voltageLevel) &&
                    tag === freqItem.tag
        }

        override fun hashCode(): Int {
            return Objects.hash(title, subtitle, isHeader, isFooter, originalPosition, actionType, targetPosition, isHighlighted, busMax, busMin, busFreq, voltageLevel, frequencyHz, tag)
        }
    }

    class FreqDiffCallback(private val oldList: List<FreqItem>, private val newList: List<FreqItem>) : DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            // If both items have a tag (Level object), compare by reference/identity
            if (oldItem.tag != null && newItem.tag != null) {
                return oldItem.tag === newItem.tag
            }

            return oldItem.actionType == newItem.actionType &&
                    Objects.equals(oldItem.title, newItem.title) &&
                    oldItem.frequencyHz == newItem.frequencyHz
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    fun interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun interface OnItemLongClickListener {
        fun onItemLongClick(position: Int)
    }

    fun interface OnDuplicateClickListener {
        fun onDuplicateClick(position: Int)
    }

    fun interface OnDeleteClickListener {
        fun onDeleteClick(position: Int)
    }

    fun interface OnStartDragListener {
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder?)
    }

    private var clickListener: OnItemClickListener? = null
    private var longClickListener: OnItemLongClickListener? = null
    private var deleteClickListener: OnDeleteClickListener? = null
    private var duplicateClickListener: OnDuplicateClickListener? = null
    private var dragStartListener: OnStartDragListener? = null
    private var sharedPreferences: SharedPreferences? = null
    private var preferenceChangeListener: OnSharedPreferenceChangeListener? = null

    init {
        // Since we are taking MutableList, assume it is ready for use
    }

    fun updateData(newItems: List<FreqItem>) {
        val diffResult = DiffUtil.calculateDiff(FreqDiffCallback(this.items, newItems))
        this.items.clear()
        this.items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.clickListener = listener
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener?) {
        this.longClickListener = listener
    }

    fun setOnDeleteClickListener(listener: OnDeleteClickListener?) {
        this.deleteClickListener = listener
    }

    fun setOnDuplicateClickListener(listener: OnDuplicateClickListener?) {
        this.duplicateClickListener = listener
    }

    fun setOnStartDragListener(listener: OnStartDragListener?) {
        this.dragStartListener = listener
    }

    // View Types
    companion object {
        const val TYPE_ITEM = 0
        const val TYPE_ACTION = 1
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        // Back is now TYPE_ITEM (Card style)
        return if (item.isActionItem) TYPE_ACTION else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ACTION -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_gpu_action, parent, false)
                ActionViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context).inflate(R.layout.gpu_freq_item_card, parent, false)
                ItemViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val viewType = getItemViewType(position)

        if (viewType == TYPE_ACTION) {
            val actionHolder = holder as ActionViewHolder
            actionHolder.bind(item)
            
            actionHolder.button?.setOnClickListener {
                clickListener?.onItemClick(holder.bindingAdapterPosition)
            }
            return
        }

        val itemHolder = holder as ItemViewHolder
        itemHolder.title?.text = item.title
        itemHolder.subtitle?.text = item.subtitle

        val isLevel = item.isLevelItem
        val isDuplicate = item.isDuplicateItem
        val isBack = item.actionType == FreqItem.ActionType.BACK

        // Reset visibility defaults
        itemHolder.subtitle?.visibility = if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE

        if (isLevel) {
            if (item.hasFrequencyValue()) {
                val formatted = SettingsActivity.formatFrequency(item.frequencyHz, context)
                if (formatted != item.title) {
                    item.title = formatted
                }
                itemHolder.title?.text = formatted
            }
            val baseColor = MaterialColors.getColor(itemHolder.card, com.google.android.material.R.attr.colorSurface)
            val highlightColor = MaterialColors.getColor(itemHolder.card, com.google.android.material.R.attr.colorSurfaceVariant)

            itemHolder.card.setCardBackgroundColor(if (item.isHighlighted) highlightColor else baseColor)
            val onSurface = MaterialColors.getColor(itemHolder.card, com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariant = MaterialColors.getColor(itemHolder.card, com.google.android.material.R.attr.colorOnSurfaceVariant)

            itemHolder.title?.setTextColor(onSurface)
            itemHolder.subtitle?.setTextColor(onSurfaceVariant)
            itemHolder.dragHandle?.visibility = View.GONE // Drag by long-press on card
            itemHolder.deleteIcon?.visibility = View.VISIBLE
            itemHolder.copyButton?.visibility = View.VISIBLE

            // Show spec details if available
            if (item.hasSpecs) {
                itemHolder.subtitle?.visibility = View.GONE
                itemHolder.specsContainer?.visibility = View.VISIBLE

                // Populate spec values
                if (item.busMax != null) {
                    itemHolder.busMaxValue?.text = item.busMax
                    itemHolder.busMaxValue?.visibility = View.VISIBLE
                    itemHolder.itemView.findViewById<View>(R.id.icon_bus_max)?.visibility = View.VISIBLE
                } else {
                    itemHolder.busMaxValue?.visibility = View.GONE
                    itemHolder.itemView.findViewById<View>(R.id.icon_bus_max)?.visibility = View.GONE
                }

                if (item.busMin != null) {
                    itemHolder.busMinValue?.text = item.busMin
                    itemHolder.busMinValue?.visibility = View.VISIBLE
                    itemHolder.itemView.findViewById<View>(R.id.icon_bus_min)?.visibility = View.VISIBLE
                } else {
                    itemHolder.busMinValue?.visibility = View.GONE
                    itemHolder.itemView.findViewById<View>(R.id.icon_bus_min)?.visibility = View.GONE
                }

                if (item.busFreq != null) {
                    itemHolder.busFreqValue?.text = item.busFreq
                    itemHolder.busFreqValue?.visibility = View.VISIBLE
                    itemHolder.itemView.findViewById<View>(R.id.icon_bus_freq)?.visibility = View.VISIBLE
                } else {
                    itemHolder.busFreqValue?.visibility = View.GONE
                    itemHolder.itemView.findViewById<View>(R.id.icon_bus_freq)?.visibility = View.GONE
                }

                if (item.voltageLevel != null) {
                    itemHolder.voltageValue?.text = item.voltageLevel
                    itemHolder.voltageValue?.visibility = View.VISIBLE
                    itemHolder.itemView.findViewById<View>(R.id.icon_voltage)?.visibility = View.VISIBLE
                } else {
                    itemHolder.voltageValue?.visibility = View.GONE
                    itemHolder.itemView.findViewById<View>(R.id.icon_voltage)?.visibility = View.GONE
                }
            } else {
                itemHolder.specsContainer?.visibility = View.GONE
            }
        } else if (isDuplicate) {
            // Duplicate action with tertiary color scheme
            val container = MaterialColors.getColor(itemHolder.card, com.google.android.material.R.attr.colorTertiaryContainer)
            val onContainer = MaterialColors.getColor(itemHolder.card, com.google.android.material.R.attr.colorOnTertiaryContainer)

            itemHolder.card.setCardBackgroundColor(container)
            itemHolder.title?.setTextColor(onContainer)
            itemHolder.subtitle?.setTextColor(onContainer)
            itemHolder.dragHandle?.visibility = View.VISIBLE
            itemHolder.dragHandle?.setImageResource(R.drawable.ic_arrow_downward)
            itemHolder.dragHandle?.imageTintList = ColorStateList.valueOf(onContainer)
            itemHolder.deleteIcon?.visibility = View.GONE
            itemHolder.copyButton?.visibility = View.GONE
            itemHolder.specsContainer?.visibility = View.GONE
        } else if (isBack) {
            val surfaceVariant = MaterialColors.getColor(itemHolder.card, com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurfaceVariant = MaterialColors.getColor(itemHolder.card, com.google.android.material.R.attr.colorOnSurfaceVariant)

            itemHolder.card.setCardBackgroundColor(surfaceVariant)
            itemHolder.title?.setTextColor(onSurfaceVariant)
            itemHolder.subtitle?.setTextColor(onSurfaceVariant)
            itemHolder.dragHandle?.visibility = View.GONE
            itemHolder.deleteIcon?.visibility = View.GONE
            itemHolder.copyButton?.visibility = View.GONE
            itemHolder.specsContainer?.visibility = View.GONE
        }

        val isInteractive = isLevel

        // Set click listeners
        itemHolder.mainContent?.setOnClickListener {
            clickListener?.onItemClick(holder.bindingAdapterPosition)
        }

        itemHolder.mainContent?.setOnLongClickListener {
            if (longClickListener != null && isInteractive) {
                longClickListener!!.onItemLongClick(holder.bindingAdapterPosition)
                true
            } else {
                false
            }
        }

        itemHolder.deleteIcon?.setOnClickListener {
            if (deleteClickListener != null && isInteractive) {
                deleteClickListener!!.onDeleteClick(holder.bindingAdapterPosition)
            }
        }

        itemHolder.copyButton?.setOnClickListener {
            // Updated to trigger duplication
            if (duplicateClickListener != null && isInteractive) {
                duplicateClickListener!!.onDuplicateClick(holder.bindingAdapterPosition)
            } else if (item.hasFrequencyValue()) {
                // Fallback to clipboard copy if no listener (legacy behavior backup)
                val label = item.title ?: ""
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Frequency", label)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Frequency copied", Toast.LENGTH_SHORT).show()
            }
        }

        // Drag is now handled by long-pressing anywhere on the card
        // (ItemTouchHelperCallback)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        sharedPreferences = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        preferenceChangeListener = OnSharedPreferenceChangeListener { _, key ->
            if (SettingsActivity.KEY_FREQ_UNIT == key) {
                refreshFrequencyUnits()
            }
        }
        sharedPreferences!!.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        refreshFrequencyUnits()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (sharedPreferences != null && preferenceChangeListener != null) {
            sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        }
        preferenceChangeListener = null
        sharedPreferences = null
    }

    private fun refreshFrequencyUnits() {
        for (i in items.indices) {
            val item = items[i]
            if (item.hasFrequencyValue()) {
                item.title = SettingsActivity.formatFrequency(item.frequencyHz, context)
                notifyItemChanged(i)
            }
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size || toPosition >= items.size) {
            return
        }

        // Prevent moving header or footer items
        val fromItem = items[fromPosition]
        val toItem = items[toPosition]

        if (fromItem.isHeader || fromItem.isFooter || toItem.isHeader || toItem.isFooter) {
            return
        }

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }


    // --- View Holders ---

    // Original ViewHolder for items, renamed to ItemViewHolder
    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val mainContent: View? = itemView.findViewById(R.id.main_content)
        val dragHandle: ImageView? = itemView.findViewById(R.id.drag_handle)
        val title: TextView? = itemView.findViewById(R.id.title)
        val subtitle: TextView? = itemView.findViewById(R.id.subtitle)
        val deleteIcon: ImageButton? = itemView.findViewById(R.id.delete_icon)
        val copyButton: ImageButton? = itemView.findViewById(R.id.btn_copy)

        // Spec details views
        val specsContainer: View? = itemView.findViewById(R.id.specs_container)
        val busMaxValue: TextView? = itemView.findViewById(R.id.bus_max_value)
        val busMinValue: TextView? = itemView.findViewById(R.id.bus_min_value)
        val busFreqValue: TextView? = itemView.findViewById(R.id.bus_freq_value)
        val voltageValue: TextView? = itemView.findViewById(R.id.voltage_value)
    }

    // New ViewHolder for Action Buttons
    class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: com.google.android.material.button.MaterialButton? = itemView.findViewById(R.id.action_button)

        fun bind(item: FreqItem) {
            button?.text = item.title
            
            // Set Icon
            when (item.actionType) {
                FreqItem.ActionType.ADD_TOP -> button?.setIconResource(R.drawable.ic_arrow_upward)
                FreqItem.ActionType.ADD_BOTTOM -> button?.setIconResource(R.drawable.ic_arrow_downward)
                FreqItem.ActionType.CURVE_EDITOR -> button?.setIconResource(R.drawable.ic_frequency)
                FreqItem.ActionType.BACK -> button?.setIconResource(R.drawable.ic_back)
                else -> button?.icon = null
            }
        }
    }
}
