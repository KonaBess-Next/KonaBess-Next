package com.ireddragonicy.konabessnext.ui.adapters

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.TableIO
import com.ireddragonicy.konabessnext.model.ExportHistoryItem
import com.ireddragonicy.konabessnext.utils.ExportHistoryManager
import com.ireddragonicy.konabessnext.utils.RootHelper
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportHistoryAdapter(
    private val historyItems: MutableList<ExportHistoryItem>,
    private val activity: Activity,
    private val historyManager: ExportHistoryManager,
    private val listener: OnHistoryChangeListener?
) : RecyclerView.Adapter<ExportHistoryAdapter.ViewHolder>() {

    fun interface OnHistoryChangeListener {
        fun onHistoryChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_export_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyItems[position]

        holder.filename.text = item.filename
        holder.description.text = if (item.description.isEmpty()) activity.getString(R.string.no_description) else item.description
        holder.chipType.text = item.chipType

        // Format timestamp
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        holder.timestamp.text = sdf.format(Date(item.timestamp))

        // Add Material You entrance animation
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 50f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(position * 50L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Apply button
        holder.btnApply.setOnClickListener {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.apply)
                .setMessage(activity.getString(R.string.confirm_apply_config))
                .setPositiveButton(R.string.confirm) { _, _ ->
                    applyConfig(item)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Share button
        holder.btnShare.setOnClickListener { shareConfig(item) }

        // Delete button
        holder.btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.confirm_delete_history)
                .setMessage(R.string.confirm_delete_history_msg)
                .setPositiveButton(R.string.delete) { _, _ ->
                    historyManager.deleteItem(item)
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        historyItems.removeAt(pos)
                        notifyItemRemoved(pos)
                        Toast.makeText(activity, R.string.history_item_deleted, Toast.LENGTH_SHORT).show()
                        listener?.onHistoryChanged()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Display file path
        holder.filePath.text = item.filePath

        // Copy path button
        holder.btnCopyPath.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("File Path", item.filePath)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(activity, R.string.path_copied, Toast.LENGTH_SHORT).show()
        }

        // Rename button
        holder.btnRename.setOnClickListener { showRenameDialog(item, holder) }
    }

    override fun getItemCount(): Int {
        return historyItems.size
    }

    private fun applyConfig(item: ExportHistoryItem) {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(activity, R.string.file_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Read file content
            val content = file.readText()
            // Apply via TableIO
            TableIO.importFromData(activity, content)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, R.string.error_occur, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareConfig(item: ExportHistoryItem) {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(activity, R.string.file_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Robust Sharing: Copy to internal cache first to ensure FileProvider works flawlessly
            val cachePath = File(activity.cacheDir, "shared_exports")
            if (!cachePath.exists()) {
                cachePath.mkdirs()
            }
            // Use original filename to preserve extension for receiving app
            val newFile = File(cachePath, file.name)

            // Use centralized root copy utility
            if (!RootHelper.copyFile(file.absolutePath, newFile.absolutePath, "666")) {
                Toast.makeText(activity, "Failed to copy file with Root.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider", newFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND)

            // Determine MIME type based on extension
            var mimeType = "*/*"
            val filename = file.name.lowercase(Locale.getDefault())
            if (filename.endsWith(".txt") || filename.endsWith(".dts")) {
                mimeType = "text/plain"
            } else if (filename.endsWith(".img") || filename.endsWith(".bin")) {
                mimeType = "application/octet-stream"
            }

            shareIntent.type = mimeType
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.filename)
            shareIntent.putExtra(Intent.EXTRA_TEXT, "KonaBess GPU Config: ${item.description}")

            // Critical for Android 10+: Add ClipData to ensure permissions are granted
            shareIntent.clipData = ClipData.newRawUri(null, uri)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            activity.startActivity(
                Intent.createChooser(
                    shareIntent,
                    activity.getString(R.string.share_config)
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, R.string.error_occur, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog(item: ExportHistoryItem, holder: ViewHolder) {
        // Create dialog with two text inputs (filename + description)
        val dialogLayout = android.widget.LinearLayout(activity)
        dialogLayout.orientation = android.widget.LinearLayout.VERTICAL
        dialogLayout.setPadding(48, 32, 48, 16)

        // Filename input
        val filenameLayout = TextInputLayout(activity)
        filenameLayout.hint = activity.getString(R.string.enter_new_filename)
        val filenameEdit = TextInputEditText(activity)

        // Get current filename without extension
        val currentName = item.filename
        var baseName = currentName
        var extension = ""
        val dotIndex = currentName.lastIndexOf('.')
        if (dotIndex > 0) {
            baseName = currentName.substring(0, dotIndex)
            extension = currentName.substring(dotIndex)
        }
        filenameEdit.setText(baseName)
        filenameLayout.addView(filenameEdit)
        dialogLayout.addView(filenameLayout)

        // Description input
        val descLayout = TextInputLayout(activity)
        descLayout.hint = activity.getString(R.string.edit_description)
        val descEdit = TextInputEditText(activity)
        descEdit.setText(item.description)
        descLayout.addView(descEdit)
        val descParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        descParams.topMargin = 24
        descLayout.layoutParams = descParams
        dialogLayout.addView(descLayout)

        val finalExtension = extension

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.edit_export)
            .setView(dialogLayout)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val newName = filenameEdit.text.toString().trim()
                val newDesc = descEdit.text.toString().trim()

                if (newName.isNotEmpty()) {
                    val newFilename = newName + finalExtension
                    renameFile(item, newFilename, newDesc, holder)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renameFile(
        item: ExportHistoryItem,
        newFilename: String,
        newDescription: String,
        holder: ViewHolder
    ) {
        val oldFile = File(item.filePath)
        if (!oldFile.exists()) {
            Toast.makeText(activity, R.string.file_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        val newFile = File(oldFile.parent, newFilename)

        if (oldFile.renameTo(newFile)) {
            // Update history item
            item.filename = newFilename
            item.filePath = newFile.absolutePath
            item.description = newDescription
            historyManager.updateItem(item)

            // Update UI
            holder.filename.text = newFilename
            holder.filePath.text = newFile.absolutePath
            holder.description.text = if (newDescription.isEmpty()) activity.getString(R.string.no_description) else newDescription

            Toast.makeText(activity, R.string.changes_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, R.string.rename_failed, Toast.LENGTH_SHORT).show()
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val filename: TextView = view.findViewById(R.id.history_filename)
        val timestamp: TextView = view.findViewById(R.id.history_timestamp)
        val description: TextView = view.findViewById(R.id.history_description)
        val filePath: TextView = view.findViewById(R.id.history_file_path)
        val chipType: Chip = view.findViewById(R.id.history_chip_type)
        val btnApply: MaterialButton = view.findViewById(R.id.history_btn_apply)
        val btnShare: MaterialButton = view.findViewById(R.id.history_btn_share)
        val btnDelete: MaterialButton = view.findViewById(R.id.history_btn_delete)
        val btnCopyPath: MaterialButton = view.findViewById(R.id.history_btn_copy_path)
        val btnRename: MaterialButton = view.findViewById(R.id.history_btn_rename)
    }
}
