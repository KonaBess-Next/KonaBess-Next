package com.ireddragonicy.konabessnext.utils

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object ThreadUtil {
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun runInBackground(runnable: Runnable) {
        executor.execute(runnable)
    }
    
    // Kotlin-friendly overload
    fun runInBackground(action: () -> Unit) {
        executor.execute(action)
    }

    @JvmStatic
    fun runOnMain(runnable: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }
    
    // Kotlin-friendly overload
    fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    @JvmStatic
    fun runOnMainDelayed(runnable: Runnable, delayMillis: Long) {
        mainHandler.postDelayed(runnable, delayMillis)
    }
    
    fun runOnMainDelayed(delayMillis: Long, action: () -> Unit) {
        mainHandler.postDelayed(action, delayMillis)
    }
}
