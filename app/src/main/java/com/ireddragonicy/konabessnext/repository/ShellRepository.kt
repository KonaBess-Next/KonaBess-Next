package com.ireddragonicy.konabessnext.repository

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a non-root process execution, mirroring Shell.Result's key fields.
 */
data class ShellResult(
    val out: List<String>,
    val err: List<String>,
    val isSuccess: Boolean
)

@Singleton
class ShellRepository @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private const val TAG = "ShellRepository"
    }

    private val kernelSuDir = File("/data/adb/ksu")

    /** Whether the app is currently configured for root mode. */
    val isRootMode: Boolean
        get() = settingsRepository.isRootMode()

    // ── Root-mode wrappers (existing behaviour) ──────────────────────

    suspend fun exec(vararg commands: String): Shell.Result = withContext(Dispatchers.IO) {
        if (isRootMode) {
            Shell.cmd(*commands).exec()
        } else {
            // Non-root: execute via ProcessBuilder then return a synthetic Shell.Result
            // that reflects success/failure (callers only check isSuccess).
            val result = execNonRoot(*commands)
            Shell.cmd(if (result.isSuccess) "true" else "false").exec()
        }
    }

    /**
     * Execute commands and return output, dispatching automatically based on root mode.
     * This is the preferred method for callers that need command output.
     */
    suspend fun execAdaptive(vararg commands: String): ShellResult = withContext(Dispatchers.IO) {
        if (isRootMode) {
            val result = Shell.cmd(*commands).exec()
            ShellResult(result.out, result.err, result.isSuccess)
        } else {
            execNonRoot(*commands)
        }
    }

    suspend fun execAndCheck(vararg commands: String): Boolean = withContext(Dispatchers.IO) {
        if (isRootMode) {
            Shell.cmd(*commands).exec().isSuccess
        } else {
            execNonRoot(*commands).isSuccess
        }
    }

    suspend fun execForOutput(vararg commands: String): List<String> = withContext(Dispatchers.IO) {
        if (isRootMode) {
            Shell.cmd(*commands).exec().out
        } else {
            execNonRoot(*commands).out
        }
    }

    // ── Non-root execution via ProcessBuilder ────────────────────────

    /**
     * Execute shell commands without root using ProcessBuilder + /system/bin/sh.
     * Each command string is executed sequentially via `sh -c`.
     * If a command starts with `cd <dir> &&`, the working directory is extracted
     * and set on the ProcessBuilder for reliability.
     */
    private fun execNonRoot(vararg commands: String): ShellResult {
        val allOut = mutableListOf<String>()
        val allErr = mutableListOf<String>()
        var allSuccess = true

        val cdPattern = Regex("""^cd\s+(\S+)\s*&&\s*(.+)$""")

        for (cmd in commands) {
            try {
                // Extract working directory from "cd /path && command" patterns
                val match = cdPattern.find(cmd)
                val (workDir, actualCmd) = if (match != null) {
                    File(match.groupValues[1]) to match.groupValues[2].trim()
                } else {
                    null to cmd
                }

                val pb = ProcessBuilder("sh", "-c", actualCmd)
                    .redirectErrorStream(false)
                if (workDir != null && workDir.isDirectory) pb.directory(workDir)

                val process = pb.start()
                val stdout = BufferedReader(InputStreamReader(process.inputStream)).readLines()
                val stderr = BufferedReader(InputStreamReader(process.errorStream)).readLines()
                val exitCode = process.waitFor()

                allOut.addAll(stdout)
                allErr.addAll(stderr)
                if (exitCode != 0) {
                    allSuccess = false
                    Log.w(TAG, "execNonRoot failed (exit=$exitCode): $cmd")
                }
            } catch (e: Exception) {
                allSuccess = false
                allErr.add(e.message ?: "Unknown error")
                Log.e(TAG, "execNonRoot exception: $cmd", e)
            }
        }

        return ShellResult(allOut, allErr, allSuccess)
    }

    // ── File operation helpers ────────────────────────────────────────

    suspend fun copyFile(source: String, dest: String): Boolean {
        return execAndCheck("cat '$source' > '$dest'")
    }

    suspend fun copyFile(source: String, dest: String, mode: String): Boolean {
        return execAndCheck("cat '$source' > '$dest' && chmod $mode '$dest'")
    }

    suspend fun readFile(path: String): List<String> {
        return execForOutput("cat '$path'")
    }

    suspend fun writeFile(path: String, content: String): Boolean {
        val escaped = content.replace("'", "'\\''")
        return execAndCheck("echo '$escaped' > '$path'")
    }

    suspend fun fileExists(path: String): Boolean {
        return execAndCheck("[ -f '$path' ]")
    }

    suspend fun deleteFile(path: String): Boolean {
        return execAndCheck("rm -f '$path'")
    }

    /**
     * Check if root access is available.
     * Checks both standard SU and KernelSU/APatch paths.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (shell.isRoot) {
                return@withContext true
            }
            // Fallback for some KSU/APatch scenarios if libsu misses them
            if (kernelSuDir.exists()) {
                val output = Shell.cmd("id").exec().out.toString()
                return@withContext output.contains("uid=0")
            }
            return@withContext false
        } catch (e: Exception) {
            return@withContext false
        }
    }

    /**
     * Ensure a file in the app's private directory has executable permission.
     * Uses standard Java IO (works without root).
     */
    fun ensureExecutable(file: File) {
        if (file.exists()) {
            file.setExecutable(true, false)
            file.setReadable(true, false)
        }
    }
}
