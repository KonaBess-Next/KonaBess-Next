package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

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


    // ========================================================================
    // Update Checker Logic
    // ========================================================================

    private val _updateChannel = MutableLiveData<String>()
    val updateChannel: LiveData<String> get() = _updateChannel

    private val _updateStatus = MutableLiveData<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: LiveData<UpdateStatus> get() = _updateStatus

    fun loadUpdateSettings(context: Context) {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        _updateChannel.value = prefs.getString("update_channel", "stable")
    }

    fun setUpdateChannel(context: Context, channel: String) {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("update_channel", channel).apply()
        _updateChannel.value = channel
    }

    fun checkForUpdates() {
        if (_updateStatus.value is UpdateStatus.Checking) return

        _updateStatus.value = UpdateStatus.Checking
        val isPrerelease = _updateChannel.value == "prerelease"
        val url = if (isPrerelease) {
            "https://api.github.com/repos/IRedDragonICY/KonaBess-Next/releases"
        } else {
            "https://api.github.com/repos/IRedDragonICY/KonaBess-Next/releases/latest"
        }

        viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateStatus.postValue(UpdateStatus.Error("Network error: ${response.code}"))
                        return@launch
                    }

                    val responseBody = response.body?.string() ?: ""
                    val releaseJson = if (isPrerelease) {
                        val jsonArray = JSONArray(responseBody)
                        if (jsonArray.length() > 0) jsonArray.getJSONObject(0) else null
                    } else {
                        JSONObject(responseBody)
                    }

                    if (releaseJson == null) {
                        _updateStatus.postValue(UpdateStatus.NoUpdate)
                        return@launch
                    }

                    val tagName = releaseJson.getString("tag_name").removePrefix("v")
                    val currentVersion = com.ireddragonicy.konabessnext.BuildConfig.VERSION_NAME

                    // Simple string comparison for date-based versions (yyyy.MM.dd...)
                    if (tagName > currentVersion) {
                        val release = GitHubRelease(
                            tagName = tagName,
                            htmlUrl = releaseJson.getString("html_url"),
                            body = releaseJson.optString("body", "No release notes.")
                        )
                        _updateStatus.postValue(UpdateStatus.Available(release))
                    } else {
                        _updateStatus.postValue(UpdateStatus.NoUpdate)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateStatus.postValue(UpdateStatus.Error(e.message ?: "Unknown error"))
            }
        }
    }

    fun clearUpdateStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }
}

data class GitHubRelease(
    val tagName: String,
    val htmlUrl: String,
    val body: String
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val release: GitHubRelease) : UpdateStatus()
    object NoUpdate : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}
