package com.ireddragonicy.konabessnext.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.compose.GpuWorkbenchSheets
import com.ireddragonicy.konabessnext.ui.compose.ManualChipsetSetupScreen
import com.ireddragonicy.konabessnext.ui.compose.WorkbenchSheetType
import com.ireddragonicy.konabessnext.viewmodel.DeviceViewModel
import com.ireddragonicy.konabessnext.viewmodel.GpuFrequencyViewModel
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import com.ireddragonicy.konabessnext.viewmodel.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GpuFrequencyFragment : Fragment() {
    private var contentContainer: LinearLayout? = null

    private val deviceViewModel: DeviceViewModel by activityViewModels()
    private val gpuFrequencyViewModel: GpuFrequencyViewModel by activityViewModels()
    private val sharedViewModel: SharedGpuViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        contentContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        return contentContainer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as MainActivity

        lifecycleScope.launch {
            launch {
                deviceViewModel.isFilesExtracted.collect { isExtracted ->
                    if (isExtracted) {
                        sharedViewModel.loadData()
                        showGpuEditor(activity)
                    } else {
                        showInitialSetup(activity)
                    }
                }
            }


        }
    }

    private fun showInitialSetup(activity: MainActivity) {
        contentContainer?.removeAllViews()
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext())
        composeView.setContent {
            com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
                val detectionState by deviceViewModel.detectionState.collectAsState()
                var showManualSetup by remember { mutableStateOf(false) }
                
                if (showManualSetup) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showManualSetup = false }) {
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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(contentAlignment = Alignment.Center) {
                        when (detectionState) {
                            is UiState.Loading -> com.ireddragonicy.konabessnext.ui.compose.LoadingScreen()
                            is UiState.Error -> {
                                com.ireddragonicy.konabessnext.ui.compose.ErrorScreen(
                                    message = (detectionState as UiState.Error).message.asString(),
                                    onRetryClick = { deviceViewModel.detectChipset() },
                                    onManualSetupClick = { showManualSetup = false },
                                    onSmartScanClick = { showManualSetup = true },
                                    onSubmitDtsClick = { }
                                )
                            }
                            else -> com.ireddragonicy.konabessnext.ui.compose.DetectionPromptScreen(onStartClick = { deviceViewModel.detectChipset() })
                        }
                    }
                }
            }
        }
        contentContainer?.addView(composeView, LinearLayout.LayoutParams(-1, -1))
    }



    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    private fun showGpuEditor(activity: MainActivity) {
        contentContainer?.removeAllViews()
        val workbenchRoot = LinearLayout(requireContext()).apply { 
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -1) 
        }
        contentContainer?.addView(workbenchRoot)

        val toolbarCompose = androidx.compose.ui.platform.ComposeView(requireContext())
        toolbarCompose.setContent {
            com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
                val isDirty by gpuFrequencyViewModel.isDirty.collectAsState()
                val canUndo by gpuFrequencyViewModel.canUndo.collectAsState()
                val canRedo by gpuFrequencyViewModel.canRedo.collectAsState()
                val history by gpuFrequencyViewModel.history.collectAsState()
                val currentMode by sharedViewModel.viewMode.collectAsState()
                
                var activeSheet by remember { mutableStateOf(WorkbenchSheetType.NONE) }
                var manualSetupIndex by remember { mutableIntStateOf(-1) }

                // Manual Setup Dialog
                if (manualSetupIndex != -1) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { manualSetupIndex = -1 }) {
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

                // Workbench Sheets (History/Chipset)
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

                com.ireddragonicy.konabessnext.ui.compose.GpuEditorToolbar(
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
                    onViewModeChanged = { mode -> updateViewMode(mode) },
                    onChipsetClick = { activeSheet = WorkbenchSheetType.CHIPSET }, 
                    onFlashClick = { activity.startRepack() }
                )
            }
        }
        workbenchRoot.addView(toolbarCompose)

        val fragmentFrame = android.widget.FrameLayout(requireContext()).apply { 
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1.0f) 
        }
        workbenchRoot.addView(fragmentFrame)

        val fm = childFragmentManager
        val transaction = fm.beginTransaction()
        
        val gui = fm.findFragmentByTag("GUI") ?: GuiEditorFragment()
        val text = fm.findFragmentByTag("TEXT") ?: UnifiedRawDtsFragment.newInstance()
        val tree = fm.findFragmentByTag("TREE") ?: VisualTreeFragment.newInstance()
        
        if (!gui.isAdded) transaction.add(fragmentFrame.id, gui, "GUI")
        if (!text.isAdded) transaction.add(fragmentFrame.id, text, "TEXT")
        if (!tree.isAdded) transaction.add(fragmentFrame.id, tree, "TREE")
        
        transaction.show(gui).hide(text).hide(tree).commitNow()
        sharedViewModel.switchViewMode(SharedGpuViewModel.ViewMode.MAIN_EDITOR)
    }

    private fun updateViewMode(mode: SharedGpuViewModel.ViewMode) {
        val fm = childFragmentManager
        val transaction = fm.beginTransaction()
        val gui = fm.findFragmentByTag("GUI")
        val text = fm.findFragmentByTag("TEXT")
        val tree = fm.findFragmentByTag("TREE")
        
        gui?.let { transaction.hide(it) }
        text?.let { transaction.hide(it) }
        tree?.let { transaction.hide(it) }
        
        when (mode) {
            SharedGpuViewModel.ViewMode.MAIN_EDITOR -> gui?.let { transaction.show(it) }
            SharedGpuViewModel.ViewMode.TEXT_ADVANCED -> text?.let { transaction.show(it) }
            SharedGpuViewModel.ViewMode.VISUAL_TREE -> tree?.let { transaction.show(it) }
        }
        transaction.commit()
        sharedViewModel.switchViewMode(mode)
    }
}