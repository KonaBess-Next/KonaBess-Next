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
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.collectLatest

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
                     // Persistent State from VM
                     val parsedTree by sharedViewModel.parsedTree.collectAsState()
                     val treeScrollIdx by sharedViewModel.treeScrollIndex.collectAsState()
                     val treeScrollOff by sharedViewModel.treeScrollOffset.collectAsState()
                     
                     // Hoisted Scroll State
                     val listState = rememberLazyListState(
                         initialFirstVisibleItemIndex = treeScrollIdx,
                         initialFirstVisibleItemScrollOffset = treeScrollOff
                     )
                     
                     // Sync Scroll to VM
                     LaunchedEffect(listState) {
                         snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
                             .collectLatest { (index, offset) ->
                                 sharedViewModel.treeScrollIndex.value = index
                                 sharedViewModel.treeScrollOffset.value = offset
                             }
                     }
                     
                     DtsTreeScreen(
                         rootNode = parsedTree,
                         listState = listState,
                         onNodeToggle = { path, expanded -> 
                             sharedViewModel.toggleNodeExpansion(path, expanded)
                         }
                     )
                 }
             }
        }
    }

    companion object {
        fun newInstance() = VisualTreeFragment()
    }
}
