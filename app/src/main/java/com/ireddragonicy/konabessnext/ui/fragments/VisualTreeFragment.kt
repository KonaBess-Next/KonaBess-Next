package com.ireddragonicy.konabessnext.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.ui.compose.DtsTreeScreen
import com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Visual Tree Editor fragment - displays DTS as an interactive tree structure.
 * Fully migrated to Jetpack Compose.
 */
@AndroidEntryPoint
class VisualTreeFragment : Fragment() {

    private val sharedViewModel: SharedGpuViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
             setContent {
                 KonaBessTheme {
                     val dtsContent by sharedViewModel.dtsContent.collectAsState()
                     var rootNode by remember { mutableStateOf<DtsNode?>(null) }
                     
                     LaunchedEffect(dtsContent) {
                         if (dtsContent.isNotEmpty()) {
                             withContext(Dispatchers.Default) {
                                 try {
                                      // TODO: DtsTreeHelper.parse might need improvement for massive files
                                      val root = DtsTreeHelper.parse(dtsContent)
                                      withContext(Dispatchers.Main) { rootNode = root }
                                 } catch(e: Exception) {
                                     // Handle error state if needed
                                 }
                             }
                         }
                     }
                     
                     DtsTreeScreen(rootNode = rootNode)
                 }
             }
        }
    }

    companion object {
        fun newInstance() = VisualTreeFragment()
    }
}
