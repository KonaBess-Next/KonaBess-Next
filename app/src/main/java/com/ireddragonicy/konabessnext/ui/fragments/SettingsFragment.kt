package com.ireddragonicy.konabessnext.ui.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.BuildConfig
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.data.KonaBessStr
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import com.ireddragonicy.konabessnext.ui.adapters.SettingsAdapter
import com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var recyclerView: RecyclerView? = null
    private var adapter: SettingsAdapter? = null
    private var prefs: SharedPreferences? = null

    // MVVM ViewModel
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Initialize MVVM ViewModel (already injected via delegate)
        settingsViewModel.loadSettings(requireContext())

        prefs = requireContext().getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

        recyclerView = RecyclerView(requireContext())
        recyclerView!!.layoutManager = LinearLayoutManager(requireContext())
        // Fix: Increase bottom padding to 88dp for navbar and set clipToPadding false
        val bottomPadding = (requireContext().resources.displayMetrics.density * 88).toInt()
        recyclerView!!.setPadding(0, 8, 0, bottomPadding)
        recyclerView!!.clipToPadding = false

        loadSettings()
        return recyclerView
    }

    private fun loadSettings() {
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
        val isDynamicColor = prefs!!.getBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, true)
        items.add(
            SettingsAdapter.SettingItem(
                R.drawable.ic_tune,
                "Dynamic Color",
                "Use wallpaper-based colors (Material You)",
                isDynamicColor
            )
        )

        // Color Palette (Only relevant if Dynamic Color is OFF)
        if (!isDynamicColor) {
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
                getString(R.string.freq_unit_description),
                currentFreqUnitName
            )
        )

        // Auto-save GPU freq table toggle
        val isAutoSave = prefs!!.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false)
        items.add(
            SettingsAdapter.SettingItem(
                R.drawable.ic_save,
                getString(R.string.auto_save_gpu_freq_table),
                getString(R.string.auto_save_gpu_freq_table_desc),
                isAutoSave
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

        adapter = SettingsAdapter(items, requireContext())
        adapter!!.setOnItemClickListener { position ->
            val item = items[position]

            if (item.title == getString(R.string.theme)) {
                showThemeDialog()
            } else if (item.title == "Dynamic Color") {
                toggleDynamicColor()
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

        recyclerView!!.adapter = adapter
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )

        val currentTheme = prefs!!.getInt(SettingsActivity.KEY_THEME, SettingsActivity.THEME_SYSTEM)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.theme))
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                prefs!!.edit().putInt(SettingsActivity.KEY_THEME, which).apply()
                adapter!!.updateItem(0, currentThemeName)
                dialog.dismiss()
                SettingsActivity.applyThemeFromSettings(requireContext())
                requireActivity().recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
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
            SettingsActivity.PALETTE_PURPLE,
            SettingsActivity.PALETTE_BLUE,
            SettingsActivity.PALETTE_GREEN,
            SettingsActivity.PALETTE_PINK,
            SettingsActivity.PALETTE_AMOLED
        )

        val currentPalette = prefs!!.getInt(SettingsActivity.KEY_COLOR_PALETTE, SettingsActivity.PALETTE_PURPLE)
        var initialSelection = 0
        for (i in paletteIds.indices) {
            if (paletteIds[i] == currentPalette) {
                initialSelection = i
                break
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Color Palette")
            .setSingleChoiceItems(palettes, initialSelection) { dialog, which ->
                prefs!!.edit().putInt(SettingsActivity.KEY_COLOR_PALETTE, paletteIds[which]).apply()
                adapter!!.updateItem(2, currentColorPaletteName) // Note: Index might vary, best to recreate
                dialog.dismiss()
                requireActivity().recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            getString(R.string.english),
            getString(R.string.german),
            getString(R.string.chinese),
            getString(R.string.indonesian)
        )

        val languageCodes = arrayOf(
            SettingsActivity.LANGUAGE_ENGLISH,
            SettingsActivity.LANGUAGE_GERMAN,
            SettingsActivity.LANGUAGE_CHINESE,
            SettingsActivity.LANGUAGE_INDONESIAN
        )

        val currentIndex = currentLanguageIndex

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.language))
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                prefs!!.edit().putString(SettingsActivity.KEY_LANGUAGE, languageCodes[which]).apply()
                adapter!!.updateItem(2, currentLanguageName)
                dialog.dismiss()
                requireActivity().recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    private fun showFreqUnitDialog() {
        val units = arrayOf(
            getString(R.string.hz),
            getString(R.string.mhz),
            getString(R.string.ghz)
        )

        val currentUnit = prefs!!.getInt(SettingsActivity.KEY_FREQ_UNIT, SettingsActivity.FREQ_UNIT_MHZ)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.gpu_freq_unit))
            .setSingleChoiceItems(units, currentUnit) { dialog, which ->
                prefs!!.edit().putInt(SettingsActivity.KEY_FREQ_UNIT, which).apply()
                adapter!!.updateItem(3, currentFreqUnitName)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.help))
            .setMessage(KonaBessStr.generic_help(requireActivity()))
            .setPositiveButton(getString(R.string.ok), null)
            .setNeutralButton(getString(R.string.about)) { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.about))
                    .setMessage(
                        getResources().getString(R.string.author) + " " +
                                "IRedDragonICY & xzr467706992 (LibXZR)\n"
                                + getResources().getString(R.string.release_at) + " www.akr-developers.com\n"
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
                            data = Uri.parse("https://github.com/ireddragonicy")
                        })
                    }
                    .create().show()
            }
            .create().show()
    }

    private fun toggleAutoSave() {
        val currentState = prefs!!.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false)
        val newState = !currentState

        prefs!!.edit().putBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, newState).apply()
        requireActivity().recreate()

        // Show toast to confirm the change
        val message = if (newState) getString(R.string.auto_save_enabled_toast)
        else getString(R.string.auto_save_disabled_toast)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private val autoSaveStatusText: String
        get() {
            val isEnabled = prefs!!.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false)
            return if (isEnabled) getString(R.string.common_on) else getString(R.string.common_off)
        }

    private fun toggleDynamicColor() {
        val currentState = prefs!!.getBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, true)
        prefs!!.edit().putBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, !currentState).apply()
        requireActivity().recreate()
    }

    private val currentThemeName: String
        get() {
            val names = arrayOf(
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
            )
            return names[prefs!!.getInt(SettingsActivity.KEY_THEME, SettingsActivity.THEME_SYSTEM)]
        }

    private val currentLanguageName: String
        get() {
            val names = arrayOf(
                getString(R.string.english),
                getString(R.string.german),
                getString(R.string.chinese),
                getString(R.string.indonesian)
            )
            val codes = arrayOf(
                SettingsActivity.LANGUAGE_ENGLISH,
                SettingsActivity.LANGUAGE_GERMAN,
                SettingsActivity.LANGUAGE_CHINESE,
                SettingsActivity.LANGUAGE_INDONESIAN
            )

            val currentLang = prefs!!.getString(SettingsActivity.KEY_LANGUAGE, SettingsActivity.LANGUAGE_ENGLISH)
            for (i in codes.indices) {
                if (codes[i] == currentLang) {
                    return names[i]
                }
            }
            return names[0]
        }

    private val currentFreqUnitName: String
        get() {
            val names = arrayOf(
                getString(R.string.hz),
                getString(R.string.mhz),
                getString(R.string.ghz)
            )
            return names[prefs!!.getInt(SettingsActivity.KEY_FREQ_UNIT, SettingsActivity.FREQ_UNIT_MHZ)]
        }

    private val currentColorPaletteName: String
        get() {
            val palette = prefs!!.getInt(SettingsActivity.KEY_COLOR_PALETTE, SettingsActivity.PALETTE_PURPLE)
            return when (palette) {
                SettingsActivity.PALETTE_PURPLE -> "Purple & Teal"
                SettingsActivity.PALETTE_BLUE -> "Blue & Orange"
                SettingsActivity.PALETTE_GREEN -> "Green & Red"
                SettingsActivity.PALETTE_PINK -> "Pink & Cyan"
                SettingsActivity.PALETTE_AMOLED -> "Pure AMOLED (Black)"
                else -> "Purple & Teal"
            }
        }

    private val currentLanguageIndex: Int
        get() {
            val codes = arrayOf(
                SettingsActivity.LANGUAGE_ENGLISH,
                SettingsActivity.LANGUAGE_GERMAN,
                SettingsActivity.LANGUAGE_CHINESE,
                SettingsActivity.LANGUAGE_INDONESIAN
            )
            val currentLang = prefs!!.getString(SettingsActivity.KEY_LANGUAGE, SettingsActivity.LANGUAGE_ENGLISH)

            for (i in codes.indices) {
                if (codes[i] == currentLang) {
                    return i
                }
            }
            return 0
        }
}
