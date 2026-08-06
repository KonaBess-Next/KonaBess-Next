package com.ireddragonicy.konabessnext.repository

/**
 * Minimal shell-execution contract used by code paths that should be unit-testable
 * without an Android runtime (libsu, /system/bin/sh, etc).
 *
 * Production code injects [ShellRepository] (which implements this interface). Tests
 * can substitute a hand-rolled fake without pulling in libsu or Android Context.
 */
interface ShellExecutor {
    /** True when the app is configured to use root shell. UI should gate root-only features on this. */
    val isRootMode: Boolean

    /** Execute the given commands in order and return the combined stdout, line by line. */
    suspend fun execForOutput(vararg commands: String): List<String>

    /**
     * Execute the given commands and return true only if every one returned exit code 0.
     * Implementations may redirect stderr to stdout when running as root.
     */
    suspend fun execAndCheck(vararg commands: String): Boolean
}