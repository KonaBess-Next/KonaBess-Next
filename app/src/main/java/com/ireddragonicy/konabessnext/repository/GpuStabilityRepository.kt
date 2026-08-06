package com.ireddragonicy.konabessnext.repository

import android.util.Log
import com.ireddragonicy.konabessnext.core.model.AppError
import com.ireddragonicy.konabessnext.core.model.DomainResult
import com.ireddragonicy.konabessnext.model.gpu.FailureReason
import com.ireddragonicy.konabessnext.model.gpu.FreqPointResult
import com.ireddragonicy.konabessnext.model.gpu.GpuSample
import com.ireddragonicy.konabessnext.model.gpu.GpuStabilityUiState
import com.ireddragonicy.konabessnext.model.gpu.StabilityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the runtime side of the GPU stability test:
 *  - reads the kernel-reported GPU frequency list (used as the candidate set);
 *  - pins the GPU to a single clock by writing min_freq == max_freq under root;
 *  - samples cur_freq / busy / temperature at ~1 Hz while the renderer is active;
 *  - restores the original governor/min/max on completion, abort or failure.
 *
 * Two modes are supported:
 *  - `runStabilityTest(state)` — root-required per-frequency sweep with locking.
 *  - `runFreeStressTest(state)` — no locking, no per-point cadence; samples
 *    cur_freq / busy / temp at ~1 Hz until the caller invokes [cancelTest].
 *
 * Both modes share the same underlying sample loop and [TestEvent] surface so
 * the UI layer can drive either mode through the same `events` flow.
 */
@Singleton
class GpuStabilityRepository @Inject constructor(
    private val shellExecutor: ShellExecutor,
) {

    companion object {
        private const val TAG = "GpuStabilityRepo"

        /** sysfs paths probed for the Adreno devfreq node. Mirrors [DeviceRepository.getRunTimeGpuFrequencies]. */
        val PROBE_COMMANDS: List<String> = listOf(
            "cat /sys/class/kgsl/kgsl-3d0/frequencies",
            "cat /sys/class/kgsl/kgsl-3d0/available_frequencies",
            "cat /sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "cat /sys/devices/platform/soc/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/frequencies",
            "cat /sys/devices/platform/soc/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/available_frequencies",
            "cat /sys/devices/platform/soc@0/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/frequencies",
            "cat /sys/devices/platform/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/frequencies",
        )

        /** sysfs paths for the per-frequency governor / min / max knobs. */
        val GOV_NODE_CANDIDATES: List<String> = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq",
            "/sys/devices/platform/soc/soc:qcom,kgsl-3d0/devfreq",
            "/sys/devices/platform/soc/*.qcom,kgsl-3d0/devfreq",
        )

        /** Cat paths used to read GPU samples. We try a list of candidate paths
         *  because different kernels expose them in slightly different places. */
        val CUR_FREQ_PATHS: List<String> = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/clock",
            "/sys/class/kgsl/kgsl-3d0/devfreq/curr_freq",
        )
        val BUSY_PATHS: List<String> = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/busy",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy",
            "/sys/class/kgsl/kgsl-3d0/devfreq/busy",
            "/sys/devices/platform/soc/soc:qcom,kgsl-3d0/devfreq/busy",
            "/sys/devices/platform/soc/*.qcom,kgsl-3d0/devfreq/busy",
            "/sys/class/devfreq/*/busy",
        )
        val TEMP_FALLBACK_PATH = "/sys/class/kgsl/kgsl-3d0/temp"
        val THERMAL_ZONE_CMDS: List<String> = listOf(
            "cat /sys/class/thermal/thermal_zone0/temp",
            "cat /sys/class/thermal/thermal_zone1/temp",
            "cat /sys/class/thermal/thermal_zone2/temp",
        )

        /** Consecutive seconds the GPU has to remain under the threshold before declaring a throttle failure. */
        const val DEFAULT_THROTTLE_WINDOW_SEC = 3

        /** Consecutive seconds with gpu_busy == 0 before declaring that the stress failed to engage. */
        const val DEFAULT_NO_LOAD_WINDOW_SEC = 5
    }

    /** Snapshot of the values we overwrote, used to restore on stop. */
    private data class OriginalLimits(
        val governor: String?,
        val minFreqHz: Long?,
        val maxFreqHz: Long?,
        val nodeDir: String?,
    )

    private val _events = MutableSharedFlow<TestEvent>(extraBufferCapacity = 16)
    private val _running = MutableStateFlow(false)
    private var currentJob: Job? = null
    private var lastSeenRenderError: Int? = null
    private var originalLimits: OriginalLimits? = null

    @Volatile private var resolvedCurFreqPath: String? = null
    @Volatile private var resolvedBusyPath: String? = null
    @Volatile private var resolvedTempPath: String? = null
    @Volatile private var resolvedTempCommand: String? = null

    val events: Flow<TestEvent> = _events.asSharedFlow()
    val isRunning: Boolean get() = _running.value

    /**
     * Read the active GPU frequency list. Reuses the same probe fallback chain as
     * [DeviceRepository.getRunTimeGpuFrequencies].
     */
    suspend fun loadActiveFrequencies(): DomainResult<List<Long>> = withContext(Dispatchers.IO) {
        if (!shellExecutor.isRootMode) {
            return@withContext DomainResult.Failure(AppError.RootAccessError())
        }
        for (command in PROBE_COMMANDS) {
            val output = try {
                shellExecutor.execForOutput(command)
            } catch (_: SecurityException) {
                continue
            } catch (_: Exception) {
                continue
            }
            val parsed = parseFrequencyList(output)
            if (parsed.isNotEmpty()) {
                return@withContext DomainResult.Success(parsed.sorted())
            }
        }
        DomainResult.Failure(AppError.IoError("Could not determine runtime GPU frequencies."))
    }

    /**
     * Set a render-error code from the GL thread. Used by [GpuStressRenderer] to flag
     * GPU crashes / context loss events. Sampled in the next iteration of [sampleLoop].
     */
    fun reportRenderError(code: Int) {
        lastSeenRenderError = code
    }

    fun clearRenderError() {
        lastSeenRenderError = null
    }

    /**
     * Run the full stability sweep sequentially across [activeFrequenciesHz]. Each
     * frequency is locked for [GpuStabilityUiState.durationPerPointSec] seconds.
     *
     * Requires root mode. Callers without root should use [runFreeStressTest].
     *
     * Events are emitted through the returned [Flow]; [events] mirrors the same
     * stream for any second observer (kept for backwards compatibility / debugging).
     * The flow completes when the sweep ends (success, failure or abort).
     */
    suspend fun runStabilityTest(state: GpuStabilityUiState): Flow<TestEvent> = flow<TestEvent> {
        suspend fun publish(event: TestEvent) {
            // Best-effort mirror on the side-channel SharedFlow for any second
            // observer (e.g. logging). Errors there don't affect the main flow.
            runCatching { _events.emit(event) }
            this@flow.emit(event)
        }
        if (!shellExecutor.isRootMode) {
            publish(TestEvent.Failed(FailureReason.RootRequired))
            return@flow
        }
        val candidates = state.activeFrequenciesHz
        if (candidates.isEmpty()) {
            publish(TestEvent.Failed(FailureReason.NoFrequencies))
            return@flow
        }
        val nodeDir = resolveGovernorNode() ?: run {
            publish(TestEvent.Failed(FailureReason.DevfreqNodeMissing))
            return@flow
        }
        val original = readOriginalLimits(nodeDir)
        originalLimits = original
        _running.value = true
        try {
            for (target in candidates) {
                if (!_running.value) break
                val pointResult = testFrequency(
                    nodeDir = nodeDir,
                    targetFreqHz = target,
                    durationSec = state.durationPerPointSec,
                    throttleRatioPct = state.throttlingRatioPct,
                    onSample = { sample -> publish(TestEvent.SampleCollected(sample)) },
                )
                publish(TestEvent.PointFinished(pointResult))
                if (!pointResult.passed) {
                    publish(TestEvent.Failed(reason = pointResult.failureReason ?: FailureReason.Unknown))
                    break
                }
            }
            if (_running.value) {
                publish(TestEvent.AllCompleted)
            }
        } catch (ce: CancellationException) {
            publish(TestEvent.Aborted)
            throw ce
        } finally {
            _running.value = false
            withContext(NonCancellable) { restoreLimits(original, nodeDir) }
            originalLimits = null
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Free-run stress test. Does **not** require root: it never writes to sysfs,
     * never iterates a candidate list, never locks the GPU to a single frequency.
     *
     * The flow runs until [cancelTest] is invoked (or the calling coroutine is
     * cancelled). It emits [TestEvent.SampleCollected] at ~1 Hz and finally
     * [TestEvent.Aborted] when stopped. No [TestEvent.PointFinished] /
     * [TestEvent.AllCompleted] / [TestEvent.Failed] events are emitted (the only
     * exception is a fatal renderer error, which surfaces through the same
     * channel). The caller (typically the ViewModel) is responsible for
     * transitioning state to Idle when the flow completes normally.
     */
    suspend fun runFreeStressTest(state: GpuStabilityUiState): Flow<TestEvent> = flow<TestEvent> {
        suspend fun publish(event: TestEvent) {
            runCatching { _events.emit(event) }
            this@flow.emit(event)
        }
        originalLimits = null
        _running.value = true
        try {
            while (_running.value) {
                val renderErr = lastSeenRenderError
                if (renderErr != null && renderErr != 0) {
                    publish(TestEvent.Failed(
                        FailureReason.RendererGlError("0x${Integer.toHexString(renderErr)}")
                    ))
                    break
                }
                delay(1_000L)
            }
            if (_running.value) {
                // Loop exited because of a renderer failure, not a user cancel.
                // Already published Failed above.
            } else {
                publish(TestEvent.Aborted)
            }
        } catch (ce: CancellationException) {
            publish(TestEvent.Aborted)
            throw ce
        } finally {
            _running.value = false
            clearRenderError()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Cancel an in-progress sweep. The current per-frequency sample loop will exit on
     * the next iteration, the sweep [Flow] terminates, and the original governor /
     * min / max values are restored.
     */
    suspend fun cancelTest() = withContext(Dispatchers.IO) {
        _running.value = false
        currentJob?.cancel()
        currentJob = null
        val orig = originalLimits
        val nodeDir = orig?.nodeDir ?: resolveGovernorNode()
        if (orig != null && nodeDir != null) {
            restoreLimits(orig, nodeDir)
        }
        originalLimits = null
    }

    /**
     * Try to lock the GPU at [targetFreqHz]. Returns true on success. Used by callers
     * (and tests) that want to drive a single frequency manually.
     */
    suspend fun pinFrequency(nodeDir: String, targetFreqHz: Long): Boolean = withContext(Dispatchers.IO) {
        if (!shellExecutor.isRootMode) return@withContext false
        shellExecutor.execAndCheck(
            "echo performance > $nodeDir/governor",
            "echo $targetFreqHz > $nodeDir/min_freq",
            "echo $targetFreqHz > $nodeDir/max_freq",
        )
    }

    /**
     * Best-effort restore of the limits captured in [original]. Always called in a
     * `NonCancellable` context, even when the sweep is cancelled, so we don't leave
     * the GPU pinned to a single frequency.
     */
    private suspend fun restoreLimits(original: OriginalLimits, nodeDir: String) = withContext(Dispatchers.IO) {
        if (!shellExecutor.isRootMode) return@withContext
        try {
            original.minFreqHz?.let { shellExecutor.execAndCheck("echo $it > $nodeDir/min_freq") }
            original.maxFreqHz?.let { shellExecutor.execAndCheck("echo $it > $nodeDir/max_freq") }
            original.governor?.let { shellExecutor.execAndCheck("echo '$it' > $nodeDir/governor") }
        } catch (e: Exception) {
            Log.w(TAG, "restoreLimits failed: ${e.message}")
        }
    }

    private suspend fun testFrequency(
        nodeDir: String,
        targetFreqHz: Long,
        durationSec: Int,
        throttleRatioPct: Int,
        onSample: suspend (GpuSample) -> Unit = {},
    ): FreqPointResult {
        val pinned = pinFrequency(nodeDir, targetFreqHz)
        if (!pinned) {
            return FreqPointResult(
                targetFreqHz = targetFreqHz,
                passed = false,
                samples = emptyList(),
                failureReason = FailureReason.WriteFreqRejected,
                durationSec = 0,
            )
        }
        // Errors observed during the previous point should not bleed into this one.
        // (We clear at the *end* of the point instead, so the caller can pre-set
        // an error code in tests without having to coordinate with timing.)
        val samples = mutableListOf<GpuSample>()
        var lastSeenErrorInPoint: Int? = null
        var throttleStreak = 0
        var noLoadStreak = 0
        val startMs = System.currentTimeMillis()
        var failureReason: FailureReason? = null
        val tickMs = startMs + 1000L
        for (sec in 0 until durationSec) {
            if (!_running.value) {
                failureReason = FailureReason.AbortedByUser
                break
            }
            val now = System.currentTimeMillis()
            val sleepMs = (tickMs + sec * 1000L) - now
            if (sleepMs > 0) delay(sleepMs)
            val sample = readSample(targetFreqHz)
            samples += sample
            // Stream this sample up to the caller (the ViewModel appends it to
            // currentSamples so the chart updates in real time).
            onSample(sample)
            val renderErr = lastSeenRenderError
            if (renderErr != null && renderErr != 0) {
                lastSeenErrorInPoint = renderErr
                failureReason = FailureReason.RendererGlError("0x${Integer.toHexString(renderErr)}")
                break
            }
            val ratio = if (targetFreqHz > 0L) sample.curFreqHz.toDouble() / targetFreqHz else 0.0
            if (sample.curFreqHz > 0 && ratio * 100 < throttleRatioPct) {
                throttleStreak++
                if (throttleStreak >= DEFAULT_THROTTLE_WINDOW_SEC) {
                    failureReason = FailureReason.ThrottledBelow(throttleRatioPct)
                    break
                }
            } else {
                throttleStreak = 0
            }
            if (sample.gpuBusyPct == 0) {
                noLoadStreak++
                if (noLoadStreak >= DEFAULT_NO_LOAD_WINDOW_SEC) {
                    failureReason = FailureReason.NoLoad
                    break
                }
            } else {
                noLoadStreak = 0
            }
        }
        val passed = failureReason == null
        // Clear at the end of the point so the next one starts fresh; this also
        // unblocks any caller that pre-set the error before this point began.
        clearRenderError()
        return FreqPointResult(
            targetFreqHz = targetFreqHz,
            passed = passed,
            samples = samples,
            failureReason = failureReason,
            durationSec = ((System.currentTimeMillis() - startMs) / 1000L).toInt(),
        )
    }

    private suspend fun readSample(targetFreqHz: Long): GpuSample {
        val curFreqPath = resolvedCurFreqPath ?: resolveFirstWorkingPath(CUR_FREQ_PATHS, asLong = true)
            ?.also { resolvedCurFreqPath = it }
        val busyPath = resolvedBusyPath ?: resolveFirstWorkingPath(BUSY_PATHS, asLong = false)
            ?.also { resolvedBusyPath = it }
        val tempPath = resolvedTempPath
        val tempCmd = resolvedTempCommand ?: pickTemperatureCommand()?.also { resolvedTempCommand = it }
        val sb = StringBuilder()
        sb.append("cat '").append(curFreqPath ?: "/dev/null").append("' 2>/dev/null; ")
        if (busyPath != null) {
            sb.append("cat '").append(busyPath).append("' 2>/dev/null; ")
        } else {
            // busy path unknown → emit a placeholder so the row index of
            // the temperature sample below stays stable across reads.
            sb.append("echo 0; ")
        }
        if (tempPath != null) {
            sb.append("cat '").append(tempPath).append("' 2>/dev/null; ")
        } else if (tempCmd != null) {
            sb.append(tempCmd).append(" 2>/dev/null; ")
        }
        val raw = shellExecutor.execForOutput(sb.toString())

        val curFreq = raw.getOrNull(0)?.trim()?.toLongOrNull()?.takeIf { it > 0L } ?: 0L
        val busy = parsePercentInt(raw.getOrNull(1)?.trim())
        val tempRaw = raw.getOrNull(2)?.trim()?.toLongOrNull()
        val temp = if (tempRaw != null && tempRaw > 0L) {
            if (tempRaw > 1000L) tempRaw / 1000f else tempRaw.toFloat()
        } else null

        return GpuSample(
            timestampMs = System.currentTimeMillis(),
            targetFreqHz = targetFreqHz,
            curFreqHz = curFreq,
            gpuBusyPct = busy,
            temperatureC = temp,
            throttled = curFreq in 1..(targetFreqHz - 1),
            renderErrorCode = lastSeenRenderError?.takeIf { it != 0 },
        )
    }

    private suspend fun resolveFirstWorkingPath(paths: List<String>, asLong: Boolean): String? {
        for (path in paths) {
            val cmd = if (path.contains('*'))
                "cat $path 2>/dev/null" else "cat '$path' 2>/dev/null"
            val raw = shellExecutor.execForOutput(cmd).firstOrNull()?.trim()
            if (asLong) {
                if (raw?.toLongOrNull()?.let { it > 0L } == true) return path
            } else {
                if (parsePercentInt(raw)?.let { it in 0..100 } == true) return path
            }
        }
        return null
    }

    /** Pick the first working temperature path. Kept separate from [resolveFirstWorkingPath]
     *  because TEMP_FALLBACK_PATH is a fixed path and THERMAL_ZONE_CMDS are shell commands. */
    private suspend fun pickTemperatureCommand(): String? {
        val direct = shellExecutor.execForOutput("cat '$TEMP_FALLBACK_PATH' 2>/dev/null")
            .firstOrNull()?.trim()?.toLongOrNull()
        if (direct != null && direct > 0L) {
            resolvedTempPath = TEMP_FALLBACK_PATH
            return null  // resolvedTempPath handles it from here
        }
        for (cmd in THERMAL_ZONE_CMDS) {
            val value = shellExecutor.execForOutput("$cmd 2>/dev/null").firstOrNull()?.trim()?.toLongOrNull()
            if (value != null && value > 0L) return cmd
        }
        return null
    }

    private suspend fun resolveGovernorNode(): String? {
        for (candidate in GOV_NODE_CANDIDATES) {
            if (candidate.contains('*')) {
                // glob, only valid through shell — use `ls` style probe
                val out = shellExecutor.execForOutput("ls -d $candidate 2>/dev/null").firstOrNull()
                if (!out.isNullOrBlank()) return out.trim()
            } else if (shellExecutor.execAndCheck("[ -d '$candidate' ]")) {
                return candidate
            }
        }
        return null
    }

    private suspend fun readOriginalLimits(nodeDir: String): OriginalLimits = withContext(Dispatchers.IO) {
        OriginalLimits(
            governor = shellExecutor.execForOutput("cat $nodeDir/governor")
                .firstOrNull()?.trim()?.ifEmpty { null },
            minFreqHz = shellExecutor.execForOutput("cat $nodeDir/min_freq")
                .firstOrNull()?.trim()?.toLongOrNull(),
            maxFreqHz = shellExecutor.execForOutput("cat $nodeDir/max_freq")
                .firstOrNull()?.trim()?.toLongOrNull(),
            nodeDir = nodeDir,
        )
    }

    private fun parseFrequencyList(lines: List<String>): List<Long> {
        if (lines.isEmpty()) return emptyList()
        return lines.asSequence()
            .flatMap { line ->
                line.replace(",", " ")
                    .split(Regex("\\s+"))
                    .asSequence()
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                if (token.startsWith("0x", ignoreCase = true)) token.substring(2).toLongOrNull(16)
                else token.toLongOrNull()
            }
            .filter { it > 0L }
            .toList()
            .distinct()
    }

    /**
     * Events emitted by [runStabilityTest]. The UI / ViewModel turns these into [StabilityStatus] transitions.
     */
    sealed class TestEvent {
        data class PointFinished(val result: FreqPointResult) : TestEvent()
        data class SampleCollected(val sample: GpuSample) : TestEvent()
        data class Failed(val reason: FailureReason) : TestEvent()
        data object AllCompleted : TestEvent()
        data object Aborted : TestEvent()
    }
}

/**
 * Parse an integer in the 0..100 range from a sysfs line, tolerating
 * vendor-specific decoration. Examples that all parse to 99:
 *  - "99"
 *  - "99 %"
 *  - " 99%"
 *  - "99%"
 *  - "99.5"  (decimal point stripped, integer part used)
 *  - "99.5%" (decimal + percent sign)
 * Returns null for any input that doesn't contain a leading 0..100 integer.
 */
internal fun parsePercentInt(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val firstToken = raw.trim().split(Regex("\\s+")).firstOrNull() ?: return null
    val digits = firstToken.trimEnd('%').trim()
    // Strip a trailing decimal fraction so "99.5" / "99.5%" map to 99.
    val intPart = digits.substringBefore('.')
    return intPart.toIntOrNull()?.takeIf { it in 0..100 }
}