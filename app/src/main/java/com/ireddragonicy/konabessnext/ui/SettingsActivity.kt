package com.ireddragonicy.konabessnext.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.utils.ChipStringHelper
import com.ireddragonicy.konabessnext.ui.compose.SettingsScreen
import com.ireddragonicy.konabessnext.utils.LocaleUtil
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "KonaBessSettings"
        const val KEY_LANGUAGE = "language"
        const val KEY_FREQ_UNIT = "freq_unit"
        const val KEY_THEME = "theme"
        const val KEY_COLOR_PALETTE = "color_palette"
        const val KEY_AUTO_SAVE_GPU_TABLE = "auto_save_gpu_table"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"

        const val FREQ_UNIT_HZ = 0
        const val FREQ_UNIT_MHZ = 1
        const val FREQ_UNIT_GHZ = 2

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val PALETTE_DYNAMIC = 0
        const val PALETTE_PURPLE = 1
        const val PALETTE_BLUE = 2
        const val PALETTE_GREEN = 3
        const val PALETTE_PINK = 4
        const val PALETTE_AMOLED = 5

        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_GERMAN = "de"
        const val LANGUAGE_CHINESE = "zh-rCN"
        const val LANGUAGE_INDONESIAN = "in"

        @JvmStatic
        fun formatFrequency(frequencyHz: Long, unit: Int, context: Context): String {
            return when (unit) {
                FREQ_UNIT_HZ -> context.getString(R.string.format_hz, frequencyHz)
                FREQ_UNIT_MHZ -> context.getString(R.string.format_mhz, frequencyHz / 1000000L)
                FREQ_UNIT_GHZ -> context.getString(R.string.format_ghz, frequencyHz / 1000000000.0)
                else -> context.getString(R.string.format_mhz, frequencyHz / 1000000L)
            }
        }

        @JvmStatic
        fun formatFrequency(frequencyHz: Long, context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val unit = prefs.getInt(KEY_FREQ_UNIT, FREQ_UNIT_MHZ)
            return formatFrequency(frequencyHz, unit, context)
        }

        @JvmStatic
        fun applyThemeFromSettings(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)

            if (dynamicColor) {
                context.setTheme(R.style.Theme_KonaBess)
            } else {
                val palette = prefs.getInt(KEY_COLOR_PALETTE, PALETTE_PURPLE)
                when (palette) {
                    PALETTE_PURPLE -> context.setTheme(R.style.Theme_KonaBess_Purple)
                    PALETTE_BLUE -> context.setTheme(R.style.Theme_KonaBess_Blue)
                    PALETTE_GREEN -> context.setTheme(R.style.Theme_KonaBess_Green)
                    PALETTE_PINK -> context.setTheme(R.style.Theme_KonaBess_Pink)
                    PALETTE_AMOLED -> context.setTheme(R.style.Theme_KonaBess_AMOLED)
                    else -> context.setTheme(R.style.Theme_KonaBess_Purple)
                }
            }

            val theme = prefs.getInt(KEY_THEME, THEME_SYSTEM)
            applyThemeMode(theme)
        }

        @JvmStatic
        fun isAutoSaveEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_SAVE_GPU_TABLE, false)
        }

        private fun applyThemeMode(theme: Int) {
            val modes = intArrayOf(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES
            )
            AppCompatDelegate.setDefaultNightMode(modes[theme])
        }
    }

    private lateinit var prefs: SharedPreferences

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtil.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applyTheme()
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(this).apply {
            setContent {
                SettingsScreen(
                    currentTheme = currentThemeName,
                    isDynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
                    currentColorPalette = currentColorPaletteName,
                    currentLanguage = currentLanguageName,
                    currentFreqUnit = currentFreqUnitName,
                    isAutoSave = prefs.getBoolean(KEY_AUTO_SAVE_GPU_TABLE, false),
                    onThemeClick = { showThemeDialog() },
                    onDynamicColorToggle = { toggleDynamicColor() },
                    onColorPaletteClick = { showColorPaletteDialog() },
                    onLanguageClick = { showLanguageDialog() },
                    onFreqUnitClick = { showFreqUnitDialog() },
                    onAutoSaveToggle = { toggleAutoSave() },
                    onHelpClick = { showHelpDialog() }
                )
            }
        }
        setContentView(composeView)
    }

    private fun applyTheme() {
        val dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)

        if (dynamicColor) {
            setTheme(R.style.Theme_KonaBess)
        } else {
            val palette = prefs.getInt(KEY_COLOR_PALETTE, PALETTE_PURPLE)
            when (palette) {
                PALETTE_PURPLE -> setTheme(R.style.Theme_KonaBess_Purple)
                PALETTE_BLUE -> setTheme(R.style.Theme_KonaBess_Blue)
                PALETTE_GREEN -> setTheme(R.style.Theme_KonaBess_Green)
                PALETTE_PINK -> setTheme(R.style.Theme_KonaBess_Pink)
                PALETTE_AMOLED -> setTheme(R.style.Theme_KonaBess_AMOLED)
                else -> setTheme(R.style.Theme_KonaBess_Purple)
            }
        }
        applyThemeMode(savedTheme)
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            getString(R.string.english),
            getString(R.string.german),
            getString(R.string.chinese),
            getString(R.string.indonesian)
        )
        val languageCodes = arrayOf(LANGUAGE_ENGLISH, LANGUAGE_GERMAN, LANGUAGE_CHINESE, LANGUAGE_INDONESIAN)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.language))
            .setSingleChoiceItems(languages, currentLanguageIndex) { dialog, which ->
                saveLanguage(languageCodes[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.theme))
            .setSingleChoiceItems(themes, savedTheme) { dialog, which ->
                saveTheme(which)
                dialog.dismiss()
                applyTheme()
                recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    private fun showFreqUnitDialog() {
        val units = arrayOf(getString(R.string.hz), getString(R.string.mhz), getString(R.string.ghz))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.gpu_freq_unit))
            .setSingleChoiceItems(units, savedFreqUnit) { dialog, which ->
                saveFreqUnit(which)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.help))
            .setMessage(ChipStringHelper.genericHelp(this))
            .setPositiveButton(getString(R.string.ok), null)
            .setNeutralButton(getString(R.string.about)) { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.about))
                    .setMessage(
                        resources.getString(R.string.author) + " " +
                                "IRedDragonICY & xzr467706992 (LibXZR)\n"
                                + resources.getString(R.string.release_at) + " www.akr-developers.com\n"
                    )
                    .setPositiveButton(getString(R.string.ok), null)
                    .setNegativeButton("Github") { _, _ ->
                        startActivity(Intent().apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse("https://github.com/ireddragonicy/KonaBess")
                        })
                    }
                    .setNeutralButton(getString(R.string.visit_akr)) { _, _ ->
                        startActivity(Intent().apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse("https://www.akr-developers.com/d/441")
                        })
                    }
                    .create().show()
            }
            .create().show()
    }

    private fun toggleAutoSave() {
        val enabled = !isAutoSaveEnabled
        prefs.edit().putBoolean(KEY_AUTO_SAVE_GPU_TABLE, enabled).apply()
        recreate()
        Toast.makeText(
            this, if (enabled) R.string.auto_save_enabled_toast else R.string.auto_save_disabled_toast,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleDynamicColor() {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, !isDynamicColorEnabled).apply()
        recreate()
    }

    private fun showColorPaletteDialog() {
        val palettes = arrayOf(
            getString(R.string.palette_purple_teal),
            getString(R.string.palette_blue_orange),
            getString(R.string.palette_green_red),
            getString(R.string.palette_pink_cyan),
            getString(R.string.palette_amoled)
        )
        val paletteIds = intArrayOf(PALETTE_PURPLE, PALETTE_BLUE, PALETTE_GREEN, PALETTE_PINK, PALETTE_AMOLED)

        val currentPalette = savedColorPalette
        var initialSelection = 0
        for (i in paletteIds.indices) {
            if (paletteIds[i] == currentPalette) { initialSelection = i; break }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.color_palette))
            .setSingleChoiceItems(palettes, initialSelection) { dialog, which ->
                saveColorPalette(paletteIds[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    private val currentLanguageName: String
        get() {
            val names = arrayOf(getString(R.string.english), getString(R.string.german), getString(R.string.chinese), getString(R.string.indonesian))
            val codes = arrayOf(LANGUAGE_ENGLISH, LANGUAGE_GERMAN, LANGUAGE_CHINESE, LANGUAGE_INDONESIAN)
            for (i in codes.indices) { if (codes[i] == savedLanguage) return names[i] }
            return names[0]
        }

    private val currentThemeName: String
        get() = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))[savedTheme]

    private val currentFreqUnitName: String
        get() = arrayOf(getString(R.string.hz), getString(R.string.mhz), getString(R.string.ghz))[savedFreqUnit]

    private val currentColorPaletteName: String
        get() = when (savedColorPalette) {
            PALETTE_PURPLE -> getString(R.string.palette_purple_teal)
            PALETTE_BLUE -> getString(R.string.palette_blue_orange)
            PALETTE_GREEN -> getString(R.string.palette_green_red)
            PALETTE_PINK -> getString(R.string.palette_pink_cyan)
            PALETTE_AMOLED -> getString(R.string.palette_amoled)
            else -> getString(R.string.palette_purple_teal)
        }

    private val currentLanguageIndex: Int
        get() {
            val codes = arrayOf(LANGUAGE_ENGLISH, LANGUAGE_GERMAN, LANGUAGE_CHINESE, LANGUAGE_INDONESIAN)
            for (i in codes.indices) { if (codes[i] == savedLanguage) return i }
            return 0
        }

    private val isAutoSaveEnabled: Boolean get() = prefs.getBoolean(KEY_AUTO_SAVE_GPU_TABLE, false)
    private val isDynamicColorEnabled: Boolean get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    private val savedLanguage: String get() = prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
    private val savedTheme: Int get() = prefs.getInt(KEY_THEME, THEME_SYSTEM)
    private val savedFreqUnit: Int get() = prefs.getInt(KEY_FREQ_UNIT, FREQ_UNIT_MHZ)
    private val savedColorPalette: Int get() = prefs.getInt(KEY_COLOR_PALETTE, PALETTE_DYNAMIC)

    private fun saveLanguage(language: String) { prefs.edit().putString(KEY_LANGUAGE, language).apply() }
    private fun saveTheme(theme: Int) { prefs.edit().putInt(KEY_THEME, theme).apply() }
    private fun saveFreqUnit(unit: Int) { prefs.edit().putInt(KEY_FREQ_UNIT, unit).apply() }
    private fun saveColorPalette(palette: Int) { prefs.edit().putInt(KEY_COLOR_PALETTE, palette).apply() }
}
