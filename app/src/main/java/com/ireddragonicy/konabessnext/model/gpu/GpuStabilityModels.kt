package com.ireddragonicy.konabessnext.model.gpu

/**
 * Status of the overall GPU stability test run.
 */
enum class StabilityStatus {
    Idle,
    Running,
    Completed,
    Failed,
    Aborted;

    val isTerminal: Boolean get() = this == Completed || this == Failed || this == Aborted
}

/**
 * One sample point recorded at ~1 Hz during a per-frequency stress test.
 *
 * @param timestampMs wall-clock time of the sample relative to the start of the current point.
 * @param targetFreqHz the locked frequency in Hz.
 * @param curFreqHz actual GPU clock as reported by sysfs.
 * @param gpuBusyPct the GPU utilization percentage, or null when unsupported.
 * @param temperatureC the SoC/GPU temperature in °C, or null when unavailable.
 * @param throttled true when curFreq dropped below the throttle threshold for the configured number of consecutive seconds.
 * @param renderErrorCode last glGetError() value, or null when no error.
 */
data class GpuSample(
    val timestampMs: Long,
    val targetFreqHz: Long,
    val curFreqHz: Long,
    val gpuBusyPct: Int?,
    val temperatureC: Float?,
    val throttled: Boolean,
    val renderErrorCode: Int? = null,
)

/**
 * Result of testing a single GPU frequency point.
 */
data class FreqPointResult(
    val targetFreqHz: Long,
    val passed: Boolean,
    val samples: List<GpuSample>,
    val failureReason: FailureReason? = null,
    val durationSec: Int,
)

sealed class FailureReason {
    /** User tapped Stop while a point was running. */
    object AbortedByUser : FailureReason()

    /** min_freq/max_freq writes were rejected (permissions, kernel locked, etc.). */
    object WriteFreqRejected : FailureReason()

    /** OpenGL renderer reported an error during the point. */
    data class RendererGlError(val glErrorHex: String) : FailureReason()

    /** GPU throttled below the configured threshold for too long. */
    data class ThrottledBelow(val throttleRatioPct: Int) : FailureReason()

    /** GPU load stayed at 0% for too long — stress failed to engage. */
    object NoLoad : FailureReason()

    /** Run-level: root mode is off, can't pin frequencies. */
    object RootRequired : FailureReason()

    /** Run-level: no candidate frequencies were discovered. */
    object NoFrequencies : FailureReason()

    /** Run-level: devfreq sysfs node could not be located on this device. */
    object DevfreqNodeMissing : FailureReason()

    /** Generic fallback — used when no more specific reason is known. */
    object Unknown : FailureReason()
}

/**
 * Which mode the GPU stability test is running in.
 *
 * - [Pinning] sweeps the active frequency list, locking min_freq==max_freq for a
 *   fixed duration per point. Requires root.
 * - [Freerun] samples the GPU continuously without locking anything. Doesn't
 *   require root; the test runs until the user explicitly stops it.
 */
enum class GpuTestMode { Pinning, Freerun }

/**
 * UI state exposed by [com.ireddragonicy.konabessnext.viewmodel.gpu.GpuStabilityViewModel].
 *
 * `isRootPinningCapable` is the derived capability flag — true only when both the
 * user's settings toggle (root mode) is on AND the device actually has root
 * access. When false, the UI should switch to freerun rendering and the
 * ViewModel should dispatch [com.ireddragonicy.konabessnext.repository.GpuStabilityRepository.runFreeStressTest]
 * instead of `runStabilityTest`.
 */
data class GpuStabilityUiState(
    val mode: GpuTestMode = GpuTestMode.Freerun,
    val isRootPinningCapable: Boolean = false,
    val activeFrequenciesHz: List<Long> = emptyList(),
    val runCandidateFrequenciesHz: List<Long> = emptyList(),
    /**
     * Subset of [activeFrequenciesHz] the user has chosen to actually test.
     * Empty set means "follow [activeFrequenciesHz] verbatim" (this is the
     * initial state right after a fresh frequency refresh — auto-populated
     * by the ViewModel so behaviour stays identical to the pre-existing
     * sweep-all flow until the user explicitly deselects something).
     */
    val selectedFrequenciesHz: Set<Long> = emptySet(),
    val status: StabilityStatus = StabilityStatus.Idle,
    val currentTargetHz: Long? = null,
    val elapsedSec: Int = 0,
    val durationPerPointSec: Int = 60,
    val currentSamples: List<GpuSample> = emptyList(),
    val results: List<FreqPointResult> = emptyList(),
    val failureReason: FailureReason? = null,
    /**
     * @deprecated Use [failureReason] for translatable failure causes. Kept as
     * an escape hatch for opaque errors coming from outside the stability flow
     * (e.g. raw [com.ireddragonicy.konabessnext.core.model.AppError] messages
     * produced during frequency discovery). The UI shows this verbatim when
     * [failureReason] is null.
     */
    @Deprecated("Use failureReason for translatable causes")
    val failureMessage: String? = null,
    val maxGpuTempC: Float? = null,
    val throttlingRatioPct: Int = 70,
    /**
     * The terminal status for which the results dialog was last presented.
     * The UI uses this together with [resultsDialogDismissed] to decide
     * whether to re-show the dialog. Both fields live in the ViewModel
     * state — not in `remember { ... }` slots — so they survive pager
     * page recycling and config changes (where Composable-level state
     * would be silently reset to its initial value).
     */
    val resultsDialogStatus: StabilityStatus? = null,
    val resultsDialogDismissed: Boolean = true,
) {
    val isRootAvailable: Boolean get() = isRootPinningCapable // back-compat alias
    /**
     * Total points the run will iterate over. Once the user has explicitly
     * touched the frequency-selector dialog we honour their selection;
     * before any user interaction we fall through to the full list so the
     * default behaviour (sweep-all) is unchanged.
     */
    val totalPoints: Int get() = if (selectedFrequenciesHz.isNotEmpty())
        selectedFrequenciesHz.size else activeFrequenciesHz.size
    val completedPoints: Int get() = results.size
    val progressRatio: Float
        get() = if (totalPoints == 0) 0f else completedPoints.toFloat() / totalPoints

    val lastResult: FreqPointResult? get() = results.lastOrNull()

    /** True when the results dialog should currently be on screen. */
    val showResultsDialog: Boolean
        get() = !resultsDialogDismissed &&
            status.isTerminal &&
            results.isNotEmpty() &&
            resultsDialogStatus == status
}