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

    public static Shell.Result exec(String... commands) {
        return Shell.cmd(commands).exec();
    }

    public static boolean execAndCheck(String... commands) {
        return exec(commands).isSuccess();
    }

    public static List<String> execForOutput(String... commands) {
        return exec(commands).getOut();
    }

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

    public static Shell.Result execSh(String... commands) {
        return Shell.cmd(commands).exec();
    }

    public static List<String> execShForOutput(String... commands) {
        return execSh(commands).getOut();
    }
}
