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
                
                ImportExportScreen(
                    isPrepared = isPrepared,
                    onExportHistory = { startActivity(Intent(requireActivity(), ExportHistoryActivity::class.java)) },
                    onImportFromFile = { importFileLauncher.launch("*/*") },
                    onExportToFile = { showExportToFileDialog() },
                    onImportFromClipboard = { showImportFromClipboardDialog() },
                    onExportToClipboard = { showExportToClipboardDialog() },
                    onExportRawDts = { handleExportRawDts() },
                    onBackupBootImage = { showBackupBootImageDialog() }
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
    
    // File Picker for Import
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImportFile(it) }
    }

    private fun showExportToFileDialog() {
        val activity = requireActivity() as MainActivity
        DialogUtil.showEditDialog(activity, R.string.export_to_file, R.string.export_data_msg) { desc ->
            if (desc.isNotEmpty()) {
                activity.runWithStoragePermission {
                    handleExportToFile(desc)
                }
            }
        }
    }

    private fun showImportFromClipboardDialog() {
        val activity = requireActivity() as MainActivity
        DialogUtil.showEditDialog(activity, R.string.import_from_clipboard, R.string.paste_here) { text ->
            if (text.isNotEmpty()) {
                handleImportFromText(text)
            }
        }
    }

    private fun showExportToClipboardDialog() {
        val activity = requireActivity() as MainActivity
        DialogUtil.showEditDialog(activity, R.string.export_to_clipboard, R.string.export_data_msg) { desc ->
            if (desc.isNotEmpty()) {
                handleExportToClipboard(desc)
            }
        }
    }

    private fun handleExportRawDts() {
        val activity = requireActivity() as MainActivity
        activity.runWithStoragePermission {
            val destPath = Environment.getExternalStorageDirectory().absolutePath + "/KonaBess/dts_dump.dts"
            File(destPath).parentFile?.mkdirs()
            importExportViewModel.exportRawDts(destPath)
        }
    }

    private fun showBackupBootImageDialog() {
        val activity = requireActivity() as MainActivity
        DialogUtil.showConfirmDialog(activity, R.string.backup_image, R.string.will_backup_to) {
            activity.runWithStoragePermission {
                deviceViewModel.backupBoot()
            }
        }
    }
    
    private fun handleImportFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                
                if (bytes != null) {
                    val decompressed = GzipUtils.uncompress(bytes)
                    if (decompressed != null) {
                        importExportViewModel.importConfig(decompressed)
                    } else {
                        val text = String(bytes)
                        if (text.trim().startsWith("{")) {
                            importExportViewModel.importConfig(text)
                        } else {
                            withContext(Dispatchers.Main) {
                                DialogUtil.showError(requireActivity(), R.string.failed_decoding)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    DialogUtil.showError(requireActivity(), R.string.error_occur)
                }
            }
        }
    }
    
    private fun handleImportFromText(text: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (text.trim().startsWith("{")) {
                importExportViewModel.importConfig(text)
            } else {
                val bytes = text.toByteArray(Charsets.ISO_8859_1)
                val decompressed = GzipUtils.uncompress(bytes)
                if (decompressed != null) {
                    importExportViewModel.importConfig(decompressed)
                } else {
                    withContext(Dispatchers.Main) {
                        importExportViewModel.importConfig(text)
                    }
                }
            }
        }
    }
    
    private fun handleExportToFile(desc: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val content = importExportViewModel.exportConfig(desc)
            if (content != null) {
                val dir = File(Environment.getExternalStorageDirectory(), "KonaBess/Export")
                if (!dir.exists()) dir.mkdirs()
                
                val filename = "config_${System.currentTimeMillis()}.json.gz"
                val file = File(dir, filename)
                
                try {
                    val fos = FileOutputStream(file)
                    fos.write(content.toByteArray(Charsets.ISO_8859_1))
                    fos.close()
                    
                    importExportViewModel.addToHistory(filename, desc, file.absolutePath, deviceViewModel.currentChipType?.name ?: "Unknown")
                    
                    withContext(Dispatchers.Main) {
                        DialogUtil.showDetailedError(requireActivity(), R.string.success_export_to, file.absolutePath)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        DialogUtil.showError(requireActivity(), R.string.failed_export)
                    }
                }
            }
        }
    }
    
    private fun handleExportToClipboard(desc: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val content = importExportViewModel.exportConfig(desc)
            if (content != null) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("KonaBess Config", content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
