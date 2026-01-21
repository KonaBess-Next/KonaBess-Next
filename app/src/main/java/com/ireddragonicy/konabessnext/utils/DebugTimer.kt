package com.ireddragonicy.konabessnext.utils

import android.util.Log

object DebugTimer {
    private var startTime: Long = 0
    private const val TAG = "DebugTimer"

    fun start(label: String) {
        startTime = System.currentTimeMillis()
        Log.e(TAG, "START [$label]: $startTime")
    }

    fun mark(label: String) {
        val now = System.currentTimeMillis()
        val diff = now - startTime
        Log.e(TAG, "STEP  [$label]: +${diff}ms")
    }
}