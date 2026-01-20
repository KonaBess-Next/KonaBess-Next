package com.ireddragonicy.konabessnext.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ireddragonicy.konabessnext.core.GpuTableEditor
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier

import com.ireddragonicy.konabessnext.R

/**
 * Wrapper Fragment for the Legacy GUI Editor (GpuTableEditor).
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
                val bins by sharedViewModel.bins.collectAsState()
                // Observe GpuFrequencyViewModel for navigation state (synced with Legacy Editor)
                val selectedBinIndex by gpuFrequencyViewModel.selectedBinIndex.collectAsState()
                val selectedLevelIndex by gpuFrequencyViewModel.selectedLevelIndex.collectAsState()
                val workbenchState by sharedViewModel.workbenchState.collectAsState()
                
                if (selectedBinIndex == -1) {
                    when (workbenchState) {
                        is com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.WorkbenchState.Loading -> {
                             androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator()
                            }
                        }
                        is com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.WorkbenchState.Error -> {
                            androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.material3.Text("Error loading table")
                            }
                        }
                        else -> {
                            com.ireddragonicy.konabessnext.ui.compose.GpuBinList(
                                bins = bins,
                                onBinClick = { index ->
                                    gpuFrequencyViewModel.selectedBinIndex.value = index
                                }
                            )
                        }
                    }
                } else if (selectedLevelIndex == -1) {
                    // Level List Screen (Replaces old GpuTableEditor logic)
                    val bin = bins.getOrNull(selectedBinIndex)
                    if (bin != null) {
                         com.ireddragonicy.konabessnext.ui.compose.GpuLevelList(
                             levels = bin.levels,
                             onLevelClick = { lvlIdx ->
                                 gpuFrequencyViewModel.selectedLevelIndex.value = lvlIdx
                             },
                             onAddLevelTop = {
                                 gpuFrequencyViewModel.addFrequency(selectedBinIndex, true)
                             },
                             onAddLevelBottom = {
                                 gpuFrequencyViewModel.addFrequency(selectedBinIndex, false)
                             },
                             onDuplicateLevel = { lvlIdx ->
                                 gpuFrequencyViewModel.duplicateFrequency(selectedBinIndex, lvlIdx)
                             },
                             onDeleteLevel = { lvlIdx ->
                                 com.ireddragonicy.konabessnext.utils.DialogUtil.showConfirmation(
                                     requireActivity(),
                                     getString(R.string.remove),
                                     "Are you sure you want to remove this frequency?",
                                     { _, _ ->
                                         gpuFrequencyViewModel.removeFrequency(selectedBinIndex, lvlIdx)
                                     }
                                 )
                             },
                             onReorder = { from, to ->
                                 gpuFrequencyViewModel.reorderFrequency(selectedBinIndex, from, to)
                             },
                             onBack = {
                                 gpuFrequencyViewModel.selectedBinIndex.value = -1
                             },
                             onOpenCurveEditor = {
                                 (requireActivity() as MainActivity).openCurveEditor(selectedBinIndex)
                             }
                         )
                    } else {
                        // Error fallback
                         androidx.compose.material3.Text("Bin not found")
                    }
                } else {
                    // Param Editor Screen
                    val bin = bins.getOrNull(selectedBinIndex)
                    val level = bin?.levels?.getOrNull(selectedLevelIndex)
                    
                    if (level != null) {
                        com.ireddragonicy.konabessnext.ui.compose.GpuParamEditor(
                            level = level,
                            onBack = {
                                gpuFrequencyViewModel.selectedLevelIndex.value = -1
                            },
                            onDeleteLevel = {
                                com.ireddragonicy.konabessnext.utils.DialogUtil.showConfirmation(
                                     requireActivity(),
                                     getString(R.string.remove),
                                     "Are you sure you want to remove this frequency?",
                                     { _, _ ->
                                         gpuFrequencyViewModel.removeFrequency(selectedBinIndex, selectedLevelIndex)
                                         gpuFrequencyViewModel.selectedLevelIndex.value = -1 // Go back
                                     }
                                 )
                            },
                            onUpdateParam = { lineIndex, encodedLine, historyMsg ->
                                try {
                                    val binIndex = gpuFrequencyViewModel.selectedBinIndex.value
                                    val levelIndex = gpuFrequencyViewModel.selectedLevelIndex.value
                                    
                                    if (binIndex != -1 && levelIndex != -1) {
                                         gpuFrequencyViewModel.updateLevelLine(
                                            binIndex,
                                            levelIndex,
                                            lineIndex,
                                            encodedLine,
                                            historyMsg
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )
                    } else {
                        androidx.compose.material3.Text("Level not found")
                    }
                }
            }
        }
    }
    
    // No longer needed but kept empty/stub if overrides required?
    // onViewCreated removed as we handle logic in setContent
    
    fun refresh() {
        // Trigger generic refresh
        // No-op for Compose
    }
}
