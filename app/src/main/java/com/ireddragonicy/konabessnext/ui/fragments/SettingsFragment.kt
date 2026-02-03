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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.BuildConfig
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.utils.ChipStringHelper
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import com.ireddragonicy.konabessnext.ui.compose.SettingsScreen
import com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var prefs: SharedPreferences? = null

    // MVVM ViewModel
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        settingsViewModel.loadSettings(requireContext())
        prefs = requireContext().getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

        return ComposeView(requireContext()).apply {
            setContent {
                var refreshKey by remember { mutableStateOf(0) }
                
                SettingsScreen(
                    currentTheme = currentThemeName,
                    isDynamicColor = prefs!!.getBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, true),
                    currentColorPalette = currentColorPaletteName,
                    currentLanguage = currentLanguageName,
                    currentFreqUnit = currentFreqUnitName,
                    isAutoSave = prefs!!.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false),
                    onThemeClick = { showThemeDialog() },
                    onDynamicColorToggle = { toggleDynamicColor() },
                    onColorPaletteClick = { showColorPaletteDialog() },
                    onLanguageClick = { showLanguageDialog() },
                    onFreqUnitClick = { showFreqUnitDialog() },
                    onAutoSaveToggle = { toggleAutoSave() },

                    onHelpClick = { showHelpDialog() },
                    isAmoledMode = prefs!!.getBoolean(SettingsActivity.KEY_AMOLED_MODE, false),
                    onAmoledModeToggle = { toggleAmoledMode() },
                    // Updater Params from ViewModel
                    updateChannel = settingsViewModel.updateChannel.observeAsState("stable").value,
                    updateStatus = settingsViewModel.updateStatus.observeAsState(com.ireddragonicy.konabessnext.viewmodel.UpdateStatus.Idle).value,
                    onUpdateChannelChange = { settingsViewModel.setUpdateChannel(requireContext(), it) },
                    onCheckForUpdates = { settingsViewModel.checkForUpdates() },
                    onClearUpdateStatus = { settingsViewModel.clearUpdateStatus() }
                )
            }
        }
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
                dialog.dismiss()
                dialog.dismiss()
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
            "Pink & Cyan"
        )
        val paletteIds = intArrayOf(
            SettingsActivity.PALETTE_PURPLE,
            SettingsActivity.PALETTE_BLUE,
            SettingsActivity.PALETTE_GREEN,
            SettingsActivity.PALETTE_PINK
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
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().show()
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.help))
            .setMessage(ChipStringHelper.genericHelp(requireActivity()))
            .setPositiveButton(getString(R.string.ok), null)
            .setNeutralButton(getString(R.string.about)) { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
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

        val message = if (newState) getString(R.string.auto_save_enabled_toast)
        else getString(R.string.auto_save_disabled_toast)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleDynamicColor() {
        val currentState = prefs!!.getBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, true)
        prefs!!.edit().putBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, !currentState).apply()
        requireActivity().recreate()
    }

    private fun toggleAmoledMode() {
        val currentState = prefs!!.getBoolean(SettingsActivity.KEY_AMOLED_MODE, false)
        prefs!!.edit().putBoolean(SettingsActivity.KEY_AMOLED_MODE, !currentState).apply()
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
