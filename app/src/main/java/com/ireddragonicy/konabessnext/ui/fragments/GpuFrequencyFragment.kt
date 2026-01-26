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
// import com.ireddragonicy.konabessnext.core.GpuTableEditor
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

@AndroidEntryPoint
class GpuFrequencyFragment : Fragment() {
    companion object {
        private const val CONTAINER_ID = 0x123456
    }
    private var contentContainer: LinearLayout? = null
    private var toolbarContainer: LinearLayout? = null
    private var statusContainer: android.widget.FrameLayout? = null
    private var fragmentContainer: android.widget.FrameLayout? = null
    // PreparationListener removed
    private var needsReload = false

    // MVVM ViewModels - shared with Activity
    private val deviceViewModel: DeviceViewModel by activityViewModels()
    private val gpuFrequencyViewModel: GpuFrequencyViewModel by activityViewModels()
    private val sharedViewModel: com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel by activityViewModels()
    
    // Inject ChipRepository if needed for setup definitions
    @javax.inject.Inject
    lateinit var chipRepository: com.ireddragonicy.konabessnext.repository.ChipRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure definitions are loaded
        chipRepository.loadDefinitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        contentContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Toolbar Container (Toolbar + Warnings)
        toolbarContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        contentContainer!!.addView(toolbarContainer)

        // Main Frame for switching between content and status (Loading/Error)
        val mainFrame = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1.0f
            )
        }
        contentContainer!!.addView(mainFrame)

        // Status Container (Loading, Error, Prompt)
        statusContainer = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        mainFrame.addView(statusContainer)

        // Fragment Container (The Editor) - Stable ID
        fragmentContainer = android.widget.FrameLayout(requireContext()).apply {
            id = CONTAINER_ID
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        mainFrame.addView(fragmentContainer)

        loadContent()
        return contentContainer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as MainActivity

        // Collect DeviceViewModel state
        lifecycleScope.launch {
            // Combine prepared state and chipset selection to trigger single data load
            launch {
                kotlinx.coroutines.flow.combine(
                    deviceViewModel.isPrepared,
                    deviceViewModel.selectedChipset
                ) { isPrepared, chipset -> isPrepared to chipset }
                .distinctUntilChanged()
                .collect { (isPrepared, chipset) ->
                    android.util.Log.d("KonaBessUI", "Observer: isPrepared=$isPrepared, chipset=${chipset?.id}, ViewModel=${System.identityHashCode(deviceViewModel)}")
                    if (isPrepared && chipset != null) {
                         android.util.Log.d("KonaBessUI", "Observer: Triggering loadData check")
                         val currentState = sharedViewModel.workbenchState.value
                         android.util.Log.d("KonaBessUI", "Observer: sharedViewModel state=$currentState")
                         if (currentState !is com.ireddragonicy.konabessnext.viewmodel.SharedGpuViewModel.WorkbenchState.Ready) {
                             android.util.Log.d("KonaBessUI", "Observer: Loading data...")
                             gpuFrequencyViewModel.resetSelection()
                             sharedViewModel.loadData(false)
                         } else {
                             android.util.Log.d("KonaBessUI", "Observer: Data already ready. Skipping load.")
                         }
                    }
                }
            }

            // Observe binsState to show GPU editor when data is loaded
            // Combine binsState and isPrepared to strictly control UI visibility
            launch {
                kotlinx.coroutines.flow.combine(
                    gpuFrequencyViewModel.binsState,
                    deviceViewModel.isPrepared
                ) { state, isPrepared -> state to isPrepared }
                .collect { (state, isPrepared) ->
                    // Only update UI if device is prepared
                    if (!isPrepared) return@collect

                    when (state) {
                        is UiState.Loading -> showLoadingState()
                        is UiState.Success -> showGpuEditor(activity)
                        is UiState.Error -> showErrorState(activity)
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
                        showErrorState(activity, state.message.asString(requireContext()))
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

            // Observe editor states
            launch {
                gpuFrequencyViewModel.isDirty.collect { isDirty ->
                    // UI observes this directly
                }
            }

            // Reactive UI refresh - single observer for all state changes
            launch {
                gpuFrequencyViewModel.stateVersion.collect { version ->
                    if (version > 0 && deviceViewModel.isPrepared.value) {
                        // UI Refresh handled by Compose state
                    }
                }
            }
            // scannerResults
            launch {
                deviceViewModel.scannerResults.collect { results ->
                    if (results.isNotEmpty()) {
                        showScanResultsDialog(activity, results)
                    }
                }
            }
        }
    }
    
    private fun refreshCurrentView() {
        if (!isAdded) return
        // No-op
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

    private fun setStatusView(view: View?) {
        statusContainer?.removeAllViews()
        if (view != null) {
            statusContainer?.addView(view)
            statusContainer?.visibility = View.VISIBLE
            fragmentContainer?.visibility = View.GONE
            toolbarContainer?.visibility = View.GONE
        } else {
            statusContainer?.visibility = View.GONE
        }
    }
    
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    private fun showGpuEditor(activity: MainActivity) {
        if (!isAdded) return

        // Clear previous toolbar/warnings
        toolbarContainer?.removeAllViews()
        toolbarContainer?.visibility = View.VISIBLE
        statusContainer?.visibility = View.GONE
        fragmentContainer?.visibility = View.VISIBLE

        // Compose Toolbar
        val toolbarComposeView = androidx.compose.ui.platform.ComposeView(requireContext())
        toolbarComposeView.setContent {
            val prefs = requireContext().getSharedPreferences(com.ireddragonicy.konabessnext.ui.SettingsActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val isDynamic = prefs.getBoolean(com.ireddragonicy.konabessnext.ui.SettingsActivity.KEY_DYNAMIC_COLOR, true)
            val paletteId = prefs.getInt(com.ireddragonicy.konabessnext.ui.SettingsActivity.KEY_COLOR_PALETTE, 0)
            com.ireddragonicy.konabessnext.ui.theme.KonaBessTheme(dynamicColor = isDynamic, colorPalette = paletteId) {
                val isDirty by gpuFrequencyViewModel.isDirty.collectAsState()
                val canUndo by gpuFrequencyViewModel.canUndo.collectAsState()
                val canRedo by gpuFrequencyViewModel.canRedo.collectAsState()
                val history by gpuFrequencyViewModel.history.collectAsState()
                val currentMode by sharedViewModel.viewMode.collectAsState()
                val currentChip by sharedViewModel.currentChip.collectAsState()

                var showSheet by remember { mutableStateOf(false) }
                var sheetType by remember { mutableStateOf("NONE") }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                if (showSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSheet = false },
                        sheetState = sheetState,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 32.dp)
                                .navigationBarsPadding()
                        ) {
                            if (sheetType == "HISTORY") {
                                Text("Edit History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(history) { item ->
                                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                            Text(text = item, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                if (history.isEmpty()) {
                                    Text("No history yet.", style = MaterialTheme.typography.bodyMedium)
                                }
                            } else if (sheetType == "CHIPSET") {
                                val detectionState by deviceViewModel.detectionState.collectAsState()
                                val selectedDtb by deviceViewModel.selectedChipset.collectAsState()
                                
                                Text("Select Data Source (DTS)", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                                Text(
                                    text = "Multi-DTS device detected. Please select which configuration to edit.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val dtbs = (detectionState as? UiState.Success)?.data ?: emptyList()
                                
                                if (detectionState is UiState.Loading) {
                                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else if (dtbs.isEmpty()) {
                                    // Trigger detection if list is missing and not loading
                                    LaunchedEffect(Unit) {
                                        deviceViewModel.detectChipset()
                                    }
                                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                        Text("Reading boot image...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(dtbs) { dtb ->
                                            val isSelected = dtb.id == selectedDtb?.id
                                            Card(
                                                onClick = { 
                                                    if (!isSelected) { gpuFrequencyViewModel.save(false); deviceViewModel.selectChipset(dtb) }
                                                    showSheet = false 
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                            ) {
                                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("DTS ${dtb.id}: ${dtb.type.name}", style = MaterialTheme.typography.titleMedium)
                                                        Text("ID: ${dtb.type.id}", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    if (isSelected) Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                        item {
                                            Card(onClick = { showSheet = false; showManualSetupDialog(requireActivity() as MainActivity) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Manual Setup / Deep Scan",
                                                            style = MaterialTheme.typography.titleMedium
                                                        )
                                                        Text(
                                                            text = "Configure unsupported device manually",
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    }
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_chevron_right),
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                com.ireddragonicy.konabessnext.ui.compose.GpuEditorToolbar(
                    isDirty = isDirty, canUndo = canUndo, canRedo = canRedo, historyCount = history.size, currentViewMode = currentMode,
                    showChipsetSelector = true, onSave = { gpuFrequencyViewModel.save(true) }, onUndo = { gpuFrequencyViewModel.undo() },
                    onRedo = { gpuFrequencyViewModel.redo() }, onShowHistory = { sheetType = "HISTORY"; showSheet = true },
                    onViewModeChanged = { mode -> updateViewMode(mode) }, onChipsetClick = { sheetType = "CHIPSET"; showSheet = true },
                    onFlashClick = { activity.startRepack() }
                )
            }
        }
        toolbarContainer!!.addView(toolbarComposeView)

        // Check for custom/unsupported definition (Added below toolbar)
        val currentChip = deviceViewModel.currentChipType
        if (currentChip != null && (currentChip.id.startsWith("custom_detected") || currentChip.id == "manual")) {
            val warningCard = MaterialCardView(requireContext())
            val warningParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            warningParams.setMargins(24, 12, 24, 12)
            warningCard.layoutParams = warningParams
            warningCard.setCardBackgroundColor(android.graphics.Color.parseColor("#44FF0000")) // Semi-transparent red
            warningCard.strokeColor = android.graphics.Color.RED
            warningCard.strokeWidth = 2
            warningCard.radius = 16f
            
            val warningText = TextView(requireContext())
            warningText.text = "WARNING: Your GPU is officially unsupported! You are using a custom/manual configuration. Proceed with caution."
            warningText.setTextColor(android.graphics.Color.RED)
            warningText.setPadding(32, 24, 32, 24)
            warningText.textSize = 14f
            warningText.typeface = android.graphics.Typeface.DEFAULT_BOLD
            
            warningCard.addView(warningText)
            toolbarContainer!!.addView(warningCard)
        }

        // Initialize Fragments if needed
        val fm = childFragmentManager
        val transaction = fm.beginTransaction()
        
        val TAG_GUI = "TAG_GUI"
        val TAG_TEXT = "TAG_TEXT"
        val TAG_TREE = "TAG_TREE"
        
        // We only add if they don't exist. If they exist (restored), we just show the right one.
        var guiFragment = fm.findFragmentByTag(TAG_GUI)
        var textFragment = fm.findFragmentByTag(TAG_TEXT)
        var treeFragment = fm.findFragmentByTag(TAG_TREE)
        
        if (guiFragment == null) {
            guiFragment = com.ireddragonicy.konabessnext.ui.fragments.GuiEditorFragment()
            transaction.add(CONTAINER_ID, guiFragment, TAG_GUI)
        }
        if (textFragment == null) {
            textFragment = com.ireddragonicy.konabessnext.ui.fragments.UnifiedRawDtsFragment.newInstance()
            transaction.add(CONTAINER_ID, textFragment, TAG_TEXT)
        }
        if (treeFragment == null) {
            treeFragment = com.ireddragonicy.konabessnext.ui.fragments.VisualTreeFragment.newInstance()
            transaction.add(CONTAINER_ID, treeFragment, TAG_TREE)
        }
        
        // Ensure state match
        // We'll let updateViewMode handle visibility logic, but here we commit additions
        if (!transaction.isEmpty) {
            transaction.commitNow()
        }
        
        // Trigger mode update to show correct one
        sharedViewModel.viewMode.value.let { updateViewMode(it) }
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
    
    /*
    private fun toggleSearch() {
        val fm = childFragmentManager
        val textFragment = fm.findFragmentByTag("TAG_TEXT") as? com.ireddragonicy.konabessnext.ui.fragments.UnifiedRawDtsFragment
        
        // Only toggle if Text Fragment is visible
        if (textFragment != null && textFragment.isVisible) {
             // textFragment.showSearchBar() // Method removed in Compose migration
        } else {
             Toast.makeText(requireContext(), "Search available in Text Mode", Toast.LENGTH_SHORT).show()
        }
    }
    */

    private fun showPromptState(activity: MainActivity) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        
        // Create a modern Material You card
        val card = MaterialCardView(requireContext()).apply {
            cardElevation = 12f
            radius = 28f
            val cardPadding = 56
            setContentPadding(cardPadding, cardPadding, cardPadding, cardPadding)
        }

        // Get theme color for card background
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true
        )
        card.setCardBackgroundColor(typedValue.data)

        val cardContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // Add icon
        val icon = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_developer_board)
            requireContext().theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, typedValue, true
            )
            setColorFilter(typedValue.data)
        }
        val iconParams = LinearLayout.LayoutParams(140, 140)
        iconParams.setMargins(0, 0, 0, 40)
        cardContent.addView(icon, iconParams)

        // Add title
        val title = TextView(requireContext()).apply {
            text = "Detect Chipset"
            textSize = 26f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            requireContext().theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true
            )
            setTextColor(typedValue.data)
        }
        val titleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleParams.setMargins(0, 0, 0, 20)
        cardContent.addView(title, titleParams)

        // Add description message
        val message = TextView(requireContext()).apply {
            setText(R.string.gpu_prep_prompt)
            textSize = 15f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            alpha = 0.85f
            requireContext().theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true
            )
            setTextColor(typedValue.data)
        }
        val messageParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        messageParams.setMargins(0, 0, 0, 40)
        cardContent.addView(message, messageParams)

        // Add Material You button
        val button = MaterialButton(requireContext()).apply {
            setText(R.string.gpu_prep_start)
            cornerRadius = 32
            elevation = 6f
            val buttonPadding = 24
            setPadding(buttonPadding * 2, buttonPadding, buttonPadding * 2, buttonPadding)
            textSize = 16f
            setOnClickListener { startPreparation(activity) }
        }
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
        container.addView(card, cardParams)
        
        setStatusView(container)
    }

    private fun showLoadingState() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        container.addView(ProgressBar(requireContext()))
        container.addView(TextView(requireContext()).apply {
            setText(R.string.gpu_prep_loading)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 24, 0, 0)
        })
        setStatusView(container)
    }

    private fun showErrorState(activity: MainActivity, customMessage: String? = null) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // Create a modern Material You error card
        val card = MaterialCardView(requireContext()).apply {
            cardElevation = 12f
            radius = 28f
            val cardPadding = 56
            setContentPadding(cardPadding, cardPadding, cardPadding, cardPadding)
        }

        // Get theme color for error card background
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorErrorContainer, typedValue, true
        )
        card.setCardBackgroundColor(typedValue.data)

        val cardContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // Add error icon
        val icon = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            requireContext().theme.resolveAttribute(
                com.google.android.material.R.attr.colorError, typedValue, true
            )
            setColorFilter(typedValue.data)
        }
        val iconParams = LinearLayout.LayoutParams(140, 140)
        iconParams.setMargins(0, 0, 0, 40)
        cardContent.addView(icon, iconParams)

        // Add title
        val title = TextView(requireContext()).apply {
            text = "Detection Failed"
            textSize = 26f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            requireContext().theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnErrorContainer, typedValue, true
            )
            setTextColor(typedValue.data)
        }
        val titleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleParams.setMargins(0, 0, 0, 20)
        cardContent.addView(title, titleParams)

        // Add error message
        val message = TextView(requireContext()).apply {
            text = customMessage ?: getString(R.string.gpu_prep_failed)
            textSize = 15f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            alpha = 0.85f
            requireContext().theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnErrorContainer, typedValue, true
            )
            setTextColor(typedValue.data)
        }
        val messageParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        messageParams.setMargins(0, 0, 0, 40)
        cardContent.addView(message, messageParams)

        // Add retry button
        val retryButton = MaterialButton(requireContext()).apply {
            setText(R.string.gpu_prep_retry)
            cornerRadius = 32
            elevation = 6f
            val buttonPadding = 24
            setPadding(buttonPadding * 2, buttonPadding, buttonPadding * 2, buttonPadding)
            textSize = 16f
            setOnClickListener { startPreparation(activity) }
        }
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        buttonParams.gravity = Gravity.CENTER_HORIZONTAL
        cardContent.addView(retryButton, buttonParams)

        // Add "Submit DTS to GitHub" button for unsupported devices
        val submitDtsButton = MaterialButton(
            requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            setText(R.string.submit_dts_to_github)
            cornerRadius = 32
            val buttonPadding = 24
            setPadding(buttonPadding * 2, buttonPadding, buttonPadding * 2, buttonPadding)
            textSize = 14f
            setIconResource(R.drawable.ic_share)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { submitDtsToGitHub(activity) }
        }
        val submitParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        submitParams.gravity = Gravity.CENTER_HORIZONTAL
        submitParams.setMargins(0, 24, 0, 0)
        cardContent.addView(submitDtsButton, submitParams)

        // Add "Deep Scan" button for unsupported devices
        val deepScanButton = MaterialButton(
            requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Deep Scan DTS Structure"
            cornerRadius = 32
            val buttonPadding = 24
            setPadding(buttonPadding * 2, buttonPadding, buttonPadding * 2, buttonPadding)
            textSize = 14f
            setIconResource(R.drawable.ic_search) // Ensure this icon exists or use generic
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { 
                deviceViewModel.performDeepScan() 
                Toast.makeText(context, "Scanning DTS files...", Toast.LENGTH_SHORT).show()
            }
        }
        val scanParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        scanParams.gravity = Gravity.CENTER_HORIZONTAL
        scanParams.setMargins(0, 24, 0, 0)
        cardContent.addView(deepScanButton, scanParams)

        // Add "Manual Setup" button
        val manualSetupButton = MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Manual Setup"
            textSize = 14f
            setOnClickListener {
                showManualSetupDialog(activity)
            }
        }
        val manualParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        manualParams.gravity = Gravity.CENTER_HORIZONTAL
        manualParams.setMargins(0, 8, 0, 0)
        cardContent.addView(manualSetupButton, manualParams)

        card.addView(cardContent)

        // Add card to container with margin
        val cardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(40, 0, 40, 0)
        contentContainer!!.addView(card, cardParams)
    }

    private fun showScanResultsDialog(activity: MainActivity, results: List<com.ireddragonicy.konabessnext.core.scanner.DtsScanResult>) {
        if (results.size == 1) {
            val result = results.first()
            showSingleResultDialog(activity, result)
        } else {
            // Show selection list
            val items = results.map { "DTB #${it.dtbIndex}: ${it.recommendedStrategy} (${it.maxLevels} Lvls)" }.toTypedArray()
            var selectedIndex = 0
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Candidate")
                .setSingleChoiceItems(items, 0) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton("Next") { _, _ ->
                    showSingleResultDialog(activity, results[selectedIndex])
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showSingleResultDialog(activity: MainActivity, result: com.ireddragonicy.konabessnext.core.scanner.DtsScanResult) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Structure Found")
            .setMessage("Candidate Found in DTB #${result.dtbIndex}.\n\nStrategy: ${result.recommendedStrategy}\nLevels: ${result.maxLevels}\nTable: ${result.voltageTablePattern ?: "None"}\n\nWould you like to try applying this?")
            .setPositiveButton("Apply & Test") { _, _ ->
                deviceViewModel.applyCustomDefinition(result)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Manual Edit") { _, _ ->
                 showManualSetupDialog(activity, result)
            }
            .show()
    }

    private fun showManualSetupDialog(activity: MainActivity, baseResult: com.ireddragonicy.konabessnext.core.scanner.DtsScanResult? = null) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        // Deep Scan Button
        val deepScanBtn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
             text = "Scan for Structure (Deep Scan)"
             setIconResource(R.drawable.ic_search)
             setOnClickListener {
                 // Close manual dialog if possible? The dialog is built later.
                 // We can't easily dismiss the dialog from here unless we save a reference.
                 // Actually, showManualSetupDialog builds a Dialog but doesn't return it easily to dismiss.
                 // But we can trigger the scan. The scan results dialog will pop up over this one or we can find a way to dismiss.
                 // For now, let's trigger scan and toast.
                 deviceViewModel.performDeepScan()
                 Toast.makeText(context, "Deep Scan started...", Toast.LENGTH_SHORT).show()
                 // Ideally we should close this dialog.
             }
        }
        val scanParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scanParams.setMargins(0, 0, 0, 24)
        layout.addView(deepScanBtn, scanParams)

        // Divider
        val divider = View(context).apply { setBackgroundColor(android.graphics.Color.GRAY); alpha = 0.2f }
        layout.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 16, 0, 24) })

        // Strategy Inputs
        val strategyGroup = android.widget.RadioGroup(context).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
        }
        val radioMulti = android.widget.RadioButton(context).apply { text = "MULTI_BIN"; id = View.generateViewId() }
        val radioSingle = android.widget.RadioButton(context).apply { text = "SINGLE_BIN"; id = View.generateViewId() }
        strategyGroup.addView(radioMulti)
        strategyGroup.addView(radioSingle)
        
        if (baseResult?.recommendedStrategy == "SINGLE_BIN") radioSingle.isChecked = true else radioMulti.isChecked = true

        layout.addView(TextView(context).apply { text = "Strategy Type" })
        layout.addView(strategyGroup)

        // DTB Index Input
        val dtbInput = com.google.android.material.textfield.TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((baseResult?.dtbIndex ?: 0).toString())
            hint = "DTB Index (0, 1, ...)"
        }
        layout.addView(createInputLayout(context, "DTB Index", dtbInput))

        // Max Levels Input
        val maxLevelsInput = com.google.android.material.textfield.TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((baseResult?.maxLevels ?: 11).toString())
            hint = "Max Table Levels"
        }
        layout.addView(createInputLayout(context, "Max Levels", maxLevelsInput))

        // Volt Table Pattern Input
        val patternInput = com.google.android.material.textfield.TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText(baseResult?.voltageTablePattern ?: "gpu-opp-table")
            hint = "Volt Table Pattern"
        }
        layout.addView(createInputLayout(context, "Volt Table Pattern", patternInput))

        // Ignore Volt Table Checkbox
        val ignoreVoltCheckbox = android.widget.CheckBox(context).apply {
            text = "Ignore Volt Table"
            isChecked = baseResult?.voltageTablePattern == null
        }
        layout.addView(ignoreVoltCheckbox)

        MaterialAlertDialogBuilder(context)
            .setTitle("Manual Configuration")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                try {
                    val strategy = if (radioSingle.isChecked) "SINGLE_BIN" else "MULTI_BIN"
                    val dtbIndex = dtbInput.text.toString().toIntOrNull() ?: 0
                    val maxLevels = maxLevelsInput.text.toString().toIntOrNull() ?: 11
                    val pattern = patternInput.text.toString().takeIf { it.isNotBlank() }
                    val ignoreVolt = ignoreVoltCheckbox.isChecked

                    // Construct result manually
                    val manualResult = com.ireddragonicy.konabessnext.core.scanner.DtsScanResult(
                        isValid = true,
                        dtbIndex = dtbIndex,
                        recommendedStrategy = strategy,
                        voltageTablePattern = if (ignoreVolt) null else pattern,
                        maxLevels = maxLevels,
                        levelCount = 416, // Default
                        confidence = "Manual"
                    )
                    
                    deviceViewModel.applyCustomDefinition(manualResult)
                    
                } catch (e: Exception) {
                    Toast.makeText(context, "Invalid Input: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createInputLayout(context: android.content.Context, label: String, editText: android.view.View): com.google.android.material.textfield.TextInputLayout {
        val til = com.google.android.material.textfield.TextInputLayout(context, null, com.google.android.material.R.attr.textInputStyle)
        til.hint = label
        til.addView(editText)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 16, 0, 0)
        til.layoutParams = params
        return til
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
        sharedViewModel.loadData(true)
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
                        exportSuccess = RootHelper.copyFile(dtsPath, exportPath, "644")
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
