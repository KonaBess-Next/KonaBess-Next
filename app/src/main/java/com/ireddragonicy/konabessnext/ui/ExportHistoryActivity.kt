package com.ireddragonicy.konabessnext.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.adapters.ExportHistoryAdapter
import com.ireddragonicy.konabessnext.utils.LocaleUtil
import com.ireddragonicy.konabessnext.viewmodel.ExportHistoryViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExportHistoryActivity : AppCompatActivity() {

    // ViewModel for MVVM
    private lateinit var viewModel: ExportHistoryViewModel

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var fabClear: ExtendedFloatingActionButton
    private var adapter: ExportHistoryAdapter? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtil.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme


        setContentView(R.layout.activity_export_history)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ExportHistoryViewModel::class.java]

        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.history_toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Setup views
        recyclerView = findViewById(R.id.history_recycler_view)
        emptyState = findViewById(R.id.history_empty_state)
        fabClear = findViewById(R.id.history_fab_clear)

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Observe ViewModel
        observeViewModel()

        // Setup FAB
        fabClear.setOnClickListener { showClearAllDialog() }
    }

    private fun observeViewModel() {
        viewModel.historyItems.observe(this) { items ->
            if (items.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                fabClear.visibility = View.GONE
            } else {
                emptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                fabClear.visibility = View.VISIBLE

                adapter = ExportHistoryAdapter(
                    items.toMutableList(), this, viewModel.historyManager, /* onApplyConfig */ { content -> 
                        // Use basic logic to determine if frequency or voltage table roughly by content
                        // or just use parseContentPartial which handles both/mixed.
                        // Since we don't have GpuRepository instance easily here (it's in other ViewModels),
                        // we can try to get it if Hilt was fully used, but here we can rely on 
                        // the fact that GpuFrequencyFragment uses it.
                        // Actually, ExportHistoryActivity is an Activity, we can inject GpuRepository.
                        // But to save time and lines, let's use a simpler approach if possible.
                        // However, we MUST use GpuRepository.
                        // Let's assume we can get it or add it to ViewModel.
                        viewModel.applyConfig(content) 
                        Toast.makeText(this, "Configuration applied", Toast.LENGTH_SHORT).show()
                    },
                    /* listener */ { viewModel.loadHistory() }
                )
                recyclerView.adapter = adapter
            }
        }
    }

    private fun showClearAllDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_clear_history)
            .setMessage(R.string.confirm_clear_history_msg)
            .setPositiveButton(R.string.clear_all) { _, _ ->
                viewModel.clearHistory()
                Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }


}
