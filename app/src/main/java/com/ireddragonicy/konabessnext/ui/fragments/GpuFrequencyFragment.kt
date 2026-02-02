package com.ireddragonicy.konabessnext.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.compose.*
import com.ireddragonicy.konabessnext.viewmodel.DeviceViewModel
import com.ireddragonicy.konabessnext.viewmodel.GpuFrequencyViewModel
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import com.ireddragonicy.konabessnext.viewmodel.UiState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GpuFrequencyFragment : Fragment() {

    private val deviceViewModel: DeviceViewModel by activityViewModels()
    private val gpuFrequencyViewModel: GpuFrequencyViewModel by activityViewModels()
    private val sharedViewModel: SharedGpuViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // CRITICAL FIX: Dispose strategy handles View lifecycle correctly during recreation
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val isFilesExtracted by deviceViewModel.isFilesExtracted.collectAsState()

                        // Load data when extracted (Preserves state across rotation/theme change)
                        LaunchedEffect(isFilesExtracted) {
                            if (isFilesExtracted) {
                                sharedViewModel.loadData()
                            }
                        }

                        if (isFilesExtracted) {
                            GpuEditorMainScreen(
                                deviceViewModel = deviceViewModel,
                                gpuFrequencyViewModel = gpuFrequencyViewModel,
                                sharedViewModel = sharedViewModel,
                                activity = requireActivity() as MainActivity
                            )
                        } else {
                            InitialSetupScreen(
                                deviceViewModel = deviceViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InitialSetupScreen(deviceViewModel: DeviceViewModel) {
    val detectionState by deviceViewModel.detectionState.collectAsState()
    var showManualSetup by remember { mutableStateOf(false) }

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
            is UiState.Loading -> LoadingScreen()
            is UiState.Error -> {
                ErrorScreen(
                    message = (detectionState as UiState.Error).message.asString(),
                    onRetryClick = { deviceViewModel.detectChipset() },
                    onManualSetupClick = { showManualSetup = false },
                    onSmartScanClick = { showManualSetup = true },
                    onSubmitDtsClick = { }
                )
            }
            else -> DetectionPromptScreen(onStartClick = { deviceViewModel.detectChipset() })
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GpuEditorMainScreen(
    deviceViewModel: DeviceViewModel,
    gpuFrequencyViewModel: GpuFrequencyViewModel,
    sharedViewModel: SharedGpuViewModel,
    activity: MainActivity
) {
    val isDirty by gpuFrequencyViewModel.isDirty.collectAsState()
    val canUndo by gpuFrequencyViewModel.canUndo.collectAsState()
    val canRedo by gpuFrequencyViewModel.canRedo.collectAsState()
    val history by gpuFrequencyViewModel.history.collectAsState()
    val currentMode by sharedViewModel.viewMode.collectAsState()

    var activeSheet by remember { mutableStateOf(WorkbenchSheetType.NONE) }
    var manualSetupIndex by remember { mutableIntStateOf(-1) }

    // Dialogs
    if (manualSetupIndex != -1) {
        Dialog(onDismissRequest = { manualSetupIndex = -1 }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                ManualChipsetSetupScreen(
                    dtbIndex = manualSetupIndex,
                    autoStartScan = true,
                    onDeepScan = { deviceViewModel.performManualScan(manualSetupIndex) },
                    onSave = { def ->
                        deviceViewModel.saveManualDefinition(def, manualSetupIndex)
                        sharedViewModel.loadData()
                        manualSetupIndex = -1
                        activeSheet = WorkbenchSheetType.NONE
                    },
                    onCancel = { manualSetupIndex = -1 }
                )
            }
        }
    }

    // Sheets
    val detectionState by deviceViewModel.detectionState.collectAsState()
    val selectedChipset by deviceViewModel.selectedChipset.collectAsState()
    val activeDtbId by deviceViewModel.activeDtbId.collectAsState()

    GpuWorkbenchSheets(
        sheetType = activeSheet,
        onDismiss = { activeSheet = WorkbenchSheetType.NONE },
        history = history,
        dtbs = (detectionState as? UiState.Success)?.data ?: emptyList(),
        selectedDtbId = selectedChipset?.id,
        activeDtbId = activeDtbId,
        onChipsetSelect = { dtb ->
            deviceViewModel.selectChipset(dtb)
            activeSheet = WorkbenchSheetType.NONE
        },
        onConfigureManual = { id -> manualSetupIndex = id },
        onAddNewDtb = { manualSetupIndex = 0 }
    )

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
            onFlashClick = { activity.startRepack() }
        )

        // Pure Compose Switching - No Fragments, No ID Mismatches
        Crossfade(targetState = currentMode, label = "ViewModeSwitch") { mode ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (mode) {
                    SharedGpuViewModel.ViewMode.MAIN_EDITOR -> GuiEditorContent(
                        sharedViewModel, 
                        deviceViewModel, 
                        gpuFrequencyViewModel,
                        onOpenCurveEditor = { binId -> activity.openCurveEditor(binId) }
                    )
                    SharedGpuViewModel.ViewMode.TEXT_ADVANCED -> UnifiedDtsEditorScreen(sharedViewModel)
                    SharedGpuViewModel.ViewMode.VISUAL_TREE -> VisualTreeContent(sharedViewModel)
                }
            }
        }
    }
}