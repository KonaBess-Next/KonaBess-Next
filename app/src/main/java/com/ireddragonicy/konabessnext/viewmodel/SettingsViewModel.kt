package com.ireddragonicy.konabessnext.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireddragonicy.konabessnext.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "en",
    val frequencyUnit: String = "MHz",
    val autoSave: Boolean = false,
    val colorPalette: String = "Green",
    val isDynamicColor: Boolean = true,
    val isAmoledMode: Boolean = false,
    val updateChannel: String = "stable",
    val updateStatus: UpdateStatus = UpdateStatus.Idle
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: com.ireddragonicy.konabessnext.repository.SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()
    
    // Updater
    val updateStatus = _uiState.asStateFlow() 

    companion object {
        const val PREFS_NAME = "KonaBessSettings"
        const val KEY_LANGUAGE = "language"
        const val KEY_FREQ_UNIT = "freq_unit"
        const val KEY_THEME = "theme"
        const val KEY_COLOR_PALETTE = "color_palette"
        const val KEY_AUTO_SAVE_GPU_TABLE = "auto_save_gpu_table"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_AMOLED_MODE = "amoled_mode"

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
        
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_GERMAN = "de"
        const val LANGUAGE_CHINESE = "zh-rCN"
        const val LANGUAGE_INDONESIAN = "in"
    }

    init {
        loadSettings()
        checkForUpdates()
    }

    private fun loadSettings() {
        // Theme
        val themeMode = when (repository.getTheme()) {
            THEME_LIGHT -> ThemeMode.LIGHT
            THEME_DARK -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }

        // Language
        val language = repository.getLanguage()

        // Freq Unit
        val freqUnitInt = repository.getFrequencyUnit()
        val freqUnit = when(freqUnitInt) {
            FREQ_UNIT_HZ -> "Hz"
            FREQ_UNIT_GHZ -> "GHz"
            else -> "MHz"
        }

        // Auto Save
        val autoSave = repository.isAutoSave()
        
        // Customization
        val dynamicColor = repository.isDynamicColor()
        val amoledMode = repository.isAmoledMode()
        val paletteInt = repository.getColorPalette()
        val paletteName = getPaletteName(paletteInt)
        val updateChannel = repository.getUpdateChannel()

        _uiState.update {
            it.copy(
                themeMode = themeMode,
                language = language,
                frequencyUnit = freqUnit,
                autoSave = autoSave,
                isDynamicColor = dynamicColor,
                isAmoledMode = amoledMode,
                colorPalette = paletteName,
                updateChannel = updateChannel
            )
        }
    }
    
    // Actions
    fun cycleThemeMode() {
        val nextMode = when (_uiState.value.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        
        val themeInt = when (nextMode) {
            ThemeMode.LIGHT -> THEME_LIGHT
            ThemeMode.DARK -> THEME_DARK
            ThemeMode.SYSTEM -> THEME_SYSTEM
        }
        
        repository.setTheme(themeInt)
        _uiState.update { it.copy(themeMode = nextMode) }
    }

    fun toggleDynamicColor() {
        val newState = !_uiState.value.isDynamicColor
        repository.setDynamicColor(newState)
        _uiState.update { it.copy(isDynamicColor = newState) }
    }
    
    fun toggleAutoSave() {
        val newState = !_uiState.value.autoSave
        repository.setAutoSave(newState)
        _uiState.update { it.copy(autoSave = newState) }
    }

    fun toggleAmoledMode() {
        val newState = !_uiState.value.isAmoledMode
        repository.setAmoledMode(newState)
        _uiState.update { it.copy(isAmoledMode = newState) }
    }
    
    fun toggleFrequencyUnit() {
        val currentStr = _uiState.value.frequencyUnit
        val (newStr, newInt) = when (currentStr) {
            "MHz" -> "GHz" to FREQ_UNIT_GHZ
            "GHz" -> "Hz" to FREQ_UNIT_HZ
            else -> "MHz" to FREQ_UNIT_MHZ
        }
        repository.setFrequencyUnit(newInt)
        _uiState.update { it.copy(frequencyUnit = newStr) }
    }
    
    fun setUpdateChannel(channel: String) {
        repository.setUpdateChannel(channel)
        _uiState.update { it.copy(updateChannel = channel) }
    }

    fun setLanguage(languageCode: String) {
        repository.setLanguage(languageCode)
        _uiState.update { it.copy(language = languageCode) }
    }

    fun setColorPalette(paletteInt: Int) {
        repository.setColorPalette(paletteInt)
        _uiState.update { it.copy(colorPalette = getPaletteName(paletteInt)) }
    }

    private fun getPaletteName(paletteInt: Int): String {
        return when(paletteInt) {
            PALETTE_PURPLE -> "Purple"
            PALETTE_BLUE -> "Blue"
            PALETTE_GREEN -> "Green"
            PALETTE_PINK -> "Pink"
            else -> "Dynamic"
        }
    }
    
    fun checkForUpdates() {
        if (_uiState.value.updateStatus is UpdateStatus.Checking) return

        _uiState.update { it.copy(updateStatus = UpdateStatus.Checking) }
        val channel = _uiState.value.updateChannel
        val isPrerelease = channel == "prerelease"
        val url = if (isPrerelease) {
            "https://api.github.com/repos/IRedDragonICY/KonaBess-Next/releases"
        } else {
            "https://api.github.com/repos/IRedDragonICY/KonaBess-Next/releases/latest"
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                    
                client.newCall(request).execute().use { response ->
                     if (!response.isSuccessful) {
                         _uiState.update { it.copy(updateStatus = UpdateStatus.Error("Network error: ${response.code}")) }
                         return@launch
                     }
                     
                     val responseBody = response.body?.string() ?: ""
                     // ... reuse parsing logic ...
                     val releaseJson = if (isPrerelease) {
                        val jsonArray = JSONArray(responseBody)
                        if (jsonArray.length() > 0) jsonArray.getJSONObject(0) else null
                    } else {
                        JSONObject(responseBody)
                    }

                    if (releaseJson == null) {
                        _uiState.update { it.copy(updateStatus = UpdateStatus.NoUpdate) }
                        return@launch
                    }

                    val tagName = releaseJson.getString("tag_name").removePrefix("v")
                    // Assuming BuildConfig is available
                    val currentVersion = com.ireddragonicy.konabessnext.BuildConfig.VERSION_NAME

                    if (tagName > currentVersion) {
                        val release = GitHubRelease(
                            tagName = tagName,
                            htmlUrl = releaseJson.getString("html_url"),
                            body = releaseJson.optString("body", "No release notes.")
                        )
                        _uiState.update { it.copy(updateStatus = UpdateStatus.Available(release)) }
                    } else {
                        _uiState.update { it.copy(updateStatus = UpdateStatus.NoUpdate) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(updateStatus = UpdateStatus.Error(e.message ?: "Unknown error")) }
            }
        }
    }
    
    fun clearUpdateStatus() {
        _uiState.update { it.copy(updateStatus = UpdateStatus.Idle) }
    }
}

// Data classes
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
