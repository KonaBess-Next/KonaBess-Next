package com.ireddragonicy.konabessnext.repository

import android.content.Context
import android.content.SharedPreferences
import com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(SettingsViewModel.PREFS_NAME, Context.MODE_PRIVATE)

    fun getTheme(): Int = prefs.getInt(SettingsViewModel.KEY_THEME, SettingsViewModel.THEME_SYSTEM)
    fun setTheme(theme: Int) = prefs.edit().putInt(SettingsViewModel.KEY_THEME, theme).apply()

    fun getLanguage(): String = prefs.getString(SettingsViewModel.KEY_LANGUAGE, SettingsViewModel.LANGUAGE_ENGLISH) ?: SettingsViewModel.LANGUAGE_ENGLISH
    fun setLanguage(lang: String) = prefs.edit().putString(SettingsViewModel.KEY_LANGUAGE, lang).apply()

    fun getFrequencyUnit(): Int = prefs.getInt(SettingsViewModel.KEY_FREQ_UNIT, SettingsViewModel.FREQ_UNIT_MHZ)
    fun setFrequencyUnit(unit: Int) = prefs.edit().putInt(SettingsViewModel.KEY_FREQ_UNIT, unit).apply()

    fun isAutoSave(): Boolean = prefs.getBoolean(SettingsViewModel.KEY_AUTO_SAVE_GPU_TABLE, false)
    fun setAutoSave(enabled: Boolean) = prefs.edit().putBoolean(SettingsViewModel.KEY_AUTO_SAVE_GPU_TABLE, enabled).apply()

    fun getColorPalette(): Int = prefs.getInt(SettingsViewModel.KEY_COLOR_PALETTE, SettingsViewModel.PALETTE_DYNAMIC)
    fun setColorPalette(palette: Int) = prefs.edit().putInt(SettingsViewModel.KEY_COLOR_PALETTE, palette).apply()

    fun isDynamicColor(): Boolean = prefs.getBoolean(SettingsViewModel.KEY_DYNAMIC_COLOR, true)
    fun setDynamicColor(enabled: Boolean) = prefs.edit().putBoolean(SettingsViewModel.KEY_DYNAMIC_COLOR, enabled).apply()

    fun isAmoledMode(): Boolean = prefs.getBoolean(SettingsViewModel.KEY_AMOLED_MODE, false)
    fun setAmoledMode(enabled: Boolean) = prefs.edit().putBoolean(SettingsViewModel.KEY_AMOLED_MODE, enabled).apply()

    fun getUpdateChannel(): String = prefs.getString("update_channel", "stable") ?: "stable"
    fun setUpdateChannel(channel: String) = prefs.edit().putString("update_channel", channel).apply()
    
    fun isAutoCheckUpdate(): Boolean = prefs.getBoolean("auto_check_update", true)
    fun setAutoCheckUpdate(enabled: Boolean) = prefs.edit().putBoolean("auto_check_update", enabled).apply()

    /**
     * Auto-detect root on first launch: if the pref was never set, probe Shell
     * and persist the result so the check only runs once.
     */
    fun isRootMode(): Boolean {
        if (!prefs.contains("is_root_mode")) {
            val hasRoot = try {
                Shell.getShell().isRoot
            } catch (_: Exception) {
                false
            }
            prefs.edit().putBoolean("is_root_mode", hasRoot).apply()
            return hasRoot
        }
        return prefs.getBoolean("is_root_mode", false)
    }
    fun setRootMode(enabled: Boolean) = prefs.edit().putBoolean("is_root_mode", enabled).apply()

    // Export Location
    fun setAndPersistExportUri(uri: android.net.Uri) {
        try {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            
            prefs.edit().putString(SettingsViewModel.KEY_EXPORT_URI, uri.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getValidExportUri(): android.net.Uri? {
        val uriString = prefs.getString(SettingsViewModel.KEY_EXPORT_URI, null) ?: return null
        val uri = android.net.Uri.parse(uriString)
        
        // Validate if we still have permission
        val persistedUris = context.contentResolver.persistedUriPermissions
        val hasPermission = persistedUris.any { 
            it.uri == uri && it.isWritePermission 
        }
        
        // If permission is revoked, we should probably clear the inconsistent state?
        // For now just return null as requested.
        return if (hasPermission) uri else null
    }

    // Deprecated: Use getValidExportUri() instead
    fun getDefaultExportUri(): String? = getValidExportUri()?.toString()
    // Deprecated: Use setAndPersistExportUri() instead
    fun setDefaultExportUri(uri: String?) = prefs.edit().putString(SettingsViewModel.KEY_EXPORT_URI, uri).apply()

    fun getExportPathDisplay(): String {
        // Default gracefully if the URI is invalid or permission revoked
        if (getValidExportUri() == null) return "/storage/emulated/0/Download/KonaBess"
        return prefs.getString(SettingsViewModel.KEY_EXPORT_PATH_DISPLAY, "") ?: ""
    }
    fun setExportPathDisplay(path: String) = prefs.edit().putString(SettingsViewModel.KEY_EXPORT_PATH_DISPLAY, path).apply()
}
