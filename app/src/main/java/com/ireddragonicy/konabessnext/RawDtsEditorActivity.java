package com.ireddragonicy.konabessnext;

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

/**
 * Raw DTS text editor with syntax highlighting and search navigation.
 */
public class RawDtsEditorActivity extends AppCompatActivity {

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

        // Initialize views
        toolbar = findViewById(R.id.editor_toolbar);
        editorContent = findViewById(R.id.editor_content);
        loadingState = findViewById(R.id.editor_loading_state);
        lineCountText = findViewById(R.id.line_count_text);

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
                    editorContent.clearSearch();
                    boolean found = editorContent.searchAndSelect(currentSearchQuery);
                    updateSearchResultDisplay(found);
                } else {
                    editorContent.clearSearch();
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
            boolean found = editorContent.searchAndSelect(currentSearchQuery);
            updateSearchResultDisplay(found);
        }
    }

    private void searchPrevious() {
        if (!currentSearchQuery.isEmpty()) {
            boolean found = editorContent.searchPrevious(currentSearchQuery);
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
        AlertDialog waitingDialog = new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.saving_file)
                .setCancelable(false)
                .create();
        waitingDialog.show();

        new Thread(() -> {
            boolean success = false;
            try {
                File file = new File(KonaBessCore.dts_path);
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));

                String content = editorContent.getText().toString();
                writer.write(content);

                writer.close();
                success = true;

            } catch (Exception e) {
                e.printStackTrace();
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

    private void updateLineCount() {
        int lineCount = editorContent.getLines().size();
        lineCountText.setText(getString(R.string.line_count, lineCount));
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save) {
            saveFile();
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
        }

        return false;
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
