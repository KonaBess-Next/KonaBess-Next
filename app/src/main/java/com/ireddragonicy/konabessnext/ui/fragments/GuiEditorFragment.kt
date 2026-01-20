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

/**
 * Wrapper Fragment for the Legacy GUI Editor (GpuTableEditor).
 */
@AndroidEntryPoint
class GuiEditorFragment : Fragment() {

    private val sharedViewModel: SharedGpuViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val dataContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            this.id = View.generateViewId() // Need ID?
        }
        
        // This is where we hook the legacy Code
        // GpuTableEditor needs the Activity context usually for Dialogs etc.
        // It populates the linear layout.
        
        scrollView.addView(dataContent)
        
        // Populate immediately? Or wait for onViewCreated?
        // Ideally wait for Data
        
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val scrollView = view as ScrollView
        val dataContent = scrollView.getChildAt(0) as LinearLayout
        
        // Assuming data is already loaded in SharedViewModel/GpuFrequencyViewModel
        // GpuTableEditor accesses ViewModel directly or statically?
        // GpuFrequencyFragment called: GpuTableEditor.setViewModel(gpuFrequencyViewModel)
        // We should ensure that is still set
        
        val activity = requireActivity()
        if (activity is MainActivity) {
            GpuTableEditor.generateBins(activity, dataContent)
        }
    }
    
    fun refresh() {
        val scrollView = view as? ScrollView ?: return
        val dataContent = scrollView.getChildAt(0) as? LinearLayout ?: return
        val activity = activity as? MainActivity ?: return
        
        dataContent.removeAllViews()
        GpuTableEditor.generateBins(activity, dataContent)
    }
}
