package com.ireddragonicy.konabessnext.utils;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.List;

/**
 * Helper class for root operations using libsu.
 * Supports Magisk, KernelSU, KernelSU Next, and APatch.
 */
public class RootHelper {

    private static final File KERNELSU_DIR = new File("/data/adb/ksu");

    static {
        Shell.enableVerboseLogging = false;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(30));
    }

    // --- Core Execution ---

    public static Shell.Result exec(String... commands) {
        return Shell.cmd(commands).exec();
    }

    public static boolean execAndCheck(String... commands) {
        return exec(commands).isSuccess();
    }

    public static List<String> execForOutput(String... commands) {
        return exec(commands).getOut();
    }

    // --- File Operations (Centralized) ---

    /**
     * Copy a file using root permissions.
     * Replaces scattered `cat source > dest` patterns.
     *
     * @param source Source file path
     * @param dest   Destination file path
     * @return true if successful
     */
    public static boolean copyFile(String source, String dest) {
        return execAndCheck(String.format("cat '%s' > '%s'", source, dest));
    }

    /**
     * Copy a file using root permissions with specific file mode.
     *
     * @param source Source file path
     * @param dest   Destination file path
     * @param mode   File mode (e.g., "644", "666")
     * @return true if successful
     */
    public static boolean copyFile(String source, String dest, String mode) {
        return execAndCheck(String.format("cat '%s' > '%s' && chmod %s '%s'", source, dest, mode, dest));
    }

    /**
     * Read a file's content using root permissions.
     *
     * @param path File path to read
     * @return List of lines from file
     */
    public static List<String> readFile(String path) {
        return execForOutput(String.format("cat '%s'", path));
    }

    /**
     * Write content to a file using root permissions.
     *
     * @param path    File path
     * @param content Content to write
     * @return true if successful
     */
    public static boolean writeFile(String path, String content) {
        // Escape single quotes in content
        String escaped = content.replace("'", "'\\''");
        return execAndCheck(String.format("echo '%s' > '%s'", escaped, path));
    }

    /**
     * Check if a file exists using root permissions.
     *
     * @param path File path
     * @return true if file exists
     */
    public static boolean fileExists(String path) {
        return execAndCheck(String.format("[ -f '%s' ]", path));
    }

    /**
     * Delete a file using root permissions.
     *
     * @param path File path
     * @return true if successful
     */
    public static boolean deleteFile(String path) {
        return execAndCheck(String.format("rm -f '%s'", path));
    }

    // --- Legacy Shell Methods (Used by KonaBessCore) ---

    public static Shell.Result execSh(String... commands) {
        return Shell.cmd(commands).exec();
    }

    public static List<String> execShForOutput(String... commands) {
        return execSh(commands).getOut();
    }

    // --- Root Availability ---

    /**
     * Check if root access is available.
     * Supports libsu detection with KernelSU fallback.
     */
    public static boolean isRootAvailable() {
        try {
            Shell shell = Shell.getShell();
            if (shell.isRoot()) {
                return true;
            }
            // KernelSU fallback: test actual root access
            if (KERNELSU_DIR.exists()) {
                return Shell.cmd("id").exec().getOut().toString().contains("uid=0");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
