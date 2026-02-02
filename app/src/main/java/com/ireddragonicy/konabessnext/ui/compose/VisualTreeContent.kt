package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.runtime.*
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VisualTreeContent(sharedViewModel: SharedGpuViewModel) {
    val dtsContent by sharedViewModel.dtsContent.collectAsState()
    var rootNode by remember { mutableStateOf<DtsNode?>(null) }

    LaunchedEffect(dtsContent) {
        if (dtsContent.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                try {
                    val root = DtsTreeHelper.parse(dtsContent)
                    withContext(Dispatchers.Main) { rootNode = root }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    DtsTreeScreen(rootNode = rootNode)
}
