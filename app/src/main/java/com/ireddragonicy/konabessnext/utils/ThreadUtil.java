package com.ireddragonicy.konabessnext.utils;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadUtil {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void runInBackground(Runnable runnable) {
        executor.execute(runnable);
    }

    public static void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
    
    public static void runOnMainDelayed(Runnable runnable, long delayMillis) {
        mainHandler.postDelayed(runnable, delayMillis);
    }
}
