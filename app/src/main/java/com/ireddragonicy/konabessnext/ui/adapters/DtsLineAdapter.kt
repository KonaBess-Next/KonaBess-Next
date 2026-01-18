package com.ireddragonicy.konabessnext.ui.adapters

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ireddragonicy.konabessnext.R
import java.util.ArrayList

/**
 * RecyclerView adapter for the virtualized DTS text editor.
 * Each item represents one line of the file.
 */
class DtsLineAdapter(
    private val context: Context,
    private var lines: ArrayList<String>
) : RecyclerView.Adapter<DtsLineAdapter.ViewHolder>() {

    private var listener: OnLineChangedListener? = null
    private var focusedPosition = -1
    private var highlightedPosition = -1

    interface OnLineChangedListener {
        /**
         * Called when line content changes.
         */
        fun onLineChanged(position: Int, newContent: String)

        /**
         * Called when Enter is pressed, requesting a new line.
         *
         * @param position     Current line position
         * @param beforeCursor Text before cursor position
         * @param afterCursor  Text after cursor position
         */
        fun onNewLineRequested(position: Int, beforeCursor: String, afterCursor: String)

        /**
         * Called when Backspace is pressed on an empty line or at line start.
         *
         * @param position Current line position
         */
        fun onLineMergeRequested(position: Int)

        /**
         * Called when focus changes between lines.
         */
        fun onFocusChanged(oldPosition: Int, newPosition: Int)
    }

    fun setOnLineChangedListener(listener: OnLineChangedListener?) {
        this.listener = listener
    }

    fun setLines(lines: ArrayList<String>) {
        this.lines = lines
        notifyDataSetChanged()
    }

    fun highlightLine(position: Int) {
        val oldHighlight = highlightedPosition
        highlightedPosition = position
        if (oldHighlight >= 0) {
            notifyItemChanged(oldHighlight)
        }
        if (position >= 0 && position < itemCount) {
            notifyItemChanged(position)
        }
    }

    fun clearHighlight() {
        val oldHighlight = highlightedPosition
        highlightedPosition = -1
        if (oldHighlight >= 0) {
            notifyItemChanged(oldHighlight)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_dts_line, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // CRITICAL: Remove TextWatcher BEFORE setting text to prevent recycling bugs
        if (holder.textWatcher != null) {
            holder.lineContent.removeTextChangedListener(holder.textWatcher)
        }

        // Set line number (1-indexed)
        holder.lineNumber.text = (position + 1).toString()

        // Set content
        val content = lines[position]
        holder.lineContent.setText(content)

        // Apply highlight if this is the searched line
        if (position == highlightedPosition) {
            holder.itemView.setBackgroundColor(context.getColor(R.color.md_theme_light_primaryContainer))
        } else {
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Store position in tag for callbacks
        holder.lineContent.tag = position

        // Create new TextWatcher for this binding
        holder.textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Get actual position from tag (in case of recycling)
                val tag = holder.lineContent.tag
                if (tag is Int) {
                    val pos = tag
                    if (pos >= 0 && pos < lines.size) {
                        val newContent = s.toString()
                        // Only update if actually changed
                        if (newContent != lines[pos]) {
                            lines[pos] = newContent
                            listener?.onLineChanged(pos, newContent)
                        }
                    }
                }
            }
        }

        // Add TextWatcher AFTER setting text
        holder.lineContent.addTextChangedListener(holder.textWatcher)

        // Handle focus changes
        holder.lineContent.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            val tag = holder.lineContent.tag
            if (tag is Int) {
                val pos = tag
                if (hasFocus) {
                    val oldFocus = focusedPosition
                    focusedPosition = pos
                    if (listener != null && oldFocus != pos) {
                        listener!!.onFocusChanged(oldFocus, pos)
                    }
                }
            }
        }

        // Handle Enter key for new line
        holder.lineContent.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                val tag = holder.lineContent.tag
                if (tag is Int && listener != null) {
                    val pos = tag
                    val cursorPos = holder.lineContent.selectionStart
                    val text = holder.lineContent.text.toString()
                    val before = text.substring(0, cursorPos)
                    val after = text.substring(cursorPos)
                    listener!!.onNewLineRequested(pos, before, after)
                    return@setOnEditorActionListener true
                }
            }
            false
        }

        // Handle Backspace on empty line or at start
        holder.lineContent.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                val tag = holder.lineContent.tag
                if (tag is Int && listener != null) {
                    val pos = tag
                    val cursorPos = holder.lineContent.selectionStart
                    // If at the beginning of line (or line is empty) and not first line
                    if (cursorPos == 0 && pos > 0) {
                        listener!!.onLineMergeRequested(pos)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }

    override fun getItemCount(): Int {
        return lines.size
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        // Clean up listeners to prevent memory leaks
        if (holder.textWatcher != null) {
            holder.lineContent.removeTextChangedListener(holder.textWatcher)
        }
        holder.lineContent.onFocusChangeListener = null
        holder.lineContent.setOnEditorActionListener(null)
        holder.lineContent.setOnKeyListener(null)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lineNumber: TextView = itemView.findViewById(R.id.line_number)
        val lineContent: EditText = itemView.findViewById(R.id.line_content)
        var textWatcher: TextWatcher? = null
    }
}
