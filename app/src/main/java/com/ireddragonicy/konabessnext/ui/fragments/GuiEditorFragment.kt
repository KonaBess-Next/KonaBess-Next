package com.ireddragonicy.konabessnext.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Wrapper Fragment for the GUI Editor.
 */
@AndroidEntryPoint
class GuiEditorFragment : Fragment() {

    private val sharedViewModel: SharedGpuViewModel by activityViewModels()
    private val gpuFrequencyViewModel: com.ireddragonicy.konabessnext.viewmodel.GpuFrequencyViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setContent {
                com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
                    val bins by sharedViewModel.bins.collectAsState()
                    // KEY: Consume pre-calculated UI Models
                    val binUiModels by sharedViewModel.binUiModels.collectAsState()
                    
                    val selectedBinIndex by gpuFrequencyViewModel.selectedBinIndex.collectAsState()
                    val selectedLevelIndex by gpuFrequencyViewModel.selectedLevelIndex.collectAsState()
                    val workbenchState by sharedViewModel.workbenchState.collectAsState()
                    
                    if (selectedBinIndex == -1) {
                        // Bin List (already fast)
                        when (workbenchState) {
                            is com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.WorkbenchState.Loading -> {
                                 Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            is com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.WorkbenchState.Error -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Error loading table")
                                }
                            }
                            else -> {
                                com.ireddragonicy.konabessnext.ui.compose.GpuBinList(
                                    bins = bins,
                                    onBinClick = { index ->
                                        com.ireddragonicy.konabessnext.utils.DebugTimer.start("Open Bin $index")
                                        gpuFrequencyViewModel.selectedBinIndex.value = index
                                    }
                                )
                            }
                        }
                    } else if (selectedLevelIndex == -1) {
                        // Level List (Optimized)
                        // Fetch the pre-calc list immediately. No map{} operations here.
                        val uiModels = binUiModels[selectedBinIndex]
                        
                        if (uiModels != null) { // If list exists (even if empty), show editor
                                    // Memoize callbacks to prevent unstable lambda recreation on parent recomposition
                                    val onLevelClick = androidx.compose.runtime.remember {
                                        { lvlIdx: Int -> gpuFrequencyViewModel.selectedLevelIndex.value = lvlIdx }
                                    }
                                    
                                    val onAddLevelTop = androidx.compose.runtime.remember(selectedBinIndex) {
                                        { sharedViewModel.addFrequencyWrapper(selectedBinIndex, true) }
                                    }

                                    val onAddLevelBottom = androidx.compose.runtime.remember(selectedBinIndex) {
                                        { sharedViewModel.addFrequencyWrapper(selectedBinIndex, false) }
                                    }

                                    val onDuplicateLevel = androidx.compose.runtime.remember(selectedBinIndex) {
                                        { lvlIdx: Int -> sharedViewModel.duplicateFrequency(selectedBinIndex, lvlIdx) }
                                    }

                                    val onDeleteLevel = androidx.compose.runtime.remember(selectedBinIndex) {
                                        { lvlIdx: Int ->
                                            com.ireddragonicy.konabessnext.utils.DialogUtil.showConfirmation(
                                                requireActivity(),
                                                getString(R.string.remove),
                                                getString(R.string.remove_frequency_message, "this"),
                                                { _, _ ->
                                                    sharedViewModel.removeFrequency(selectedBinIndex, lvlIdx)
                                                }
                                            )
                                        }
                                    }

                                    val onReorder = androidx.compose.runtime.remember(selectedBinIndex) {
                                        { from: Int, to: Int -> sharedViewModel.reorderFrequency(selectedBinIndex, from, to) }
                                    }
                                    
                                    val onBack = androidx.compose.runtime.remember {
                                        { gpuFrequencyViewModel.selectedBinIndex.value = -1 }
                                    }
                                    
                                    val onOpenCurveEditor = androidx.compose.runtime.remember(selectedBinIndex) {
                                        { (requireActivity() as MainActivity).openCurveEditor(selectedBinIndex) }
                                    }

                             com.ireddragonicy.konabessnext.ui.compose.GpuLevelList(
                                 uiModels = uiModels,
                                 onLevelClick = onLevelClick,
                                 onAddLevelTop = onAddLevelTop,
                                 onAddLevelBottom = onAddLevelBottom,
                                 onDuplicateLevel = onDuplicateLevel,
                                 onDeleteLevel = onDeleteLevel,
                                 onReorder = onReorder,
                                 onBack = onBack,
                                 onOpenCurveEditor = onOpenCurveEditor
                             )
                        } else {
                             // This handles the tiny gap where bins are ready but precalculation isn't finished
                             Box(
                                 modifier = Modifier.fillMaxSize(),
                                 contentAlignment = Alignment.Center
                             ) {
                                 CircularProgressIndicator() // Show spinner while background thread finishes
                             }
                        }
                    } else {
                        // Param Editor (already fast)
                        val bin = bins.getOrNull(selectedBinIndex)
                        val level = bin?.levels?.getOrNull(selectedLevelIndex)
                        
                        if (level != null) {
                            com.ireddragonicy.konabessnext.ui.compose.GpuParamEditor(
                                level = level,
                                onBack = androidx.compose.runtime.remember {
                                    { gpuFrequencyViewModel.selectedLevelIndex.value = -1 }
                                },
                                onDeleteLevel = androidx.compose.runtime.remember(selectedBinIndex, selectedLevelIndex) {
                                    {
                                        com.ireddragonicy.konabessnext.utils.DialogUtil.showConfirmation(
                                             requireActivity(),
                                             getString(R.string.remove),
                                             getString(R.string.remove_frequency_message, "this"),
                                             { _, _ ->
                                                 sharedViewModel.removeFrequency(selectedBinIndex, selectedLevelIndex)
                                                 gpuFrequencyViewModel.selectedLevelIndex.value = -1 
                                             }
                                         )
                                    }
                                },
                                onUpdateParam = androidx.compose.runtime.remember(selectedBinIndex, selectedLevelIndex) {
                                    { lineIndex, encodedLine, historyMsg ->
                                        try {
                                            sharedViewModel.updateParameter(
                                                selectedBinIndex,
                                                selectedLevelIndex,
                                                lineIndex,
                                                encodedLine,
                                                historyMsg
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Level not found")
                            }
                        }
                    }
                }
            }
        }
    }
}