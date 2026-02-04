package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.ireddragonicy.konabessnext.ui.navigation.AppDestinations
import com.ireddragonicy.konabessnext.viewmodel.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    deviceViewModel: DeviceViewModel,
    gpuFrequencyViewModel: GpuFrequencyViewModel,
    sharedViewModel: SharedGpuViewModel,
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    snackbarHostState: SnackbarHostState,
    onStartRepack: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onNavigateToExportHistory: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MainNavigationBar(
                selectedItem = pagerState.currentPage,
                onItemSelected = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(
                            index,
                            animationSpec = androidx.compose.animation.core.spring(
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    deviceViewModel = deviceViewModel,
                    gpuFrequencyViewModel = gpuFrequencyViewModel,
                    sharedViewModel = sharedViewModel,
                    onStartRepack = onStartRepack
                )
                1 -> ImportExportScreenWrapper(
                    deviceViewModel = deviceViewModel,
                    importExportViewModel = importExportViewModel,
                    snackbarHostState = snackbarHostState,
                    onNavigateToExportHistory = onNavigateToExportHistory
                )
                2 -> SettingsScreenWrapper(
                    settingsViewModel = settingsViewModel,
                    onLanguageChange = onLanguageChange
                )
            }
        }
    }
}
