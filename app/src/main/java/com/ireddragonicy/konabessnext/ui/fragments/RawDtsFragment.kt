package com.ireddragonicy.konabessnext.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.editor.core.CodeEditor
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.repository.GpuRepository
import com.ireddragonicy.konabessnext.ui.adapters.DtsTreeAdapter
import com.ireddragonicy.konabessnext.ui.adapters.StickyHeaderItemDecoration
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import com.ireddragonicy.konabessnext.utils.ExportHistoryManager
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * Raw DTS text editor fragment with syntax highlighting and search navigation.
 * Uses SharedGpuViewModel for state synchronization with other workbench views.
 */
@AndroidEntryPoint
class RawDtsFragment : Fragment() {

    // Shared ViewModel - synced with GpuFrequencyFragment
    private val sharedViewModel: SharedGpuViewModel by activityViewModels()

    @Inject
    lateinit var exportHistoryManager: ExportHistoryManager

    private lateinit var editorContent: CodeEditor
    private lateinit var loadingState: LinearLayout
    private lateinit var lineCountText: TextView

    // Search bar views
    private var searchBar: LinearLayout? = null
    private var searchInput: EditText? = null
    private var searchResultCount: TextView? = null
    private var btnSearchPrev: ImageButton? = null
    private var btnSearchNext: ImageButton? = null
    private var btnSearchClose: ImageButton? = null

    // Visual Editor
    private var visualRecycler: RecyclerView? = null
    private lateinit var visualAdapter: DtsTreeAdapter
    private var isVisualMode = false
    private var currentRootNode: DtsNode? = null

    // State tracking
    private var isUserEditing = false
    private var textUpdateJob: Job? = null
    
    // Debounce delay for text changes
    private val TEXT_UPDATE_DEBOUNCE_MS = 500L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_raw_dts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        editorContent = view.findViewById(R.id.editor_content)
        loadingState = view.findViewById(R.id.editor_loading_state)
        lineCountText = view.findViewById(R.id.line_count_text)

        // Initialize Visual Editor
        visualRecycler = view.findViewById(R.id.visual_editor_recycler)
        visualRecycler?.layoutManager = LinearLayoutManager(requireContext())
        visualAdapter = DtsTreeAdapter()
        visualRecycler?.adapter = visualAdapter
        val stickHeaderDecoration = StickyHeaderItemDecoration(visualAdapter)
        stickHeaderDecoration.attachToRecyclerView(visualRecycler!!)

        // Initialize search bar views (optional - may not exist in fragment layout)
        searchBar = view.findViewById(R.id.search_bar)
        searchInput = view.findViewById(R.id.search_input)
        searchResultCount = view.findViewById(R.id.search_result_count)
        btnSearchPrev = view.findViewById(R.id.btn_search_prev)
        btnSearchNext = view.findViewById(R.id.btn_search_next)
        btnSearchClose = view.findViewById(R.id.btn_search_close)

        setupEditor()
        setupSearchBar()
        observeViewModel()
    }

    private fun setupEditor() {
        editorContent.setOnTextChangedListener {
            if (!isUserEditing) return@setOnTextChangedListener
            
            updateLineCount()
            
            // Debounce text updates to repository
            textUpdateJob?.cancel()
            textUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(TEXT_UPDATE_DEBOUNCE_MS)
                val newContent = editorContent.text.toString()
                sharedViewModel.updateFromText(newContent, "Text edit")
            }
        }
    }

    private fun setupSearchBar() {
        if (searchBar == null) return

        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val query = s.toString()
                sharedViewModel.search(query)
            }
        })

        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                sharedViewModel.nextSearchResult()
                true
            } else false
        }

        btnSearchPrev?.setOnClickListener { sharedViewModel.previousSearchResult() }
        btnSearchNext?.setOnClickListener { sharedViewModel.nextSearchResult() }
        btnSearchClose?.setOnClickListener { hideSearchBar() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe DTS content changes
                launch {
                    sharedViewModel.dtsContent.collectLatest { content ->
                        updateEditorContent(content)
                    }
                }

                // Observe workbench state
                launch {
                    sharedViewModel.workbenchState.collectLatest { state ->
                        when (state) {
                            is SharedGpuViewModel.WorkbenchState.Loading -> {
                                loadingState.visibility = View.VISIBLE
                                editorContent.visibility = View.GONE
                            }
                            is SharedGpuViewModel.WorkbenchState.Ready -> {
                                loadingState.visibility = View.GONE
                                editorContent.visibility = View.VISIBLE
                            }
                            is SharedGpuViewModel.WorkbenchState.Error -> {
                                loadingState.visibility = View.GONE
                                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // Observe search state
                launch {
                    sharedViewModel.searchState.collectLatest { state ->
                        if (state.query.isNotEmpty() && state.results.isNotEmpty() && state.currentIndex >= 0) {
                            val result = state.results[state.currentIndex]
                            // Scroll to result position (CodeEditor doesn't have setSelection)
                            editorContent.searchAndSelect(state.query)
                            searchResultCount?.text = "${state.currentIndex + 1}/${state.results.size}"
                            searchResultCount?.visibility = View.VISIBLE
                        } else if (state.query.isNotEmpty() && state.results.isEmpty()) {
                            searchResultCount?.setText(R.string.not_found)
                            searchResultCount?.visibility = View.VISIBLE
                        } else {
                            searchResultCount?.visibility = View.GONE
                        }
                    }
                }

                // Observe toast events
                launch {
                    sharedViewModel.toastEvent.collectLatest { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }

                // Observe error events
                launch {
                    sharedViewModel.errorEvent.collectLatest { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateEditorContent(content: String) {
        val currentContent = editorContent.text.toString()
        if (currentContent != content) {
            // Temporarily disable user editing flag to prevent feedback loop
            isUserEditing = false
            editorContent.setText(content)
            updateLineCount()
            isUserEditing = true
        }
    }

    // ===== Public Actions (called from toolbar/parent) =====

    fun showSearchBar() {
        searchBar?.visibility = View.VISIBLE
        searchInput?.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideSearchBar() {
        searchBar?.visibility = View.GONE
        editorContent.clearSearch()
        visualAdapter.clearSearch()
        sharedViewModel.clearSearch()
        searchResultCount?.visibility = View.GONE

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput?.windowToken, 0)
        editorContent.requestFocus()
    }

    fun performUndo() {
        sharedViewModel.undo()
    }

    fun performRedo() {
        sharedViewModel.redo()
    }

    fun performSave() {
        // Sync visual changes if in visual mode
        if (isVisualMode) syncVisualToText()
        sharedViewModel.save()
    }

    fun copyAllToClipboard() {
        val content = editorContent.text.toString()
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("DTS Content", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    fun toggleViewMode() {
        if (isVisualMode) {
            syncVisualToText()
            visualRecycler?.visibility = View.GONE
            editorContent.visibility = View.VISIBLE
            isVisualMode = false
        } else {
            syncTextToVisual()
            editorContent.visibility = View.GONE
            visualRecycler?.visibility = View.VISIBLE
            if (searchBar?.visibility == View.VISIBLE) hideSearchBar()
            isVisualMode = true
        }
    }

    fun handleExport() {
        if (isVisualMode) syncVisualToText()

        val input = EditText(requireContext()).apply {
            setSingleLine(true)
            setText("export_${System.currentTimeMillis()}.dts")
            selectAll()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Raw DTS")
            .setMessage("Enter filename for export:")
            .setView(input)
            .setPositiveButton("Export") { _, _ ->
                val filename = input.text.toString().trim()
                if (filename.isNotEmpty()) performExport(filename)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performExport(filename: String) {
        var finalFilename = filename
        if (!finalFilename.endsWith(".dts")) finalFilename += ".dts"

        val content = editorContent.text.toString()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val exportDir = File(requireContext().filesDir, "konabess_exports")
                if (!exportDir.exists()) exportDir.mkdirs()

                val exportFile = File(exportDir, finalFilename)
                BufferedWriter(FileWriter(exportFile)).use { it.write(content) }

                val chipType = ChipInfo.which?.takeIf { it != ChipInfo.Type.unknown }?.name ?: "Unknown"
                exportHistoryManager.addExport(finalFilename, "Raw DTS Export", exportFile.absolutePath, chipType)

                Toast.makeText(context, "Exported to ${exportFile.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== Visual Mode Sync =====

    private fun syncTextToVisual() {
        try {
            val content = editorContent.text.toString()
            currentRootNode = DtsTreeHelper.parse(content)
            currentRootNode?.let { visualAdapter.setRootNode(it) }
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(context, "Error parsing DTS: ${e.message}", Toast.LENGTH_SHORT).show()
            isVisualMode = false
            editorContent.visibility = View.VISIBLE
            visualRecycler?.visibility = View.GONE
        }
    }

    private fun syncVisualToText() {
        currentRootNode?.let { node ->
            try {
                val content = DtsTreeHelper.generate(node)
                editorContent.setText(content)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error generating DTS: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateLineCount() {
        lineCountText.text = getString(R.string.line_count, editorContent.lines.size)
    }

    override fun onResume() {
        super.onResume()
        isUserEditing = true
    }

    override fun onPause() {
        super.onPause()
        isUserEditing = false
        textUpdateJob?.cancel()
    }

    companion object {
        fun newInstance() = RawDtsFragment()
    }
}
