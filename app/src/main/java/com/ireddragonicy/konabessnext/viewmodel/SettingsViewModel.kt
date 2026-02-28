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
    val isAutoCheckUpdate: Boolean = true,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val isRootMode: Boolean = true,
    val exportPath: String = ""
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
        const val KEY_EXPORT_URI = "export_uri"
        const val KEY_EXPORT_PATH_DISPLAY = "export_path_display"

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
        const val LANGUAGE_POLISH = "pl"
    }

    init {
        loadSettings()
        // Only check for updates on startup if setting is enabled
        if (repository.isAutoCheckUpdate()) {
            checkForUpdates()
        }
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
        val amoledMode = repository.isAmoledMode()
        val paletteInt = repository.getColorPalette()
        val paletteName = getPaletteName(paletteInt)
        val dynamicColor = paletteInt == PALETTE_DYNAMIC
        // Update Channel
        val updateChannel = repository.getUpdateChannel()
        
        // Auto Check Update
        val autoCheckUpdate = repository.isAutoCheckUpdate()
        
        // Root Mode
        val isRootMode = repository.isRootMode()

        _uiState.update {
            it.copy(
                themeMode = themeMode,
                language = language,
                frequencyUnit = freqUnit,
                autoSave = autoSave,
                isDynamicColor = dynamicColor,
                isAmoledMode = amoledMode,
                colorPalette = paletteName,
                updateChannel = updateChannel,
                isAutoCheckUpdate = autoCheckUpdate,
                isRootMode = isRootMode,
                exportPath = repository.getExportPathDisplay()
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
    
    fun toggleAutoCheckUpdate() {
        val newState = !_uiState.value.isAutoCheckUpdate
        repository.setAutoCheckUpdate(newState)
        _uiState.update { it.copy(isAutoCheckUpdate = newState) }
    }

    fun setLanguage(languageCode: String) {
        repository.setLanguage(languageCode)
        _uiState.update { it.copy(language = languageCode) }
    }

    fun toggleRootMode() {
        val newState = !_uiState.value.isRootMode
        repository.setRootMode(newState)
        _uiState.update { it.copy(isRootMode = newState) }
    }

    fun setColorPalette(paletteInt: Int) {
        val isDynamic = paletteInt == PALETTE_DYNAMIC
        repository.setColorPalette(paletteInt)
        repository.setDynamicColor(isDynamic)
        _uiState.update {
            it.copy(
                colorPalette = getPaletteName(paletteInt),
                isDynamicColor = isDynamic
            )
        }
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
    
    fun checkForUpdates(isManual: Boolean = false) {
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
                         if (isManual) {
                             _uiState.update { it.copy(updateStatus = UpdateStatus.Error("Network error: ${response.code}")) }
                         } else {
                             _uiState.update { it.copy(updateStatus = UpdateStatus.Idle) }
                         }
                         return@launch
                     }
                     
                     val responseBody = response.body.string()
                     val releaseJson = if (isPrerelease) {
                        val jsonArray = JSONArray(responseBody)
                        if (jsonArray.length() > 0) jsonArray.getJSONObject(0) else null
                    } else {
                        JSONObject(responseBody)
                    }

                    if (releaseJson == null) {
                        if (isManual) {
                            _uiState.update { it.copy(updateStatus = UpdateStatus.NoUpdate) }
                        } else {
                            _uiState.update { it.copy(updateStatus = UpdateStatus.Idle) }
                        }
                        return@launch
                    }

                    val tagName = releaseJson.getString("tag_name").removePrefix("v")
                    val currentVersion = com.ireddragonicy.konabessnext.BuildConfig.VERSION_NAME

                    if (tagName > currentVersion) {
                        val release = GitHubRelease(
                            tagName = tagName,
                            htmlUrl = releaseJson.getString("html_url"),
                            body = releaseJson.optString("body", "No release notes.")
                        )
                        _uiState.update { it.copy(updateStatus = UpdateStatus.Available(release)) }
                    } else {
                        if (isManual) {
                            _uiState.update { it.copy(updateStatus = UpdateStatus.NoUpdate) }
                        } else {
                            _uiState.update { it.copy(updateStatus = UpdateStatus.Idle) }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isManual) {
                    _uiState.update { it.copy(updateStatus = UpdateStatus.Error(e.message ?: "Unknown error")) }
                } else {
                    _uiState.update { it.copy(updateStatus = UpdateStatus.Idle) }
                }
            }
        }
    }
    
    fun clearUpdateStatus() {
        _uiState.update { it.copy(updateStatus = UpdateStatus.Idle) }
    }

    fun setDefaultExportLocation(context: Context, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                // Try to resolve a readable path
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                val rawPath = com.ireddragonicy.konabessnext.utils.UriPathHelper.getPath(context, uri)
                
                // If it's a raw path and looks like /storage/emulated/0/Download, maybe we can simplify
                val displayPath = if (!rawPath.isNullOrEmpty()) {
                    rawPath.replace("/storage/emulated/0", "Internal Storage")
                } else {
                    docFile?.name ?: uri.path ?: "Unknown"
                }

                repository.setAndPersistExportUri(uri)
                repository.setExportPathDisplay(displayPath)
                _uiState.update { it.copy(exportPath = displayPath) }
            } catch (e: Exception) {
                // If permission fails, don't save
                e.printStackTrace()
            }
        }
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
