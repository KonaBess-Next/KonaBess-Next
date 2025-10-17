package xzr.konabess;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.List;

import xzr.konabess.adapters.ExportHistoryAdapter;
import xzr.konabess.models.ExportHistoryItem;
import xzr.konabess.utils.ExportHistoryManager;
import xzr.konabess.utils.LocaleUtil;

public class ExportHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LinearLayout emptyState;
    private ExtendedFloatingActionButton fabClear;
    private ExportHistoryManager historyManager;
    private ExportHistoryAdapter adapter;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleUtil.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply theme
        applyColorPalette();
        
        setContentView(R.layout.activity_export_history);

        historyManager = new ExportHistoryManager(this);

        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.history_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup views
        recyclerView = findViewById(R.id.history_recycler_view);
        emptyState = findViewById(R.id.history_empty_state);
        fabClear = findViewById(R.id.history_fab_clear);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Load and display history
        loadHistory();

        // Setup FAB
        fabClear.setOnClickListener(v -> showClearAllDialog());
    }

    private void loadHistory() {
        List<ExportHistoryItem> historyItems = historyManager.getHistory();

        if (historyItems.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            fabClear.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            fabClear.setVisibility(View.VISIBLE);

            adapter = new ExportHistoryAdapter(historyItems, this, historyManager,
                    () -> loadHistory());
            recyclerView.setAdapter(adapter);
        }
    }

    private void showClearAllDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_clear_history)
                .setMessage(R.string.confirm_clear_history_msg)
                .setPositiveButton(R.string.clear_all, (dialog, which) -> {
                    historyManager.clearHistory();
                    Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
                    loadHistory();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyColorPalette() {
        android.content.SharedPreferences prefs = getSharedPreferences("KonaBessSettings", MODE_PRIVATE);
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
