package com.ireddragonicy.konabessnext.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.ireddragonicy.konabessnext.R

class ParamAdapter(private val items: List<Item>, private val context: Context) : BaseAdapter() {

    class Item {
        @JvmField var title: String? = null
        @JvmField var subtitle: String? = null
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): Any {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.param_list_item, parent, false)
        val title = view.findViewById<TextView>(R.id.title)
        val subtitle = view.findViewById<TextView>(R.id.subtitle)

        title.text = items[position].title
        subtitle.text = items[position].subtitle
        return view
    }
}
