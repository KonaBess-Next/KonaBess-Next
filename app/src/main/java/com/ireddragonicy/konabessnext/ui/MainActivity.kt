package com.ireddragonicy.konabessnext.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.GpuTableEditor
import com.ireddragonicy.konabessnext.ui.adapters.ViewPagerAdapter
import com.ireddragonicy.konabessnext.ui.fragments.GpuFrequencyFragment
import com.ireddragonicy.konabessnext.ui.fragments.ImportExportFragment
import com.ireddragonicy.konabessnext.ui.fragments.SettingsFragment
import com.ireddragonicy.konabessnext.utils.LocaleUtil
import com.ireddragonicy.konabessnext.viewmodel.DeviceViewModel
import com.ireddragonicy.konabessnext.viewmodel.UiState
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList
import java.util.Arrays

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        private const val KEY_LAST_GPU_TITLE = "key_last_gpu_toolbar_title"
        private const val KEY_CURRENT_TITLE = "key_current_toolbar_title"
    }

    private var gpuTableEditorBackCallback: OnBackPressedCallback? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private var toolbar: MaterialToolbar? = null
    private var isPageChangeFromUser = true
    private var currentTitle: String? = null
    private var lastGpuToolbarTitle: String? = null
    private var gpuFrequencyFragment: GpuFrequencyFragment? = null

    // MVVM ViewModel
    val deviceViewModel: DeviceViewModel by viewModels()

    // Permission and File Result Launchers
    private var pendingPermissionAction: (() -> Unit)? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            pendingPermissionAction?.invoke()
        } else {
            Toast.makeText(this, R.string.storage_permission_failed, Toast.LENGTH_SHORT).show()
        }
        pendingPermissionAction = null
    }

    private var pendingFileAction: ((Intent?) -> Unit)? = null
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingFileAction?.invoke(result.data)
        }
        pendingFileAction = null
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtil.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply color palette theme BEFORE super.onCreate()
        applyColorPalette()
        super.onCreate(savedInstanceState)

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            title = "${getString(R.string.app_name)} ${pInfo.versionName}"
        } catch (ignored: PackageManager.NameNotFoundException) {
        }

        if (savedInstanceState != null) {
            lastGpuToolbarTitle = savedInstanceState.getString(KEY_LAST_GPU_TITLE)
            currentTitle = savedInstanceState.getString(KEY_CURRENT_TITLE)
        }

        // Check if device is prepared
        // We observe logic in UI, but initial check trigger is fine here
        if (!deviceViewModel.isPrepared.value) { // Accessing StateFlow value directly for initial check
            deviceViewModel.detectChipset()
        }

        showMainView()

        // Observe repack state
        observeRepackState()
    }

    private fun observeRepackState() {
        // Collect StateFlow in lifecycle scope
        // For simplicity using LiveData observation pattern on StateFlow via generic approach or just standard flow collection
        // Since we are in Activity, let's use simple observation helper or launch
        lifecycleScope.launchWhenStarted {
            deviceViewModel.repackState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        // Show loading dialog if not already shown?
                        // Or GpuActionToolbar handles it? 
                        // Actually, packAndFlash runs in background. 
                        // Ideally we show a non-cancellable dialog here.
                        showRepackLoading()
                    }
                    is UiState.Success -> {
                        hideRepackLoading()
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(R.string.success)
                            .setMessage(state.data) // "Repack and Flash successful..."
                            .setPositiveButton(R.string.reboot) { _, _ -> deviceViewModel.reboot() }
                            .setNegativeButton(R.string.ok, null)
                            .show()
                    }
                    is UiState.Error -> {
                        hideRepackLoading()
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(R.string.error)
                            .setMessage(state.message)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                    null -> {} // Idle
                }
            }
        }
    }

    private var repackDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showRepackLoading() {
        if (repackDialog == null) {
            repackDialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.processing)
                .setMessage("Repacking and Flashing...")
                .setCancelable(false)
                .create()
        }
        repackDialog?.show()
    }

    private fun hideRepackLoading() {
        repackDialog?.dismiss()
    }

    fun startRepack() {
        deviceViewModel.packAndFlash(this)
    }

    fun openRawDtsEditor() {
        val intent = Intent(this, RawDtsEditorActivity::class.java)
        startActivity(intent)
    }

    fun openCurveEditor(binId: Int) {
        val fragment = com.ireddragonicy.konabessnext.ui.fragments.GpuCurveEditorFragment()
        val args = Bundle()
        args.putInt("binId", binId)
        fragment.arguments = args

        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack("curve_editor")
            .commit()
    }

    fun runWithStoragePermission(action: () -> Unit) {
        pendingPermissionAction = action

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            action()
            pendingPermissionAction = null
        } else {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            val needed = permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (needed) {
                requestPermissionLauncher.launch(permissions)
            } else {
                action()
                pendingPermissionAction = null
            }
        }
    }

    fun runWithFilePath(callback: (Intent?) -> Unit) {
        pendingFileAction = callback
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        filePickerLauncher.launch(intent)
    }

    private fun showMainView() {
        // Initialize OnBackPressedCallback for GpuTableEditor navigation
        if (gpuTableEditorBackCallback == null) {
            gpuTableEditorBackCallback = object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    GpuTableEditor.handleBackNavigation()
                }
            }
            onBackPressedDispatcher.addCallback(this, gpuTableEditorBackCallback!!)
        } else {
            gpuTableEditorBackCallback?.isEnabled = false
        }

        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        
        if (lastGpuToolbarTitle == null) {
            lastGpuToolbarTitle = defaultGpuToolbarTitle
        }
        if (currentTitle == null) {
            currentTitle = lastGpuToolbarTitle
        }
        updateToolbarTitle(currentTitle)

        viewPager = findViewById(R.id.view_pager)
        bottomNav = findViewById(R.id.bottom_navigation)

        setupViewPager()
        setupBottomNavigation()
    }

    private fun setupViewPager() {
        gpuFrequencyFragment = GpuFrequencyFragment()

        val fragments = ArrayList<Fragment>(
            Arrays.asList(
                gpuFrequencyFragment,
                ImportExportFragment(),
                SettingsFragment()
            )
        )

        val adapter = ViewPagerAdapter(this, fragments)
        viewPager.adapter = adapter

        // Sync ViewPager with BottomNavigation
        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Disable GpuTableEditor callback when changing pages
                gpuTableEditorBackCallback?.isEnabled = false

                if (isPageChangeFromUser) {
                    when (position) {
                        0 -> {
                            restoreGpuToolbarTitle()
                            bottomNav.selectedItemId = R.id.nav_edit_freq
                        }
                        1 -> {
                            updateToolbarTitle(getString(R.string.import_export))
                            bottomNav.selectedItemId = R.id.nav_import_export
                        }
                        2 -> {
                            updateToolbarTitle(getString(R.string.settings))
                            bottomNav.selectedItemId = R.id.nav_settings
                        }
                    }
                }
            }
        })

        // Start with GPU Frequency section
        restoreGpuToolbarTitle()
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            isPageChangeFromUser = false
            val itemId = item.itemId
            if (itemId == R.id.nav_edit_freq) {
                viewPager.setCurrentItem(0, true)
                restoreGpuToolbarTitle()
            } else if (itemId == R.id.nav_import_export) {
                viewPager.setCurrentItem(1, true)
                updateToolbarTitle(getString(R.string.import_export))
            } else if (itemId == R.id.nav_settings) {
                viewPager.setCurrentItem(2, true)
                updateToolbarTitle(getString(R.string.settings))
            }
            isPageChangeFromUser = true
            true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LAST_GPU_TITLE, lastGpuToolbarTitle)
        outState.putString(KEY_CURRENT_TITLE, currentTitle)
    }

    fun updateToolbarTitle(title: String?) {
        currentTitle = title
        if (toolbar != null) {
            toolbar?.title = title
        }
    }

    private val defaultGpuToolbarTitle: String
        get() = getString(R.string.edit_freq_table)

    fun updateGpuToolbarTitle(title: String) {
        lastGpuToolbarTitle = title
        updateToolbarTitle(title)
    }

    fun restoreGpuToolbarTitle() {
        if (lastGpuToolbarTitle == null) {
            lastGpuToolbarTitle = defaultGpuToolbarTitle
        }
        updateToolbarTitle(lastGpuToolbarTitle)
    }

    fun notifyGpuTableChanged() {
        runOnUiThread {
            gpuFrequencyFragment?.markDataDirty()
        }
    }

    fun notifyPreparationSuccess() {
        notifyGpuTableChanged()
    }

    val currentToolbarTitle: String?
        get() = currentTitle

    /**
     * Returns the callback used by GpuTableEditor to handle back navigation.
     */
    fun getGpuTableEditorBackCallback(): OnBackPressedCallback? {
        return gpuTableEditorBackCallback
    }

    private fun applyColorPalette() {
        SettingsActivity.applyThemeFromSettings(this)
    }
}
