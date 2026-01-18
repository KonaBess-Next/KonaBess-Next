package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.ui.SettingsActivity

/**
 * ViewModel for Settings management.
 * Provides observable settings state.
 */
class SettingsViewModel : ViewModel() {

    private val _themeMode = MutableLiveData<Int>()
    private val _language = MutableLiveData<String>()
    private val _frequencyUnit = MutableLiveData<Int>()
    private val _autoSaveEnabled = MutableLiveData<Boolean>()
    private val _colorPalette = MutableLiveData<Int>()
    private val _dynamicColorEnabled = MutableLiveData<Boolean>()

    // Events
    private val _restartRequired = MutableLiveData<Event<Boolean>>()

    // ========================================================================
    // LiveData Getters
    // ========================================================================

    val themeMode: LiveData<Int> get() = _themeMode
    val language: LiveData<String> get() = _language
    val frequencyUnit: LiveData<Int> get() = _frequencyUnit
    val autoSaveEnabled: LiveData<Boolean> get() = _autoSaveEnabled
    val colorPalette: LiveData<Int> get() = _colorPalette
    val dynamicColorEnabled: LiveData<Boolean> get() = _dynamicColorEnabled
    val restartRequired: LiveData<Event<Boolean>> get() = _restartRequired

    // ========================================================================
    // Load Settings
    // ========================================================================

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

        _themeMode.value = prefs.getInt(SettingsActivity.KEY_THEME, SettingsActivity.THEME_SYSTEM)
        _language.value = prefs.getString(SettingsActivity.KEY_LANGUAGE, "system")
        _frequencyUnit.value = prefs.getInt(SettingsActivity.KEY_FREQ_UNIT, 1) // Default MHz
        _autoSaveEnabled.value = prefs.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false)
        _colorPalette.value = prefs.getInt(SettingsActivity.KEY_COLOR_PALETTE, 0)
        _dynamicColorEnabled.value = prefs.getBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, false)
    }

    // ========================================================================
    // Update Settings
    // ========================================================================

    fun setThemeMode(context: Context, theme: Int) {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(SettingsActivity.KEY_THEME, theme).apply()
        _themeMode.value = theme
        _restartRequired.value = Event(true)
    }

    fun setLanguage(context: Context, lang: String) {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SettingsActivity.KEY_LANGUAGE, lang).apply()
        _language.value = lang
        _restartRequired.value = Event(true)
    }

    fun setFrequencyUnit(context: Context, unit: Int) {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(SettingsActivity.KEY_FREQ_UNIT, unit).apply()
        _frequencyUnit.value = unit
    }

    fun toggleAutoSave(context: Context) {
        val current = _autoSaveEnabled.value
        val newValue = current == null || !current

        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, newValue).apply()
        _autoSaveEnabled.value = newValue
    }

    fun setColorPalette(context: Context, palette: Int) {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(SettingsActivity.KEY_COLOR_PALETTE, palette).apply()
        _colorPalette.value = palette
        _restartRequired.value = Event(true)
    }

    fun toggleDynamicColor(context: Context) {
        val current = _dynamicColorEnabled.value
        val newValue = current == null || !current

        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, newValue).apply()
        _dynamicColorEnabled.value = newValue
        _restartRequired.value = Event(true)
    }

    // ========================================================================
    // Getters for current values
    // ========================================================================

    val currentTheme: Int
        get() = _themeMode.value ?: SettingsActivity.THEME_SYSTEM

    val currentFrequencyUnit: Int
        get() = _frequencyUnit.value ?: 1

    val isAutoSaveEnabledValue: Boolean
        get() = _autoSaveEnabled.value == true
}
