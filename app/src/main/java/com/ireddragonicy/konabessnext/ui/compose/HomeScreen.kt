package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import android.net.Uri
import com.ireddragonicy.konabessnext.viewmodel.*

@Composable
fun HomeScreen(
    deviceViewModel: DeviceViewModel,
    gpuFrequencyViewModel: GpuFrequencyViewModel,
    sharedViewModel: SharedGpuViewModel,
    highlightCache: Map<Int, androidx.compose.ui.text.AnnotatedString> = emptyMap(),
    onStartRepack: () -> Unit
) {
    val isFilesExtracted by deviceViewModel.isFilesExtracted.collectAsState()
    val reloadTrigger by deviceViewModel.dataReloadTrigger.collectAsState()

    // Load data when extracted (Preserves state across rotation/theme change)
    LaunchedEffect(isFilesExtracted) {
        if (isFilesExtracted) {
            sharedViewModel.loadData()
        }
    }
    
    // Reload data when import triggers it (reloadTrigger > 0 means import happened)
    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0) {
            sharedViewModel.loadData()
        }
    }

    if (isFilesExtracted) {
        GpuEditorMainContent(
            deviceViewModel = deviceViewModel,
            gpuFrequencyViewModel = gpuFrequencyViewModel,
            sharedViewModel = sharedViewModel,
            onStartRepack = onStartRepack
        )
    } else {
        InitialSetupScreen(
            deviceViewModel = deviceViewModel
        )
    }
}

@Composable
fun InitialSetupScreen(deviceViewModel: DeviceViewModel) {
    val detectionState by deviceViewModel.detectionState.collectAsState()
    var showManualSetup by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isRootMode = deviceViewModel.isRootMode

    // SAF launcher for importing any file in non-root mode (auto-detects type)
    val fileImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            deviceViewModel.importFile(context, uri)
        }
    }

    if (showManualSetup) {
        Dialog(onDismissRequest = { showManualSetup = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                ManualChipsetSetupScreen(
                    dtbIndex = 0,
                    autoStartScan = true,
                    onDeepScan = { deviceViewModel.performManualScan(0) },
                    onSave = { def ->
                        deviceViewModel.saveManualDefinition(def, 0)
                        showManualSetup = false
                    },
                    onCancel = { showManualSetup = false }
                )
            }
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        when (detectionState) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> {
                // Only root mode reaches Error state now; non-root shows toast + stays on import screen
                ErrorScreen(
                    message = (detectionState as UiState.Error).message.asString(),
                    onRetryClick = { deviceViewModel.detectChipset() },
                    onManualSetupClick = { showManualSetup = false },
                    onSmartScanClick = { showManualSetup = true },
                    onSubmitDtsClick = { }
                )
            }
            else -> {
                if (isRootMode) {
                    DetectionPromptScreen(onStartClick = { deviceViewModel.detectChipset() })
                } else {
                    NonRootImportScreen(
                        onImportFile = { fileImportLauncher.launch(arrayOf("*/*")) }
                    )
                }
            }
        }
    }
}

@Composable
fun GpuEditorMainContent(
    deviceViewModel: DeviceViewModel,
    gpuFrequencyViewModel: GpuFrequencyViewModel,
    sharedViewModel: SharedGpuViewModel,
    onStartRepack: () -> Unit
) {
    val isDirty by gpuFrequencyViewModel.isDirty.collectAsState()
    val canUndo by gpuFrequencyViewModel.canUndo.collectAsState()
    val canRedo by gpuFrequencyViewModel.canRedo.collectAsState()
    val history by gpuFrequencyViewModel.history.collectAsState()
    val currentMode by sharedViewModel.viewMode.collectAsState()

    val selectedBinIndex by gpuFrequencyViewModel.selectedBinIndex.collectAsState()
    val selectedLevelIndex by gpuFrequencyViewModel.selectedLevelIndex.collectAsState()

    // Local State for Curve Editor Overlay (Keeps BottomBar visible)
    var activeCurveEditorBinId by remember { mutableIntStateOf(-1) }

    // Handle Back Press for Curve Editor
    BackHandler(enabled = activeCurveEditorBinId != -1) {
        activeCurveEditorBinId = -1
    }

    // Handle Back Press for Editor Navigation (Only if Curve Editor is closed)
    BackHandler(enabled = activeCurveEditorBinId == -1 && selectedBinIndex != -1) {
        if (selectedLevelIndex != -1) {
            gpuFrequencyViewModel.selectedLevelIndex.value = -1
        } else {
            gpuFrequencyViewModel.selectedBinIndex.value = -1
        }
    }

    var activeSheet by remember { mutableStateOf(WorkbenchSheetType.NONE) }
    var manualSetupIndex by remember { mutableStateOf<Int?>(null) }

    // Sheets
    val detectionState by deviceViewModel.detectionState.collectAsState()
    val selectedChipset by deviceViewModel.selectedChipset.collectAsState()
    val activeDtbId by deviceViewModel.activeDtbId.collectAsState()
    
    val context = LocalContext.current
    val canFlashOrRepack by deviceViewModel.canFlashOrRepack.collectAsState()
    val isRootMode = deviceViewModel.isRootMode

    // Dialogs
    if (manualSetupIndex != null) {
        val setupIndex = manualSetupIndex!!
        // Determine if this DTB has an official/known definition
        val dtbsList = (detectionState as? UiState.Success)?.data ?: emptyList()
        val targetDtb = dtbsList.find { it.id == setupIndex }
        val isOfficial = targetDtb != null &&
            !targetDtb.type.id.startsWith("custom") &&
            !targetDtb.type.id.startsWith("unsupported")

        Dialog(onDismissRequest = { manualSetupIndex = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                ManualChipsetSetupScreen(
                    dtbIndex = setupIndex,
                    autoStartScan = !isOfficial,
                    existingDefinition = targetDtb?.type,
                    onDeepScan = { deviceViewModel.performManualScan(setupIndex) },
                    onSave = { def ->
                        deviceViewModel.saveManualDefinition(def, setupIndex)
                        sharedViewModel.loadData()
                        manualSetupIndex = null
                        activeSheet = WorkbenchSheetType.NONE
                    },
                    onCancel = { manualSetupIndex = null }
                )
            }
        }
    }

    val exportDtsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) sharedViewModel.exportRawDts(context, uri)
    }
    
    val exportImgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) deviceViewModel.exportBootImage(context, uri)
    }

    GpuWorkbenchSheets(
        sheetType = activeSheet,
        onDismiss = { activeSheet = WorkbenchSheetType.NONE },
        history = history,
        dtbs = (detectionState as? UiState.Success)?.data ?: emptyList(),
        selectedDtbId = selectedChipset?.id,
        activeDtbId = activeDtbId,
        onChipsetSelect = { dtb ->
            deviceViewModel.selectChipset(dtb)
            gpuFrequencyViewModel.selectedBinIndex.value = -1
            gpuFrequencyViewModel.selectedLevelIndex.value = -1
            sharedViewModel.loadData()
            activeSheet = WorkbenchSheetType.NONE
        },
        onConfigureManual = { id -> manualSetupIndex = id },
        onDeleteDts = { id -> deviceViewModel.deleteDts(id) },
        onImportDts = { uri -> deviceViewModel.importExternalDts(context, uri) }
    )

    if (activeCurveEditorBinId != -1) {
        CurveEditorScreen(
            binId = activeCurveEditorBinId,
            sharedViewModel = sharedViewModel,
            onBack = { activeCurveEditorBinId = -1 },
            onRepack = onStartRepack,
            onExportDts = { exportDtsLauncher.launch("gpu_config.dts") },
            onExportImg = { exportImgLauncher.launch("boot_repack.img") },
            canFlashOrRepack = canFlashOrRepack,
            isRootMode = isRootMode
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            GpuEditorToolbar(
                isDirty = isDirty,
                canUndo = canUndo,
                canRedo = canRedo,
                historyCount = history.size,
                currentViewMode = currentMode,
                showChipsetSelector = true,
                onSave = { gpuFrequencyViewModel.save(true) },
                onUndo = { gpuFrequencyViewModel.undo() },
                onRedo = { gpuFrequencyViewModel.redo() },
                onShowHistory = { activeSheet = WorkbenchSheetType.HISTORY },
                onViewModeChanged = { mode -> sharedViewModel.switchViewMode(mode) },
                onChipsetClick = { activeSheet = WorkbenchSheetType.CHIPSET },
                onFlashClick = { onStartRepack() },
                onExportDts = { exportDtsLauncher.launch("gpu_config.dts") },
                onExportImg = { exportImgLauncher.launch("boot_repack.img") },
                canFlashOrRepack = canFlashOrRepack,
                isRootMode = isRootMode
            )
    
            Crossfade(targetState = currentMode, label = "ViewModeSwitch") { mode ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (mode) {
                        SharedGpuViewModel.ViewMode.MAIN_EDITOR -> GuiEditorContent(
                            sharedViewModel, 
                            deviceViewModel, 
                            gpuFrequencyViewModel,
                            onOpenCurveEditor = { binId -> activeCurveEditorBinId = binId }
                        )
                        SharedGpuViewModel.ViewMode.TEXT_ADVANCED -> UnifiedDtsEditorScreen(sharedViewModel)
                        SharedGpuViewModel.ViewMode.VISUAL_TREE -> VisualTreeContent(sharedViewModel)
                    }
                }
            }
        }
    }
}
