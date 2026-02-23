package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.TargetPartition
import com.ireddragonicy.konabessnext.utils.BinDiffResult
import com.ireddragonicy.konabessnext.viewmodel.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    deviceViewModel: DeviceViewModel,
    gpuFrequencyViewModel: GpuFrequencyViewModel,
    sharedViewModel: SharedGpuViewModel,
    displayViewModel: DisplayViewModel,
    highlightCache: Map<Int, androidx.compose.ui.text.AnnotatedString> = emptyMap(),
    onStartRepack: () -> Unit,
    onSelectionDragStateChanged: (Boolean) -> Unit = {}
) {
    val isFilesExtracted by deviceViewModel.isFilesExtracted.collectAsState()
    val reloadTrigger by deviceViewModel.dataReloadTrigger.collectAsState()
    val selectedPartition by deviceViewModel.selectedPartition.collectAsState()

    // Obtain the same ViewModel instances used by UnifiedDtsEditorScreen and VisualTreeContent
    val textEditorViewModel: TextEditorViewModel = hiltViewModel()
    val visualTreeViewModel: VisualTreeViewModel = hiltViewModel()
    val dtboNavViewModel: com.ireddragonicy.konabessnext.viewmodel.dtbo.DtboNavViewModel = hiltViewModel()

    // Load data when extracted (Preserves state across rotation/theme change)
    LaunchedEffect(isFilesExtracted) {
        if (isFilesExtracted) {
            sharedViewModel.loadData()
            textEditorViewModel.setActivePartition(selectedPartition)
            visualTreeViewModel.setActivePartition(selectedPartition)
            if (selectedPartition == TargetPartition.DTBO) {
                dtboNavViewModel.loadData()
            }
        }
    }
    
    // Reload data when import triggers it (reloadTrigger > 0 means import happened)
    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0) {
            sharedViewModel.loadData()
            if (selectedPartition == TargetPartition.DTBO) {
                dtboNavViewModel.loadData()
            }
        }
    }

    // Switch text/tree editors and load display data when partition changes
    LaunchedEffect(selectedPartition) {
        textEditorViewModel.setActivePartition(selectedPartition)
        visualTreeViewModel.setActivePartition(selectedPartition)
        if (isFilesExtracted && selectedPartition == TargetPartition.DTBO) {
            dtboNavViewModel.loadData()
        }
    }

    if (isFilesExtracted) {
        GpuEditorMainContent(
            deviceViewModel = deviceViewModel,
            gpuFrequencyViewModel = gpuFrequencyViewModel,
            sharedViewModel = sharedViewModel,
            displayViewModel = displayViewModel,
            onStartRepack = onStartRepack,
            onSelectionDragStateChanged = onSelectionDragStateChanged
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
    displayViewModel: DisplayViewModel,
    onStartRepack: () -> Unit,
    onSelectionDragStateChanged: (Boolean) -> Unit = {}
) {
    val selectedPartition by deviceViewModel.selectedPartition.collectAsState()
    val isDtboPartition = selectedPartition == TargetPartition.DTBO

    // Partition-aware toolbar state: GPU or Display
    val gpuIsDirty by gpuFrequencyViewModel.isDirty.collectAsState()
    val gpuCanUndo by gpuFrequencyViewModel.canUndo.collectAsState()
    val gpuCanRedo by gpuFrequencyViewModel.canRedo.collectAsState()
    val gpuHistory by gpuFrequencyViewModel.history.collectAsState()

    val dtboNavViewModel: com.ireddragonicy.konabessnext.viewmodel.dtbo.DtboNavViewModel = hiltViewModel()
    val displayIsDirty by dtboNavViewModel.isDirty.collectAsState()
    val displayCanUndo by dtboNavViewModel.canUndo.collectAsState()
    val displayCanRedo by dtboNavViewModel.canRedo.collectAsState()
    val displayHistory by dtboNavViewModel.history.collectAsState()

    val isDirty = if (isDtboPartition) displayIsDirty else gpuIsDirty
    val canUndo = if (isDtboPartition) displayCanUndo else gpuCanUndo
    val canRedo = if (isDtboPartition) displayCanRedo else gpuCanRedo
    val history = if (isDtboPartition) displayHistory else gpuHistory
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
    var showInactiveInstallDialog by remember { mutableStateOf(false) }
    var showDiffSheet by remember { mutableStateOf(false) }
    var pendingDiffAction by remember { mutableStateOf<DiffCommitAction?>(null) }
    var isDiffLoading by remember { mutableStateOf(false) }
    var diffResults by remember { mutableStateOf<List<BinDiffResult>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Obtain the same ViewModel instances for partition-aware text/tree switching
    val textEditorViewModel: TextEditorViewModel = hiltViewModel()
    val visualTreeViewModel: VisualTreeViewModel = hiltViewModel()

    // Sheets
    val detectionState by deviceViewModel.detectionState.collectAsState()
    val selectedChipset by deviceViewModel.selectedChipset.collectAsState()
    val activeDtbId by deviceViewModel.activeDtbId.collectAsState()
    val availablePartitions by deviceViewModel.availablePartitions.collectAsState()
    
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

    if (showInactiveInstallDialog) {
        var backupChecked by remember { mutableStateOf(true) }
        val targetSlot = deviceViewModel.getInactiveSlotSuffixOrNull() ?: "unknown"

        AlertDialog(
            onDismissRequest = { showInactiveInstallDialog = false },
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text(stringResource(R.string.install_to_inactive_slot)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.install_to_inactive_slot_desc_format, targetSlot)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { backupChecked = !backupChecked }
                    ) {
                        Checkbox(checked = backupChecked, onCheckedChange = { backupChecked = it })
                        Text(stringResource(R.string.backup_target_boot_image_first))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deviceViewModel.installToInactiveSlot(backupChecked)
                        showInactiveInstallDialog = false
                    }
                ) {
                    Text(stringResource(R.string.install))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInactiveInstallDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val exportDtsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) sharedViewModel.exportRawDts(context, uri)
    }
    
    fun launchExportDts() {
        scope.launch {
             if (!sharedViewModel.tryExportRawDtsToDefault(context)) {
                 exportDtsLauncher.launch("gpu_config.dts")
             }
        }
    }
    
    val exportImgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) deviceViewModel.exportBootImage(context, uri)
    }

    fun launchExportImage() {
        scope.launch {
            if (!deviceViewModel.tryExportBootImageToDefault(context)) {
                val filename = "${selectedPartition.partitionName}_repack.img"
                exportImgLauncher.launch(filename)
            }
        }
    }

    val dismissDiffSheet = {
        showDiffSheet = false
        pendingDiffAction = null
        isDiffLoading = false
        diffResults = emptyList()
    }

    val requestDiffPreview: (DiffCommitAction) -> Unit = { action ->
        pendingDiffAction = action
        showDiffSheet = true
        scope.launch {
            isDiffLoading = true
            diffResults = sharedViewModel.calculateDiff()
            isDiffLoading = false
        }
    }

    val confirmDiffAction = {
        when (pendingDiffAction) {
            DiffCommitAction.SAVE -> {
                if (selectedPartition == TargetPartition.DTBO) dtboNavViewModel.save()
                else gpuFrequencyViewModel.save(true)
            }
            DiffCommitAction.EXPORT_IMAGE -> launchExportImage()
            DiffCommitAction.FLASH_DEVICE -> onStartRepack()
            DiffCommitAction.INSTALL_INACTIVE_SLOT -> showInactiveInstallDialog = true
            null -> Unit
        }
        dismissDiffSheet()
    }

    if (showDiffSheet && pendingDiffAction != null) {
        DtsDiffViewer(
            action = pendingDiffAction!!,
            diffResults = diffResults,
            isLoading = isDiffLoading,
            onDismissRequest = dismissDiffSheet,
            onConfirm = confirmDiffAction
        )
    }

    GpuWorkbenchSheets(
        sheetType = activeSheet,
        onDismiss = { activeSheet = WorkbenchSheetType.NONE },
        history = history,
        dtbs = (detectionState as? UiState.Success)?.data ?: emptyList(),
        availablePartitions = availablePartitions,
        selectedPartition = selectedPartition,
        selectedDtbId = selectedChipset?.takeIf { it.partition == selectedPartition }?.id,
        activeDtbId = activeDtbId,
        onPartitionSelect = { partition ->
            deviceViewModel.selectPartition(partition)
        },
        onChipsetSelect = { dtb ->
            deviceViewModel.selectChipset(dtb)
            gpuFrequencyViewModel.selectedBinIndex.value = -1
            gpuFrequencyViewModel.selectedLevelIndex.value = -1
            sharedViewModel.loadData()
            if (dtb.partition == TargetPartition.DTBO) {
                textEditorViewModel.setActivePartition(TargetPartition.DTBO)
                visualTreeViewModel.setActivePartition(TargetPartition.DTBO)
                dtboNavViewModel.loadData()
            }
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
            onInstallToInactiveSlot = { showInactiveInstallDialog = true },
            onExportDts = { launchExportDts() },
            onExportImg = { launchExportImage() },
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
                onSave = {
                    if (isDtboPartition) dtboNavViewModel.save()
                    else gpuFrequencyViewModel.save(true)
                },
                onRequireDiffConfirmation = requestDiffPreview,
                onUndo = {
                    if (isDtboPartition) dtboNavViewModel.undo()
                    else gpuFrequencyViewModel.undo()
                },
                onRedo = {
                    if (isDtboPartition) dtboNavViewModel.redo()
                    else gpuFrequencyViewModel.redo()
                },
                onShowHistory = { activeSheet = WorkbenchSheetType.HISTORY },
                onViewModeChanged = { mode -> sharedViewModel.switchViewMode(mode) },
                onChipsetClick = { activeSheet = WorkbenchSheetType.CHIPSET },
                onFlashClick = { onStartRepack() },
                onInstallToInactiveSlot = { showInactiveInstallDialog = true },
                onExportDts = { launchExportDts() },
                onExportImg = { launchExportImage() },
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
                            displayViewModel = displayViewModel,
                            onOpenCurveEditor = { binId -> activeCurveEditorBinId = binId }
                        )
                        SharedGpuViewModel.ViewMode.TEXT_ADVANCED -> UnifiedDtsEditorScreen(
                            onSelectionDragStateChanged = onSelectionDragStateChanged
                        )
                        SharedGpuViewModel.ViewMode.VISUAL_TREE -> VisualTreeContent()
                    }
                }
            }
        }
    }
}
