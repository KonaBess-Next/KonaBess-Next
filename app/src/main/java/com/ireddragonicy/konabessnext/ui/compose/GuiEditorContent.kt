package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ireddragonicy.konabessnext.viewmodel.*

@Composable
fun GuiEditorContent(
    sharedViewModel: SharedGpuViewModel,
    deviceViewModel: DeviceViewModel,
    gpuFrequencyViewModel: GpuFrequencyViewModel,
    onOpenCurveEditor: (Int) -> Unit
) {
    val isPrepared by deviceViewModel.isPrepared.collectAsState()
    val detectionState by deviceViewModel.detectionState.collectAsState()
    
    // EXPERT OPTIMIZATION: Consume the unified state from ViewModel
    val binListState by sharedViewModel.binListState.collectAsState()
    
    val bins by sharedViewModel.bins.collectAsState()
    val binUiModels by sharedViewModel.binUiModels.collectAsState()
    val selectedBinIndex by gpuFrequencyViewModel.selectedBinIndex.collectAsState()
    val selectedLevelIndex by gpuFrequencyViewModel.selectedLevelIndex.collectAsState()
    val workbenchState by sharedViewModel.workbenchState.collectAsState()
    
    val currentChip = sharedViewModel.currentChip.collectAsState().value

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

    if (!isPrepared) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                val msg = (detectionState as? UiState.Error)?.message?.asString() ?: "Unsupported Chipset"
                ErrorScreen(
                    message = msg,
                    onRetryClick = { deviceViewModel.detectChipset() },
                    onManualSetupClick = { launchWithAutoScan = false; showManualSetup = true },
                    onSmartScanClick = { launchWithAutoScan = true; showManualSetup = true },
                    onSubmitDtsClick = { }
                )
            }
        }
    } else if (selectedBinIndex == -1) {
        GpuBinList(
            state = binListState,
            chipDef = currentChip,
            onBinClick = { gpuFrequencyViewModel.selectedBinIndex.value = it },
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
            
            GpuParamEditor(
                level = level,
                levelStrings = strings,
                levelValues = values,
                ignoreVoltTable = currentChip?.ignoreVoltTable == true,
                onBack = { gpuFrequencyViewModel.selectedLevelIndex.value = -1 },
                onDeleteLevel = {
                    sharedViewModel.removeFrequency(selectedBinIndex, selectedLevelIndex)
                    gpuFrequencyViewModel.selectedLevelIndex.value = -1
                },
                onUpdateParam = { lineIdx, encoded, history ->
                    sharedViewModel.updateParameter(selectedBinIndex, selectedLevelIndex, lineIdx, encoded, history)
                }
            )
        }
    }
}
