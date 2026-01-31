package com.ireddragonicy.konabessnext.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.compose.ManualChipsetSetupScreen
import com.ireddragonicy.konabessnext.viewmodel.DeviceViewModel
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import com.ireddragonicy.konabessnext.viewmodel.UiState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GuiEditorFragment : Fragment() {

    private val sharedViewModel: SharedGpuViewModel by activityViewModels()
    private val deviceViewModel: DeviceViewModel by activityViewModels()
    private val gpuFrequencyViewModel: com.ireddragonicy.konabessnext.viewmodel.GpuFrequencyViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
                    val isPrepared by deviceViewModel.isPrepared.collectAsState()
                    val detectionState by deviceViewModel.detectionState.collectAsState()
                    val bins by sharedViewModel.bins.collectAsState()
                    val binUiModels by sharedViewModel.binUiModels.collectAsState()
                    val selectedBinIndex by gpuFrequencyViewModel.selectedBinIndex.collectAsState()
                    val selectedLevelIndex by gpuFrequencyViewModel.selectedLevelIndex.collectAsState()
                    val workbenchState by sharedViewModel.workbenchState.collectAsState()

                    var showManualSetup by remember { mutableStateOf(false) }
                    var launchWithAutoScan by remember { mutableStateOf(false) }

                    if (showManualSetup) {
                        androidx.compose.ui.window.Dialog(onDismissRequest = { showManualSetup = false }) {
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
                                com.ireddragonicy.konabessnext.ui.compose.ErrorScreen(
                                    message = msg,
                                    onRetryClick = { deviceViewModel.detectChipset() },
                                    onManualSetupClick = { launchWithAutoScan = false; showManualSetup = true },
                                    onSmartScanClick = { launchWithAutoScan = true; showManualSetup = true },
                                    onSubmitDtsClick = { }
                                )
                            }
                        }
                    } else if (selectedBinIndex == -1) {
                        com.ireddragonicy.konabessnext.ui.compose.GpuBinList(
                            bins = bins,
                            isLoading = workbenchState is SharedGpuViewModel.WorkbenchState.Loading,
                            onBinClick = { gpuFrequencyViewModel.selectedBinIndex.value = it },
                            onReload = { sharedViewModel.loadData() }
                        )
                    } else if (selectedLevelIndex == -1) {
                        val uiModels = binUiModels[selectedBinIndex]
                        if (uiModels != null) {
                            com.ireddragonicy.konabessnext.ui.compose.GpuLevelList(
                                uiModels = uiModels,
                                onLevelClick = { gpuFrequencyViewModel.selectedLevelIndex.value = it },
                                onAddLevelTop = { sharedViewModel.addFrequencyWrapper(selectedBinIndex, true) },
                                onAddLevelBottom = { sharedViewModel.addFrequencyWrapper(selectedBinIndex, false) },
                                onDuplicateLevel = { sharedViewModel.duplicateFrequency(selectedBinIndex, it) },
                                onDeleteLevel = { sharedViewModel.removeFrequency(selectedBinIndex, it) },
                                onReorder = { from, to -> sharedViewModel.reorderFrequency(selectedBinIndex, from, to) },
                                onBack = { gpuFrequencyViewModel.selectedBinIndex.value = -1 },
                                onOpenCurveEditor = { (requireActivity() as MainActivity).openCurveEditor(selectedBinIndex) }
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    } else {
                        val level = bins.getOrNull(selectedBinIndex)?.levels?.getOrNull(selectedLevelIndex)
                        if (level != null) {
                            com.ireddragonicy.konabessnext.ui.compose.GpuParamEditor(
                                level = level,
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
            }
        }
    }
}