package com.ireddragonicy.konabessnext.utils;

import com.topjohnwu.superuser.Shell;

import java.util.List;

public class RootHelper {
    
    static {
        // Initialize libsu with Shell.Config
        Shell.enableVerboseLogging = false;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10));
    }
    
    /**
     * Execute a root command and return the result
     */
    public static Shell.Result exec(String... commands) {
        return Shell.cmd(commands).exec();
    }
    
    /**
     * Execute a root command and check if it was successful
     */
    public static boolean execAndCheck(String... commands) {
        Shell.Result result = exec(commands);
        return result.isSuccess();
    }
    
    /**
     * Execute a root command and return output lines
     */
    public static List<String> execForOutput(String... commands) {
        Shell.Result result = exec(commands);
        return result.getOut();
    }
    
    /**
     * Check if root access is available
     */
    public static boolean isRootAvailable() {
        return Shell.getShell().isRoot();
    }
    
    /**
     * Execute a non-root shell command
     */
    public static Shell.Result execSh(String... commands) {
        return Shell.cmd(commands).exec();
    }
    
    /**
     * Execute a non-root shell command and return output lines
     */
    public static List<String> execShForOutput(String... commands) {
        Shell.Result result = execSh(commands);
        return result.getOut();
    }
}

