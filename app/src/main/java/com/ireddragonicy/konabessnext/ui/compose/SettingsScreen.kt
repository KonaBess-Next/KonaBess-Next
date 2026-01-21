package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.BuildConfig
import com.ireddragonicy.konabessnext.R

sealed class SettingItem {
    data class Clickable(
        val iconRes: Int,
        val title: String,
        val subtitle: String,
        val currentValue: String = ""
    ) : SettingItem()
    
    data class Toggle(
        val iconRes: Int,
        val title: String,
        val subtitle: String,
        val isChecked: Boolean
    ) : SettingItem()
}

@Composable
fun SettingsScreen(
    currentTheme: String,
    isDynamicColor: Boolean,
    currentColorPalette: String,
    currentLanguage: String,
    currentFreqUnit: String,
    isAutoSave: Boolean,
    onThemeClick: () -> Unit,
    onDynamicColorToggle: () -> Unit,
    onColorPaletteClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onFreqUnitClick: () -> Unit,
    onAutoSaveToggle: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsItems = remember(currentTheme, isDynamicColor, currentColorPalette, currentLanguage, currentFreqUnit, isAutoSave) {
        buildList {
            add(SettingItem.Clickable(R.drawable.ic_dark_mode, "Theme", "Choose light, dark, or system theme", currentTheme))
            add(SettingItem.Toggle(R.drawable.ic_tune, "Dynamic Color", "Use wallpaper-based colors (Material You)", isDynamicColor))
            if (!isDynamicColor) {
                add(SettingItem.Clickable(R.drawable.ic_tune, "Color Palette", "Choose your color scheme", currentColorPalette))
            }
            add(SettingItem.Clickable(R.drawable.ic_language, "Language", "Select your preferred language", currentLanguage))
            add(SettingItem.Clickable(R.drawable.ic_frequency, "GPU Frequency Unit", "Display frequency in Hz, MHz, or GHz", currentFreqUnit))
            add(SettingItem.Toggle(R.drawable.ic_save, "Auto-save GPU Table", "Automatically save changes to GPU frequency table", isAutoSave))
            add(SettingItem.Clickable(R.drawable.ic_help, "Help & About", "Version ${BuildConfig.VERSION_NAME}", ""))
        }
    }

    com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(settingsItems) { item ->
                    when (item) {
                        is SettingItem.Clickable -> {
                            SettingsClickableItem(
                                icon = painterResource(item.iconRes),
                                title = item.title,
                                subtitle = item.subtitle,
                                currentValue = item.currentValue,
                                onClick = {
                                    when (item.title) {
                                        "Theme" -> onThemeClick()
                                        "Color Palette" -> onColorPaletteClick()
                                        "Language" -> onLanguageClick()
                                        "GPU Frequency Unit" -> onFreqUnitClick()
                                        "Help & About" -> onHelpClick()
                                    }
                                }
                            )
                        }
                        is SettingItem.Toggle -> {
                            SettingsToggleItem(
                                icon = painterResource(item.iconRes),
                                title = item.title,
                                subtitle = item.subtitle,
                                isChecked = item.isChecked,
                                onToggle = {
                                    when (item.title) {
                                        "Dynamic Color" -> onDynamicColorToggle()
                                        "Auto-save GPU Table" -> onAutoSaveToggle()
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Bottom spacer for navigation bar
                item {
                    Spacer(Modifier.height(88.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsClickableItem(
    icon: Painter,
    title: String,
    subtitle: String,
    currentValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (currentValue.isNotEmpty()) {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: Painter,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = isChecked,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
