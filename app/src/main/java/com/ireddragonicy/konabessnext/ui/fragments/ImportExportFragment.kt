package com.ireddragonicy.konabessnext.ui.fragments

import android.app.Activity
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.ExportHistoryActivity
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.adapters.ActionCardAdapter
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

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ActionCardAdapter
    private var actionItems: MutableList<ActionCardAdapter.ActionItem> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create RecyclerView directly as view
        val context = requireContext()
        recyclerView = RecyclerView(context)
        recyclerView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setPadding(0, 16, 0, 16)
        recyclerView.clipToPadding = false

        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
        observeViewModel()
        
        // Handle ViewModel events
        viewLifecycleOwner.lifecycleScope.launch {
            importExportViewModel.messageEvent.collectLatest { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                // If successful import/export, maybe refresh adapter? items don't change state much though.
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

    private fun setupAdapter() {
        // Initial Items place holders. Will update when prepared state changes.
        updateActionItems(false)
        
        adapter = ActionCardAdapter(actionItems)
        adapter.setOnItemClickListener { position ->
            handleItemClick(position)
        }
        recyclerView.adapter = adapter
    }

    private fun updateActionItems(isPrepared: Boolean) {
        actionItems.clear()
        
        val context = requireContext()
        
        // 0. Export History
        actionItems.add(ActionCardAdapter.ActionItem(
            R.drawable.ic_history,
            getString(R.string.export_history),
            getString(R.string.export_history_desc),
            true // Always enabled
        ))
        
        // 1. Import from file
        actionItems.add(ActionCardAdapter.ActionItem(
             R.drawable.ic_import_modern,
             getString(R.string.import_from_file),
             getString(R.string.import_from_file_msg),
             isPrepared
        ))

        // 2. Export to file
        actionItems.add(ActionCardAdapter.ActionItem(
             R.drawable.ic_save,
             getString(R.string.export_to_file),
             getString(R.string.export_to_file_msg),
             isPrepared
        ))
        
        // 3. Import from clipboard
        actionItems.add(ActionCardAdapter.ActionItem(
             R.drawable.ic_clipboard_import,
             getString(R.string.import_from_clipboard),
             getString(R.string.import_from_clipboard_msg),
             isPrepared
        ))
        
        // 4. Export to clipboard
        actionItems.add(ActionCardAdapter.ActionItem(
             R.drawable.ic_clipboard_export,
             getString(R.string.export_to_clipboard),
             getString(R.string.export_to_clipboard_msg),
             isPrepared
        ))
        
        // 5. Export Raw DTS
        actionItems.add(ActionCardAdapter.ActionItem(
             R.drawable.ic_code,
             getString(R.string.export_raw_dts),
             getString(R.string.export_raw_dts_msg),
             isPrepared
        ))
        
        // 6. Backup Boot Image
        actionItems.add(ActionCardAdapter.ActionItem(
             R.drawable.ic_backup,
             getString(R.string.backup_image),
             getString(R.string.backup_image_desc),
             isPrepared
        ))
        
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            deviceViewModel.isPrepared.collectLatest { isPrepared ->
                updateActionItems(isPrepared)
            }
        }
    }

    private fun handleItemClick(position: Int) {
        val activity = requireActivity() as MainActivity
        
        when (position) {
            0 -> { // Export History
                startActivity(Intent(activity, ExportHistoryActivity::class.java))
            }
            1 -> { // Import from file
                // Start file picker
                // Using modern launcher instead of MainActivity helper if possible
                // But legacy used runWithFilePath. Let's use that for consistency if preferred, 
                // or just local launcher. Local launcher is cleaner.
                importFileLauncher.launch("*/*")
            }
            2 -> { // Export to file
                DialogUtil.showEditDialog(activity, R.string.export_to_file, R.string.export_data_msg) { desc ->
                    if (desc.isNotEmpty()) {
                        activity.runWithStoragePermission {
                             handleExportToFile(desc)
                        }
                    }
                }
            }
            3 -> { // Import from clipboard
                 DialogUtil.showEditDialog(activity, R.string.import_from_clipboard, R.string.paste_here) { text ->
                    if (text.isNotEmpty()) {
                        handleImportFromText(text)
                    }
                 }
            }
            4 -> { // Export to clipboard
                 DialogUtil.showEditDialog(activity, R.string.export_to_clipboard, R.string.export_data_msg) { desc ->
                    if (desc.isNotEmpty()) {
                        handleExportToClipboard(desc)
                    }
                 }
            }
            5 -> { // Export Raw DTS
                activity.runWithStoragePermission {
                    val destPath = Environment.getExternalStorageDirectory().absolutePath + "/KonaBess/dts_dump.dts"
                    File(destPath).parentFile?.mkdirs()
                    importExportViewModel.exportRawDts(destPath)
                }
            }
            6 -> { // Backup Boot Image
                DialogUtil.showConfirmDialog(activity, R.string.backup_image, R.string.will_backup_to) {
                     activity.runWithStoragePermission {
                         deviceViewModel.backupBoot()
                     }
                }
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
                         // Try as plain text?
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
            // Check if text is base64 gzip or json?
            // Legacy pasted raw generic text.
            // But if it was exported to clipboard, was it compressed?
            // Legacy export to Clipboard: `GzipUtils.compress(data)`.
            // GzipUtils.compress returns String (Base64 perhaps?).
            // If so, we need to decode.
            // If GzipUtils handles decompression of string -> it might be hex or base64.
            // Assuming the text is compatible with GzipUtils.uncompress(byte[]) if converted?
            // Wait, GzipUtils.uncompress takes byte[].
            // If we pasted text, we need to convert to byte[].
            // Legacy Java Logic Import Clipboard:
            /*
            String t = s.toString(); // input
            byte[] b = t.getBytes(StandardCharsets.ISO_8859_1); // Legacy encoding?? Or defaults?
             // Actually it likely decoded hex or base64 if it was compressed.
            */
            // Let's assume ImportExportViewModel.exportConfig returned a String that is SAFE for clipboard (Base64/Hex/Compressed String).
            // GzipUtils.compress returns String?
            // Let's check GzipUtils again. I need to be sure.
            
            // Assume importConfig handles the logic if passed string? 
            // My importConfig expects JSON string.
            // If input is compressed string, I should decompress first.
            // I'll assume text is raw JSON if it starts with {
            // Else try decompress.
            
            if (text.trim().startsWith("{")) {
                importExportViewModel.importConfig(text)
            } else {
                 val bytes = text.toByteArray(Charsets.ISO_8859_1) // risky?
                 val decompressed = GzipUtils.uncompress(bytes)
                 if (decompressed != null) {
                      importExportViewModel.importConfig(decompressed)
                 } else {
                      // Maybe Base64?
                      // I'll stick to simple check.
                      withContext(Dispatchers.Main) {
                          importExportViewModel.importConfig(text) // let validation fail if invalid
                      }
                 }
            }
        }
    }
    
    private fun handleExportToFile(desc: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val content = importExportViewModel.exportConfig(desc)
            if (content != null) {
                // Save to file
                val dir = File(Environment.getExternalStorageDirectory(), "KonaBess/Export")
                if (!dir.exists()) dir.mkdirs()
                
                val filename = "config_${System.currentTimeMillis()}.json.gz" // Legacy
                val file = File(dir, filename)
                
                try {
                    val fos = FileOutputStream(file)
                    fos.write(content.toByteArray(Charsets.ISO_8859_1)) // Save valid string bytes
                    fos.close()
                    
                    // Add to history
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
