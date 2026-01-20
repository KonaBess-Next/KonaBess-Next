package com.ireddragonicy.konabessnext.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.GpuTableEditor
import com.ireddragonicy.konabessnext.core.KonaBessCore
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.adapters.ChipsetSelectorAdapter
import com.ireddragonicy.konabessnext.utils.RootHelper
import com.ireddragonicy.konabessnext.viewmodel.DeviceViewModel
import com.ireddragonicy.konabessnext.viewmodel.GpuFrequencyViewModel
import com.ireddragonicy.konabessnext.viewmodel.UiState
import com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@AndroidEntryPoint
class GpuFrequencyFragment : Fragment() {
    private var contentContainer: LinearLayout? = null
    // PreparationListener removed
    private var needsReload = false

    // MVVM ViewModels - shared with Activity
    private val deviceViewModel: DeviceViewModel by activityViewModels() // Scoped to Activity in logic? Hilt 'activityViewModels' does that.
    // Wait, original code used: deviceViewModel = activity.getDeviceViewModel() and gpu = ViewModelProvider(requireActivity())...
    // activityViewModels() is the correct Hilt/Jetpack way for shared ViewModels.
    private val gpuFrequencyViewModel: GpuFrequencyViewModel by activityViewModels()
    private val sharedViewModel: com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bridge ViewModel to GpuTableEditor for gradual migration
        GpuTableEditor.setViewModel(gpuFrequencyViewModel)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        contentContainer = LinearLayout(requireContext())
        contentContainer!!.orientation = LinearLayout.VERTICAL
        val padding = (resources.displayMetrics.density * 16).toInt()
        contentContainer!!.setPadding(padding, padding, padding, padding)

        loadContent()
        return contentContainer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as MainActivity

        // Collect DeviceViewModel state
        lifecycleScope.launch {
            // isPrepared
            launch {
                deviceViewModel.isPrepared.collect { isPrepared ->
                    if (isPrepared) {
                        // Load GPU table data when prepared
                        gpuFrequencyViewModel.loadData()
                        // Ensure SharedViewModel (New Architecture) also synced/loaded
                        sharedViewModel.loadData()
                    } else {
                        // Not prepared logic
                    }
                }
            }

            // Observe binsState to show GPU editor when data is loaded
            launch {
                gpuFrequencyViewModel.binsState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            if (deviceViewModel.isPrepared.value) {
                                showLoadingState()
                            }
                        }
                        is UiState.Success -> {
                            if (deviceViewModel.isPrepared.value) {
                                showGpuEditor(activity)
                            }
                        }
                        is UiState.Error -> {
                            if (deviceViewModel.isPrepared.value) {
                                // Show error for GPU loading
                                showErrorState(activity)
                            }
                        }
                    }
                }
            }

            // detectionState
            launch {
                deviceViewModel.detectionState.collect { state ->
                    if (state == null) {
                        showPromptState(activity)
                        return@collect
                    }
                    if (state is UiState.Loading) {
                        showLoadingState()
                    } else if (state is UiState.Error) {
                        showErrorState(activity)
                    } else if (state is UiState.Success) {
                        // Success handled
                    }
                }
            }
            
            // recommendedIndex
            launch {
                deviceViewModel.recommendedIndex.collect { index ->
                    // Only show selection if not already prepared
                    if (index != null && !deviceViewModel.isPrepared.value) {
                        showSelectionState(activity, index)
                    }
                }
            }

            // Observe editor states to sync with GpuTableEditor static state
            launch {
                gpuFrequencyViewModel.isDirty.collect { isDirty ->
                    GpuTableEditor.setDirty(isDirty)
                }
            }

            // Reactive UI refresh - single observer for all state changes
            launch {
                gpuFrequencyViewModel.stateVersion.collect { version ->
                    if (version > 0 && deviceViewModel.isPrepared.value) {
                        // Auto-refresh UI and buttons when state changes
                        GpuTableEditor.updateUndoRedoButtons()
                        GpuTableEditor.updateHistoryButtonLabel()
                        refreshCurrentView()
                    }
                }
            }
        }
    }
    
    private fun refreshCurrentView() {
        if (!isAdded) return
        // Delegate to GpuTableEditor which preserves navigation state
        GpuTableEditor.refreshCurrentView()
    }

    override fun onResume() {
        super.onResume()
        reloadIfNeeded()
        if (deviceViewModel.isPrepared.value) {
            (activity as? MainActivity)?.gpuTableEditorBackCallback?.isEnabled = true
        }
    }

    fun markDataDirty() {
        needsReload = true
        if (!isAdded) {
            return
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            reloadIfNeeded()
        }
    }

    private fun loadContent() {
        if (!isAdded) return

        val activity = activity as? MainActivity ?: return

        if (!deviceViewModel.isPrepared.value) {
             deviceViewModel.tryRestoreLastChipset()
        }
    }

    private var gpuEditorContainer: LinearLayout? = null
    private var dataArea: LinearLayout? = null

    private fun showGpuEditor(activity: MainActivity) {
        if (!isAdded) return

        contentContainer!!.removeAllViews()
        contentContainer!!.gravity = Gravity.NO_GRAVITY
        
        // Root Workbench Container
        gpuEditorContainer = LinearLayout(requireContext())
        gpuEditorContainer!!.orientation = LinearLayout.VERTICAL
        gpuEditorContainer!!.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // Compose Toolbar (replaces legacy GpuActionToolbar)
        val toolbarComposeView = androidx.compose.ui.platform.ComposeView(requireContext())
        toolbarComposeView.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val dynamicColor = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            
            val colorScheme = when {
                dynamicColor && darkTheme -> androidx.compose.material3.dynamicDarkColorScheme(context)
                dynamicColor && !darkTheme -> androidx.compose.material3.dynamicLightColorScheme(context)
                darkTheme -> androidx.compose.material3.darkColorScheme()
                else -> androidx.compose.material3.lightColorScheme()
            }
            
            androidx.compose.material3.MaterialTheme(colorScheme = colorScheme) {
                val isDirty by gpuFrequencyViewModel.isDirty.collectAsState()
                val canUndo by gpuFrequencyViewModel.canUndo.collectAsState()
                val canRedo by gpuFrequencyViewModel.canRedo.collectAsState()
                val history by gpuFrequencyViewModel.history.collectAsState()
                val currentMode by sharedViewModel.viewMode.collectAsState()
                
                com.ireddragonicy.konabessnext.ui.compose.GpuEditorToolbar(
                    isDirty = isDirty,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    historyCount = history.size,
                    currentViewMode = currentMode,
                    showChipsetSelector = true,
                    onSave = { GpuTableEditor.saveFrequencyTable(activity, true, "Saved manually") },
                    onUndo = { GpuTableEditor.handleUndo() },
                    onRedo = { GpuTableEditor.handleRedo() },
                    onShowHistory = { GpuTableEditor.showHistoryDialog(activity) },
                    onViewModeChanged = { mode -> updateViewMode(mode) },
                    onChipsetClick = {
                        val listener = GpuTableEditor()
                        com.ireddragonicy.konabessnext.core.editor.ChipsetManager.showChipsetSelectorDialog(
                            activity, contentContainer!!, android.widget.TextView(activity), listener
                        )
                    },
                    onFlashClick = { activity.startRepack() }
                )
            }
        }
        
        val density = resources.displayMetrics.density
        val toolbarParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        toolbarParams.setMargins((density * 8).toInt(), (density * 8).toInt(), (density * 8).toInt(), (density * 16).toInt())
        gpuEditorContainer!!.addView(toolbarComposeView, toolbarParams)

        // Fragment Container for Frames (GUI / Text / Tree)
        val fragmentContainer = android.widget.FrameLayout(requireContext())
        fragmentContainer.id = View.generateViewId()
        val containerParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        )
        gpuEditorContainer!!.addView(fragmentContainer, containerParams)

        contentContainer!!.addView(gpuEditorContainer)

        // Initialize Fragments
        val fm = childFragmentManager
        val transaction = fm.beginTransaction()
        
        // Define tags
        val TAG_GUI = "TAG_GUI"
        val TAG_TEXT = "TAG_TEXT"
        val TAG_TREE = "TAG_TREE"
        
        // Check if already added to avoid duplication on config change
        var guiFragment = fm.findFragmentByTag(TAG_GUI)
        var textFragment = fm.findFragmentByTag(TAG_TEXT)
        var treeFragment = fm.findFragmentByTag(TAG_TREE)
        
        if (guiFragment == null) {
            guiFragment = com.ireddragonicy.konabessnext.ui.fragments.GuiEditorFragment()
            transaction.add(fragmentContainer.id, guiFragment, TAG_GUI)
        }
        
        if (textFragment == null) {
            textFragment = com.ireddragonicy.konabessnext.ui.fragments.UnifiedRawDtsFragment.newInstance()
            transaction.add(fragmentContainer.id, textFragment, TAG_TEXT)
        }
        
        if (treeFragment == null) {
            treeFragment = com.ireddragonicy.konabessnext.ui.fragments.VisualTreeFragment.newInstance()
            transaction.add(fragmentContainer.id, treeFragment, TAG_TREE)
        }
        
        // Default State: Show GUI, Hide others.
        transaction.show(guiFragment!!)
        if (textFragment != null) transaction.hide(textFragment)
        if (treeFragment != null) transaction.hide(treeFragment)
        
        transaction.commitNow()
        
        // Sync ViewModel state (default)
        sharedViewModel.switchViewMode(com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.ViewMode.MAIN_EDITOR)
    }

    private fun updateViewMode(mode: com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.ViewMode) {
        val fm = childFragmentManager
        val gui = fm.findFragmentByTag("TAG_GUI")
        val text = fm.findFragmentByTag("TAG_TEXT")
        val tree = fm.findFragmentByTag("TAG_TREE")
        
        val transaction = fm.beginTransaction()
        
        sharedViewModel.switchViewMode(mode)
        
        // Hide all first
        if (gui != null) transaction.hide(gui)
        if (text != null) transaction.hide(text)
        if (tree != null) transaction.hide(tree)
        
        // Show selected
        when (mode) {
            com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.ViewMode.MAIN_EDITOR -> if (gui != null) transaction.show(gui)
            com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.ViewMode.TEXT_ADVANCED -> if (text != null) transaction.show(text)
            com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.ViewMode.VISUAL_TREE -> if (tree != null) transaction.show(tree)
        }
        transaction.commit()
    }
    
    private fun toggleSearch() {
        val fm = childFragmentManager
        val textFragment = fm.findFragmentByTag("TAG_TEXT") as? com.ireddragonicy.konabessnext.ui.fragments.UnifiedRawDtsFragment
        
        // Only toggle if Text Fragment is visible
        if (textFragment != null && textFragment.isVisible) {
             textFragment.showSearchBar()
        } else {
             Toast.makeText(requireContext(), "Search available in Text Mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPromptState(activity: MainActivity) {
        contentContainer!!.removeAllViews()
        contentContainer!!.gravity = Gravity.CENTER

        // Create a modern Material You card
        val card = MaterialCardView(requireContext())
        card.cardElevation = 12f
        card.radius = 28f
        val cardPadding = 56
        card.setContentPadding(cardPadding, cardPadding, cardPadding, cardPadding)

        // Get theme color for card background
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true
        )
        card.setCardBackgroundColor(typedValue.data)

        val cardContent = LinearLayout(requireContext())
        cardContent.orientation = LinearLayout.VERTICAL
        cardContent.gravity = Gravity.CENTER

        // Add icon
        val icon = ImageView(requireContext())
        icon.setImageResource(R.drawable.ic_developer_board)
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimary, typedValue, true
        )
        icon.setColorFilter(typedValue.data)
        val iconParams = LinearLayout.LayoutParams(140, 140)
        iconParams.setMargins(0, 0, 0, 40)
        cardContent.addView(icon, iconParams)

        // Add title
        val title = TextView(requireContext())
        title.text = "Detect Chipset"
        title.textSize = 26f
        title.textAlignment = View.TEXT_ALIGNMENT_CENTER
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true
        )
        title.setTextColor(typedValue.data)
        val titleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleParams.setMargins(0, 0, 0, 20)
        cardContent.addView(title, titleParams)

        // Add description message
        val message = TextView(requireContext())
        message.setText(R.string.gpu_prep_prompt)
        message.textSize = 15f
        message.textAlignment = View.TEXT_ALIGNMENT_CENTER
        message.alpha = 0.85f
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true
        )
        message.setTextColor(typedValue.data)
        val messageParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        messageParams.setMargins(0, 0, 0, 40)
        cardContent.addView(message, messageParams)

        // Add Material You button
        val button = MaterialButton(requireContext())
        button.setText(R.string.gpu_prep_start)
        button.cornerRadius = 32
        button.elevation = 6f
        val buttonPadding = 24
        button.setPadding(buttonPadding * 2, buttonPadding, buttonPadding * 2, buttonPadding)
        button.textSize = 16f
        button.setOnClickListener { startPreparation(activity) }
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        buttonParams.gravity = Gravity.CENTER_HORIZONTAL
        cardContent.addView(button, buttonParams)

        card.addView(cardContent)

        // Add card to container with margin
        val cardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(40, 0, 40, 0)
        contentContainer!!.addView(card, cardParams)
    }

    private fun showLoadingState() {
        contentContainer!!.removeAllViews()
        contentContainer!!.gravity = Gravity.CENTER

        val progressBar = ProgressBar(requireContext())
        val message = TextView(requireContext())
        message.setText(R.string.gpu_prep_loading)
        message.setPadding(0, 24, 0, 0)
        message.textAlignment = View.TEXT_ALIGNMENT_CENTER

        contentContainer!!.addView(progressBar)
        contentContainer!!.addView(
            message, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun showErrorState(activity: MainActivity) {
        contentContainer!!.removeAllViews()
        contentContainer!!.gravity = Gravity.CENTER

        // Create a modern Material You error card
        val card = MaterialCardView(requireContext())
        card.cardElevation = 12f
        card.radius = 28f
        val cardPadding = 56
        card.setContentPadding(cardPadding, cardPadding, cardPadding, cardPadding)

        // Get theme color for error card background
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorErrorContainer, typedValue, true
        )
        card.setCardBackgroundColor(typedValue.data)

        val cardContent = LinearLayout(requireContext())
        cardContent.orientation = LinearLayout.VERTICAL
        cardContent.gravity = Gravity.CENTER

        // Add error icon
        val icon = ImageView(requireContext())
        icon.setImageResource(android.R.drawable.ic_dialog_alert)
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorError, typedValue, true
        )
        icon.setColorFilter(typedValue.data)
        val iconParams = LinearLayout.LayoutParams(140, 140)
        iconParams.setMargins(0, 0, 0, 40)
        cardContent.addView(icon, iconParams)

        // Add title
        val title = TextView(requireContext())
        title.text = "Detection Failed"
        title.textSize = 26f
        title.textAlignment = View.TEXT_ALIGNMENT_CENTER
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnErrorContainer, typedValue, true
        )
        title.setTextColor(typedValue.data)
        val titleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleParams.setMargins(0, 0, 0, 20)
        cardContent.addView(title, titleParams)

        // Add error message
        val message = TextView(requireContext())
        message.setText(R.string.gpu_prep_failed)
        message.textSize = 15f
        message.textAlignment = View.TEXT_ALIGNMENT_CENTER
        message.alpha = 0.85f
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnErrorContainer, typedValue, true
        )
        message.setTextColor(typedValue.data)
        val messageParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        messageParams.setMargins(0, 0, 0, 40)
        cardContent.addView(message, messageParams)

        // Add retry button
        val retryButton = MaterialButton(requireContext())
        retryButton.setText(R.string.gpu_prep_retry)
        retryButton.cornerRadius = 32
        retryButton.elevation = 6f
        val buttonPadding = 24
        retryButton.setPadding(buttonPadding * 2, buttonPadding, buttonPadding * 2, buttonPadding)
        retryButton.textSize = 16f
        retryButton.setOnClickListener { startPreparation(activity) }
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        buttonParams.gravity = Gravity.CENTER_HORIZONTAL
        cardContent.addView(retryButton, buttonParams)

        // Add "Submit DTS to GitHub" button for unsupported devices
        val submitDtsButton = MaterialButton(
            requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        )
        submitDtsButton.setText(R.string.submit_dts_to_github)
        submitDtsButton.cornerRadius = 32
        submitDtsButton.setPadding(buttonPadding * 2, buttonPadding, buttonPadding * 2, buttonPadding)
        submitDtsButton.textSize = 14f
        submitDtsButton.setIconResource(R.drawable.ic_share)
        submitDtsButton.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        submitDtsButton.setOnClickListener { submitDtsToGitHub(activity) }
        val submitParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        submitParams.gravity = Gravity.CENTER_HORIZONTAL
        submitParams.setMargins(0, 24, 0, 0)
        cardContent.addView(submitDtsButton, submitParams)

        card.addView(cardContent)

        // Add card to container with margin
        val cardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(40, 0, 40, 0)
        contentContainer!!.addView(card, cardParams)
    }

    private fun startPreparation(activity: MainActivity) {
        deviceViewModel.detectChipset()
    }

    private fun showSelectionState(activity: MainActivity, recommendedIndex: Int) {
        contentContainer!!.removeAllViews()
        contentContainer!!.gravity = Gravity.CENTER

        // Create a modern Material You card
        val card = MaterialCardView(requireContext())
        card.cardElevation = 12f
        card.radius = 28f
        val cardPadding = 56
        card.setContentPadding(cardPadding, cardPadding, cardPadding, cardPadding)

        // Get theme color for card background
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true
        )
        card.setCardBackgroundColor(typedValue.data)

        val cardContent = LinearLayout(requireContext())
        cardContent.orientation = LinearLayout.VERTICAL
        cardContent.gravity = Gravity.CENTER

        // Add icon
        val icon = ImageView(requireContext())
        icon.setImageResource(R.drawable.ic_developer_board)
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimary, typedValue, true
        )
        icon.setColorFilter(typedValue.data)
        val iconParams = LinearLayout.LayoutParams(140, 140)
        iconParams.setMargins(0, 0, 0, 40)
        cardContent.addView(icon, iconParams)

        // Add title
        val title = TextView(requireContext())
        title.setText(R.string.title_select_chipset)
        title.textSize = 24f
        title.textAlignment = View.TEXT_ALIGNMENT_CENTER
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true
        )
        title.setTextColor(typedValue.data)
        val titleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleParams.setMargins(0, 0, 0, 20)
        cardContent.addView(title, titleParams)

        // Add description message
        val message = TextView(requireContext())
        message.text = "Multiple chipsets found. Please select one."
        message.textSize = 14f
        message.textAlignment = View.TEXT_ALIGNMENT_CENTER
        message.alpha = 0.85f
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true
        )
        message.setTextColor(typedValue.data)
        val messageParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        messageParams.setMargins(0, 0, 0, 40)
        cardContent.addView(message, messageParams)

        // Add RecyclerView for list
        val recyclerView = RecyclerView(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER

        val adapter = ChipsetSelectorAdapter(
            (deviceViewModel.detectionState.value as? UiState.Success)?.data ?: emptyList(),
            activity,
            recommendedIndex
        ) { dtb ->
            deviceViewModel.selectChipset(dtb)
            activity.notifyPreparationSuccess()
        }
        recyclerView.adapter = adapter

        val recyclerParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cardContent.addView(recyclerView, recyclerParams)

        card.addView(cardContent)

        // Add card to container with margin
        val cardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(40, 0, 40, 0)
        contentContainer!!.addView(card, cardParams)
    }

    private fun reloadIfNeeded() {
        if (!needsReload) {
            return
        }
        if (contentContainer == null) {
            return
        }
        needsReload = false;
        loadContent();
    }

    /**
     * Submit DTS files to GitHub for device support request.
     * Exports boot.img DTS and opens GitHub issue URL with device info.
     */
    private fun submitDtsToGitHub(activity: MainActivity) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.preparing_dts_submission)
            .setMessage(R.string.extracting_dts_files)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get device info
                val deviceModel = Build.MODEL
                val deviceBrand = Build.BRAND
                val deviceBoard = Build.BOARD
                val androidVersion = Build.VERSION.RELEASE
                val sdkVersion = Build.VERSION.SDK_INT.toString()

                // Check if DTS was already extracted, if not try to extract it
                var dtsPath = KonaBessCore.dts_path
                var hasDts = dtsPath != null && File(dtsPath).exists()

                // If no DTS, try to extract boot image and generate DTS
                if (!hasDts) {
                    try {
                        // Setup environment and extract boot image
                        KonaBessCore.setupEnv(activity)
                        KonaBessCore.getBootImage(activity)
                        // bootImage2dts calls unpackBootImage internally, then converts to DTS
                        KonaBessCore.bootImage2dts(activity)

                        // Check again after extraction - find the first DTS file
                        val filesDir = activity.filesDir
                        val dtsFiles = filesDir.listFiles { _, name -> name.endsWith(".dts") }
                        if (dtsFiles != null && dtsFiles.isNotEmpty()) {
                            dtsPath = dtsFiles[0].absolutePath
                            hasDts = true
                        }
                    } catch (extractError: Exception) {
                        extractError.printStackTrace()
                        // Continue anyway - we'll show manual instructions
                    }
                }

                // Build issue body
                val issueBody = StringBuilder()
                issueBody.append("## Device Information\n\n")
                issueBody.append("| Property | Value |\n")
                issueBody.append("|-------------|-------|\n")
                issueBody.append("| Brand | ").append(deviceBrand).append(" |\n")
                issueBody.append("| Model | ").append(deviceModel).append(" |\n")
                issueBody.append("| Board | ").append(deviceBoard).append(" |\n")
                issueBody.append("| Android | ").append(androidVersion).append(" (SDK ").append(sdkVersion)
                    .append(") |\n")
                issueBody.append("\n## DTS Status\n\n")

                var exportPath: String? = null
                var exportSuccess = false

                if (hasDts) {
                    // Try to export DTS to accessible location
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val exportFilename =
                        "konabess_dts_" + deviceModel.replace(" ", "_") + "_" + timestamp + ".dts"
                    exportPath =
                        Environment.getExternalStorageDirectory().absolutePath + "/" + exportFilename

                    if (dtsPath != null) {
                        exportSuccess = RootHelper.copyFile(dtsPath!!, exportPath, "644")
                    }

                    if (exportSuccess) {
                        issueBody.append("✅ DTS file extracted successfully.\n\n")
                        issueBody.append("**File exported to:** `").append(exportPath).append("`\n\n")
                        issueBody.append("> Please attach the DTS file to this issue.\n\n")
                    } else {
                        issueBody.append("⚠️ DTS extraction completed but export failed.\n\n")
                        issueBody.append("> Please manually extract and attach your device's DTS file.\n\n")
                    }
                } else {
                    issueBody.append("❌ No DTS file available. Boot image extraction may have failed.\n\n")
                    issueBody.append("> Please try boot image extraction manually and attach the DTS.\n\n")
                }

                issueBody.append("## Additional Information\n\n")
                issueBody.append("<!-- Add any additional details about your device or GPU here -->\n\n")

                // Build GitHub issue URL
                val issueTitle = "[Device Support] $deviceBrand $deviceModel"
                val githubUrl = "https://github.com/KonaBess-Next/KonaBess-Next/issues/new" +
                        "?title=" + URLEncoder.encode(issueTitle, "UTF-8") +
                        "&body=" + URLEncoder.encode(issueBody.toString(), "UTF-8") +
                        "&labels=" + URLEncoder.encode("device-support", "UTF-8")

                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()

                    // Show confirmation dialog
                    val confirmDialog = MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.submit_dts_to_github)
                        .setPositiveButton(R.string.open_github) { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = Uri.parse(githubUrl)
                            startActivity(intent)
                        }
                        .setNegativeButton(R.string.cancel, null)

                    if (exportSuccess) {
                        confirmDialog.setMessage(getString(R.string.dts_exported_success, exportPath))
                    } else {
                        confirmDialog.setMessage(R.string.dts_export_manual_required)
                    }

                    confirmDialog.show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(activity, R.string.error_occur, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
