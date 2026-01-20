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
                                    val activity = requireActivity()
                                    // Sync data to Legacy Editor static field
                                    GpuTableEditor.bins = java.util.ArrayList(bins)
                                    
                                    val editor = GpuTableEditor()
                                    GpuTableEditor.currentActivity = activity
                                    editor.onOpenLevels(index)
                                }
                            )
                        }
                    }
                } else {
                    // Legacy Editor Container
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                // Store reference so GpuTableEditor can find it?
                                // GpuTableEditor uses 'currentPage'. We need to set it.
                                GpuTableEditor.currentPage = this
                                
                                // Initial population if needed (e.g. if we navigated back or deep linked)
                                val activity = context as? com.ireddragonicy.konabessnext.ui.MainActivity
                                if (activity != null) {
                                     val editor = GpuTableEditor() // or access current?
                                     // We need to trigger generation based on current indices
                                     // But GpuTableEditor logic usually does: "onOpenLevels -> generateLevels -> adds views to currentPage"
                                     // Since we just set 'currentPage', we might need to re-trigger generation?
                                     // Actually, GpuTableEditor.refreshCurrentView() does exactly this.
                                     
                                     // Ensure activity ref is set
                                     GpuTableEditor.currentActivity = activity
                                     GpuTableEditor.refreshCurrentView()
                                }
                            }
                        },
                        update = { view ->
                             // Ensure currentPage is always correct when recomposing/updating
                             GpuTableEditor.currentPage = view as LinearLayout
                             // Sync data to ensure legacy view can refresh
                             GpuTableEditor.bins = java.util.ArrayList(bins)
                             GpuTableEditor.currentActivity = requireActivity() // Ensure context is fresh
                             
                             // Force refresh if view is empty or just to be safe?
                             // refreshCurrentView checks internal indices.
                             GpuTableEditor.refreshCurrentView()
                        },
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    )
                }
            }
        }
    }
    
    // No longer needed but kept empty/stub if overrides required?
    // onViewCreated removed as we handle logic in setContent
    
    fun refresh() {
        // Trigger generic refresh
        GpuTableEditor.refreshCurrentView()
    }
}
