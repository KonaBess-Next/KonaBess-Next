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

sealed class SettingsListItem {
    data class Header(val title: String) : SettingsListItem()
    data class Setting(val item: SettingItem) : SettingsListItem()
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
    isAmoledMode: Boolean,
    onAmoledModeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeTitle = androidx.compose.ui.res.stringResource(R.string.settings_theme)
    val themeDesc = androidx.compose.ui.res.stringResource(R.string.settings_theme_desc)
    val dynamicColorTitle = androidx.compose.ui.res.stringResource(R.string.settings_dynamic_color)
    val dynamicColorDesc = androidx.compose.ui.res.stringResource(R.string.settings_dynamic_color_desc)
    val paletteTitle = androidx.compose.ui.res.stringResource(R.string.settings_color_palette)
    val paletteDesc = androidx.compose.ui.res.stringResource(R.string.settings_color_palette_desc)
    val langTitle = androidx.compose.ui.res.stringResource(R.string.settings_language)
    val langDesc = androidx.compose.ui.res.stringResource(R.string.settings_language_desc)
    val freqTitle = androidx.compose.ui.res.stringResource(R.string.gpu_freq_unit)
    val freqDesc = androidx.compose.ui.res.stringResource(R.string.settings_freq_unit_desc)
    val autoSaveTitle = androidx.compose.ui.res.stringResource(R.string.settings_auto_save)
    val autoSaveDesc = androidx.compose.ui.res.stringResource(R.string.settings_auto_save_desc)
    val helpTitle = androidx.compose.ui.res.stringResource(R.string.settings_help_about)
    val versionTitle = androidx.compose.ui.res.stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME)
    val amoledTitle = androidx.compose.ui.res.stringResource(R.string.palette_amoled)

    val settingsItems = remember(currentTheme, isDynamicColor, currentColorPalette, currentLanguage, currentFreqUnit, isAutoSave, isAmoledMode, themeTitle) {
        buildList {
            // Appearance section
            add(SettingsListItem.Header("Appearance"))
            add(SettingsListItem.Setting(SettingItem.Clickable(R.drawable.ic_dark_mode, themeTitle, themeDesc, currentTheme)))
            add(SettingsListItem.Setting(SettingItem.Toggle(R.drawable.ic_dark_mode, amoledTitle, "Pure black background in Dark Mode", isAmoledMode)))
            add(SettingsListItem.Setting(SettingItem.Toggle(R.drawable.ic_tune, dynamicColorTitle, dynamicColorDesc, isDynamicColor)))
            if (!isDynamicColor) {
                add(SettingsListItem.Setting(SettingItem.Clickable(R.drawable.ic_tune, paletteTitle, paletteDesc, currentColorPalette)))
            }

            // Localization section
            add(SettingsListItem.Header("Localization"))
            add(SettingsListItem.Setting(SettingItem.Clickable(R.drawable.ic_language, langTitle, langDesc, currentLanguage)))
            add(SettingsListItem.Setting(SettingItem.Clickable(R.drawable.ic_frequency, freqTitle, freqDesc, currentFreqUnit)))

            // Behavior section
            add(SettingsListItem.Header("Behavior"))
            add(SettingsListItem.Setting(SettingItem.Toggle(R.drawable.ic_save, autoSaveTitle, autoSaveDesc, isAutoSave)))

            // About section
            add(SettingsListItem.Header("About"))
            add(SettingsListItem.Setting(SettingItem.Clickable(R.drawable.ic_help, helpTitle, versionTitle, "")))
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
                items(settingsItems) { listItem ->
                    when (listItem) {
                        is SettingsListItem.Header -> {
                            SettingsSectionHeader(title = listItem.title)
                        }
                        is SettingsListItem.Setting -> {
                            when (val item = listItem.item) {
                                is SettingItem.Clickable -> {
                                    SettingsClickableItem(
                                        icon = painterResource(item.iconRes),
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        currentValue = item.currentValue,
                                        onClick = {
                                            when (item.title) {
                                                themeTitle -> onThemeClick()
                                                paletteTitle -> onColorPaletteClick()
                                                langTitle -> onLanguageClick()
                                                freqTitle -> onFreqUnitClick()
                                                helpTitle -> onHelpClick()
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
                                                dynamicColorTitle -> onDynamicColorToggle()
                                                autoSaveTitle -> onAutoSaveToggle()
                                                amoledTitle -> onAmoledModeToggle()
                                            }
                                        }
                                    )
                                }
                            }
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
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
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
