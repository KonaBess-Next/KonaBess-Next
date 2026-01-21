package com.ireddragonicy.konabessnext.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.ExportHistoryActivity
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.compose.ImportExportScreen
import com.ireddragonicy.konabessnext.utils.DialogUtil
import com.ireddragonicy.konabessnext.utils.GzipUtils
import com.ireddragonicy.konabessnext.viewmodel.DeviceViewModel
import com.ireddragonicy.konabessnext.viewmodel.ImportExportViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class ImportExportFragment : Fragment() {

    private val deviceViewModel: DeviceViewModel by activityViewModels()
    private val importExportViewModel: ImportExportViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val isPrepared by deviceViewModel.isPrepared.collectAsState(initial = false)
                val lastExportedResult by importExportViewModel.lastExportedContent.collectAsState(initial = null)
                
                ImportExportScreen(
                    isPrepared = isPrepared,
                    onExportHistory = { startActivity(Intent(requireActivity(), ExportHistoryActivity::class.java)) },
                    onImportFromFile = { importConfigLauncher.launch(arrayOf("*/*")) },
                    onExportToFile = { desc -> 
                        pendingExportDesc = desc
                        exportConfigLauncher.launch("config_${System.currentTimeMillis()}.txt")
                    },
                    onImportFromClipboard = { text -> importExportViewModel.importConfig(text) },
                    onExportToClipboard = { desc -> handleExportToClipboard(desc) },
                    onExportRawDts = { exportRawDtsLauncher.launch("dts_dump_${System.currentTimeMillis()}.txt") },
                    onBackupBootImage = { backupBootLauncher.launch("boot_backup_${System.currentTimeMillis()}.img") },
                    lastExportedResult = lastExportedResult
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Handle ViewModel events
        viewLifecycleOwner.lifecycleScope.launch {
            importExportViewModel.messageEvent.collectLatest { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            importExportViewModel.errorEvent.collectLatest { msg ->
                DialogUtil.showError(requireActivity(), msg)
            }
        }
    }
    
    // SAF Launchers
    private val importConfigLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importExportViewModel.importConfigFromUri(requireContext(), it) }
    }

    private var pendingExportDesc: String = ""
    private val exportConfigLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri?.let { importExportViewModel.exportConfigToUri(requireContext(), it, pendingExportDesc) }
    }

    private val exportRawDtsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri?.let { importExportViewModel.exportRawDtsToUri(requireContext(), it) }
    }

    private val backupBootLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri?.let { importExportViewModel.backupBootToUri(requireContext(), it) }
    }

    private fun handleExportToClipboard(desc: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val content = importExportViewModel.exportConfig(desc)
            if (content != null) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("KonaBess Config", content)
                clipboard.setPrimaryClip(clip)
                
                // Track in history
                importExportViewModel.addToHistory("Clipboard", desc, "clipboard://text", com.ireddragonicy.konabessnext.core.ChipInfo.current?.id ?: "Unknown")
                
                // Notify UI to show result
                importExportViewModel.notifyExportResult(content)
                
                Toast.makeText(requireContext(), R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
