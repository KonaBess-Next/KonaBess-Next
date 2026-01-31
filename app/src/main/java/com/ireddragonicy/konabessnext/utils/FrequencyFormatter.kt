package com.ireddragonicy.konabessnext.utils

import android.content.Context
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.SettingsActivity

object FrequencyFormatter {
    
    fun format(context: Context, hz: Long): String {
        // We use the direct shared preference access here as it's a utility, 
        // passing context is flexible. 
        // Alternatively we could injecting preferences but this is a static helper mostly.
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val unit = prefs.getInt(SettingsActivity.KEY_FREQ_UNIT, SettingsActivity.FREQ_UNIT_MHZ)
        return format(context, hz, unit)
    }

    fun format(context: Context, hz: Long, unit: Int): String {
        return when (unit) {
            SettingsActivity.FREQ_UNIT_HZ -> context.getString(R.string.format_hz, hz)
            SettingsActivity.FREQ_UNIT_MHZ -> context.getString(R.string.format_mhz, hz / 1_000_000L)
            SettingsActivity.FREQ_UNIT_GHZ -> context.getString(R.string.format_ghz, hz / 1_000_000_000.0)
            else -> context.getString(R.string.format_mhz, hz / 1_000_000L)
        }
    }
}
