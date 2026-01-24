package com.ireddragonicy.konabessnext.utils

import android.content.Context
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.ChipInfo

object ChipStringHelper {

    private const val QCOM_PREFIX = "qcom,"
    private const val GPU_FREQ = "gpu-freq"
    private const val LEVEL = "level"
    private const val BUS = "bus"
    private const val ACD = "acd"

    fun convertBins(which: Int, context: Context): String {
        val current = ChipInfo.current ?: return context.getString(R.string.unknown_table) + which
        
        // Priority 1: Check dynamic mapping in ChipDefinition (from JSON)
        val description = current.binDescriptions?.get(which)
        if (description != null) {
            return description
        }

        // Fallback: If no mapping found
        return context.getString(R.string.unknown_table) + which
    }

    fun convertLevelParams(input: String, context: Context): String {
        val processed = input.replace(QCOM_PREFIX, "")

        if (GPU_FREQ == processed) {
            return context.getString(R.string.freq)
        }
        if (LEVEL == processed) {
            return context.getString(R.string.volt)
        }

        return processed
    }

    fun help(what: String, context: Context): String {
        val current = ChipInfo.current
        if (what == QCOM_PREFIX + GPU_FREQ) {
            val ignore = current?.ignoreVoltTable == true
            return context.getString(
                if (ignore) R.string.help_gpufreq_aio else R.string.help_gpufreq
            )
        }
        if (what.contains(BUS)) {
            return context.getString(R.string.help_bus)
        }
        if (what.contains(ACD)) {
            return context.getString(R.string.help_acd)
        }
        return ""
    }

    fun genericHelp(context: Context): String {
        val current = ChipInfo.current
        val ignore = current?.ignoreVoltTable == true
        return context.getString(
            if (ignore) R.string.help_msg_aio else R.string.help_msg
        )
    }
}
