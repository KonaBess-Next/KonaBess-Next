package com.ireddragonicy.konabessnext.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel
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
        val language = prefs.getString(KEY_LANGUAGE, SettingsViewModel.LANGUAGE_ENGLISH) ?: SettingsViewModel.LANGUAGE_ENGLISH

        val locale = parseLocale(language)
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

    private fun parseLocale(localeStr: String): Locale {
        // Fix for "zh-rCN" which is a common legacy Android format
        val normalized = localeStr.replace("-r", "-").replace("_", "-")
        val parts = normalized.split("-")
        
        return when (parts.size) {
            1 -> Locale.Builder().setLanguage(parts[0]).build()
            2 -> Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build()
            3 -> Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).setVariant(parts[2]).build()
            else -> Locale.Builder().setLanguage(localeStr).build()
        }
    }
}
