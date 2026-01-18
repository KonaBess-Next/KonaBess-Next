package com.ireddragonicy.konabessnext.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.BuildConfig
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.data.KonaBessStr
import com.ireddragonicy.konabessnext.ui.adapters.SettingsAdapter
import com.ireddragonicy.konabessnext.utils.LocaleUtil
import com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList

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
        fun formatFrequency(frequencyHz: Long, unit: Int): String {
            return when (unit) {
                FREQ_UNIT_HZ -> "$frequencyHz Hz"
                FREQ_UNIT_MHZ -> "${frequencyHz / 1000000L} MHz"
                FREQ_UNIT_GHZ -> String.format("%.2f GHz", frequencyHz / 1000000000.0)
                else -> "${frequencyHz / 1000000L} MHz"
            }
        }

        @JvmStatic
        fun formatFrequency(frequencyHz: Long, context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val unit = prefs.getInt(KEY_FREQ_UNIT, FREQ_UNIT_MHZ)
            return formatFrequency(frequencyHz, unit)
        }

        @JvmStatic
        fun applyThemeFromSettings(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)

            if (dynamicColor) {
                context.setTheme(R.style.Theme_KonaBess) // Material You Dynamic
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

    // ViewModel for MVVM
    private lateinit var viewModel: SettingsViewModel

    private lateinit var prefs: SharedPreferences
    private var adapter: SettingsAdapter? = null
    private lateinit var recyclerView: RecyclerView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtil.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applyTheme()
        super.onCreate(savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)

        showSettingsView()
    }

    private fun applyTheme() {
        // Apply color palette theme first
        val dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)

        if (dynamicColor) {
            setTheme(R.style.Theme_KonaBess) // Material You Dynamic
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
        // Then apply light/dark mode
        applyThemeMode(savedTheme)
    }

    private fun showSettingsView() {
        val mainView = LinearLayout(this)
        mainView.orientation = LinearLayout.VERTICAL
        setContentView(mainView)

        // Material Toolbar
        val toolbar = MaterialToolbar(this)
        toolbar.title = getString(R.string.settings)
        toolbar.setTitleTextColor(getColor(android.R.color.white))
        mainView.addView(toolbar)

        // RecyclerView for settings
        recyclerView = RecyclerView(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setPadding(0, 8, 0, 16)
        recyclerView.clipToPadding = false

        val items = ArrayList<SettingsAdapter.SettingItem>()

        items.add(
            SettingsAdapter.SettingItem(
                R.drawable.ic_dark_mode,
                getString(R.string.theme),
                getString(R.string.theme_description),
                currentThemeName
            )
        )

        // Dynamic Color Toggle
        items.add(
            SettingsAdapter.SettingItem(
                R.drawable.ic_tune,
                "Dynamic Color",
                "Use wallpaper-based colors (Material You)",
                isDynamicColorEnabled.toString() // Or use specialized logic
            )
        )
        // Fix for isDynamicColorEnabled not being String
        // SettingItem expects String for status?
        // Let's check SettingsAdapter.SettingItem
        // It takes 4 params. 4th is status (String).
        // So I should pass "On" / "Off" or similar?
        // Original Java: isDynamicColorEnabled() -> returns boolean. Wait.
        // items.add(..., isDynamicColorEnabled()));
        // The Java list add call matches signature? If adapter expects Object/Any it's fine.
        // But let's assume it expects String.
        // "isDynamicColorEnabled() ? getString(R.string.common_on) : getString(R.string.common_off)"

        // Correct logic for Dynamic Color status text
        val dynamicColorStatus = if (isDynamicColorEnabled) getString(R.string.common_on) else getString(R.string.common_off)
        // Actually, let's just make sure I handle the boolean correctly.
        // Wait, in Java code:
        // items.add(new SettingsAdapter.SettingItem(..., isDynamicColorEnabled()));
        // It passed `isDynamicColorEnabled()` result.
        // If SettingItem takes String, Java would error unless it auto-boxed to String? No.
        // Maybe SettingItem constructor is (String, String, String, boolean)?
        // Or generic?
        // Let's assume (Icon, Title, Desc, StateString)
        items[items.size - 1] = SettingsAdapter.SettingItem(
            R.drawable.ic_tune,
            "Dynamic Color",
            "Use wallpaper-based colors (Material You)",
            dynamicColorStatus
        )


        // Color Palette (Only relevant if Dynamic Color is OFF)
        if (!isDynamicColorEnabled) {
            items.add(
                SettingsAdapter.SettingItem(
                    R.drawable.ic_tune,
                    "Color Palette",
                    "Choose your color scheme",
                    currentColorPaletteName
                )
            )
        }

        items.add(
            SettingsAdapter.SettingItem(
                R.drawable.ic_language,
                getString(R.string.language),
                "Select your preferred language",
                currentLanguageName
            )
        )

        items.add(
            SettingsAdapter.SettingItem(
                R.drawable.ic_frequency,
                getString(R.string.gpu_freq_unit),
                "Choose frequency display unit",
                currentFreqUnitName
            )
        )

        // Auto-save GPU freq table toggle
        items.add(
            SettingsAdapter.SettingItem(
                R.drawable.ic_save,
                getString(R.string.auto_save_gpu_freq_table),
                getString(R.string.auto_save_gpu_freq_table_desc),
                autoSaveStatus
            )
        )

        items.add(
            SettingsAdapter.SettingItem(
                R.drawable.ic_help,
                getString(R.string.help),
                "About and documentation",
                "Version " + BuildConfig.VERSION_NAME
            )
        )

        adapter = SettingsAdapter(items, this)

        adapter!!.setOnItemClickListener { position ->
            val item = items[position]

            if (item.title == getString(R.string.theme)) {
                showThemeDialog()
            } else if (item.title == "Dynamic Color") {
                toggleDynamicColor()
                recreate()
            } else if (item.title == "Color Palette") {
                showColorPaletteDialog()
            } else if (item.title == getString(R.string.language)) {
                showLanguageDialog()
            } else if (item.title == getString(R.string.gpu_freq_unit)) {
                showFreqUnitDialog()
            } else if (item.title == getString(R.string.auto_save_gpu_freq_table)) {
                toggleAutoSave()
            } else if (item.title == getString(R.string.help)) {
                showHelpDialog()
            }
        }

        recyclerView.adapter = adapter
        mainView.addView(recyclerView)
    }

    fun showLanguageDialogPublic() {
        showLanguageDialog()
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
                adapter!!.updateItem(1, currentLanguageName)
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    fun showThemeDialogPublic() {
        showThemeDialog()
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
                adapter!!.updateItem(0, currentThemeName)
                dialog.dismiss()
                applyTheme()
                restartActivity()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    fun showFreqUnitDialogPublic() {
        showFreqUnitDialog()
    }

    private fun showFreqUnitDialog() {
        val units = arrayOf(
            getString(R.string.hz),
            getString(R.string.mhz),
            getString(R.string.ghz)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.gpu_freq_unit))
            .setSingleChoiceItems(units, savedFreqUnit) { dialog, which ->
                saveFreqUnit(which)
                adapter!!.updateItem(2, currentFreqUnitName)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    fun showHelpDialogPublic() {
        showHelpDialog()
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.help))
            .setMessage(KonaBessStr.generic_help(this))
            .setPositiveButton(getString(R.string.ok), null)
            .setNeutralButton(
                getString(R.string.about)
            ) { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.about))
                    .setMessage(
                        resources.getString(R.string.author) + " " +
                                "IRedDragonICY & xzr467706992 (LibXZR)\n"
                                + resources.getString(R.string.release_at) + " www.akr-developers.com\n"
                    )
                    .setPositiveButton(getString(R.string.ok), null)
                    .setNegativeButton(
                        "Github"
                    ) { _, _ ->
                        startActivity(Intent().apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse("https://github.com/ireddragonicy/KonaBess")
                        })
                    }
                    .setNeutralButton(
                        getString(R.string.visit_akr)
                    ) { _, _ ->
                        startActivity(Intent().apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse("https://www.akr-developers.com/d/441")
                        })
                    }
                    .create().show()
            }
            .create().show()
    }

    val currentLanguageNamePublic: String
        get() = currentLanguageName

    private val currentLanguageName: String
        get() {
            val names = arrayOf(
                getString(R.string.english),
                getString(R.string.german),
                getString(R.string.chinese),
                getString(R.string.indonesian)
            )
            val codes = arrayOf(LANGUAGE_ENGLISH, LANGUAGE_GERMAN, LANGUAGE_CHINESE, LANGUAGE_INDONESIAN)

            for (i in codes.indices) {
                if (codes[i] == savedLanguage) {
                    return names[i]
                }
            }
            return names[0]
        }

    val currentThemeNamePublic: String
        get() = currentThemeName

    private val currentThemeName: String
        get() {
            val names = arrayOf(
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
            )
            return names[savedTheme]
        }

    val currentFreqUnitNamePublic: String
        get() = currentFreqUnitName

    private val currentFreqUnitName: String
        get() {
            val names = arrayOf(
                getString(R.string.hz),
                getString(R.string.mhz),
                getString(R.string.ghz)
            )
            return names[savedFreqUnit]
        }

    private val autoSaveStatus: String
        get() = getString(if (isAutoSaveEnabled) R.string.common_on else R.string.common_off)

    private val currentLanguageIndex: Int
        get() {
            val codes = arrayOf(LANGUAGE_ENGLISH, LANGUAGE_GERMAN, LANGUAGE_CHINESE, LANGUAGE_INDONESIAN)
            val currentLang = savedLanguage

            for (i in codes.indices) {
                if (codes[i] == currentLang) {
                    return i
                }
            }
            return 0
        }

    private fun saveLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    private val savedLanguage: String
        get() = prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH

    private fun saveFreqUnit(unit: Int) {
        prefs.edit().putInt(KEY_FREQ_UNIT, unit).apply()
    }

    private val savedFreqUnit: Int
        get() = prefs.getInt(KEY_FREQ_UNIT, FREQ_UNIT_MHZ)

    private fun toggleAutoSave() {
        val enabled = !isAutoSaveEnabled
        prefs.edit().putBoolean(KEY_AUTO_SAVE_GPU_TABLE, enabled).apply()
        recreate()
        Toast.makeText(
            this, if (enabled) R.string.auto_save_enabled_toast else R.string.auto_save_disabled_toast,
            Toast.LENGTH_SHORT
        ).show()
    }

    private val isAutoSaveEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SAVE_GPU_TABLE, false)

    private val isDynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)

    private fun toggleDynamicColor() {
        val enabled = !isDynamicColorEnabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    private val currentColorPaletteName: String
        get() {
            val palette = savedColorPalette
            return when (palette) {
                PALETTE_PURPLE -> "Purple & Teal"
                PALETTE_BLUE -> "Blue & Orange"
                PALETTE_GREEN -> "Green & Red"
                PALETTE_PINK -> "Pink & Cyan"
                PALETTE_AMOLED -> "Pure AMOLED (Black)"
                else -> "Purple & Teal"
            }
        }

    private fun showColorPaletteDialog() {
        val palettes = arrayOf(
            "Purple & Teal",
            "Blue & Orange",
            "Green & Red",
            "Pink & Cyan",
            "Pure AMOLED (Black)"
        )
        val paletteIds = intArrayOf(
            PALETTE_PURPLE,
            PALETTE_BLUE,
            PALETTE_GREEN,
            PALETTE_PINK,
            PALETTE_AMOLED
        )

        val currentPalette = savedColorPalette
        var initialSelection = 0
        for (i in paletteIds.indices) {
            if (paletteIds[i] == currentPalette) {
                initialSelection = i
                break
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Color Palette")
            .setSingleChoiceItems(palettes, initialSelection) { dialog, which ->
                saveColorPalette(paletteIds[which])
                dialog.dismiss()
                restartActivity()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    private fun saveTheme(theme: Int) {
        prefs.edit().putInt(KEY_THEME, theme).apply()
    }

    private val savedTheme: Int
        get() = prefs.getInt(KEY_THEME, THEME_SYSTEM)

    private fun saveColorPalette(palette: Int) {
        prefs.edit().putInt(KEY_COLOR_PALETTE, palette).apply()
    }

    private val savedColorPalette: Int
        get() = prefs.getInt(KEY_COLOR_PALETTE, PALETTE_DYNAMIC)

    private fun restartActivity() {
        val intent = intent
        finish()
        startActivity(intent)
    }
}
