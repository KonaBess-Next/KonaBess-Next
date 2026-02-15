package com.ireddragonicy.konabessnext.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.compose.MainNavigationBar
import com.ireddragonicy.konabessnext.ui.navigation.AppDestinations
import com.ireddragonicy.konabessnext.ui.navigation.AppNavGraph
import com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme
import com.ireddragonicy.konabessnext.utils.LocaleUtil
import com.ireddragonicy.konabessnext.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val deviceViewModel: DeviceViewModel by viewModels()
    private val gpuFrequencyViewModel: GpuFrequencyViewModel by viewModels()
    private val sharedViewModel: SharedGpuViewModel by viewModels()
    private val displayViewModel: DisplayViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val importExportViewModel: ImportExportViewModel by viewModels()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleUtil.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (!deviceViewModel.isFilesExtracted.value) {
            if (deviceViewModel.isRootMode) {
                deviceViewModel.detectChipset()
            } else {
                deviceViewModel.loadSavedDts()
            }
        }

        setContent {
            val uiState by settingsViewModel.uiState.collectAsState()
            
            // Calculate Theme Params
            val darkTheme = when (uiState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            
            val paletteId = when (uiState.colorPalette) {
                "Blue" -> SettingsViewModel.PALETTE_BLUE
                "Green" -> SettingsViewModel.PALETTE_GREEN
                "Pink" -> SettingsViewModel.PALETTE_PINK
                "Purple" -> SettingsViewModel.PALETTE_PURPLE
                else -> SettingsViewModel.PALETTE_DYNAMIC
            }

            KonaBessTheme(
                darkTheme = darkTheme,
                isDynamicColor = uiState.isDynamicColor,
                colorPaletteId = paletteId,
                isAmoledMode = uiState.isAmoledMode
            ) {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                val context = LocalContext.current
                AppNavGraph(
                    navController = navController,
                    deviceViewModel = deviceViewModel,
                    gpuFrequencyViewModel = gpuFrequencyViewModel,
                    sharedViewModel = sharedViewModel,
                    displayViewModel = displayViewModel,
                    settingsViewModel = settingsViewModel,
                    importExportViewModel = importExportViewModel,
                    snackbarHostState = snackbarHostState,
                    onStartRepack = { deviceViewModel.packAndFlash(this) },
                    onLanguageChange = { lang ->
                        settingsViewModel.setLanguage(lang)
                        (context as? android.app.Activity)?.recreate()
                    }
                )

                RepackStatusScreen(deviceViewModel)
            }
        }
    }

    @Composable
    private fun RepackStatusScreen(deviceViewModel: DeviceViewModel) {
        val repackState by deviceViewModel.repackState.collectAsState(initial = null)

        when (repackState) {
            is UiState.Loading -> {
                Dialog(onDismissRequest = {}) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(R.string.processing))
                        }
                    }
                }
            }
            is UiState.Success -> {
                AlertDialog(
                    onDismissRequest = { deviceViewModel.clearRepackState() },
                    title = { Text(stringResource(R.string.success)) },
                    text = { Text((repackState as UiState.Success).data.asString()) },
                    confirmButton = {
                        TextButton(onClick = {
                            deviceViewModel.reboot()
                            deviceViewModel.clearRepackState()
                        }) {
                            Text(stringResource(R.string.reboot))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deviceViewModel.clearRepackState() }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }
            is UiState.Error -> {
                AlertDialog(
                    onDismissRequest = { deviceViewModel.clearRepackState() },
                    title = { Text(stringResource(R.string.error)) },
                    text = { Text((repackState as UiState.Error).message.asString()) },
                    confirmButton = {
                        TextButton(onClick = { deviceViewModel.clearRepackState() }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }
            else -> {}
        }
    }
}