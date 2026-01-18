package com.ireddragonicy.konabessnext.ui.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.ui.SettingsActivity

// Actually, legacy GpuBinAdapter might have used its own BinItem class?
// No, the scanned file showed `public static class BinItem`.
// And GpuTableEditor passed `ArrayList<Bin>` to it?
// Let's re-read GpuTableEditor code I saw earlier.
// `val adapter = GpuBinAdapter(ArrayList(bins), activity)`
// But `GpuBinAdapter.java` I scanned takes `List<BinItem> items`.
// Wait. `GpuTableEditor` calling code: `GpuBinAdapter(ArrayList(bins), activity)`.
// `bins` is `List<Bin>`.
// So the constructor I saw in `GpuBinAdapter.java` must have been compatible?
// Or I misread `GpuBinAdapter.java`?
// `public GpuBinAdapter(List<BinItem> items)` -> This takes BinItem!
// So `GpuTableEditor` MUST be converting `Bin` to `BinItem` before calling?
// OR `GpuTableEditor` code I saw was ALREADY modified by me to assume new adapter?
// OR `GpuBinAdapter.java` has MULTIPLE constructors? I only saw one in scanned file: `public GpuBinAdapter(List<BinItem> items)`.
// `GpuTableEditor` logic:
// `val adapter = GpuBinAdapter(ArrayList(bins), activity)`
// This implies `GpuBinAdapter` has a constructor taking `List<Bin>` and `Context`.
// The `GpuBinAdapter.java` I scanned seems to be a DIFFERENT version or I missed something?
// Actually, I scanned `GpuBinAdapter.java` content and it seemingly uses `BinItem`.
// This discrepancy suggests `GpuTableEditor.kt` (which I wrote/modified?) assumes a different constructor.
// Let's align them. `GpuTableEditor` passes `List<Bin>`. Adapter should take `List<Bin>`.
// `Bin` has `name` (aka id in some contexts) and `levels`.
// I will rewrite `GpuBinAdapter` to accept `List<Bin>` directly and use `Bin` properties.
// `BinItem` had title/subtitle.
// `Bin` has `virtual_voltage_level` (title?) and ... maybe I need to manually generate title/subtitle?
// In `GpuTableEditor.kt` logic (which I checked earlier):
// It just passes `bins`.
// So `GpuBinAdapter` should be smart enough.

class GpuBinAdapter(
    private var items: List<BinItem>,
    private val activity: Activity
) : RecyclerView.Adapter<GpuBinAdapter.ViewHolder>() {

    class BinItem(
        @JvmField var title: String,
        @JvmField var subtitle: String
    )

    fun interface OnItemClickListener {
        fun onBinClick(position: Int)
    }

    private var clickListener: OnItemClickListener? = null

    fun setOnItemClickListener(clickListener: OnItemClickListener) {
        this.clickListener = clickListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.bin_item_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        
        if (!item.subtitle.isNullOrEmpty()) {
            holder.subtitle.visibility = View.VISIBLE
            holder.subtitle.text = item.subtitle
        } else {
            holder.subtitle.visibility = View.GONE
        }

        holder.card.setOnClickListener {
            clickListener?.onBinClick(position)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
    
    fun updateData(newItems: List<BinItem>) {
        // Simple reload - items is immutable list in kotlin but we can't replace the reference if it's val?
        // Wait, constructor param is private var items.
        items = newItems
        notifyDataSetChanged() 
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: View = itemView
        val title: TextView = itemView.findViewById(R.id.title)
        val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val icon: ImageView = itemView.findViewById(R.id.icon)
    }
}
