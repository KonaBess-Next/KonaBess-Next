package com.ireddragonicy.konabessnext.ui.compose.gpu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.gpu.FailureReason
import com.ireddragonicy.konabessnext.model.gpu.FreqPointResult
import com.ireddragonicy.konabessnext.model.gpu.GpuStabilityUiState
import com.ireddragonicy.konabessnext.model.gpu.StabilityStatus
import com.ireddragonicy.konabessnext.viewmodel.gpu.GpuStabilityViewModel

/**
 * Top-level screen for the GPU stability test. Hosts the per-point GLSurfaceView,
 * live chart, configuration card and pass/fail summary list.
 */
@Composable
fun GpuStabilityScreen(
    viewModel: GpuStabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showFreqPicker by remember { mutableStateOf(false) }
    val surfaceView = remember {
        GpuStressSurfaceView(context).apply {
            bindErrorCallback { code -> viewModel.onRenderError(code) }
        }
    }

    DisposableEffect(lifecycleOwner, surfaceView) {
        if (surfaceView == null) {
            return@DisposableEffect onDispose { }
        }
        surfaceView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> surfaceView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.status, surfaceView) {
        surfaceView?.setStressActive(state.status == StabilityStatus.Running)
        surfaceView?.keepScreenOn = state.status == StabilityStatus.Running
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 1. Header — pinned at the top.
        HeaderCard(
            state = state,
            onRefresh = viewModel::refreshFrequencies,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )

        // 2. Renderer (AndroidView). Lives outside the LazyColumn so that
        // changes in the LazyColumn's items (e.g. toggling between Pinning
        // and Freerun modes swaps in/out ConfigCard, FreerunNotice, etc.)
        // don't force the GpuStressSurfaceView to be detached and re-attached
        // to a different ViewGroup. Re-attaching a View that's already
        // attached throws IllegalStateException at the Android framework
        // level ("The specified child already has a parent"), which crashed
        // the app the moment the user toggled Root Mode in Settings after
        // a run finished.
        SurfaceViewCard(
            surfaceView = surfaceView,
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // 3. Scrollable middle: notice / config / chart / results.
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Freerun mode has no candidate frequency list, so the regular
            // pinning "no frequencies detected" empty state doesn't apply.
            // Show a dedicated notice explaining what freerun does instead.
            if (state.mode == com.ireddragonicy.konabessnext.model.gpu.GpuTestMode.Freerun) {
                if (state.status == StabilityStatus.Idle) {
                    item { FreerunNotice() }
                }
            } else if (state.activeFrequenciesHz.isEmpty() && state.status == StabilityStatus.Idle) {
                item { EmptyState(state = state) }
            }
            // Pinning-only config card (duration per point). Hidden in freerun
            // because there's no per-point cadence to configure.
            if (state.mode == com.ireddragonicy.konabessnext.model.gpu.GpuTestMode.Pinning &&
                state.status != StabilityStatus.Running
            ) {
                item {
                    ConfigCard(
                        state = state,
                        onDurationChanged = viewModel::setDurationPerPoint,
                        onSelectFrequenciesClick = { showFreqPicker = true },
                    )
                }
            }
            // The chart reads cur_freq and GPU temperature from sysfs, which
            // requires a working root shell. Hide the chart when no root is
            // available — the UI state flags both "root toggle off" and "app
            // has no root" as !isRootPinningCapable (see
            // GpuStabilityViewModel.refreshPinningCapabilityInternal).
            if (state.isRootPinningCapable) {
                item { ChartCard(state = state) }
            }
            // Results list only makes sense in pinning mode.
            if (state.mode == com.ireddragonicy.konabessnext.model.gpu.GpuTestMode.Pinning &&
                state.results.isNotEmpty()
            ) {
                item { ResultsList(state = state) }
            }
        }

        // 3. Action row — always visible below the renderer card.
        ActionRow(
            state = state,
            onStart = viewModel::startTest,
            onStop = viewModel::stopTest,
            onClear = viewModel::clearResults,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    // Show the results dialog only the *first time* a terminal status arrives
    // with non-empty results. After the user dismisses it (OK / outside tap),
    // the dialog stays hidden until the next test finishes — we don't clear
    // results, since "Clear Results" is the explicit affordance for that.
    //
    // Both the "should it show" and "has the user dismissed it" pieces of
    // state live in the ViewModel ([GpuStabilityUiState.showResultsDialog]),
    // NOT in `remember { ... }` slots. Composable-level state would be
    // silently reset whenever HorizontalPager recycles the GPU page out of
    // the composition (e.g. when the user opens the Settings tab), which
    // caused the dialog to re-pop after the page was scrolled back into
    // view — even though the user had already dismissed it.
    if (state.showResultsDialog) {
        ResultsDialog(
            state = state,
            onDismiss = { viewModel.dismissResultsDialog() },
        )
    }

    // Frequency-selection dialog. Only available in pinning mode and only
    // when there is at least one discovered frequency to choose from; the
    // button that opens it is hidden under the same conditions, so the
    // dialog itself can't be opened in an unsupported state. We render it
    // unconditionally here so the click handler can set showFreqPicker=true
    // without first having to re-check the gating conditions.
    if (showFreqPicker) {
        SelectFrequenciesDialog(
            state = state,
            onToggle = { freq ->
                val newSet = state.selectedFrequenciesHz.toMutableSet().apply {
                    if (!add(freq)) remove(freq)
                }
                viewModel.setSelectedFrequencies(newSet)
            },
            onSelectAll = viewModel::selectAllFrequencies,
            onDeselectAll = viewModel::deselectAllFrequencies,
            onConfirm = { showFreqPicker = false },
            onDismiss = { showFreqPicker = false },
        )
    }
}

@Composable
private fun HeaderCard(state: GpuStabilityUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.stability_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.stability_refresh))
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = headerStatusLine(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            if (state.status == StabilityStatus.Running && state.currentTargetHz != null) {
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(
                    progress = {
                        val ratio = state.elapsedSec.toFloat() /
                            state.durationPerPointSec.toFloat().coerceAtLeast(1f)
                        ratio.coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(
                        R.string.stability_running_fmt,
                        state.currentTargetHz / 1_000_000,
                        state.elapsedSec,
                        state.durationPerPointSec,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                val latestBusy = state.currentSamples.lastOrNull()?.gpuBusyPct
                val peakTemp = state.maxGpuTempC
                if (latestBusy != null || peakTemp != null) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = buildString {
                            if (latestBusy != null) {
                                append(stringResource(R.string.stability_running_busy_fmt, latestBusy))
                            }
                            if (latestBusy != null && peakTemp != null) {
                                append("  •  ")
                            }
                            if (peakTemp != null) {
                                append(stringResource(R.string.stability_running_temp_fmt, peakTemp))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun headerStatusLine(state: GpuStabilityUiState): String {
    return when (state.status) {
        StabilityStatus.Idle -> {
            if (state.mode == com.ireddragonicy.konabessnext.model.gpu.GpuTestMode.Freerun) {
                stringResource(R.string.stability_status_freerun_idle)
            } else {
                stringResource(R.string.stability_status_idle)
            }
        }
        StabilityStatus.Running -> {
            if (state.mode == com.ireddragonicy.konabessnext.model.gpu.GpuTestMode.Freerun) {
                // In freerun mode we don't lock to a specific MHz, just count
                // the samples we've collected.
                stringResource(R.string.stability_freerun_running_fmt, state.elapsedSec)
            } else {
                val cur = state.currentTargetHz?.let { "${it / 1_000_000} MHz" } ?: ""
                "$cur — ${state.completedPoints}/${state.totalPoints}"
            }
        }
        StabilityStatus.Completed -> stringResource(R.string.stability_status_completed)
        StabilityStatus.Failed -> state.failureReason?.localized()
            ?: state.failureMessage
            ?: stringResource(R.string.stability_status_failed)
        StabilityStatus.Aborted -> stringResource(R.string.stability_status_aborted)
    }
}

@Composable
private fun ConfigCard(
    state: GpuStabilityUiState,
    onDurationChanged: (Int) -> Unit,
    onSelectFrequenciesClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.stability_config),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.stability_duration_label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Stepper(
                    value = state.durationPerPointSec,
                    onChange = onDurationChanged,
                    step = 5,
                    min = 5,
                    max = 600,
                    suffix = "s",
                )
            }
            // Frequency picker. Only meaningful when at least one frequency
            // has been discovered; otherwise the button would just open a
            // empty dialog.
            if (state.activeFrequenciesHz.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.stability_select_freq_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onSelectFrequenciesClick,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.stability_select_freq_action))
                    }
                }
            }
            // Bottom-line copy mirrors the existing affordance ("XX frequency
            // points will be tested") but is now driven by the user's picker
            // selection instead of the raw active count. While the user has
            // never touched the picker (selectedFrequenciesHz empty), we
            // fall back to the full active count so the first-launch UX is
            // unchanged from the pre-feature behaviour.
            val pendingCount = if (state.selectedFrequenciesHz.isNotEmpty())
                state.selectedFrequenciesHz.size
            else
                state.activeFrequenciesHz.size
            Text(
                text = stringResource(R.string.stability_total_points_fmt, pendingCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SurfaceViewCard(
    surfaceView: GpuStressSurfaceView?,
    state: GpuStabilityUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
    ) {
        // The vsbm shader renders a square fractal scene. Force the surface
        // to a 1:1 aspect ratio (letterboxed horizontally if the card is
        // wider than tall) so the image is never stretched.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .heightIn(min = 180.dp, max = 360.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (surfaceView != null) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(1f),
                    factory = { surfaceView },
                )
            }
            if (surfaceView == null || state.status != StabilityStatus.Running) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = when (state.status) {
                            StabilityStatus.Idle -> stringResource(R.string.stability_idle_hint)
                            StabilityStatus.Completed -> stringResource(R.string.stability_summary_pass)
                            StabilityStatus.Failed -> state.failureReason?.localized()
                                ?: state.failureMessage
                                ?: stringResource(R.string.stability_failure_generic)
                            StabilityStatus.Aborted -> stringResource(R.string.stability_aborted)
                            else -> ""
                        },
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(state: GpuStabilityUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.stability_chart_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
            // Pre-resolve format strings inside the Composable scope so the formatters
            // passed down to the chart can be plain (Int) -> String lambdas.
            val freqAxisFmtTemplate = stringResource(R.string.stability_chart_axis_freq_fmt)
            val secondsAxisFmtTemplate = stringResource(R.string.stability_chart_axis_seconds_fmt)
            GpuStabilityChart(
                samples = state.currentSamples,
                // Frequency is drawn in blue, temperature in red — these are
                // deliberately fixed (not theme-derived) so the two series stay
                // high-contrast and visually distinct in light/dark themes.
                primaryColor = Color(0xFF2196F3), // blue — frequency
                secondaryColor = MaterialTheme.colorScheme.tertiary,
                accentColor = Color(0xFFE53935), // red — temperature
                surfaceColor = MaterialTheme.colorScheme.surface,
                onSurfaceColor = MaterialTheme.colorScheme.onSurface,
                gridColor = MaterialTheme.colorScheme.outline,
                // Pinning mode: lock axis to the candidate freq range.
                // Freerun mode: pass nulls so the chart auto-scales from
                // whatever samples actually arrive.
                minFreqHz = if (state.mode == com.ireddragonicy.konabessnext.model.gpu.GpuTestMode.Pinning)
                    state.activeFrequenciesHz.minOrNull() ?: 0L else null,
                maxFreqHz = if (state.mode == com.ireddragonicy.konabessnext.model.gpu.GpuTestMode.Pinning)
                    state.activeFrequenciesHz.maxOrNull() ?: 1500_000_000L else null,
                freqLegend = stringResource(R.string.stability_chart_legend_freq),
                tempLegend = stringResource(R.string.stability_chart_legend_temp),
                freqAxisFmt = { value -> freqAxisFmtTemplate.format(value) },
                secondsAxisFmt = { value -> secondsAxisFmtTemplate.format(value) },
            )
        }
    }
}

@Composable
private fun ActionRow(
    state: GpuStabilityUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val isRunning = state.status == StabilityStatus.Running
        Button(
            onClick = { if (isRunning) onStop() else onStart() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primary,
                contentColor = if (isRunning)
                    MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(if (isRunning) R.string.stability_stop else R.string.stability_start))
        }
        // "Clear Results" only makes sense in pinning mode (freerun has no
        // per-point results). Hide it to keep the action row uncluttered.
        if (state.mode == com.ireddragonicy.konabessnext.model.gpu.GpuTestMode.Pinning) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                enabled = state.status != StabilityStatus.Running,
            ) {
                Text(stringResource(R.string.stability_clear))
            }
        }
    }
}

@Composable
private fun ResultsList(state: GpuStabilityUiState) {
    if (state.results.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stability_results_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.size(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.results.forEach { result ->
                    ResultRow(result)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: FreqPointResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (result.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null,
            tint = if (result.passed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val mhz = result.targetFreqHz / 1_000_000
            Text(
                text = if (result.passed) {
                    stringResource(R.string.stability_pass_fmt, mhz, result.durationSec)
                } else {
                    // Translate the reason to a localized string, then build the
                    // per-point failure row. Falls back to the legacy
                    // "FAIL — X MHz (Y s): <reason>" format if no specific reason
                    // was attached (e.g. tests that don't set failureReason).
                    val reasonText = result.failureReason?.localized() ?: ""
                    stringResource(R.string.stability_fail_reason_fmt, mhz, reasonText)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ResultsDialog(state: GpuStabilityUiState, onDismiss: () -> Unit) {
    val passedCount = state.results.count { it.passed }
    val totalCount = state.results.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stability_summary_title)) },
        text = {
            Column {
                if (state.status == StabilityStatus.Completed) {
                    Text(stringResource(R.string.stability_summary_pass))
                } else {
                    val localized = state.failureReason?.localized()
                    Text(
                        text = localized
                            ?: state.failureMessage
                            ?: stringResource(R.string.stability_summary_fail_fmt, totalCount - passedCount, totalCount),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.stability_summary_passes_fmt, passedCount, totalCount))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}

/**
 * Multi-select dialog for choosing the subset of GPU frequency points to
 * sweep. Driven entirely by [state.activeFrequenciesHz]; the user's tick
 * state lives in [state.selectedFrequenciesHz].
 *
 * The "OK" button is greyed out unless at least one frequency is selected —
 * the same shape used by Material's confirmation-alert conventions.
 */
@Composable
private fun SelectFrequenciesDialog(
    state: GpuStabilityUiState,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canConfirm = state.selectedFrequenciesHz.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stability_select_freq_title)) },
        text = {
            // LazyColumn so 100+ frequency points don't trip
            // Compose's recomposition cost on every click.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                state = rememberLazyListState(),
            ) {
                items(items = state.activeFrequenciesHz, key = { it }) { freq ->
                    val checked = state.selectedFrequenciesHz.contains(freq)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(freq) }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggle(freq) },
                        )
                        Text(
                            text = "${freq / 1_000_000} MHz",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            // "Select all" / "Deselect all" sit in the dismiss slot so they
            // don't crowd the confirm area; tapping outside the dialog
            // behaves the same as Cancel.
            Row {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.stability_select_all))
                }
                TextButton(onClick = onDeselectAll) {
                    Text(stringResource(R.string.stability_deselect_all))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun FreerunNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.stability_freerun_notice_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                stringResource(R.string.stability_freerun_notice_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun EmptyState(state: GpuStabilityUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isRootAvailable.not()) {
                Text(
                    stringResource(R.string.stability_root_required),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                CircularProgressIndicator()
                Spacer(Modifier.size(8.dp))
                Text(
                    state.failureReason?.localized()
                        ?: state.failureMessage
                        ?: stringResource(R.string.stability_no_freq),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    step: Int,
    min: Int,
    max: Int,
    suffix: String = "",
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            shape = RoundedCornerShape(12.dp),
        ) { Text("-") }
        Text(
            text = "$value$suffix",
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(72.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            shape = RoundedCornerShape(12.dp),
        ) { Text("+") }
    }
}

/**
 * Localize a [FailureReason] using the current resource configuration.
 * Mirrors the [FailureReason] sealed-class cases; adding a new case will make
 * the `when` exhaustive-check fire at compile time as a reminder.
 */
@Composable
private fun FailureReason.localized(): String = when (this) {
    FailureReason.AbortedByUser -> stringResource(R.string.stability_failure_aborted_by_user)
    FailureReason.WriteFreqRejected -> stringResource(R.string.stability_failure_write_freq_rejected)
    is FailureReason.RendererGlError ->
        stringResource(R.string.stability_failure_renderer_gl_fmt, glErrorHex)
    is FailureReason.ThrottledBelow ->
        stringResource(R.string.stability_failure_throttled_below_fmt, throttleRatioPct)
    FailureReason.NoLoad -> stringResource(R.string.stability_failure_no_load)
    FailureReason.RootRequired -> stringResource(R.string.stability_failure_root_required)
    FailureReason.NoFrequencies -> stringResource(R.string.stability_failure_no_frequencies)
    FailureReason.DevfreqNodeMissing -> stringResource(R.string.stability_failure_devfreq_node_missing)
    FailureReason.Unknown -> stringResource(R.string.stability_failure_unknown)
}