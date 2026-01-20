package com.ireddragonicy.konabessnext.core

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.ExportHistoryActivity
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.adapters.ActionCardAdapter
import com.ireddragonicy.konabessnext.utils.DialogUtil
import com.ireddragonicy.konabessnext.utils.ExportHistoryManager
import com.ireddragonicy.konabessnext.utils.FileUtil
import com.ireddragonicy.konabessnext.utils.GzipUtils
import com.ireddragonicy.konabessnext.utils.RootHelper
import com.ireddragonicy.konabessnext.utils.ThreadUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date

object TableIO {

    private object json_keys {
        const val MODEL = "model"
        const val BRAND = "brand"
        const val ID = "id"
        const val VERSION = "version"
        const val FINGERPRINT = "fingerprint"
        const val MANUFACTURER = "manufacturer"
        const val DEVICE = "device"
        const val NAME = "name"
        const val BOARD = "board"
        const val CHIP = "chip"
        const val DESCRIPTION = "desc"
        const val FREQ = "freq"
        const val VOLT = "volt"
    }

    private var waiting_import: AlertDialog? = null

    @Synchronized
    @Throws(Exception::class)
    private fun prepareTables() {
        GpuTableEditor.init()
        GpuTableEditor.decode()
        if (ChipInfo.which?.ignoreVoltTable == false) {
            GpuVoltEditor.init()
            GpuVoltEditor.decode()
        }
    }

    @Throws(Exception::class)
    private fun decodeAndWriteData(jsonObject: JSONObject): Boolean {
        if (ChipInfo.which?.isEquivalentTo(ChipInfo.Type.valueOf(jsonObject.getString(json_keys.CHIP))) == false)
            return true
        prepareTables()
        val freq = ArrayList(listOf(*jsonObject.getString(json_keys.FREQ).split("\n".toRegex()).toTypedArray()))
        GpuTableEditor.writeOut(GpuTableEditor.genBack(freq))
        if (ChipInfo.which?.ignoreVoltTable == false) {
            val volt = ArrayList(listOf(*jsonObject.getString(json_keys.VOLT).split("\n".toRegex()).toTypedArray()))
            // Init again because the dts file has been updated
            GpuVoltEditor.init()
            GpuVoltEditor.decode()
            GpuVoltEditor.writeOut(GpuVoltEditor.genBack(volt))
        }

        return false
    }

    private fun getFreqData(): String {
        val data = StringBuilder()
        for (line in GpuTableEditor.genTable())
            data.append(line).append("\n")
        return data.toString()
    }

    private fun getVoltData(): String {
        val data = StringBuilder()
        for (line in GpuVoltEditor.genTable())
            data.append(line).append("\n")
        return data.toString()
    }

    @Throws(IOException::class)
    private fun getConfig(desc: String): String {
        val jsonObject = JSONObject()
        try {
            prepareTables()
            /*
             * jsonObject.put(json_keys.MODEL, getCurrent("model"));
             * jsonObject.put(json_keys.BRAND, getCurrent("brand"));
             * jsonObject.put(json_keys.ID, getCurrent("id"));
             * jsonObject.put(json_keys.VERSION, getCurrent("version"));
             * jsonObject.put(json_keys.FINGERPRINT, getCurrent("fingerprint"));
             * jsonObject.put(json_keys.MANUFACTURER, getCurrent("manufacturer"));
             * jsonObject.put(json_keys.DEVICE, getCurrent("device"));
             * jsonObject.put(json_keys.NAME, getCurrent("name"));
             * jsonObject.put(json_keys.BOARD, getCurrent("board"));
             */
            jsonObject.put(json_keys.CHIP, ChipInfo.which?.name)
            jsonObject.put(json_keys.DESCRIPTION, desc)
            jsonObject.put(json_keys.FREQ, getFreqData())
            if (ChipInfo.which?.ignoreVoltTable == false)
                jsonObject.put(json_keys.VOLT, getVoltData())
        } catch (e: JSONException) {
            e.printStackTrace()
        } catch (e: Exception) {
            throw IOException("Failed to prepare configuration", e)
        }
        return GzipUtils.compress(jsonObject.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun import_edittext(activity: Activity) {
        DialogUtil.showEditDialog(
            activity,
            activity.getString(R.string.import_data),
            null,
            "",
            InputType.TYPE_CLASS_TEXT
        ) { text -> showDecodeDialog(activity, text).start() }
    }

    private fun interface ConfirmExportCallback {
        fun onConfirm(desc: String, filename: String)
    }

    private fun showExportDialog(activity: Activity, confirmExportCallback: ConfirmExportCallback) {
        // Create layout with two EditTexts
        val layout = LinearLayout(activity)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        // Description field
        val descEditText = EditText(activity)
        descEditText.setHint(R.string.input_introduction_here)
        layout.addView(descEditText)

        // Filename field
        val filenameEditText = EditText(activity)
        filenameEditText.hint = activity.resources.getString(R.string.export_filename_hint)
        layout.addView(filenameEditText)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.export_data)
            .setMessage(R.string.export_data_msg)
            .setView(layout)
            .setPositiveButton(R.string.confirm) { dialog, which ->
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    dialog.dismiss()
                    val filename = filenameEditText.text.toString().trim { it <= ' ' }
                    confirmExportCallback.onConfirm(descEditText.text.toString(), filename)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create().show()
    }

    private fun export_cpy(activity: Activity, desc: String) {
        val waiting = DialogUtil.getWaitDialog(activity, R.string.prepare_import_export)
        waiting.show()

        ThreadUtil.runInBackground {
            try {
                val data = "konabess://" + getConfig(desc)
                ThreadUtil.runOnMain {
                    waiting.dismiss()
                    DialogUtil.showDetailedInfo(activity, R.string.export_done, R.string.export_done_msg, data)
                }
            } catch (e: Exception) {
                ThreadUtil.runOnMain {
                    waiting.dismiss()
                    DialogUtil.showError(activity, R.string.error_occur)
                }
            }
        }
    }

    private class exportToFile(
        var activity: Activity,
        var desc: String,
        var filename: String?
    ) : Thread() {
        var error: Boolean = false
        var waiting: AlertDialog? = null

        override fun run() {
            ThreadUtil.runInBackground {
                error = false
                ThreadUtil.runOnMain {
                    waiting = DialogUtil.getWaitDialog(activity, R.string.prepare_import_export)
                    waiting!!.show()
                }

                // Generate filename
                val finalFilename: String
                if (!filename.isNullOrEmpty()) {
                    finalFilename = if (filename!!.endsWith(".txt")) filename!! else "$filename.txt"
                } else {
                    finalFilename = "konabess-" + SimpleDateFormat("MMddHHmmss").format(Date()) + ".txt"
                }

                val destPath = Environment.getExternalStorageDirectory().absolutePath + "/" + finalFilename

                try {
                    val data = "konabess://" + getConfig(desc)
                    val tempFile = File(activity.filesDir, "temp_export.txt")
                    FileUtil.writeString(tempFile.absolutePath, data)

                    if (!RootHelper.copyFile(tempFile.absolutePath, destPath, "644")) {
                        error = true
                    }
                    tempFile.delete()
                } catch (e: Exception) {
                    error = true
                    e.printStackTrace()
                }

                val savedFilename = finalFilename
                val finalPath = destPath
                ThreadUtil.runOnMain {
                    if (waiting != null && waiting!!.isShowing) {
                        waiting!!.dismiss()
                    }
                    if (!error) {
                        val historyManager = ExportHistoryManager(activity)
                        historyManager.addExport(
                            savedFilename, desc, finalPath,
                            ChipInfo.which?.getDescription(activity) ?: "Unknown"
                        )

                        Toast.makeText(
                            activity,
                            activity.resources.getString(R.string.success_export_to) + " " + finalPath,
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(activity, R.string.failed_export, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private class showDecodeDialog(
        var activity: Activity,
        var data: String
    ) : Thread() {
        var error: Boolean = false
        var jsonObject: JSONObject? = null

        override fun run() {
            error = !data.startsWith("konabess://")
            if (!error) {
                try {
                    data = data.replace("konabess://", "")
                    val decodedData = GzipUtils.uncompress(data)
                    jsonObject = JSONObject(decodedData)
                    activity.runOnUiThread {
                        waiting_import?.dismiss()
                        try {
                            MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.going_import)
                                .setMessage(
                                    jsonObject!!.getString(json_keys.DESCRIPTION) + "\n"
                                            + activity.resources.getString(R.string.compatible_chip)
                                            + ChipInfo.Type.valueOf(jsonObject!!.getString(json_keys.CHIP))
                                        .getDescription(activity)
                                )
                                .setPositiveButton(R.string.confirm) { dialog, which ->
                                    if (which == DialogInterface.BUTTON_POSITIVE) {
                                        dialog.dismiss()
                                        waiting_import?.show()
                                        Thread {
                                            try {
                                                error = decodeAndWriteData(jsonObject!!)
                                            } catch (e: Exception) {
                                                error = true
                                            }
                                            activity.runOnUiThread {
                                                waiting_import?.dismiss()
                                                if (!error) {
                                                    Toast.makeText(
                                                        activity,
                                                        R.string.success_import,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    val act = activity
                                                    if (act is MainActivity) {
                                                        act.notifyGpuTableChanged()
                                                    }
                                                } else
                                                    Toast.makeText(
                                                        activity,
                                                        R.string.failed_incompatible,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                            }
                                        }.start()
                                    }
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .create().show()
                        } catch (e: Exception) {
                            error = true
                        }
                    }
                } catch (e: Exception) {
                    error = true
                }
            }
            if (error)
                activity.runOnUiThread {
                    waiting_import?.dismiss()
                    Toast.makeText(activity, R.string.failed_decoding, Toast.LENGTH_LONG).show()
                }
        }
    }

    // Public method for importing from data string (used by history)
    fun importFromData(activity: Activity, data: String) {
        showDecodeDialog(activity, data).start()
    }

    private fun generateView(activity: Activity, page: LinearLayout) {
        // Back navigation handled by OnBackPressedDispatcher in MainActivity
        // No custom callback needed - system back works correctly

        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        // Fix: Increase bottom padding to 88dp for navbar and set clipToPadding false
        val bottomPadding = (activity.resources.displayMetrics.density * 88).toInt()
        recyclerView.setPadding(0, 12, 0, bottomPadding)
        recyclerView.clipToPadding = false

        val items = ArrayList<ActionCardAdapter.ActionItem>()
        val canExport = KonaBessCore.isPrepared()

        items.add(
            ActionCardAdapter.ActionItem(
                R.drawable.ic_history_modern,
                activity.resources.getString(R.string.export_history),
                activity.resources.getString(R.string.export_history_desc),
                canExport
            )
        )
        items.add(
            ActionCardAdapter.ActionItem(
                R.drawable.ic_import_modern,
                activity.resources.getString(R.string.import_from_file),
                activity.resources.getString(R.string.import_from_file_msg),
                canExport
            )
        )
        items.add(
            ActionCardAdapter.ActionItem(
                R.drawable.ic_export_modern,
                activity.resources.getString(R.string.export_to_file),
                activity.resources.getString(R.string.export_to_file_msg),
                canExport
            )
        )
        items.add(
            ActionCardAdapter.ActionItem(
                R.drawable.ic_clipboard_import,
                activity.resources.getString(R.string.import_from_clipboard),
                activity.resources.getString(R.string.import_from_clipboard_msg),
                canExport
            )
        )
        items.add(
            ActionCardAdapter.ActionItem(
                R.drawable.ic_clipboard_export,
                activity.resources.getString(R.string.export_to_clipboard),
                activity.resources.getString(R.string.export_to_clipboard_msg),
                canExport
            )
        )
        items.add(
            ActionCardAdapter.ActionItem(
                R.drawable.ic_description,
                activity.resources.getString(R.string.export_raw_dts),
                activity.resources.getString(R.string.export_raw_dts_msg),
                canExport
            )
        )
        items.add(
            ActionCardAdapter.ActionItem(
                R.drawable.ic_backup,
                activity.resources.getString(R.string.backup_image),
                activity.resources.getString(R.string.backup_image_desc),
                canExport
            )
        )

        val adapter = ActionCardAdapter(items)
        adapter.setOnItemClickListener { position ->
            val selectedItem = items[position]
            if (!selectedItem.enabled) {
                Toast.makeText(activity, R.string.export_requires_chipset, Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            if (position == 0) {
                // Open history activity
                val intent = Intent(activity, ExportHistoryActivity::class.java)
                activity.startActivity(intent)
            } else if (position == 1) {
                if (activity is MainActivity) {
                    (activity as MainActivity).runWithFilePath { intent ->
                        if (intent?.data != null) {
                            val uri = intent.data
                            activity.runOnUiThread {
                                waiting_import?.show()
                            }
                            ThreadUtil.runInBackground {
                                try {
                                    val bufferedReader = BufferedReader(
                                        InputStreamReader(activity.contentResolver.openInputStream(uri!!))
                                    )
                                    showDecodeDialog(activity, bufferedReader.readLine()).start()
                                    bufferedReader.close()
                                } catch (e: Exception) {
                                    activity.runOnUiThread {
                                        Toast.makeText(
                                            activity,
                                            R.string.unable_get_target_file, Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                        Unit
                    }
                }
            } else if (position == 2) {
                showExportDialog(activity) { desc, filename ->
                    if (activity is MainActivity) {
                        (activity as MainActivity).runWithStoragePermission {
                            exportToFile(activity, desc, filename).start()
                            Unit
                        }
                    }
                }
            } else if (position == 3) {
                import_edittext(activity)
            } else if (position == 4) {
                showExportDialog(activity) { desc, filename ->
                    export_cpy(activity, desc)
                }
            } else if (position == 5) {
                if (activity is MainActivity) {
                    (activity as MainActivity).runWithStoragePermission {
                        exportRawDts(activity).start()
                        Unit
                    }
                }
            } else if (position == 6) {
                val mainActivity = activity as MainActivity

                // Backup path is now always in internal storage root
                val backupPath = "/sdcard/" + KonaBessCore.boot_name + ".img"

                MaterialAlertDialogBuilder(mainActivity)
                    .setTitle(R.string.backup_old_image)
                    .setMessage(activity.getResources().getString(R.string.will_backup_to) + " " + backupPath)
                    .setPositiveButton(R.string.ok) { dialog, which ->
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            dialog.dismiss()
                            mainActivity.runWithStoragePermission {
                                ThreadUtil.runInBackground {
                                    if (RootHelper.run("dd if=/dev/block/bootdevice/by-name/" + KonaBessCore.boot_name + " of=" + backupPath)) {
                                        ThreadUtil.runOnMain {
                                            Toast.makeText(
                                                activity,
                                                R.string.backup_success,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        ThreadUtil.runOnMain {
                                            Toast.makeText(
                                                activity,
                                                R.string.backup_fail,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                Unit
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .create().show()
            }
        }

        recyclerView.adapter = adapter

        page.removeAllViews()
        if (!canExport) {
            val hintView = TextView(activity)
            hintView.setText(R.string.export_requires_chipset)
            hintView.setPadding(0, 0, 0, 24)
            page.addView(
                hintView, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // Use weight 1.0f to fill remaining vertical space, ensuring scrolling works
        val recyclerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1.0f
        )
        page.addView(recyclerView, recyclerParams)
    }

    private class exportRawDts(
        var activity: Activity
    ) : Thread() {
        var error: Boolean = false
        var destPath: String? = null

        override fun run() {
            ThreadUtil.runInBackground {
                error = false
                val timestamp = SimpleDateFormat("MMddHHmmss").format(Date())
                val filename = "konabess_export_$timestamp.dts"
                destPath = Environment.getExternalStorageDirectory().absolutePath + "/" + filename

                val srcFile = File(KonaBessCore.dts_path)

                try {
                    // Use centralized root copy utility
                    if (!RootHelper.copyFile(srcFile.absolutePath, destPath!!)) {
                        // Fallback to java IO if shell fails
                        FileUtil.copyFile(srcFile, File(destPath!!))
                    }
                } catch (e: Exception) {
                    error = true
                    e.printStackTrace()
                }

                ThreadUtil.runOnMain {
                    if (!error) {
                        // Add to history
                        val historyManager = ExportHistoryManager(activity)
                        var chipType = "Unknown"
                        if (ChipInfo.which !== ChipInfo.Type.unknown) {
                            chipType = ChipInfo.which!!.name
                        }
                        historyManager.addExport(
                            filename, "Raw DTS Export (Main Menu)", destPath!!, chipType
                        )

                        Toast.makeText(
                            activity,
                            activity.resources.getString(R.string.success_export_to) + " " + destPath,
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(activity, R.string.failed_export, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    class TableIOLogic(
        var activity: Activity,
        var showedView: LinearLayout
    ) : Thread() {
        var page: LinearLayout? = null

        override fun run() {
            activity.runOnUiThread {
                if (waiting_import == null || waiting_import!!.context != activity) {
                    if (waiting_import != null && waiting_import!!.isShowing) {
                        waiting_import!!.dismiss()
                    }
                    waiting_import = DialogUtil.getWaitDialog(activity, R.string.wait_importing)
                }
                showedView.removeAllViews()
                page = LinearLayout(activity)
                page!!.orientation = LinearLayout.VERTICAL
                try {
                    generateView(activity, page!!)
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.error_occur)
                }
                showedView.addView(page)
            }
        }
    }
}
