package com.ireddragonicy.konabessnext.utils

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import java.util.concurrent.CountDownLatch

/**
 * Modern Material You Dialog Utility
 */
object DialogUtil {

    /**
     * Show error dialog
     */
    @JvmStatic
    fun showError(activity: Activity, text: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.error)
            .setMessage(text)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> activity.finish() }
            .show()
    }

    @JvmStatic
    fun showError(activity: Activity, text_res: Int) {
        showError(activity, activity.resources.getString(text_res))
    }

    /**
     * Show error dialog with detailed copyable text
     */
    @JvmStatic
    fun showDetailedError(activity: Activity, err: String, detail: String) {
        val message = err + "\n" + activity.resources.getString(R.string.long_press_to_copy)

        val scrollView = ScrollView(activity)
        scrollView.setPadding(48, 24, 48, 0)

        val textView = TextView(activity)
        textView.setTextIsSelectable(true)
        textView.text = detail
        textView.textSize = 14f

        scrollView.addView(textView)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.error)
            .setMessage(message)
            .setOnDismissListener { activity.finish() }
            .setView(scrollView)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    @JvmStatic
    fun showDetailedError(activity: Activity, err: Int, detail: String) {
        showDetailedError(activity, activity.resources.getString(err), detail)
    }

    /**
     * Show info dialog with details
     */
    @JvmStatic
    fun showDetailedInfo(activity: Activity, title: String, what: String, detail: String) {
        val message = what + "\n" + activity.resources.getString(R.string.long_press_to_copy)

        val scrollView = ScrollView(activity)
        scrollView.setPadding(48, 24, 48, 0)

        val textView = TextView(activity)
        textView.setTextIsSelectable(true)
        textView.text = detail
        textView.textSize = 14f

        scrollView.addView(textView)

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setView(scrollView)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    @JvmStatic
    fun showDetailedInfo(activity: Activity, title: Int, what: Int, detail: String) {
        showDetailedInfo(activity, activity.resources.getString(title),
            activity.resources.getString(what), detail)
    }

    /**
     * Create wait dialog
     */
    @JvmStatic
    fun getWaitDialog(context: Context, id: Int): AlertDialog {
        return getWaitDialog(context, context.resources.getString(id))
    }

    @JvmStatic
    fun getWaitDialog(context: Context, text: String): AlertDialog {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        container.setPadding(48, 32, 48, 32)
        container.gravity = android.view.Gravity.CENTER_VERTICAL

        val progressBar = ProgressBar(context)
        val progressParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        progressParams.marginEnd = 32
        progressBar.layoutParams = progressParams

        val textView = TextView(context)
        textView.text = text
        textView.textSize = 16f

        container.addView(progressBar)
        container.addView(textView)

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.please_wait)
            .setCancelable(false)
            .setView(container)
            .create()
    }

    @JvmStatic
    fun createProgressDialog(activity: Activity): ProgressDialogController {
        return ProgressDialogController(activity)
    }

    class ProgressDialogController(private val activity: Activity) {
        private var dialog: AlertDialog? = null
        private var messageView: TextView? = null

        init {
            initializeDialog()
        }

        private fun initializeDialog() {
            if (!isActivityUsable()) return

            val latch = CountDownLatch(1)
            activity.runOnUiThread {
                try {
                    if (!isActivityUsable()) return@runOnUiThread

                    val container = LinearLayout(activity)
                    container.orientation = LinearLayout.HORIZONTAL
                    container.setPadding(48, 32, 48, 32)
                    container.gravity = android.view.Gravity.CENTER_VERTICAL

                    val progressBar = ProgressBar(activity)
                    val progressParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    progressParams.marginEnd = 32
                    progressBar.layoutParams = progressParams

                    messageView = TextView(activity)
                    messageView!!.textSize = 16f

                    container.addView(progressBar)
                    container.addView(messageView)

                    dialog = MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.please_wait)
                        .setCancelable(false)
                        .setView(container)
                        .create()
                } finally {
                    latch.countDown()
                }
            }

            try {
                latch.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        fun show(@StringRes messageRes: Int) {
            show(activity.getString(messageRes))
        }

        fun show(message: String) {
            if (!isActivityUsable() || dialog == null || messageView == null) return
            activity.runOnUiThread {
                if (!isActivityUsable() || dialog == null || messageView == null) return@runOnUiThread
                messageView!!.text = message
                if (!dialog!!.isShowing) {
                    dialog!!.show()
                }
            }
        }

        fun updateMessage(@StringRes messageRes: Int) {
            updateMessage(activity.getString(messageRes))
        }

        fun updateMessage(message: String) {
            if (!isActivityUsable() || messageView == null) return
            activity.runOnUiThread { messageView!!.text = message }
        }

        fun dismiss() {
            if (!isActivityUsable() || dialog == null) return
            activity.runOnUiThread {
                if (!isActivityUsable() || dialog == null) return@runOnUiThread
                if (dialog!!.isShowing) {
                    dialog!!.dismiss()
                }
            }
        }

        private fun isActivityUsable(): Boolean {
            if (activity.isFinishing) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed) {
                return false
            }
            return true
        }
    }

    @JvmStatic
    fun showConfirmation(context: Context, title: String, message: String,
                         positiveListener: DialogInterface.OnClickListener?) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm, positiveListener)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @JvmStatic
    fun showConfirmation(context: Context, title: Int, message: Int,
                         positiveListener: DialogInterface.OnClickListener?) {
        showConfirmation(context,
            context.resources.getString(title),
            context.resources.getString(message),
            positiveListener)
    }

    @JvmStatic
    fun showInfo(context: Context, title: String, message: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    @JvmStatic
    fun showInfo(context: Context, title: Int, message: Int) {
        showInfo(context,
            context.resources.getString(title),
            context.resources.getString(message))
    }

    @JvmStatic
    fun createDialogBuilder(context: Context): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(context)
    }

    @JvmStatic
    fun showEditDialog(activity: Activity, title: String, message: String?,
                       initialValue: String, inputType: Int,
                       listener: OnInputListener?) {
        val input = EditText(activity)
        input.setText(initialValue)
        input.inputType = inputType

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                listener?.onInput(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun interface OnInputListener {
        fun onInput(text: String)
    }

    @JvmStatic
    fun showSingleChoiceDialog(activity: Activity, title: String,
                               items: Array<String>, checkedItem: Int,
                               listener: DialogInterface.OnClickListener?) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setSingleChoiceItems(items, checkedItem, listener)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @JvmStatic
    fun showEditDialog(activity: Activity, @StringRes title: Int, @StringRes message: Int, listener: (String) -> Unit) {
        val input = EditText(activity)
        val container = android.widget.FrameLayout(activity)
        val params = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = 48 // Px or Dp? strictly speaking should convert dp to px. 
        // Using simple padding on view or container is safer.
        // Let's us container padding.
        container.setPadding(50, 0, 50, 0)
        input.layoutParams = params
        container.addView(input)
        
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton(R.string.confirm) { _, _ -> listener(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    @JvmStatic
    fun showConfirmDialog(activity: Activity, @StringRes title: Int, @StringRes message: Int, onConfirm: () -> Unit) {
         MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
