package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.utils.ChipStringHelper
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel
import com.ireddragonicy.konabessnext.utils.DialogUtil
import com.ireddragonicy.konabessnext.utils.DtsHelper
import java.util.Locale
import java.util.Objects

/**
 * Handles frequency edit dialog with unit conversion (Hz, MHz, GHz).
 */
object FrequencyDialogHelper {

    fun interface OnFrequencySavedListener {
        @Throws(Exception::class)
        fun onSaved(lineIndex: Int, encodedLine: String, freqLabel: String)
    }

    /**
     * Show frequency edit dialog with unit selector.
     */
    @JvmStatic
    fun showFrequencyEditDialog(
        activity: Activity, bins: ArrayList<Bin>,
        binIndex: Int, levelIndex: Int, lineIndex: Int,
        rawName: String, rawValue: String, paramTitle: String,
        listener: OnFrequencySavedListener
    ) {
        val container = LinearLayout(activity)
        container.orientation = LinearLayout.VERTICAL
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        container.setPadding(padding, padding, padding, 0)

        val inputRow = LinearLayout(activity)
        inputRow.orientation = LinearLayout.HORIZONTAL

        val editText = EditText(activity)
        editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val editParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )
        editText.layoutParams = editParams
        editText.hint = activity.getString(R.string.enter_frequency)

        val unitSpinner = Spinner(activity)
        val units = arrayOf(activity.getString(R.string.hz), activity.getString(R.string.mhz), activity.getString(R.string.ghz))
        val unitAdapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item, units
        )
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        unitSpinner.adapter = unitAdapter

        var currentHz: Long = 0
        try {
            currentHz = rawValue.toLong()
        } catch (ignored: NumberFormatException) {
        }

        val prefs = activity.getSharedPreferences(
            SettingsViewModel.PREFS_NAME, android.content.Context.MODE_PRIVATE
        )
        val preferredUnit = prefs.getInt(SettingsViewModel.KEY_FREQ_UNIT, SettingsViewModel.FREQ_UNIT_MHZ)

        val finalCurrentHz = currentHz
        when (preferredUnit) {
            SettingsViewModel.FREQ_UNIT_HZ -> {
                editText.setText(currentHz.toString())
                unitSpinner.setSelection(0)
            }
            SettingsViewModel.FREQ_UNIT_MHZ -> {
                editText.setText((currentHz / 1000000).toString())
                unitSpinner.setSelection(1)
            }
            SettingsViewModel.FREQ_UNIT_GHZ -> {
                editText.setText(String.format(Locale.US, "%.3f", currentHz / 1000000000.0))
                unitSpinner.setSelection(2)
            }
            else -> {
                editText.setText((currentHz / 1000000).toString())
                unitSpinner.setSelection(1)
            }
        }

        inputRow.addView(editText)
        inputRow.addView(unitSpinner)

        val previewText = TextView(activity)
        previewText.setPadding(0, padding / 2, 0, 0)
        previewText.textSize = 12f
        previewText.text = "= " + activity.getString(R.string.format_hz, finalCurrentHz)

        val previousUnit = IntArray(1)
        previousUnit[0] = when (preferredUnit) {
            SettingsViewModel.FREQ_UNIT_HZ -> 0
            SettingsViewModel.FREQ_UNIT_GHZ -> 2
            else -> 1
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                updateFrequencyPreview(activity, editText, unitSpinner, previewText)
            }
        }
        editText.addTextChangedListener(textWatcher)

        unitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val oldUnit = previousUnit[0]
                val newUnit = position

                if (oldUnit != newUnit) {
                    try {
                        val currentText = editText.text.toString().trim()
                        if (currentText.isNotEmpty()) {
                            val hzValue = parseFrequencyToHz(currentText, oldUnit)
                            if (hzValue > 0) {
                                val newText = when (newUnit) {
                                    0 -> hzValue.toString() // Hz
                                    1 -> (hzValue / 1000000).toString() // MHz
                                    2 -> String.format(Locale.US, "%.3f", hzValue / 1000000000.0) // GHz
                                    else -> currentText
                                }
                                editText.setText(newText)
                                editText.setSelection(newText.length)
                            }
                        }
                    } catch (ignored: Exception) {
                    }
                    previousUnit[0] = newUnit
                }
                updateFrequencyPreview(activity, editText, unitSpinner, previewText)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        container.addView(inputRow)
        container.addView(previewText)

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.edit) + " \"" + paramTitle + "\"")
            .setView(container)
            .setMessage(ChipStringHelper.help(rawName, activity))
            .setPositiveButton(R.string.save) { dialog, which ->
                try {
                    // Sanitize Input first
                    val rawInput = editText.text.toString().replace(Regex("[^0-9.]"), "")
                    
                    val hzValue = parseFrequencyToHz(
                        rawInput,
                        unitSpinner.selectedItemPosition
                    )
                    
                    // Range Validation: 1 MHz to 2000 MHz
                    // 1 MHz = 1,000,000 Hz
                    // 2000 MHz = 2,000,000,000 Hz
                    val MIN_FREQ_HZ = 1_000_000L
                    val MAX_FREQ_HZ = 2_000_000_000L
                    
                    if (hzValue < MIN_FREQ_HZ || hzValue > MAX_FREQ_HZ) {
                        Toast.makeText(activity, "Frequency must be between 1 MHz and 2000 MHz", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    
                    val newValue = hzValue.toString()
                    val encodedLine = DtsHelper.encodeIntOrHexLine(rawName, newValue)
                    val existingLine = bins[binIndex].levels[levelIndex].lines[lineIndex]
                    if (existingLine == encodedLine) {
                        return@setPositiveButton
                    }
                    val freqLabel = com.ireddragonicy.konabessnext.utils.FrequencyFormatter.format(activity, hzValue)
                    listener.onSaved(lineIndex, encodedLine, freqLabel)
                    Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.save_failed)
                    e.printStackTrace()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create().show()
    }

    private fun updateFrequencyPreview(activity: Activity, editText: EditText, unitSpinner: Spinner, previewText: TextView) {
        try {
            // Sanitize in preview too
            val rawInput = editText.text.toString().replace(Regex("[^0-9.]"), "")
            val hz = parseFrequencyToHz(rawInput, unitSpinner.selectedItemPosition)
            if (hz > 0) {
                previewText.text = "= " + activity.getString(R.string.format_hz, hz)
            } else {
                previewText.text = "= ? " + activity.getString(R.string.hz)
            }
        } catch (e: Exception) {
            previewText.text = "= ? " + activity.getString(R.string.hz)
        }
    }

    @JvmStatic
    fun parseFrequencyToHz(value: String, unitIndex: Int): Long {
        return try {
            if (value.isBlank()) return -1
            val inputValue = value.trim().toDouble()
            when (unitIndex) {
                0 -> inputValue.toLong() // Hz
                1 -> (inputValue * 1000000).toLong() // MHz
                2 -> (inputValue * 1000000000).toLong() // GHz
                else -> inputValue.toLong()
            }
        } catch (e: NumberFormatException) {
            -1
        }
    }

    @JvmStatic
    fun formatFrequencyWithUnit(hz: Long, preferredUnit: Int, context: android.content.Context): String {
        return com.ireddragonicy.konabessnext.utils.FrequencyFormatter.format(context, hz, preferredUnit)
    }
}
