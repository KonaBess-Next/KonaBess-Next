package com.ireddragonicy.konabessnext.ui.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ireddragonicy.konabessnext.viewmodel.DeviceViewModel
import com.ireddragonicy.konabessnext.viewmodel.DtsFileInfo
import com.ireddragonicy.konabessnext.viewmodel.ImportExportViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImportExportScreenWrapper(
    deviceViewModel: DeviceViewModel,
    importExportViewModel: ImportExportViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToExportHistory: () -> Unit
) {
    val isPrepared by deviceViewModel.isPrepared.collectAsState()
    val context = LocalContext.current
    val importPreview by importExportViewModel.importPreview.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        importExportViewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(Unit) {
        importExportViewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
        }
    }

    // Import Config
    val importConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importExportViewModel.importConfigFromUri(context, it) }
    }

    // Export Config
    val exportConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { importExportViewModel.exportConfigToUri(context, it, "Exported Config") }
    }

    // Export Raw DTS — single file (legacy, kept for reference but now uses new flow)
    val exportRawDtsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { importExportViewModel.exportRawDtsToUri(context, it) }
    }

    // ── New: Export single DTS file via CreateDocument ──
    var pendingDtsExport by remember { mutableStateOf<DtsFileInfo?>(null) }
    
    val exportSingleDtsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { selectedUri ->
            pendingDtsExport?.let { dtsInfo ->
                importExportViewModel.exportDtsFileToUri(context, selectedUri, dtsInfo)
                pendingDtsExport = null
            }
        }
    }

    // ── New: Export all DTS files to a folder ──
    val exportAllDtsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { importExportViewModel.exportAllDtsFilesToFolder(context, it) }
    }

    // ── New: Export all DTS files as a single ZIP ──
    val exportAllDtsZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { importExportViewModel.exportAllDtsAsZipToUri(context, it) }
    }

    // Backup Boot Image
    val backupBootLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { importExportViewModel.backupBootToUri(context, it) }
    }

    // Batch DTB to DTS
    val batchDtbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            importExportViewModel.batchConvertDtbToDts(context, uris)
        }
    }

    // Batch Progress Dialog
    val batchProgress by importExportViewModel.batchProgress.collectAsState()
    
    // State for Export Description
    var pendingExportDesc by remember { mutableStateOf("Exported Config") }
    
    val exportConfigLauncherWithDesc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { importExportViewModel.exportConfigToUri(context, it, pendingExportDesc) }
    }

    // ── Retrieve DTS files list & device info for the export sheet ──
    val dtsFiles = remember(isPrepared) {
        if (isPrepared) importExportViewModel.getAllDtsFiles() else emptyList()
    }
    val deviceModel = remember { deviceViewModel.getDeviceModel() }
    val deviceBrand = remember { deviceViewModel.getDeviceBrand() }

    ImportExportScreen(
        isPrepared = isPrepared,
        onExportHistory = onNavigateToExportHistory, 
        onImportFromFile = { importConfigLauncher.launch(arrayOf("*/*")) },
        onExportToFile = { desc: String -> 
            pendingExportDesc = desc
            exportConfigLauncherWithDesc.launch("konabess_config.txt") 
        },
        onImportFromClipboard = { content: String -> importExportViewModel.importConfig(content) }, 
        onExportToClipboard = { desc: String -> 
             val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
             val content = importExportViewModel.exportConfig(desc)
             if (content != null) {
                 val clip = android.content.ClipData.newPlainText("KonaBess Config", content)
                 clipboard.setPrimaryClip(clip)
                 importExportViewModel.addToHistory("Clipboard", desc, "clipboard://text")
                 importExportViewModel.notifyExportResult(content)
                 scope.launch {
                    snackbarHostState.showSnackbar("Copied to clipboard")
                 }
             }
        },
        onExportRawDts = { /* Now handled by the sheet mechanism inside ImportExportScreen */ },
        onExportSingleDts = { dtsInfo: DtsFileInfo ->
            pendingDtsExport = dtsInfo
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = "konabess_dts_dtb${dtsInfo.index}_$timestamp.dts"
            exportSingleDtsLauncher.launch(safeName)
        },
        onExportAllDts = {
            exportAllDtsFolderLauncher.launch(null)
        },
        onExportAllDtsAsZip = {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            exportAllDtsZipLauncher.launch("konabess_dts_$timestamp.zip")
        },
        dtsFiles = dtsFiles,
        deviceModel = deviceModel,
        deviceBrand = deviceBrand,
        onBackupBootImage = { backupBootLauncher.launch("boot_modified.img") },
        onBatchDtbToDts = { batchDtbLauncher.launch(arrayOf("*/*")) },
        onDismissResult = { importExportViewModel.clearExportResult() },
        onOpenFile = { path: String -> 
            val file = java.io.File(path)
            if (file.exists()) {
                try {
                    val builder = android.os.StrictMode.VmPolicy.Builder()
                    android.os.StrictMode.setVmPolicy(builder.build())
                    
                    val uri = android.net.Uri.fromFile(file)
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "text/plain")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
                } catch (e: Exception) {
                    // Replace Toast
                }
            } else {
                 // Replace Toast
            }
        },
        lastExportedResult = importExportViewModel.lastExportedContent.collectAsState(initial = null).value,
        importPreview = importPreview,
        onConfirmImport = { importExportViewModel.confirmImport() },
        onCancelImport = { importExportViewModel.cancelImport() }
    )
}
