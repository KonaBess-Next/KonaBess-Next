package com.ireddragonicy.konabessnext.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.core.KonaBessCore
import com.ireddragonicy.konabessnext.editor.core.CodeEditor
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.ui.adapters.DtsTreeAdapter
import com.ireddragonicy.konabessnext.ui.adapters.StickyHeaderItemDecoration
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import com.ireddragonicy.konabessnext.utils.ExportHistoryManager
import com.ireddragonicy.konabessnext.utils.LocaleUtil
import com.ireddragonicy.konabessnext.utils.RootHelper
import com.ireddragonicy.konabessnext.repository.DeviceRepository
import com.ireddragonicy.konabessnext.viewmodel.RawDtsEditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Raw DTS text editor with syntax highlighting and search navigation.
 */
@AndroidEntryPoint
class RawDtsEditorActivity : AppCompatActivity() {

    // ViewModel for MVVM
    private lateinit var viewModel: RawDtsEditorViewModel

    @Inject
    lateinit var deviceRepository: DeviceRepository

    private lateinit var editorContent: CodeEditor
    private lateinit var loadingState: LinearLayout
    private lateinit var lineCountText: TextView
    private lateinit var toolbar: MaterialToolbar

    // Search bar views
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchResultCount: TextView
    private lateinit var btnSearchPrev: ImageButton
    private lateinit var btnSearchNext: ImageButton
    private lateinit var btnSearchClose: ImageButton

    // Visual Editor
    private lateinit var visualRecycler: RecyclerView
    private lateinit var visualAdapter: DtsTreeAdapter
    private var isVisualMode = false
    private var currentRootNode: DtsNode? = null

    private var hasUnsavedChanges = false

    // Search state
    private var currentSearchQuery = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtil.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme before setting content view
        applyColorPalette()

        setContentView(R.layout.activity_raw_dts_editor)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[RawDtsEditorViewModel::class.java]

        // Initialize views
        toolbar = findViewById(R.id.editor_toolbar)
        editorContent = findViewById(R.id.editor_content)
        loadingState = findViewById(R.id.editor_loading_state)
        lineCountText = findViewById(R.id.line_count_text)

        // Initialize Visual Editor
        visualRecycler = findViewById(R.id.visual_editor_recycler)
        visualRecycler.layoutManager = LinearLayoutManager(this)
        visualAdapter = DtsTreeAdapter()
        visualRecycler.adapter = visualAdapter
        val stickHeaderDecoration = StickyHeaderItemDecoration(visualAdapter)
        stickHeaderDecoration.attachToRecyclerView(visualRecycler)

        // Initialize search bar views
        searchBar = findViewById(R.id.search_bar)
        searchInput = findViewById(R.id.search_input)
        searchResultCount = findViewById(R.id.search_result_count)
        btnSearchPrev = findViewById(R.id.btn_search_prev)
        btnSearchNext = findViewById(R.id.btn_search_next)
        btnSearchClose = findViewById(R.id.btn_search_close)

        // Setup toolbar
        toolbar.setNavigationOnClickListener { handleBackPress() }
        toolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }

        // Setup editor
        setupEditor()

        // Setup search bar
        setupSearchBar()

        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

        // Load file
        loadFile()
    }

    private fun setupEditor() {
        editorContent.setOnTextChangedListener {
            hasUnsavedChanges = true
            updateLineCount()
            updateMenuState()
        }
    }

    private fun setupSearchBar() {
        // Search text change listener - search as you type
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                currentSearchQuery = s.toString()
                if (currentSearchQuery.isNotEmpty()) {
                    // Reset search position and find first match
                    var found = false
                    if (isVisualMode) {
                        found = visualAdapter.search(currentSearchQuery)
                    } else {
                        editorContent.clearSearch()
                        found = editorContent.searchAndSelect(currentSearchQuery)
                    }
                    updateSearchResultDisplay(found)
                } else {
                    if (isVisualMode) {
                        visualAdapter.clearSearch()
                    } else {
                        editorContent.clearSearch()
                    }
                    searchResultCount.visibility = View.GONE
                }
            }
        })

        // Handle Enter key in search input
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchNext()
                return@setOnEditorActionListener true
            }
            false
        }

        // Search previous button
        btnSearchPrev.setOnClickListener { searchPrevious() }

        // Search next button
        btnSearchNext.setOnClickListener { searchNext() }

        // Close search bar
        btnSearchClose.setOnClickListener { hideSearchBar() }
    }

    private fun showSearchBar() {
        searchBar.visibility = View.VISIBLE
        searchInput.requestFocus()

        // Show keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)

        // Pre-fill with previous search query
        if (currentSearchQuery.isNotEmpty()) {
            searchInput.setText(currentSearchQuery)
            searchInput.selectAll()
        }
    }

    private fun hideSearchBar() {
        searchBar.visibility = View.GONE
        editorContent.clearSearch()
        visualAdapter.clearSearch()
        searchResultCount.visibility = View.GONE

        // Hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)

        // Return focus to editor
        editorContent.requestFocus()
    }

    private fun searchNext() {
        if (currentSearchQuery.isNotEmpty()) {
            var found = false
            if (isVisualMode) {
                found = visualAdapter.nextSearchResult()
            } else {
                found = editorContent.searchAndSelect(currentSearchQuery)
            }
            updateSearchResultDisplay(found)
        }
    }

    private fun searchPrevious() {
        if (currentSearchQuery.isNotEmpty()) {
            var found = false
            if (isVisualMode) {
                found = visualAdapter.previousSearchResult()
            } else {
                found = editorContent.searchPrevious(currentSearchQuery)
            }
            updateSearchResultDisplay(found)
        }
    }

    private fun updateSearchResultDisplay(found: Boolean) {
        searchResultCount.visibility = View.VISIBLE
        if (found) {
            searchResultCount.text = null // Clear - we don't have total count
            searchResultCount.visibility = View.GONE
        } else {
            searchResultCount.setText(R.string.not_found)
        }
    }

    private fun loadFile() {
        loadingState.visibility = View.VISIBLE
        editorContent.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = StringBuilder()
                val dtsPath = deviceRepository.dtsPath
                if (dtsPath == null) {
                    throw IllegalStateException("DTS Path not initialized")
                }
                val file = File(dtsPath)

                if (file.exists()) {
                    val reader = BufferedReader(FileReader(file))
                    var line: String? = reader.readLine()
                    while (line != null) {
                        content.append(line).append("\n")
                        line = reader.readLine()
                    }
                    reader.close()
                }

                val finalContent = content.toString()

                withContext(Dispatchers.Main) {
                    editorContent.setText(finalContent)

                    updateLineCount()
                    updateMenuState()

                    loadingState.visibility = View.GONE
                    editorContent.visibility = View.VISIBLE

                    hasUnsavedChanges = false
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingState.visibility = View.GONE
                    Toast.makeText(this@RawDtsEditorActivity, R.string.error_occur, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun saveFile() {
        // Sync visual changes to text before saving
        if (isVisualMode) {
            syncVisualToText()
        }

        val waitingDialog = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.saving_file)
            .setCancelable(false)
            .create()
        waitingDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            var tempFile: File? = null
            try {
                // Create a temporary file in the app's cache directory
                tempFile = File(cacheDir, "dts_edit_temp_" + System.currentTimeMillis())
                val writer = BufferedWriter(FileWriter(tempFile))

                val dtsPath = deviceRepository.dtsPath ?: throw IllegalStateException("DTS Path is null")
                
                // Use RootHelper to copy the temp file to the destination
                // relying on the shell to handle permissions
                val cmd = "cat '${tempFile.absolutePath}' > '$dtsPath'"

                // Also ensure 644 permissions
                val chmodCmd = "chmod 644 '$dtsPath'"

                if (RootHelper.execAndCheck(cmd) && RootHelper.execAndCheck(chmodCmd)) {
                    success = true
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Clean up temp file
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete()
                }
            }

            withContext(Dispatchers.Main) {
                waitingDialog.dismiss()
                if (success) {
                    hasUnsavedChanges = false
                    Toast.makeText(this@RawDtsEditorActivity, R.string.save_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@RawDtsEditorActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleExport() {
        if (isVisualMode) {
            syncVisualToText()
        }

        val input = EditText(this)
        input.setSingleLine(true)
        input.setText("export_" + System.currentTimeMillis() + ".dts")
        input.selectAll()

        MaterialAlertDialogBuilder(this)
            .setTitle("Export Raw DTS")
            .setMessage("Enter filename for export:")
            .setView(input)
            .setPositiveButton("Export") { _, _ ->
                val filename = input.text.toString().trim { it <= ' ' }
                if (filename.isNotEmpty()) {
                    performExport(filename)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performExport(filename: String) {
        var finalFilename = filename
        if (!finalFilename.endsWith(".dts")) {
            finalFilename += ".dts"
        }

        val content = editorContent.text.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Ensure export directory exists
                val exportDir = File(filesDir, "konabess_exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val exportFile = File(exportDir, finalFilename)
                val writer = BufferedWriter(FileWriter(exportFile))
                writer.write(content)
                writer.close()

                // Add to history
                // We use ChipInfo.which.name() if available, or "Unknown"
                var chipType = "Unknown"
                if (ChipInfo.which != ChipInfo.Type.unknown) {
                    chipType = ChipInfo.which.name
                }

                val historyManager = ExportHistoryManager(this@RawDtsEditorActivity)
                historyManager.addExport(finalFilename, "Raw DTS Export", exportFile.absolutePath, chipType)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RawDtsEditorActivity, "Exported to " + exportFile.name, Toast.LENGTH_LONG)
                        .show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RawDtsEditorActivity, "Export failed: " + e.message, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun updateLineCount() {
        val lineCount = editorContent.lines.size
        lineCountText.text = getString(R.string.line_count, lineCount)
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_save) {
            saveFile()
            return true
        } else if (id == R.id.action_export) {
            handleExport()
            return true
        } else if (id == R.id.action_undo) {
            performUndo()
            return true
        } else if (id == R.id.action_redo) {
            performRedo()
            return true
        } else if (id == R.id.action_search) {
            showSearchBar()
            return true
        } else if (id == R.id.action_copy_all) {
            copyAllToClipboard()
            return true
        } else if (id == R.id.action_toggle_view) {
            toggleViewMode(item)
            return true
        }

        return false
    }

    private fun toggleViewMode(item: MenuItem) {
        if (isVisualMode) {
            // Switch to Text Mode
            syncVisualToText()
            visualRecycler.visibility = View.GONE
            editorContent.visibility = View.VISIBLE
            item.setIcon(R.drawable.ic_drag_handle) // Set icon to "List" for next toggle
            item.setTitle(R.string.visual_view)
            isVisualMode = false
        } else {
            // Switch to Visual Mode
            syncTextToVisual()
            editorContent.visibility = View.GONE
            visualRecycler.visibility = View.VISIBLE
            // Hide search bar if open
            if (searchBar.visibility == View.VISIBLE)
                hideSearchBar()

            item.setIcon(R.drawable.ic_code) // Set icon to "Code" for next toggle
            item.setTitle(R.string.text_view) // Assuming you have a string resource or just hardcode for valid
            isVisualMode = true
        }
    }

    private fun syncTextToVisual() {
        try {
            val content = editorContent.text.toString()
            currentRootNode = DtsTreeHelper.parse(content)
            if (currentRootNode != null) {
                visualAdapter.setRootNode(currentRootNode!!)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, "Error parsing DTS: " + e.message, Toast.LENGTH_SHORT).show()
            // Fallback to text mode if visual fails
            isVisualMode = false
            editorContent.visibility = View.VISIBLE
            visualRecycler.visibility = View.GONE
            if (toolbar.menu != null) {
                val item = toolbar.menu.findItem(R.id.action_toggle_view)
                if (item != null) {
                    item.setIcon(R.drawable.ic_drag_handle)
                    item.setTitle(R.string.visual_view)
                }
            }
        }
    }

    private fun syncVisualToText() {
        val node = currentRootNode
        if (node != null) {
            try {
                val content = DtsTreeHelper.generate(node)
                editorContent.setText(content)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error generating DTS: " + e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performUndo() {
        if (editorContent.canUndo()) {
            editorContent.undo()
            updateMenuState()
        }
    }

    private fun performRedo() {
        if (editorContent.canRedo()) {
            editorContent.redo()
            updateMenuState()
        }
    }

    private fun copyAllToClipboard() {
        val content = editorContent.text.toString()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("DTS Content", content)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun updateMenuState() {
        if (toolbar.menu == null) {
            return
        }

        val undoItem = toolbar.menu.findItem(R.id.action_undo)
        val redoItem = toolbar.menu.findItem(R.id.action_redo)

        if (undoItem != null) {
            val canUndo = editorContent.canUndo()
            undoItem.isEnabled = canUndo
            undoItem.icon?.alpha = if (canUndo) 255 else 77
        }

        if (redoItem != null) {
            val canRedo = editorContent.canRedo()
            redoItem.isEnabled = canRedo
            redoItem.icon?.alpha = if (canRedo) 255 else 77
        }
    }

    private fun handleBackPress() {
        // Close search bar first if visible
        if (searchBar.visibility == View.VISIBLE) {
            hideSearchBar()
            return
        }

        if (hasUnsavedChanges) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unsaved_changes_title)
                .setMessage(R.string.unsaved_changes_msg)
                .setPositiveButton(R.string.save) { _, _ ->
                    saveFile()
                    finish()
                }
                .setNegativeButton(R.string.discard) { _, _ -> finish() }
                .setNeutralButton(R.string.cancel, null)
                .show()
        } else {
            finish()
        }
    }

    private fun applyColorPalette() {
        val prefs = getSharedPreferences("KonaBessSettings", Context.MODE_PRIVATE)
        val palette = prefs.getInt("color_palette", 0)

        when (palette) {
            1 -> setTheme(R.style.Theme_KonaBess_Purple)
            2 -> setTheme(R.style.Theme_KonaBess_Blue)
            3 -> setTheme(R.style.Theme_KonaBess_Green)
            4 -> setTheme(R.style.Theme_KonaBess_Pink)
            5 -> setTheme(R.style.Theme_KonaBess_AMOLED)
            else -> setTheme(R.style.Theme_KonaBess)
        }
    }
}
