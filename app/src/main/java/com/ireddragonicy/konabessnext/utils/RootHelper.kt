package com.ireddragonicy.konabessnext.utils

import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Helper class for root operations using libsu.
 */
object RootHelper {

    private val KERNELSU_DIR = File("/data/adb/ksu")

    init {
        Shell.enableVerboseLogging = false
        try {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(30)
            )
        } catch (e: IllegalStateException) {
            // Shell already initialized, ignore
        }
    }

    // --- Core Execution ---

    fun exec(vararg commands: String): Shell.Result {
        return Shell.cmd(*commands).exec()
    }

    @JvmStatic
    fun run(command: String): Boolean {
        return execAndCheck(command)
    }

    @JvmStatic
    fun execAndCheck(vararg commands: String): Boolean {
        return exec(*commands).isSuccess
    }

    fun execForOutput(vararg commands: String): List<String> {
        return exec(*commands).out
    }

    // --- File Operations (Centralized) ---

    @JvmStatic
    fun copyFile(source: String, dest: String): Boolean {
        return execAndCheck("cat '$source' > '$dest'")
    }

    @JvmStatic
    fun copyFile(source: String, dest: String, mode: String): Boolean {
        return execAndCheck("cat '$source' > '$dest' && chmod $mode '$dest'")
    }

    fun readFile(path: String): List<String> {
        return execForOutput("cat '$path'")
    }

    fun writeFile(path: String, content: String): Boolean {
        // Escape single quotes in content
        val escaped = content.replace("'", "'\\''")
        return execAndCheck("echo '$escaped' > '$path'")
    }

    fun fileExists(path: String): Boolean {
        return execAndCheck("[ -f '$path' ]")
    }

    fun deleteFile(path: String): Boolean {
        return execAndCheck("rm -f '$path'")
    }

    // --- Legacy Shell Methods ---

    fun execSh(vararg commands: String): Shell.Result {
        return Shell.cmd(*commands).exec()
    }

    fun execShForOutput(vararg commands: String): List<String> {
        return execSh(*commands).out
    }

    // --- Root Availability ---

    fun isRootAvailable(): Boolean {
        return try {
            val shell = Shell.getShell()
            if (shell.isRoot) {
                return true
            }
            // KernelSU fallback: test actual root access
            if (KERNELSU_DIR.exists()) {
                Shell.cmd("id").exec().out.toString().contains("uid=0")
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
