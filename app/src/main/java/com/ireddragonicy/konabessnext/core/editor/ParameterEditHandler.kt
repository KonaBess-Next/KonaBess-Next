package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import android.text.InputType
import android.widget.Toast
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.core.GpuVoltEditor
import com.ireddragonicy.konabessnext.data.KonaBessStr
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import com.ireddragonicy.konabessnext.utils.DialogUtil
import com.ireddragonicy.konabessnext.utils.DtsHelper
import java.util.Objects

/**
 * Handles parameter editing for different parameter types:
 * - Voltage levels (spinner)
 * - GPU frequency (unit-aware dialog)
 * - Generic parameters (text input)
 */
object ParameterEditHandler {

    interface OnParameterEditedListener {
        @Throws(Exception::class)
        fun onEdited(lineIndex: Int, encodedLine: String, historyMessage: String)

        @Throws(Exception::class)
        fun refreshView()
    }

    /**
     * Handle parameter edit based on parameter type.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun handleParameterEdit(
        activity: Activity, bins: ArrayList<Bin>,
        binIndex: Int, levelIndex: Int, lineIndex: Int,
        rawName: String, rawValue: String, paramTitle: String,
        listener: OnParameterEditedListener
    ) {

        // Handle voltage level editing with spinner
        if (rawName == "qcom,level" || rawName == "qcom,cx-level") {
            handleVoltageLevelEdit(
                activity, bins, binIndex, levelIndex, lineIndex,
                rawName, rawValue, listener
            )
        } else if (rawName == "qcom,gpu-freq") {
            FrequencyDialogHelper.showFrequencyEditDialog(
                activity, bins, binIndex, levelIndex, lineIndex,
                rawName, rawValue, paramTitle
            ) { idx, encodedLine, freqLabel ->
                val historyMsg =
                    activity.getString(R.string.history_edit_parameter, paramTitle, freqLabel)
                listener.onEdited(idx, encodedLine, historyMsg)
                listener.refreshView()
            }
        } else {
            handleGenericParameterEdit(
                activity, bins, binIndex, levelIndex, lineIndex,
                rawName, rawValue, paramTitle, listener
            )
        }
    }

    /**
     * Handle voltage level editing with spinner selector.
     */
    private fun handleVoltageLevelEdit(
        activity: Activity, bins: ArrayList<Bin>,
        binIndex: Int, levelIndex: Int, lineIndex: Int,
        rawName: String, rawValue: String,
        listener: OnParameterEditedListener
    ) {
        DialogUtil.showSingleChoiceDialog(
            activity,
            activity.getString(R.string.edit),
            ChipInfo.rpmh_levels?.level_str() ?: emptyArray(),
            getLevelIndexSafe(rawValue)
        ) { dialog, which ->
            try {
                val newValue = ChipInfo.rpmh_levels?.levels()?.get(which)?.toString() ?: "0"
                val encodedLine = DtsHelper.encodeIntOrHexLine(rawName, newValue)
                val existingLine = bins[binIndex].levels[levelIndex].lines[lineIndex]

                if (existingLine == encodedLine) {
                    dialog.dismiss()
                    return@showSingleChoiceDialog
                }

                val freqLabel = SettingsActivity.formatFrequency(
                    LevelOperations.getFrequencyFromLevel(bins[binIndex].levels[levelIndex]),
                    activity
                )
                val historyMsg = activity.getString(R.string.history_update_voltage_level, freqLabel)

                listener.onEdited(lineIndex, encodedLine, historyMsg)
                dialog.dismiss()
                listener.refreshView()
                Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                DialogUtil.showError(activity, R.string.save_failed)
                e.printStackTrace()
            }
        }
    }

    /**
     * Handle generic parameter editing with text input.
     */
    private fun handleGenericParameterEdit(
        activity: Activity, bins: ArrayList<Bin>,
        binIndex: Int, levelIndex: Int, lineIndex: Int,
        rawName: String, rawValue: String, paramTitle: String,
        listener: OnParameterEditedListener
    ) {
        val inputType =
            if (DtsHelper.shouldUseHex(rawName)) InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_NUMBER

        DialogUtil.showEditDialog(
            activity,
            activity.resources.getString(R.string.edit) + " \"" + paramTitle + "\"",
            KonaBessStr.help(rawName, activity),
            rawValue,
            inputType
        ) { text ->
            try {
                val encodedLine = DtsHelper.encodeIntOrHexLine(rawName, text)
                val existingLine = bins[binIndex].levels[levelIndex].lines[lineIndex]

                if (existingLine == encodedLine) {
                    return@showEditDialog
                }

                val freqLabel = SettingsActivity.formatFrequency(
                    LevelOperations.getFrequencyFromLevel(bins[binIndex].levels[levelIndex]),
                    activity
                )
                val historyMsg =
                    activity.getString(R.string.history_edit_parameter, paramTitle, freqLabel)

                listener.onEdited(lineIndex, encodedLine, historyMsg)
                listener.refreshView()
                Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                DialogUtil.showError(activity, R.string.save_failed)
            }
        }
    }

    /**
     * Handle quick frequency adjustment (+/- MHz buttons).
     */
    @JvmStatic
    fun handleFrequencyAdjust(
        activity: Activity, bins: ArrayList<Bin>,
        binIndex: Int, levelIndex: Int, lineIndex: Int,
        rawName: String, deltaMHz: Int,
        listener: OnParameterEditedListener
    ) {
        try {
            val line = bins[binIndex].levels[levelIndex].lines[lineIndex]

            var currentVal: Long = 0
            if (DtsHelper.shouldUseHex(line)) {
                val hexVal = DtsHelper.decode_hex_line(line).value!!
                currentVal = java.lang.Long.parseLong(hexVal.replace("0x", ""), 16)
            } else {
                currentVal = DtsHelper.decode_int_line(line).value
            }

            var newVal = currentVal + (deltaMHz * 1000000L)
            if (newVal < 0) newVal = 0

            val newValueStr = newVal.toString()
            val encodedLine = DtsHelper.encodeIntOrHexLine(rawName, newValueStr)
            val freqLabel = SettingsActivity.formatFrequency(newVal, activity)
            val historyMsg =
                activity.getString(R.string.history_edit_parameter, "GPU Frequency", freqLabel)

            listener.onEdited(lineIndex, encodedLine, historyMsg)
            listener.refreshView()

        } catch (e: Exception) {
            DialogUtil.showError(activity, R.string.save_failed)
            e.printStackTrace()
        }
    }

    private fun getLevelIndexSafe(rawValue: String): Int {
        return try {
            GpuVoltEditor.levelint2int(rawValue.toLong())
        } catch (e: Exception) {
            0
        }
    }
}
