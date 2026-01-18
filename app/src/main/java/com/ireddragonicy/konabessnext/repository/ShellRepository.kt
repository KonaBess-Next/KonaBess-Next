package com.ireddragonicy.konabessnext.repository

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellRepository @Inject constructor() {



    private val kernelSuDir = File("/data/adb/ksu")

    suspend fun exec(vararg commands: String): Shell.Result = withContext(Dispatchers.IO) {
        Shell.cmd(*commands).exec()
    }

    suspend fun execAndCheck(vararg commands: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd(*commands).exec().isSuccess
    }

    suspend fun execForOutput(vararg commands: String): List<String> = withContext(Dispatchers.IO) {
        Shell.cmd(*commands).exec().out
    }

    suspend fun copyFile(source: String, dest: String): Boolean {
        // Use 'cat' to copy with root permissions
        return execAndCheck("cat '$source' > '$dest'")
    }

    suspend fun copyFile(source: String, dest: String, mode: String): Boolean {
        return execAndCheck("cat '$source' > '$dest' && chmod $mode '$dest'")
    }

    suspend fun readFile(path: String): List<String> {
        return execForOutput("cat '$path'")
    }

    suspend fun writeFile(path: String, content: String): Boolean {
        // Escape single quotes in content to prevent shell syntax errors
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
            // Fallback for some KSU/APatch scenarios if libsu misses them (unlikely in v6 but possible)
            if (kernelSuDir.exists()) {
                val output = Shell.cmd("id").exec().out.toString()
                return@withContext output.contains("uid=0")
            }
            return@withContext false
        } catch (e: Exception) {
            return@withContext false
        }
    }
}
