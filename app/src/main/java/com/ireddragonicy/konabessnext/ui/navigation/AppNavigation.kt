package com.ireddragonicy.konabessnext.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ireddragonicy.konabessnext.ui.compose.HomeScreen
import com.ireddragonicy.konabessnext.ui.compose.CurveEditorScreen
import com.ireddragonicy.konabessnext.ui.compose.ImportExportScreen
import com.ireddragonicy.konabessnext.ui.compose.SettingsScreen
import com.ireddragonicy.konabessnext.ui.compose.LanguageSelectionDialog
import com.ireddragonicy.konabessnext.ui.compose.PaletteSelectionDialog
import com.ireddragonicy.konabessnext.viewmodel.*
import kotlinx.coroutines.launch

object AppDestinations {
    const val HOME = "home"
    const val IMPORT_EXPORT = "import_export"
    const val SETTINGS = "settings"
    const val CURVE_EDITOR = "curve_editor/{binId}"
    const val EXPORT_HISTORY = "export_history"

    fun curveEditor(binId: Int) = "curve_editor/$binId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    deviceViewModel: DeviceViewModel,
    gpuFrequencyViewModel: GpuFrequencyViewModel,
    sharedViewModel: SharedGpuViewModel,
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    snackbarHostState: SnackbarHostState,
    onStartRepack: () -> Unit,
    onLanguageChange: (String) -> Unit, // Added param
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.HOME,
        modifier = modifier
    ) {
        composable(AppDestinations.HOME) {
            HomeScreen(
                deviceViewModel = deviceViewModel,
                gpuFrequencyViewModel = gpuFrequencyViewModel,
                sharedViewModel = sharedViewModel,
                onOpenCurveEditor = { binId ->
                    navController.navigate(AppDestinations.curveEditor(binId))
                },
                onStartRepack = onStartRepack
            )
        }

        composable(AppDestinations.IMPORT_EXPORT) {
            val isPrepared by deviceViewModel.isPrepared.collectAsState()
            val context = LocalContext.current
            val importPreview by importExportViewModel.importPreview.collectAsState()
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                importExportViewModel.messageEvent.collect { message ->
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short
                    )
                }
            }

            LaunchedEffect(Unit) {
                importExportViewModel.errorEvent.collect { message ->
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Long
                    )
                }
            }

            // Import Config
            val importConfigLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let { importExportViewModel.importConfigFromUri(context, it) }
            }

            // Export Config
            val exportConfigLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                uri?.let { importExportViewModel.exportConfigToUri(context, it, "Exported Config") }
            }

            // Export Raw DTS (Assuming .dts or .txt is fine, keeps text/plain)
            val exportRawDtsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                uri?.let { importExportViewModel.exportRawDtsToUri(context, it) }
            }

            // Backup Boot Image
            val backupBootLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri ->
                uri?.let { importExportViewModel.backupBootToUri(context, it) }
            }

            // Batch DTB to DTS
            val batchDtbLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    importExportViewModel.batchConvertDtbToDts(context, uris)
                }
            }

            // Batch Progress Dialog
            val batchProgress by importExportViewModel.batchProgress.collectAsState()
            
            // State for Export Description
            var pendingExportDesc by remember { mutableStateOf("Exported Config") }
            
            val exportConfigLauncherWithDesc = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                uri?.let { importExportViewModel.exportConfigToUri(context, it, pendingExportDesc) }
            }
            
            ImportExportScreen(
                isPrepared = isPrepared,
                onExportHistory = { navController.navigate(AppDestinations.EXPORT_HISTORY) }, 
                onImportFromFile = { importConfigLauncher.launch(arrayOf("*/*")) },
                onExportToFile = { desc: String -> 
                    pendingExportDesc = desc
                    exportConfigLauncherWithDesc.launch("konabess_config.txt") 
                },
                onImportFromClipboard = { content: String -> importExportViewModel.importConfig(content) }, 
                onExportToClipboard = { desc: String -> 
                     val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                     val content = importExportViewModel.exportConfig(desc)
                     if (content != null) {
                         val clip = android.content.ClipData.newPlainText("KonaBess Config", content)
                         clipboard.setPrimaryClip(clip)
                         importExportViewModel.addToHistory("Clipboard", desc, "clipboard://text")
                         importExportViewModel.notifyExportResult(content)
                         scope.launch {
                            snackbarHostState.showSnackbar("Copied to clipboard")
                         }
                     }
                },
                onExportRawDts = { exportRawDtsLauncher.launch("raw_dts_dump.dts") },
                onBackupBootImage = { backupBootLauncher.launch("boot_modified.img") },
                onBatchDtbToDts = { batchDtbLauncher.launch(arrayOf("*/*")) },
                onDismissResult = { importExportViewModel.clearExportResult() },
                onOpenFile = { path: String -> 
                    val file = java.io.File(path)
                    if (file.exists()) {
                        try {
                            val builder = android.os.StrictMode.VmPolicy.Builder()
                            android.os.StrictMode.setVmPolicy(builder.build())
                            
                            val uri = android.net.Uri.fromFile(file)
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "text/plain")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
                        } catch (e: Exception) {
                            // Replace Toast
                        }
                    } else {
                         // Replace Toast
                    }
                },
                lastExportedResult = importExportViewModel.lastExportedContent.collectAsState(initial = null).value,
                importPreview = importPreview,
                onConfirmImport = { importExportViewModel.confirmImport() },
                onCancelImport = { importExportViewModel.cancelImport() }
            )
        }

        composable(AppDestinations.SETTINGS) {
            val uiState by settingsViewModel.uiState.collectAsState()
            val context = LocalContext.current
            
            var showLanguageDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var showPaletteDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var showHelpDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

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
        
        // Curve Editor route placeholder if needed
        composable(
            route = AppDestinations.CURVE_EDITOR,
            arguments = listOf(navArgument("binId") { type = NavType.IntType })
        ) { backStackEntry ->
            val binId = backStackEntry.arguments?.getInt("binId") ?: 0
            CurveEditorScreen(
                binId = binId,
                sharedViewModel = sharedViewModel,
                onBack = { navController.popBackStack() },
                onRepack = onStartRepack
            )
        }
        
        composable(AppDestinations.EXPORT_HISTORY) {
            com.ireddragonicy.konabessnext.ui.compose.ExportHistoryScreen(
                viewModel = importExportViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
