package com.ireddragonicy.konabessnext.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ireddragonicy.konabessnext.ui.compose.HomeScreen
import com.ireddragonicy.konabessnext.ui.compose.CurveEditorScreen
import com.ireddragonicy.konabessnext.ui.compose.ImportExportScreen
import com.ireddragonicy.konabessnext.ui.compose.SettingsScreen
import com.ireddragonicy.konabessnext.ui.compose.LanguageSelectionDialog
import com.ireddragonicy.konabessnext.ui.compose.PaletteSelectionDialog
import com.ireddragonicy.konabessnext.viewmodel.*
import kotlinx.coroutines.launch

object AppDestinations {
    const val MAIN = "main"
    const val HOME = "home"
    const val IMPORT_EXPORT = "import_export"
    const val SETTINGS = "settings"
    const val CURVE_EDITOR = "curve_editor/{binId}"
    const val EXPORT_HISTORY = "export_history"

    fun curveEditor(binId: Int) = "curve_editor/$binId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    deviceViewModel: DeviceViewModel,
    gpuFrequencyViewModel: GpuFrequencyViewModel,
    sharedViewModel: SharedGpuViewModel,
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    snackbarHostState: SnackbarHostState,
    onStartRepack: () -> Unit,
    onLanguageChange: (String) -> Unit, // Added param
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.MAIN,
        modifier = modifier
    ) {
        composable(AppDestinations.MAIN) {
            com.ireddragonicy.konabessnext.ui.compose.MainScreen(
                deviceViewModel = deviceViewModel,
                gpuFrequencyViewModel = gpuFrequencyViewModel,
                sharedViewModel = sharedViewModel,
                settingsViewModel = settingsViewModel,
                importExportViewModel = importExportViewModel,
                snackbarHostState = snackbarHostState,
                onStartRepack = onStartRepack,
                onLanguageChange = onLanguageChange,
                onNavigateToCurveEditor = { binId ->
                    navController.navigate(AppDestinations.curveEditor(binId))
                },
                onNavigateToExportHistory = {
                    navController.navigate(AppDestinations.EXPORT_HISTORY)
                }
            )
        }
        
        // Curve Editor route
        composable(
            route = AppDestinations.CURVE_EDITOR,
            arguments = listOf(navArgument("binId") { type = NavType.IntType })
        ) { backStackEntry ->
            val binId = backStackEntry.arguments?.getInt("binId") ?: 0
            CurveEditorScreen(
                binId = binId,
                sharedViewModel = sharedViewModel,
                onBack = { navController.popBackStack() },
                onRepack = onStartRepack
            )
        }
        
        composable(AppDestinations.EXPORT_HISTORY) {
            com.ireddragonicy.konabessnext.ui.compose.ExportHistoryScreen(
                viewModel = importExportViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
