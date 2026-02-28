package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.TargetPartition
import com.ireddragonicy.konabessnext.viewmodel.*

@Composable
fun GuiEditorContent(
    sharedViewModel: SharedDtsViewModel,
    deviceViewModel: DeviceViewModel,
    gpuFrequencyViewModel: GpuFrequencyViewModel,
    displayViewModel: DisplayViewModel,
    onOpenCurveEditor: (Int) -> Unit
) {
    val isPrepared by deviceViewModel.isPrepared.collectAsState()
    val detectionState by deviceViewModel.detectionState.collectAsState()
    val selectedPartition by deviceViewModel.selectedPartition.collectAsState()

    // EXPERT OPTIMIZATION: Consume the unified state from ViewModel
    val binListState by sharedViewModel.binListState.collectAsState()
    val detectedActiveBinIndex by sharedViewModel.detectedActiveBinIndex.collectAsState()
    val runtimeGpuFrequencies by sharedViewModel.runtimeGpuFrequencies.collectAsState()

    val bins by sharedViewModel.bins.collectAsState()
    val binUiModels by sharedViewModel.binUiModels.collectAsState()

    val navigationStep by gpuFrequencyViewModel.navigationStep.collectAsState()
    val selectedBinIndex by gpuFrequencyViewModel.selectedBinIndex.collectAsState()
    val selectedLevelIndex by gpuFrequencyViewModel.selectedLevelIndex.collectAsState()

    val currentChip = sharedViewModel.currentChip.collectAsState().value

    val dtboNavViewModel: com.ireddragonicy.konabessnext.viewmodel.dtbo.DtboNavViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val displayNavStep by dtboNavViewModel.currentStep.collectAsState()

    // Back Handler Logic
    androidx.activity.compose.BackHandler(
        enabled = (selectedPartition == TargetPartition.DTBO && displayNavStep > 0) || 
                  (selectedPartition != TargetPartition.DTBO && (navigationStep > 0 || selectedBinIndex != -1))
    ) {
        if (selectedPartition == TargetPartition.DTBO) {
            dtboNavViewModel.currentStep.value = 0
        } else if (selectedLevelIndex != -1) {
            gpuFrequencyViewModel.selectedLevelIndex.value = -1
        } else if (selectedBinIndex != -1) {
            gpuFrequencyViewModel.selectedBinIndex.value = -1
        } else if (navigationStep > 0) {
            gpuFrequencyViewModel.navigationStep.value = 0
        }
    }

    var showManualSetup by remember { mutableStateOf(false) }
    var launchWithAutoScan by remember { mutableStateOf(false) }

    if (showManualSetup) {
        Dialog(onDismissRequest = { showManualSetup = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                ManualChipsetSetupScreen(
                    dtbIndex = 0,
                    autoStartScan = launchWithAutoScan,
                    onDeepScan = { deviceViewModel.performManualScan(0) },
                    onSave = { def ->
                        deviceViewModel.saveManualDefinition(def, 0)
                        sharedViewModel.loadData()
                        showManualSetup = false
                    },
                    onCancel = { showManualSetup = false }
                )
            }
        }
    }

    val isDtboMode = selectedPartition == TargetPartition.DTBO
    val showUnsupportedState = !isPrepared && !isDtboMode

    if (isDtboMode) {
        androidx.compose.animation.Crossfade(targetState = displayNavStep, label = "DtboNav") { step ->
            when (step) {
                0 -> {
                    DtboDashboard(
                        onNavigateToTimings = { dtboNavViewModel.currentStep.value = 1 },
                        onNavigateToTouch = { dtboNavViewModel.currentStep.value = 2 },
                        onNavigateToSpeaker = { dtboNavViewModel.currentStep.value = 3 }
                    )
                }
                1 -> {
                    val scopedDisplayVM: DisplayViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(tonalElevation = 3.dp, shadowElevation = 3.dp, modifier = Modifier.zIndex(1f)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { dtboNavViewModel.currentStep.value = 0 }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_back), null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(androidx.compose.ui.res.stringResource(R.string.btn_back))
                                }
                            }
                        }
                        DtboTimingEditor(displayViewModel = scopedDisplayVM)
                    }
                }
                2 -> {
                    val touchViewModel: com.ireddragonicy.konabessnext.viewmodel.dtbo.TouchOverclockViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val touchPanels by touchViewModel.touchPanels.collectAsState()
                    TouchOverclockScreen(
                        touchPanels = touchPanels,
                        onBack = { dtboNavViewModel.currentStep.value = 0 },
                        onSaveFrequency = touchViewModel::updateFrequency
                    )
                }
                3 -> {
                    val speakerViewModel: com.ireddragonicy.konabessnext.viewmodel.dtbo.SpeakerOverclockViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val speakerPanels by speakerViewModel.speakerPanels.collectAsState()
                    SpeakerOverclockScreen(
                        speakerPanels = speakerPanels,
                        onBack = { dtboNavViewModel.currentStep.value = 0 },
                        onSaveReBounds = speakerViewModel::updateReBounds
                    )
                }
            }
        }
    } else if (showUnsupportedState) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                val msg = (detectionState as? UiState.Error)?.message?.asString()
                    ?: "No compatible chipset profile selected."
                ErrorScreen(
                    message = msg,
                    onRetryClick = { deviceViewModel.detectChipset() },
                    onManualSetupClick = { launchWithAutoScan = false; showManualSetup = true },
                    onSmartScanClick = { launchWithAutoScan = true; showManualSetup = true },
                    onSubmitDtsClick = { }
                )
            }
        }
    } else {
        // Main Content Area
        androidx.compose.animation.Crossfade(targetState = navigationStep, label = "DashboardNav") { step ->
            when (step) {
                0 -> {
                    GpuDashboard(
                        sharedViewModel = sharedViewModel,
                        onNavigateToFrequencyTable = { gpuFrequencyViewModel.navigationStep.value = 1 },
                        onNavigateToMemoryTable = { gpuFrequencyViewModel.navigationStep.value = 2 },
                        onNavigateToUfsTable = { gpuFrequencyViewModel.navigationStep.value = 3 },
                        onNavigateToGmuTable = { gpuFrequencyViewModel.navigationStep.value = 4 }
                    )
                }
                1 -> {
                    // Existing Editor Flow
                    if (selectedBinIndex == -1) {
                        GpuBinList(
                            state = binListState,
                            chipDef = currentChip,
                            activeBinIndex = detectedActiveBinIndex,
                            runtimeGpuFrequencies = runtimeGpuFrequencies,
                            onBinClick = { gpuFrequencyViewModel.selectedBinIndex.value = it },
                            onBack = { gpuFrequencyViewModel.navigationStep.value = 0 },
                            onReload = { sharedViewModel.loadData() }
                        )
                    } else if (selectedLevelIndex == -1) {
                        val uiModels = binUiModels[selectedBinIndex]
                        if (uiModels != null) {
                            GpuLevelList(
                                uiModels = uiModels,
                                onLevelClick = { gpuFrequencyViewModel.selectedLevelIndex.value = it },
                                onAddLevelTop = { sharedViewModel.addFrequencyWrapper(selectedBinIndex, true) },
                                onAddLevelBottom = { sharedViewModel.addFrequencyWrapper(selectedBinIndex, false) },
                                onDuplicateLevel = { sharedViewModel.duplicateFrequency(selectedBinIndex, it) },
                                onDeleteLevel = { sharedViewModel.removeFrequency(selectedBinIndex, it) },
                                onReorder = { from, to -> sharedViewModel.reorderFrequency(selectedBinIndex, from, to) },
                                onBack = { gpuFrequencyViewModel.selectedBinIndex.value = -1 },
                                onOpenCurveEditor = { onOpenCurveEditor(selectedBinIndex) }
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    } else {
                        val level = bins.getOrNull(selectedBinIndex)?.levels?.getOrNull(selectedLevelIndex)
                        if (level != null) {
                            val strings = sharedViewModel.getLevelStrings()
                            val values = sharedViewModel.getLevelValues()

                            // Get OPP voltage by matching frequency
                            val opps by sharedViewModel.opps.collectAsState()
                            val oppVoltage = remember(level, opps) {
                                val freq = level.frequency
                                opps.find { it.frequency == freq }?.volt
                                    ?: opps.minByOrNull { kotlin.math.abs(it.frequency - freq) }?.volt
                            }

                            GpuParamEditor(
                                level = level,
                                levelStrings = strings,
                                levelValues = values,
                                ignoreVoltTable = currentChip?.ignoreVoltTable == true,
                                oppVoltage = oppVoltage,
                                levelFrequency = level.frequency,
                                onBack = { gpuFrequencyViewModel.selectedLevelIndex.value = -1 },
                                onDeleteLevel = {
                                    sharedViewModel.removeFrequency(selectedBinIndex, selectedLevelIndex)
                                    gpuFrequencyViewModel.selectedLevelIndex.value = -1
                                },
                                onUpdateParam = { lineIdx, encoded, history ->
                                    sharedViewModel.updateParameter(selectedBinIndex, selectedLevelIndex, lineIdx, encoded, history)
                                },
                                onUpdateOppVoltage = { newVolt ->
                                    sharedViewModel.updateOppVoltage(level.frequency, newVolt)
                                }
                            )
                        }
                    }
                }
                2 -> {
                    // DDR / LLCC Memory Editor
                    val ddrViewModel: com.ireddragonicy.konabessnext.viewmodel.DdrViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val ddrMemoryTables by ddrViewModel.memoryTables.collectAsState()
                    DdrEditorScreen(
                        memoryTables = ddrMemoryTables,
                        onBack = { gpuFrequencyViewModel.navigationStep.value = 0 },
                        onEditFrequency = ddrViewModel::editFrequency,
                        onAddFrequency = ddrViewModel::addFrequency,
                        onDeleteFrequency = ddrViewModel::deleteFrequency
                    )
                }
                3 -> {
                    // UFS Editor
                    val ufsViewModel: com.ireddragonicy.konabessnext.viewmodel.UfsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val ufsTables by ufsViewModel.ufsTables.collectAsState()
                    UfsEditorScreen(
                        ufsTables = ufsTables,
                        onBack = { gpuFrequencyViewModel.navigationStep.value = 0 },
                        onEditClockFrequencies = ufsViewModel::editClockFrequencies
                    )
                }
                4 -> {
                    val gmuViewModel: com.ireddragonicy.konabessnext.viewmodel.GmuViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val gmuTables by gmuViewModel.gmuTables.collectAsState()
                    GmuEditorScreen(
                        gmuTables = gmuTables,
                        onBack = { gpuFrequencyViewModel.navigationStep.value = 0 },
                        onEditPair = gmuViewModel::editPair,
                        onAddPair = gmuViewModel::addPair,
                        onDeletePair = gmuViewModel::deletePair
                    )
                }
            }
        }
    }
}