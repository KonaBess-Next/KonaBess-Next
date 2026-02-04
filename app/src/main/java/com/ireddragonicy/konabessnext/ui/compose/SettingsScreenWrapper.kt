package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ireddragonicy.konabessnext.viewmodel.SettingsViewModel

@Composable
fun SettingsScreenWrapper(
    settingsViewModel: SettingsViewModel,
    onLanguageChange: (String) -> Unit
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) { 
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help") },
            text = { 
                Text("KonaBess Next\nA tool to modify GPU frequency and voltage tables for Qualcomm Snapdragon devices.\n\nUse at your own risk.") 
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                     val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/ireddragonicy/KonaBess"))
                     context.startActivity(intent)
                }) { Text("GitHub") }
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = uiState.language,
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { 
                onLanguageChange(it)
                showLanguageDialog = false 
            }
        )
    }
    
    if (showPaletteDialog) {
        PaletteSelectionDialog(
            currentColorPalette = uiState.colorPalette,
            onDismiss = { showPaletteDialog = false },
            onPaletteSelected = {
                val paletteInt = when(it) {
                    "Purple" -> SettingsViewModel.PALETTE_PURPLE
                    "Blue" -> SettingsViewModel.PALETTE_BLUE
                    "Green" -> SettingsViewModel.PALETTE_GREEN
                    "Pink" -> SettingsViewModel.PALETTE_PINK
                    else -> SettingsViewModel.PALETTE_DYNAMIC
                }
                settingsViewModel.setColorPalette(paletteInt)
                showPaletteDialog = false
            }
        )
    }

    SettingsScreen(
        currentTheme = uiState.themeMode.name,
        isDynamicColor = uiState.isDynamicColor,
        currentColorPalette = uiState.colorPalette,
        currentLanguage = uiState.language,
        currentFreqUnit = uiState.frequencyUnit,
        isAutoSave = uiState.autoSave,
        isAmoledMode = uiState.isAmoledMode,
        // Actions
        onThemeClick = { settingsViewModel.cycleThemeMode() },
        onDynamicColorToggle = { settingsViewModel.toggleDynamicColor() },
        onColorPaletteClick = { showPaletteDialog = true },
        onLanguageClick = { showLanguageDialog = true },
        onFreqUnitClick = { settingsViewModel.toggleFrequencyUnit() },
        onAutoSaveToggle = { settingsViewModel.toggleAutoSave() },
        onAmoledModeToggle = { settingsViewModel.toggleAmoledMode() },
        onHelpClick = { showHelpDialog = true },
        
        // Updater
        updateChannel = uiState.updateChannel,
        updateStatus = uiState.updateStatus,
        onUpdateChannelChange = { channel -> settingsViewModel.setUpdateChannel(channel) },
        onCheckForUpdates = { settingsViewModel.checkForUpdates() },
        onClearUpdateStatus = { settingsViewModel.clearUpdateStatus() }
    )
}
