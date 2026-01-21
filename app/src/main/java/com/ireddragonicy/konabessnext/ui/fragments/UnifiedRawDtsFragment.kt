package com.ireddragonicy.konabessnext.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import android.content.res.ColorStateList
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.editor.core.CodeEditor
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Raw DTS Text Editor Fragment (Unified Workbench).
 * Pure text edition component with its own action toolbar.
 */
@AndroidEntryPoint
class UnifiedRawDtsFragment : Fragment() {

    private val sharedViewModel: SharedGpuViewModel by activityViewModels()

    private lateinit var loadingState: LinearLayout
    private lateinit var lineCountText: TextView
    
    // Advanced Editor Toolbar
    private lateinit var editorToolbar: LinearLayout
    private var btnCopyAll: MaterialButton? = null
    private var btnSearch: MaterialButton? = null

    // Search bar components
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchResultCount: TextView
    private lateinit var btnSearchPrev: ImageButton
    private lateinit var btnSearchNext: ImageButton
    private lateinit var btnSearchClose: ImageButton

    private var isUserEditing = false
    private var textUpdateJob: Job? = null
    private val TEXT_UPDATE_DEBOUNCE_MS = 300L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // --- Advanced Editor Toolbar (Undo, Redo, Copy All, Search) ---
        editorToolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val chipSpacing = (density * 8).toInt()
        
        btnCopyAll = createIconButton(context, R.drawable.ic_content_copy).apply {
            setOnClickListener { copyAllToClipboard() }
        }
        btnSearch = createIconButton(context, android.R.drawable.ic_menu_search).apply {
            setOnClickListener { toggleSearchBar() }
        }
        
        val iconParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        iconParams.marginEnd = chipSpacing
        
        btnCopyAll!!.layoutParams = iconParams
        btnSearch!!.layoutParams = iconParams
        
        editorToolbar.addView(btnCopyAll)
        editorToolbar.addView(btnSearch)
        
        root.addView(editorToolbar)

        // --- Search Bar ---
        searchBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setBackgroundColor(0xFFF0F0F0.toInt()) // Light gray
            gravity = Gravity.CENTER_VERTICAL
        }

        searchInput = EditText(context).apply {
            hint = "Search"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
        }
        searchBar.addView(searchInput)

        searchResultCount = TextView(context).apply {
            text = "0/0"
            visibility = View.GONE
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
        }
        searchBar.addView(searchResultCount)

        btnSearchPrev = createImgButton(context, android.R.drawable.arrow_up_float)
        searchBar.addView(btnSearchPrev)

        btnSearchNext = createImgButton(context, android.R.drawable.arrow_down_float)
        searchBar.addView(btnSearchNext)
        
        btnSearchClose = createImgButton(context, android.R.drawable.ic_menu_close_clear_cancel)
        searchBar.addView(btnSearchClose)

        root.addView(searchBar)

        // --- Loading Overlay ---
        loadingState = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)
            visibility = View.GONE
        }
        loadingState.addView(android.widget.ProgressBar(context))
        root.addView(loadingState)

        // --- Editor Content ---
        val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)
            setContent {
                com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme {
                    val dtsLines by sharedViewModel.dtsLines.collectAsState()
                    val searchState by sharedViewModel.searchState.collectAsState()
                    
                    com.ireddragonicy.konabessnext.ui.compose.DtsEditor(
                        lines = dtsLines,
                        onLinesChanged = { newLines ->
                            sharedViewModel.updateFromText(newLines.joinToString("\n"), "Text edit")
                        },
                        searchQuery = searchState.query,
                        searchResultIndex = searchState.currentIndex,
                        searchResults = emptyList() // TODO: Map SharedGpuViewModel.SearchResult to LineSearchResult if needed
                    )
                }
            }
        }
        root.addView(composeView)

        // --- Status Bar (Line Count) ---
        val statusBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
        lineCountText = TextView(context).apply {
            text = "Lines: 0"
            textSize = 12f
        }
        statusBar.addView(lineCountText)
        root.addView(statusBar)

        return root
    }
    
    private fun createIconButton(context: Context, iconResId: Int): MaterialButton {
        val button = MaterialButton(context)
        button.setIconResource(iconResId)
        button.iconSize = (context.resources.displayMetrics.density * 20).toInt()
        button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        button.iconPadding = 0
        button.text = ""
        
        // Tonal Button Style
        val bg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorSecondaryContainer)
        val fg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSecondaryContainer)
        
        button.backgroundTintList = ColorStateList.valueOf(bg)
        button.iconTint = ColorStateList.valueOf(fg)
        button.rippleColor = ColorStateList.valueOf(MaterialColors.getColor(button, com.google.android.material.R.attr.colorSecondary))
        
        button.cornerRadius = (context.resources.displayMetrics.density * 24).toInt()
        val pad = (context.resources.displayMetrics.density * 12).toInt()
        button.setPadding(pad, pad, pad, pad)
        button.insetTop = 0
        button.insetBottom = 0
        button.minWidth = 0
        button.minHeight = 0
        
        return button
    }
    
    private fun createImgButton(context: Context, resId: Int): ImageButton {
        return ImageButton(context).apply {
            setImageResource(resId)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(16, 16, 16, 16)
            setBackgroundResource(0)
            setColorFilter(0xFF555555.toInt())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isUserEditing = true
        setupEditor()
        setupSearchBar()
        observeViewModel()
    }

    private fun setupEditor() {
        // Line count update is now handled via ViewModel observation or Compose
    }
    
    private fun copyAllToClipboard() {
        val content = sharedViewModel.dtsContent.value
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("DTS Content", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
    
    private fun toggleSearchBar() {
        if (searchBar.visibility == View.VISIBLE) {
            hideSearchBar()
        } else {
            showSearchBar()
        }
    }

    private fun setupSearchBar() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sharedViewModel.search(s.toString())
            }
        })

        // On Enter, find next
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                sharedViewModel.nextSearchResult()
                true
            } else false
        }

        btnSearchPrev.setOnClickListener { sharedViewModel.previousSearchResult() }
        btnSearchNext.setOnClickListener { sharedViewModel.nextSearchResult() }
        btnSearchClose.setOnClickListener { 
            hideSearchBar()
        }
    }
    
    fun showSearchBar() {
        searchBar.visibility = View.VISIBLE
        searchInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }
    
    fun hideSearchBar() {
        searchBar.visibility = View.GONE
        sharedViewModel.clearSearch()
        searchInput.setText("")
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // DTS Content
                launch {
                    sharedViewModel.dtsContent.collectLatest { content ->
                        // Content updated automatically in ComposeView
                    }
                }
                
                launch {
                    sharedViewModel.dtsLines.collectLatest { lines ->
                        lineCountText.text = "Lines: ${lines.size}"
                    }
                }

                // Workbench State (Loading)
                launch {
                    sharedViewModel.workbenchState.collectLatest { state ->
                         when (state) {
                             is SharedGpuViewModel.WorkbenchState.Loading -> {
                                 loadingState.visibility = View.VISIBLE
                             }
                             is SharedGpuViewModel.WorkbenchState.Ready -> {
                                 loadingState.visibility = View.GONE
                             }
                             else -> {}
                         }
                    }
                }

                // Search Results
                launch {
                    sharedViewModel.searchState.collectLatest { state ->
                        if (state.query.isNotEmpty()) {
                             if (state.results.isNotEmpty() && state.currentIndex >= 0) {
                                  searchResultCount.visibility = View.VISIBLE
                                  searchResultCount.text = "${state.currentIndex + 1}/${state.results.size}"
                             } else {
                                  searchResultCount.visibility = View.VISIBLE
                                  searchResultCount.text = "0/0"
                             }
                        } else {
                            searchResultCount.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        isUserEditing = true
    }

    override fun onPause() {
        super.onPause()
        textUpdateJob?.cancel()
    }

    companion object {
        fun newInstance() = UnifiedRawDtsFragment()
    }
}
