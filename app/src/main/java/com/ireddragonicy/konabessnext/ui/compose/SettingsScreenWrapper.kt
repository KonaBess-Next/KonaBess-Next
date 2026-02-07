package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
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
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // App Icon
                    AndroidView(
                        factory = { ctx ->
                            android.widget.ImageView(ctx).apply {
                                setImageResource(com.ireddragonicy.konabessnext.R.mipmap.icon)
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // App Name & Version
                    Text(
                        text = "KonaBess Next",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "v" + com.ireddragonicy.konabessnext.BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Credits
                    Text(
                        text = "Developed by IRedDragonICY",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Based on work by LibXZR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons
                    // GitHub
                    OutlinedButton(
                        onClick = {
                             val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/KonaBess-Next/KonaBess-Next"))
                             context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GitHub Source")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { showHelpDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
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
        currentColorPalette = uiState.colorPalette,
        currentLanguage = uiState.language,
        currentFreqUnit = uiState.frequencyUnit,
        isAutoSave = uiState.autoSave,
        isAmoledMode = uiState.isAmoledMode,
        // Actions
        onThemeClick = { settingsViewModel.cycleThemeMode() },
        onColorPaletteClick = { showPaletteDialog = true },
        onLanguageClick = { showLanguageDialog = true },
        onFreqUnitClick = { settingsViewModel.toggleFrequencyUnit() },
        onAutoSaveToggle = { settingsViewModel.toggleAutoSave() },
        onAmoledModeToggle = { settingsViewModel.toggleAmoledMode() },
        onHelpClick = { showHelpDialog = true },
        
        // Updater
        updateChannel = uiState.updateChannel,
        isAutoCheckUpdate = uiState.isAutoCheckUpdate,
        updateStatus = uiState.updateStatus,
        onUpdateChannelChange = { channel -> settingsViewModel.setUpdateChannel(channel) },
        onAutoCheckUpdateToggle = { settingsViewModel.toggleAutoCheckUpdate() },
        onCheckForUpdates = { settingsViewModel.checkForUpdates(isManual = true) },
        onClearUpdateStatus = { settingsViewModel.clearUpdateStatus() }
    )
}
