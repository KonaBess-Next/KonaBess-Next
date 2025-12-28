package com.ireddragonicy.konabessnext.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ireddragonicy.konabessnext.editor.core.CodeEditor;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import com.ireddragonicy.konabessnext.utils.LocaleUtil;
import com.ireddragonicy.konabessnext.viewmodel.RawDtsEditorViewModel;

import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.core.KonaBessCore;
import com.ireddragonicy.konabessnext.model.dts.DtsNode;
import com.ireddragonicy.konabessnext.ui.adapters.DtsTreeAdapter;
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper;
import com.ireddragonicy.konabessnext.utils.LocaleUtil;
import com.ireddragonicy.konabessnext.viewmodel.RawDtsEditorViewModel;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Raw DTS text editor with syntax highlighting and search navigation.
 */
public class RawDtsEditorActivity extends AppCompatActivity {

    // ViewModel for MVVM
    private RawDtsEditorViewModel viewModel;

    private CodeEditor editorContent;
    private LinearLayout loadingState;
    private TextView lineCountText;
    private MaterialToolbar toolbar;

    // Search bar views
    private LinearLayout searchBar;
    private EditText searchInput;
    private TextView searchResultCount;
    private ImageButton btnSearchPrev;
    private ImageButton btnSearchNext;
    private ImageButton btnSearchClose;

    // Visual Editor
    private RecyclerView visualRecycler;
    private DtsTreeAdapter visualAdapter;
    private boolean isVisualMode = false;
    private DtsNode currentRootNode;

    private boolean hasUnsavedChanges = false;

    // Search state
    private String currentSearchQuery = "";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleUtil.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply theme before setting content view
        applyColorPalette();

        setContentView(R.layout.activity_raw_dts_editor);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(RawDtsEditorViewModel.class);

        // Initialize views
        toolbar = findViewById(R.id.editor_toolbar);
        editorContent = findViewById(R.id.editor_content);
        loadingState = findViewById(R.id.editor_loading_state);
        lineCountText = findViewById(R.id.line_count_text);

        // Initialize Visual Editor
        visualRecycler = findViewById(R.id.visual_editor_recycler);
        visualRecycler.setLayoutManager(new LinearLayoutManager(this));
        visualAdapter = new DtsTreeAdapter();
        visualRecycler.setAdapter(visualAdapter);
        com.ireddragonicy.konabessnext.ui.adapters.StickyHeaderItemDecoration stickHeaderDecoration = new com.ireddragonicy.konabessnext.ui.adapters.StickyHeaderItemDecoration(
                visualAdapter);
        stickHeaderDecoration.attachToRecyclerView(visualRecycler);

        // Initialize search bar views
        searchBar = findViewById(R.id.search_bar);
        searchInput = findViewById(R.id.search_input);
        searchResultCount = findViewById(R.id.search_result_count);
        btnSearchPrev = findViewById(R.id.btn_search_prev);
        btnSearchNext = findViewById(R.id.btn_search_next);
        btnSearchClose = findViewById(R.id.btn_search_close);

        // Setup toolbar
        toolbar.setNavigationOnClickListener(v -> handleBackPress());
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        // Setup editor
        setupEditor();

        // Setup search bar
        setupSearchBar();

        // Setup back press handler
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        // Load file
        loadFile();
    }

    private void setupEditor() {
        editorContent.setOnTextChangedListener(() -> {
            hasUnsavedChanges = true;
            updateLineCount();
            updateMenuState();
        });
    }

    private void setupSearchBar() {
        // Search text change listener - search as you type
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                currentSearchQuery = s.toString();
                if (!currentSearchQuery.isEmpty()) {
                    // Reset search position and find first match
                    boolean found = false;
                    if (isVisualMode) {
                        found = visualAdapter.search(currentSearchQuery);
                    } else {
                        editorContent.clearSearch();
                        found = editorContent.searchAndSelect(currentSearchQuery);
                    }
                    updateSearchResultDisplay(found);
                } else {
                    if (isVisualMode) {
                        visualAdapter.clearSearch();
                    } else {
                        editorContent.clearSearch();
                    }
                    searchResultCount.setVisibility(View.GONE);
                }
            }
        });

        // Handle Enter key in search input
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchNext();
                return true;
            }
            return false;
        });

        // Search previous button
        btnSearchPrev.setOnClickListener(v -> searchPrevious());

        // Search next button
        btnSearchNext.setOnClickListener(v -> searchNext());

        // Close search bar
        btnSearchClose.setOnClickListener(v -> hideSearchBar());
    }

    private void showSearchBar() {
        searchBar.setVisibility(View.VISIBLE);
        searchInput.requestFocus();

        // Show keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        }

        // Pre-fill with previous search query
        if (!currentSearchQuery.isEmpty()) {
            searchInput.setText(currentSearchQuery);
            searchInput.selectAll();
        }
    }

    private void hideSearchBar() {
        searchBar.setVisibility(View.GONE);
        editorContent.clearSearch();
        if (visualAdapter != null) {
            visualAdapter.clearSearch();
        }
        searchResultCount.setVisibility(View.GONE);

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }

        // Return focus to editor
        editorContent.requestFocus();
    }

    private void searchNext() {
        if (!currentSearchQuery.isEmpty()) {
            boolean found = false;
            if (isVisualMode) {
                found = visualAdapter.nextSearchResult();
            } else {
                found = editorContent.searchAndSelect(currentSearchQuery);
            }
            updateSearchResultDisplay(found);
        }
    }

    private void searchPrevious() {
        if (!currentSearchQuery.isEmpty()) {
            boolean found = false;
            if (isVisualMode) {
                found = visualAdapter.previousSearchResult();
            } else {
                found = editorContent.searchPrevious(currentSearchQuery);
            }
            updateSearchResultDisplay(found);
        }
    }

    private void updateSearchResultDisplay(boolean found) {
        searchResultCount.setVisibility(View.VISIBLE);
        if (found) {
            searchResultCount.setText(null); // Clear - we don't have total count
            searchResultCount.setVisibility(View.GONE);
        } else {
            searchResultCount.setText(R.string.not_found);
        }
    }

    private void loadFile() {
        loadingState.setVisibility(View.VISIBLE);
        editorContent.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                StringBuilder content = new StringBuilder();
                File file = new File(KonaBessCore.dts_path);

                if (file.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    reader.close();
                }

                final String finalContent = content.toString();

                runOnUiThread(() -> {
                    editorContent.setText(finalContent);

                    updateLineCount();
                    updateMenuState();

                    loadingState.setVisibility(View.GONE);
                    editorContent.setVisibility(View.VISIBLE);

                    hasUnsavedChanges = false;
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    loadingState.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.error_occur, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void saveFile() {
        // Sync visual changes to text before saving
        if (isVisualMode) {
            syncVisualToText();
        }

        AlertDialog waitingDialog = new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.saving_file)
                .setCancelable(false)
                .create();
        waitingDialog.show();

        new Thread(() -> {
            boolean success = false;
            File tempFile = null;
            try {
                // Create a temporary file in the app's cache directory
                tempFile = new File(getCacheDir(), "dts_edit_temp_" + System.currentTimeMillis());
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

                String content = editorContent.getText().toString();
                writer.write(content);
                writer.close();

                // Use RootHelper to copy the temp file to the destination
                // relying on the shell to handle permissions
                String cmd = String.format("cat '%s' > '%s'", tempFile.getAbsolutePath(), KonaBessCore.dts_path);

                // Also ensure 644 permissions
                String chmodCmd = String.format("chmod 644 '%s'", KonaBessCore.dts_path);

                if (com.ireddragonicy.konabessnext.utils.RootHelper.execAndCheck(cmd) &&
                        com.ireddragonicy.konabessnext.utils.RootHelper.execAndCheck(chmodCmd)) {
                    success = true;
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Clean up temp file
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }

            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                waitingDialog.dismiss();
                if (finalSuccess) {
                    hasUnsavedChanges = false;
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void handleExport() {
        if (isVisualMode) {
            syncVisualToText();
        }

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("export_" + System.currentTimeMillis() + ".dts");
        input.selectAll();

        new MaterialAlertDialogBuilder(this)
                .setTitle("Export Raw DTS")
                .setMessage("Enter filename for export:")
                .setView(input)
                .setPositiveButton("Export", (dialog, which) -> {
                    String filename = input.getText().toString().trim();
                    if (!filename.isEmpty()) {
                        performExport(filename);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performExport(String filename) {
        if (!filename.endsWith(".dts")) {
            filename += ".dts";
        }

        final String finalFilename = filename;
        final String content = editorContent.getText().toString();

        new Thread(() -> {
            try {
                // Ensure export directory exists
                File exportDir = new File(getFilesDir(), "konabess_exports");
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }

                File exportFile = new File(exportDir, finalFilename);
                BufferedWriter writer = new BufferedWriter(new FileWriter(exportFile));
                writer.write(content);
                writer.close();

                // Add to history
                // We use ChipInfo.which.name() if available, or "Unknown"
                String chipType = "Unknown";
                if (com.ireddragonicy.konabessnext.core.ChipInfo.which != com.ireddragonicy.konabessnext.core.ChipInfo.type.unknown) {
                    chipType = com.ireddragonicy.konabessnext.core.ChipInfo.which.name();
                }

                com.ireddragonicy.konabessnext.utils.ExportHistoryManager historyManager = new com.ireddragonicy.konabessnext.utils.ExportHistoryManager(
                        this);
                historyManager.addExport(finalFilename, "Raw DTS Export", exportFile.getAbsolutePath(), chipType);

                runOnUiThread(
                        () -> Toast.makeText(this, "Exported to " + exportFile.getName(), Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(
                        () -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateLineCount() {
        int lineCount = editorContent.getLines().size();
        lineCountText.setText(getString(R.string.line_count, lineCount));
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save) {
            saveFile();
            return true;
        } else if (id == R.id.action_export) {
            handleExport();
            return true;
        } else if (id == R.id.action_undo) {
            performUndo();
            return true;
        } else if (id == R.id.action_redo) {
            performRedo();
            return true;
        } else if (id == R.id.action_search) {
            showSearchBar();
            return true;
        } else if (id == R.id.action_copy_all) {
            copyAllToClipboard();
            return true;
        } else if (id == R.id.action_toggle_view) {
            toggleViewMode(item);
            return true;
        }

        return false;
    }

    private void toggleViewMode(MenuItem item) {
        if (isVisualMode) {
            // Switch to Text Mode
            syncVisualToText();
            visualRecycler.setVisibility(View.GONE);
            editorContent.setVisibility(View.VISIBLE);
            item.setIcon(R.drawable.ic_drag_handle); // Set icon to "List" for next toggle
            item.setTitle(R.string.visual_view);
            isVisualMode = false;
        } else {
            // Switch to Visual Mode
            syncTextToVisual();
            editorContent.setVisibility(View.GONE);
            visualRecycler.setVisibility(View.VISIBLE);
            // Hide search bar if open
            if (searchBar.getVisibility() == View.VISIBLE)
                hideSearchBar();

            item.setIcon(R.drawable.ic_code); // Set icon to "Code" for next toggle
            item.setTitle(R.string.text_view); // Assuming you have a string resource or just hardcode for valid
            isVisualMode = true;
        }
    }

    private void syncTextToVisual() {
        try {
            String content = editorContent.getText().toString();
            currentRootNode = DtsTreeHelper.parse(content);
            visualAdapter.setRootNode(currentRootNode);
        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing DTS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            // Fallback to text mode if visual fails
            isVisualMode = false;
            editorContent.setVisibility(View.VISIBLE);
            visualRecycler.setVisibility(View.GONE);
            if (toolbar != null && toolbar.getMenu() != null) {
                MenuItem item = toolbar.getMenu().findItem(R.id.action_toggle_view);
                if (item != null) {
                    item.setIcon(R.drawable.ic_drag_handle);
                    item.setTitle(R.string.visual_view);
                }
            }
        }
    }

    private void syncVisualToText() {
        if (currentRootNode != null) {
            try {
                String content = DtsTreeHelper.generate(currentRootNode);
                editorContent.setText(content);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error generating DTS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performUndo() {
        if (editorContent.canUndo()) {
            editorContent.undo();
            updateMenuState();
        }
    }

    private void performRedo() {
        if (editorContent.canRedo()) {
            editorContent.redo();
            updateMenuState();
        }
    }

    private void copyAllToClipboard() {
        String content = editorContent.getText().toString();

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("DTS Content", content);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    private void updateMenuState() {
        if (toolbar == null || toolbar.getMenu() == null) {
            return;
        }

        MenuItem undoItem = toolbar.getMenu().findItem(R.id.action_undo);
        MenuItem redoItem = toolbar.getMenu().findItem(R.id.action_redo);

        if (undoItem != null) {
            boolean canUndo = editorContent.canUndo();
            undoItem.setEnabled(canUndo);
            undoItem.getIcon().setAlpha(canUndo ? 255 : 77);
        }

        if (redoItem != null) {
            boolean canRedo = editorContent.canRedo();
            redoItem.setEnabled(canRedo);
            redoItem.getIcon().setAlpha(canRedo ? 255 : 77);
        }
    }

    private void handleBackPress() {
        // Close search bar first if visible
        if (searchBar.getVisibility() == View.VISIBLE) {
            hideSearchBar();
            return;
        }

        if (hasUnsavedChanges) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.unsaved_changes_title)
                    .setMessage(R.string.unsaved_changes_msg)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        saveFile();
                        finish();
                    })
                    .setNegativeButton(R.string.discard, (dialog, which) -> finish())
                    .setNeutralButton(R.string.cancel, null)
                    .show();
        } else {
            finish();
        }
    }

    private void applyColorPalette() {
        SharedPreferences prefs = getSharedPreferences("KonaBessSettings", MODE_PRIVATE);
        int palette = prefs.getInt("color_palette", 0);

        switch (palette) {
            case 1:
                setTheme(R.style.Theme_KonaBess_Purple);
                break;
            case 2:
                setTheme(R.style.Theme_KonaBess_Blue);
                break;
            case 3:
                setTheme(R.style.Theme_KonaBess_Green);
                break;
            case 4:
                setTheme(R.style.Theme_KonaBess_Pink);
                break;
            case 5:
                setTheme(R.style.Theme_KonaBess_AMOLED);
                break;
            default:
                setTheme(R.style.Theme_KonaBess);
                break;
        }
    }
}
