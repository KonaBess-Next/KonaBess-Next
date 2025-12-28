package com.ireddragonicy.konabessnext.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.ireddragonicy.konabessnext.ui.adapters.ChipsetSelectorAdapter;
import com.ireddragonicy.konabessnext.ui.adapters.ParamAdapter;
import com.ireddragonicy.konabessnext.ui.adapters.ViewPagerAdapter;
import com.ireddragonicy.konabessnext.ui.fragments.GpuFrequencyFragment;
import com.ireddragonicy.konabessnext.ui.fragments.ImportExportFragment;
import com.ireddragonicy.konabessnext.ui.fragments.SettingsFragment;
import com.ireddragonicy.konabessnext.utils.DialogUtil;
import com.ireddragonicy.konabessnext.utils.LocaleUtil;
import com.ireddragonicy.konabessnext.viewmodel.DeviceViewModel;

import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.core.KonaBessCore;
import com.ireddragonicy.konabessnext.core.ChipInfo;
import com.ireddragonicy.konabessnext.core.GpuTableEditor;

import androidx.lifecycle.ViewModelProvider;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_LAST_GPU_TITLE = "key_last_gpu_toolbar_title";
    private static final String KEY_CURRENT_TITLE = "key_current_toolbar_title";

    androidx.appcompat.app.AlertDialog waiting;
    boolean cross_device_debug = false;
    private OnBackPressedCallback gpuTableEditorBackCallback;

    private final Object preparationLock = new Object();
    private final ArrayList<DevicePreparationListener> preparationListeners = new ArrayList<>();
    private boolean isPreparingDevice = false;

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private MaterialToolbar toolbar;
    private boolean isPageChangeFromUser = true;
    private String currentTitle;
    private String lastGpuToolbarTitle;
    private GpuFrequencyFragment gpuFrequencyFragment;

    // MVVM ViewModel
    private DeviceViewModel deviceViewModel;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleUtil.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply color palette theme BEFORE super.onCreate()
        applyColorPalette();
        super.onCreate(savedInstanceState);

        // Initialize MVVM ViewModel
        deviceViewModel = new ViewModelProvider(this).get(DeviceViewModel.class);

        try {
            setTitle(getString(R.string.app_name) + " " +
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        if (savedInstanceState != null) {
            lastGpuToolbarTitle = savedInstanceState.getString(KEY_LAST_GPU_TITLE);
            currentTitle = savedInstanceState.getString(KEY_CURRENT_TITLE);
        }

        if (!KonaBessCore.isPrepared()) {
            ChipInfo.which = ChipInfo.type.unknown;

            try {
                if (!cross_device_debug)
                    KonaBessCore.cleanEnv(this);
                KonaBessCore.setupEnv(this);
            } catch (Exception e) {
                DialogUtil.showError(this, R.string.environ_setup_failed);
                return;
            }
        }

        showMainView();
    }

    /**
     * Returns the callback used by GpuTableEditor to handle back navigation.
     * This allows GpuTableEditor to enable/disable the callback based on navigation
     * depth.
     */
    public OnBackPressedCallback getGpuTableEditorBackCallback() {
        return gpuTableEditorBackCallback;
    }

    private static Thread permission_worker;

    public static void runWithStoragePermission(Activity activity, Thread what) {
        MainActivity.permission_worker = what;

        // For Android 10 and above, we need to check API level
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): No need to request permission for app-specific
            // directory
            // or use MediaStore for shared storage
            what.start();
            permission_worker = null;
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10 (API 29): Check WRITE permission
            if (activity.checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[] {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, 0);
            } else {
                what.start();
                permission_worker = null;
            }
        } else {
            // Android 9 and below: Check WRITE permission
            if (activity.checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 0);
            } else {
                what.start();
                permission_worker = null;
            }
        }
    }

    public static class fileWorker extends Thread {
        public Uri uri;
    }

    private static fileWorker file_worker;

    public static void runWithFilePath(Activity activity, fileWorker what) {
        MainActivity.file_worker = what;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        activity.startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            file_worker.uri = data.getData();
            if (file_worker != null) {
                file_worker.start();
                file_worker = null;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (permission_worker != null) {
                permission_worker.start();
                permission_worker = null;
            }
        } else {
            Toast.makeText(this, R.string.storage_permission_failed, Toast.LENGTH_SHORT).show();
        }
    }

    LinearLayout mainView;
    LinearLayout showdView;

    void showMainView() {
        // Initialize OnBackPressedCallback for GpuTableEditor navigation
        // Starts disabled; GpuTableEditor enables it when navigating into sub-levels
        if (gpuTableEditorBackCallback == null) {
            gpuTableEditorBackCallback = new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    GpuTableEditor.handleBackNavigation();
                }
            };
            getOnBackPressedDispatcher().addCallback(this, gpuTableEditorBackCallback);
        } else {
            gpuTableEditorBackCallback.setEnabled(false);
        }
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        if (lastGpuToolbarTitle == null) {
            lastGpuToolbarTitle = getDefaultGpuToolbarTitle();
        }
        if (currentTitle == null) {
            currentTitle = lastGpuToolbarTitle;
        }
        updateToolbarTitle(currentTitle);
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_navigation);

        setupViewPager();
        setupBottomNavigation();
    }

    private void setupViewPager() {
        gpuFrequencyFragment = new GpuFrequencyFragment();

        ArrayList<Fragment> fragments = new ArrayList<>(Arrays.asList(
                gpuFrequencyFragment,
                new ImportExportFragment(),
                new SettingsFragment()));

        ViewPagerAdapter adapter = new ViewPagerAdapter(this, fragments);
        viewPager.setAdapter(adapter);

        // Sync ViewPager with BottomNavigation
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // Disable GpuTableEditor callback when changing pages
                if (gpuTableEditorBackCallback != null) {
                    gpuTableEditorBackCallback.setEnabled(false);
                }
                if (isPageChangeFromUser) {
                    switch (position) {
                        case 0:
                            restoreGpuToolbarTitle();
                            bottomNav.setSelectedItemId(R.id.nav_edit_freq);
                            break;
                        case 1:
                            updateToolbarTitle(getString(R.string.import_export));
                            bottomNav.setSelectedItemId(R.id.nav_import_export);
                            break;
                        case 2:
                            updateToolbarTitle(getString(R.string.settings));
                            bottomNav.setSelectedItemId(R.id.nav_settings);
                            break;
                    }
                }
            }
        });

        // Start with GPU Frequency section
        restoreGpuToolbarTitle();
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            isPageChangeFromUser = false;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_edit_freq) {
                viewPager.setCurrentItem(0, true);
                restoreGpuToolbarTitle();
            } else if (itemId == R.id.nav_import_export) {
                viewPager.setCurrentItem(1, true);
                updateToolbarTitle(getString(R.string.import_export));
            } else if (itemId == R.id.nav_settings) {
                viewPager.setCurrentItem(2, true);
                updateToolbarTitle(getString(R.string.settings));
            }
            isPageChangeFromUser = true;
            return true;
        });
    }

    private void hideMainView() {
        // Handled by back navigation now
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_LAST_GPU_TITLE, lastGpuToolbarTitle);
        outState.putString(KEY_CURRENT_TITLE, currentTitle);
    }

    public void updateToolbarTitle(String title) {
        currentTitle = title;
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }

    private String getDefaultGpuToolbarTitle() {
        return getString(R.string.edit_freq_table);
    }

    public void updateGpuToolbarTitle(String title) {
        lastGpuToolbarTitle = title;
        updateToolbarTitle(title);
    }

    public void restoreGpuToolbarTitle() {
        if (lastGpuToolbarTitle == null) {
            lastGpuToolbarTitle = getDefaultGpuToolbarTitle();
        }
        updateToolbarTitle(lastGpuToolbarTitle);
    }

    public void notifyGpuTableChanged() {
        runOnUiThread(() -> {
            if (gpuFrequencyFragment != null) {
                gpuFrequencyFragment.markDataDirty();
            }
        });
    }

    public String getCurrentToolbarTitle() {
        return currentTitle;
    }

    /**
     * Get the DeviceViewModel for fragment integration.
     * 
     * @return DeviceViewModel instance
     */
    public DeviceViewModel getDeviceViewModel() {
        return deviceViewModel;
    }

    public interface DevicePreparationListener {
        void onPrepared();

        void onFailed();

        void onSelectionRequired(int recommendedIndex);
    }

    public void ensureDevicePrepared(DevicePreparationListener listener) {
        if (KonaBessCore.isPrepared()) {
            if (listener != null) {
                runOnUiThread(listener::onPrepared);
            }
            return;
        }

        boolean shouldStart = false;
        synchronized (preparationLock) {
            if (listener != null) {
                preparationListeners.add(listener);
            }
            if (!isPreparingDevice) {
                isPreparingDevice = true;
                shouldStart = true;
            }
        }

        if (shouldStart) {
            new unpackLogic().start();
        }
    }

    public boolean isDevicePreparationRunning() {
        synchronized (preparationLock) {
            return isPreparingDevice;
        }
    }

    public void notifyPreparationSuccess() {
        ArrayList<DevicePreparationListener> listeners;
        synchronized (preparationLock) {
            listeners = new ArrayList<>(preparationListeners);
            preparationListeners.clear();
            isPreparingDevice = false;
        }
        for (DevicePreparationListener listener : listeners) {
            if (listener != null) {
                runOnUiThread(listener::onPrepared);
            }
        }
    }

    private void notifyPreparationFailed() {
        ArrayList<DevicePreparationListener> listeners;
        synchronized (preparationLock) {
            listeners = new ArrayList<>(preparationListeners);
            preparationListeners.clear();
            isPreparingDevice = false;
        }
        for (DevicePreparationListener listener : listeners) {
            if (listener != null) {
                runOnUiThread(listener::onFailed);
            }
        }
    }

    private void notifyPreparationSelection(int recommendedIndex) {
        ArrayList<DevicePreparationListener> listeners;
        // Don't clear listeners yet, as selection will proceed to prepared/failed later
        // synchronized (preparationLock) {
        // listeners = new ArrayList<>(preparationListeners);
        // }
        // Actually we should keep them?
        // Logic: Selection -> Selection UI -> User Click -> chooseTarget ->
        // notifySuccess.
        // So listeners must remain attached!
        synchronized (preparationLock) {
            listeners = new ArrayList<>(preparationListeners);
        }

        for (DevicePreparationListener listener : listeners) {
            if (listener != null) {
                runOnUiThread(() -> listener.onSelectionRequired(recommendedIndex));
            }
        }
    }

    public class backupBoot extends Thread {
        Activity activity;
        AlertDialog waiting;
        boolean is_err;

        public backupBoot(Activity activity) {
            this.activity = activity;
        }

        public void run() {
            is_err = false;
            runOnUiThread(() -> {
                waiting = DialogUtil.getWaitDialog(activity, R.string.backuping_img);
                waiting.show();
            });
            try {
                KonaBessCore.backupBootImage(activity);
            } catch (Exception e) {
                is_err = true;
            }
            runOnUiThread(() -> {
                waiting.dismiss();
                if (is_err)
                    DialogUtil.showError(activity, R.string.failed_backup);
                else {
                    // Add to Export History
                    try {
                        String destPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/"
                                + KonaBessCore.boot_name + ".img";
                        com.ireddragonicy.konabessnext.utils.ExportHistoryManager historyManager = new com.ireddragonicy.konabessnext.utils.ExportHistoryManager(
                                activity);

                        String chipType = "Unknown";
                        if (ChipInfo.which != ChipInfo.type.unknown) {
                            chipType = ChipInfo.which.name();
                        }

                        historyManager.addExport(KonaBessCore.boot_name + ".img", "Boot Image Backup", destPath,
                                chipType);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Toast.makeText(activity, R.string.backup_success, Toast.LENGTH_SHORT).show();
                }
            });

        }
    }

    public class repackLogic extends Thread {
        boolean is_err;
        String error = "";

        public void run() {
            is_err = false;
            {
                runOnUiThread(() -> {
                    waiting = DialogUtil.getWaitDialog(MainActivity.this, R.string.repacking);
                    waiting.show();
                });

                try {
                    KonaBessCore.dts2bootImage(MainActivity.this);
                } catch (Exception e) {
                    is_err = true;
                    error = e.getMessage();
                }
                runOnUiThread(() -> {
                    waiting.dismiss();
                    if (is_err)
                        DialogUtil.showDetailedError(MainActivity.this, R.string.repack_failed,
                                error);
                });
                if (is_err)
                    return;
            }

            if (!cross_device_debug) {
                runOnUiThread(() -> {
                    waiting = DialogUtil.getWaitDialog(MainActivity.this, R.string.flashing_boot);
                    waiting.show();
                });

                try {
                    KonaBessCore.writeBootImage(MainActivity.this);
                } catch (Exception e) {
                    is_err = true;
                }
                runOnUiThread(() -> {
                    waiting.dismiss();
                    if (is_err)
                        DialogUtil.showError(MainActivity.this, R.string.flashing_failed);
                    else {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                                .setTitle(R.string.reboot_complete_title)
                                .setMessage(R.string.reboot_complete_msg)
                                .setPositiveButton(R.string.yes, (dialog, which) -> {
                                    try {
                                        KonaBessCore.reboot();
                                    } catch (IOException e) {
                                        DialogUtil.showError(MainActivity.this,
                                                R.string.failed_reboot);
                                    }
                                })
                                .setNegativeButton(R.string.no, null)
                                .create().show();
                    }
                });
            }
        }
    }

    class unpackLogic extends Thread {
        String error = "";
        boolean is_err;
        int dtb_index;

        public void run() {
            // REMOVED: ProgressDialog usage
            // DialogUtil.ProgressDialogController progressDialog =
            // DialogUtil.createProgressDialog(MainActivity.this);
            // progressDialog.show(R.string.getting_image);

            is_err = false;
            try {
                if (!cross_device_debug) {
                    KonaBessCore.getBootImage(MainActivity.this);
                }
            } catch (Exception e) {
                is_err = true;
            }
            if (is_err) {
                // progressDialog.dismiss();
                MainActivity.this
                        .runOnUiThread(() -> DialogUtil.showError(MainActivity.this, R.string.failed_get_boot));
                notifyPreparationFailed();
                return;
            }

            // progressDialog.updateMessage(R.string.unpacking);
            is_err = false;
            try {
                KonaBessCore.bootImage2dts(MainActivity.this);
            } catch (Exception e) {
                is_err = true;
                error = e.getMessage();
            }
            if (is_err) {
                // progressDialog.dismiss();
                final String detail = error;
                MainActivity.this.runOnUiThread(
                        () -> DialogUtil.showDetailedError(MainActivity.this, R.string.unpack_failed, detail));
                notifyPreparationFailed();
                return;
            }

            // progressDialog.updateMessage(R.string.checking_device);
            is_err = false;
            try {
                KonaBessCore.checkDevice(MainActivity.this);
                dtb_index = KonaBessCore.getDtbIndex();
            } catch (Exception e) {
                is_err = true;
                error = e.getMessage();
            }
            if (is_err) {
                // progressDialog.dismiss();
                final String detail = error;
                MainActivity.this.runOnUiThread(() -> DialogUtil.showDetailedError(MainActivity.this,
                        R.string.failed_checking_platform, detail));
                notifyPreparationFailed();
                return;
            }

            // progressDialog.dismiss();

            MainActivity.this.runOnUiThread(() -> {
                if (KonaBessCore.dtbs.size() == 0) {
                    // DialogUtil.showError(MainActivity.this, R.string.incompatible_device);
                    notifyPreparationFailed();
                    return;
                }
                if (KonaBessCore.dtbs.size() == 1) {
                    KonaBessCore.chooseTarget(KonaBessCore.dtbs.get(0), MainActivity.this);
                    notifyPreparationSuccess();
                    return;
                }

                // Show modern chipset selector INLINE via fragment
                notifyPreparationSelection(dtb_index);
            });
        }
    }

    private void applyColorPalette() {
        SettingsActivity.applyThemeFromSettings(this);
    }

    // Removed: deprecated onBackPressedListener interface
    // Navigation is now handled via OnBackPressedDispatcher

}
