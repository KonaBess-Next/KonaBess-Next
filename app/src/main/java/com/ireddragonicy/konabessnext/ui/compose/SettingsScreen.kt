package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.BuildConfig
import com.ireddragonicy.konabessnext.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Contrast
import com.ireddragonicy.konabessnext.viewmodel.UpdateStatus
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

sealed class SettingItem {
    data class Clickable(
        val icon: Any, // Can be Int (Res ID) or ImageVector
        val title: String,
        val subtitle: String,
        val currentValue: String = ""
    ) : SettingItem()
    
    data class Toggle(
        val icon: Any,
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
    // Updater Params
    updateChannel: String,
    updateStatus: UpdateStatus,
    onUpdateChannelChange: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onClearUpdateStatus: () -> Unit,
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

    // Updater Strings
    val headerAppearance = stringResource(R.string.header_appearance)
    val headerLocalization = stringResource(R.string.header_localization)
    val headerBehavior = stringResource(R.string.header_behavior)
    val headerAbout = stringResource(R.string.header_about)
    
    val channelTitle = stringResource(R.string.update_channel)
    val channelStable = stringResource(R.string.channel_stable)
    val channelPrerelease = stringResource(R.string.channel_prerelease)
    val channelDesc = if (updateChannel == "prerelease") stringResource(R.string.channel_desc_prerelease) else stringResource(R.string.channel_desc_stable)
    val checkUpdatesTitle = stringResource(R.string.check_updates)
    val checkUpdatesDesc = if (updateStatus is UpdateStatus.Checking) stringResource(R.string.checking_updates) else stringResource(R.string.check_updates_desc)

    val context = LocalContext.current

    val settingsItems = remember(currentTheme, isDynamicColor, currentColorPalette, currentLanguage, currentFreqUnit, isAutoSave, isAmoledMode, themeTitle, updateChannel, updateStatus) {
        buildList {
            // Appearance section
            add(SettingsListItem.Header(headerAppearance))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.DarkMode, themeTitle, themeDesc, currentTheme)))
            add(SettingsListItem.Setting(SettingItem.Toggle(
                Icons.Rounded.Contrast, // Pure Amoled - Contrast icon fits well
                amoledTitle, 
                "Pure black background in Dark Mode", 
                isAmoledMode
            )))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.Palette, paletteTitle, paletteDesc, currentColorPalette)))

            // Localization section
            add(SettingsListItem.Header(headerLocalization))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.Translate, langTitle, langDesc, currentLanguage)))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.Speed, freqTitle, freqDesc, currentFreqUnit)))

            // Behavior section
            add(SettingsListItem.Header(headerBehavior))
            add(SettingsListItem.Setting(SettingItem.Toggle(Icons.Rounded.Save, autoSaveTitle, autoSaveDesc, isAutoSave)))

            // About section
            add(SettingsListItem.Header(headerAbout))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.SystemUpdate, channelTitle, channelDesc, if (updateChannel == "prerelease") channelPrerelease else channelStable)))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.SystemUpdate, checkUpdatesTitle, checkUpdatesDesc, "")))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.Info, helpTitle, versionTitle, "")))
        }
    }

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
                                val iconPainter = if (item.icon is Int) painterResource(item.icon) else rememberVectorPainter(item.icon as ImageVector)
                                SettingsClickableItem(
                                    icon = iconPainter,
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
                                            channelTitle -> onUpdateChannelChange(if (updateChannel == "stable") "prerelease" else "stable")
                                            checkUpdatesTitle -> onCheckForUpdates()
                                        }
                                    }
                                )
                            }
                                    is SettingItem.Toggle -> {
                                val iconPainter = if (item.icon is Int) painterResource(item.icon) else rememberVectorPainter(item.icon as ImageVector)
                                SettingsToggleItem(
                                    icon = iconPainter,
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


    // Update Dialog Handling
    if (updateStatus is UpdateStatus.Available) {
        val release = updateStatus.release
        AlertDialog(
            onDismissRequest = { onClearUpdateStatus() },
            title = { Text(stringResource(R.string.update_available_title, release.tagName)) },
            text = {
                Column {
                    Text(release.body.take(500) + if(release.body.length > 500) "..." else "")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearUpdateStatus()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.btn_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { onClearUpdateStatus() }) {
                    Text(stringResource(R.string.btn_later))
                }
            }
        )
    }

    // Error Snackbar/Toast Handling
    if (updateStatus is UpdateStatus.Error) {
        val msg = updateStatus.message
        val toastMsg = stringResource(R.string.toast_update_error, msg)
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_LONG).show()
            onClearUpdateStatus()
        }
    }

    if (updateStatus is UpdateStatus.NoUpdate) {
        val toastMsg = stringResource(R.string.toast_no_updates)
        LaunchedEffect(updateStatus) { // Key on UpdateStatus
            android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
            onClearUpdateStatus()
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
