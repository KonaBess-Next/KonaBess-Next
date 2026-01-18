package com.ireddragonicy.konabessnext.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import java.util.Locale

object LocaleUtil {
    private const val PREFS_NAME = "KonaBessSettings"
    private const val KEY_LANGUAGE = "language"

    @JvmStatic
    fun wrap(context: Context): Context {
        return applyLocale(context)
    }

    private fun applyLocale(context: Context): Context {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val language = prefs.getString(KEY_LANGUAGE, SettingsActivity.LANGUAGE_ENGLISH) ?: SettingsActivity.LANGUAGE_ENGLISH

        val locale = Locale(language)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
        } else {
            configuration.setLocale(locale)
        }

        return context.createConfigurationContext(configuration)
    }
}
