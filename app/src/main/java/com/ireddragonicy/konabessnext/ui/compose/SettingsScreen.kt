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
import androidx.compose.material.icons.rounded.Security
import com.ireddragonicy.konabessnext.viewmodel.UpdateStatus
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape

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
    currentColorPalette: String,
    currentLanguage: String,
    currentFreqUnit: String,
    isAutoSave: Boolean,
    onThemeClick: () -> Unit,
    onColorPaletteClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onFreqUnitClick: () -> Unit,
    onAutoSaveToggle: () -> Unit,
    onHelpClick: () -> Unit,

    isAmoledMode: Boolean,
    onAmoledModeToggle: () -> Unit,
    // Root Mode
    isRootMode: Boolean,
    onRootModeToggle: () -> Unit,
    // Updater Params
    updateChannel: String,
    isAutoCheckUpdate: Boolean,
    updateStatus: UpdateStatus,
    onUpdateChannelChange: (String) -> Unit,
    onAutoCheckUpdateToggle: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onClearUpdateStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeTitle = androidx.compose.ui.res.stringResource(R.string.settings_theme)
    val themeDesc = androidx.compose.ui.res.stringResource(R.string.settings_theme_desc)
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
    val amoledDesc = stringResource(R.string.settings_amoled_desc)

    val currentThemeDisplay = when (currentTheme.uppercase()) {
        "LIGHT" -> stringResource(R.string.theme_light)
        "DARK" -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }

    val currentLanguageDisplay = when (currentLanguage) {
        "en" -> stringResource(R.string.english)
        "de" -> stringResource(R.string.german)
        "zh-rCN" -> stringResource(R.string.chinese)
        "in" -> stringResource(R.string.indonesian)
        "pl" -> stringResource(R.string.polish)
        else -> currentLanguage
    }

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
    val autoCheckUpdateTitle = stringResource(R.string.auto_check_updates)
    val autoCheckUpdateDesc = stringResource(R.string.auto_check_updates_desc)

    val context = LocalContext.current

    val rootModeTitle = stringResource(R.string.settings_root_mode)
    val rootModeDesc = if (isRootMode) stringResource(R.string.settings_root_mode_desc_on) else stringResource(R.string.settings_root_mode_desc_off)

    val settingsItems = remember(currentThemeDisplay, currentColorPalette, currentLanguageDisplay, currentFreqUnit, isAutoSave, isAmoledMode, themeTitle, updateChannel, isAutoCheckUpdate, updateStatus, isRootMode, rootModeDesc, amoledDesc) {
        buildList {
            // Appearance section
            add(SettingsListItem.Header(headerAppearance))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.DarkMode, themeTitle, themeDesc, currentThemeDisplay)))
            add(SettingsListItem.Setting(SettingItem.Toggle(
                Icons.Rounded.Contrast, // Pure Amoled - Contrast icon fits well
                amoledTitle, 
                amoledDesc,
                isAmoledMode
            )))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.Palette, paletteTitle, paletteDesc, currentColorPalette)))

            // Localization section
            add(SettingsListItem.Header(headerLocalization))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.Translate, langTitle, langDesc, currentLanguageDisplay)))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.Speed, freqTitle, freqDesc, currentFreqUnit)))

            // Behavior section
            add(SettingsListItem.Header(headerBehavior))
            add(SettingsListItem.Setting(SettingItem.Toggle(Icons.Rounded.Save, autoSaveTitle, autoSaveDesc, isAutoSave)))
            add(SettingsListItem.Setting(SettingItem.Toggle(Icons.Rounded.Security, rootModeTitle, rootModeDesc, isRootMode)))

            // About section
            add(SettingsListItem.Header(headerAbout))
            add(SettingsListItem.Setting(SettingItem.Clickable(Icons.Rounded.SystemUpdate, channelTitle, channelDesc, if (updateChannel == "prerelease") channelPrerelease else channelStable)))
            add(SettingsListItem.Setting(SettingItem.Toggle(Icons.Rounded.SystemUpdate, autoCheckUpdateTitle, autoCheckUpdateDesc, isAutoCheckUpdate)))
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
                                            amoledTitle -> onAmoledModeToggle()
                                            autoSaveTitle -> onAutoSaveToggle()
                                            autoCheckUpdateTitle -> onAutoCheckUpdateToggle()
                                            rootModeTitle -> onRootModeToggle()
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
        val scrollState = androidx.compose.foundation.rememberScrollState()
        
        Dialog(onDismissRequest = { onClearUpdateStatus() }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp) // Limit height
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp).padding(bottom = 16.dp)
                        )
                        Text(
                            text = stringResource(R.string.update_available_title, release.tagName),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    // Markdown Content
                    Box(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
                         Column(modifier = Modifier.verticalScroll(scrollState)) {
                             MarkdownText(
                                 markdown = release.body,
                                 style = MaterialTheme.typography.bodyMedium
                             )
                         }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Actions
                    Row(
                         modifier = Modifier
                             .fillMaxWidth()
                             .padding(top = 16.dp),
                         horizontalArrangement = Arrangement.End,
                         verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onClearUpdateStatus() }
                        ) {
                            Text(stringResource(R.string.btn_later))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onClearUpdateStatus()
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.btn_download))
                        }
                    }
                }
            }
        }
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
