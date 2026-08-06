package com.ireddragonicy.konabessnext.core.system

import android.app.Activity
import android.util.Log
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WindowBrightnessController @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context
) {

    @Volatile private var window: android.view.Window? = null
    @Volatile private var savedBrightness: Float? = null
    @Volatile private var isDimmed: Boolean = false

    fun attachActivity(activity: Activity) {
        val newWindow = activity.window ?: return
        window = newWindow
        if (isDimmed) {
            // Re-apply the override to the new window so the screen stays
            // dim across an activity recreation (mirrors PiliPlus' choice
            // to call `setAutoReset(false)`).
            runCatching {
                val lp = newWindow.attributes
                lp.screenBrightness = 0f
                newWindow.attributes = lp
            }.onFailure { Log.w(TAG, "re-apply dim on attach failed: ${it.message}") }
        }
    }

    fun dimForStressTest() {
        val target = window
        if (target == null) {
            Log.w(TAG, "dimForStressTest() ignored: WindowBrightnessController has no attached activity")
            return
        }
        if (isDimmed) return
        runCatching {
            val lp = target.attributes
            savedBrightness = lp.screenBrightness // could be BRIGHTNESS_OVERRIDE_NONE = -1f
            lp.screenBrightness = DIMMED_LEVEL
            target.attributes = lp
            isDimmed = true
        }.onFailure { Log.w(TAG, "dimForStressTest failed: ${it.message}") }
    }

    fun restore() {
        val target = window ?: return
        if (!isDimmed) return
        runCatching {
            val lp = target.attributes
            lp.screenBrightness = savedBrightness
                ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            target.attributes = lp
        }.onFailure { Log.w(TAG, "restore failed: ${it.message}") }
        savedBrightness = null
        isDimmed = false
    }

    val isCurrentlyDimmed: Boolean get() = isDimmed

    companion object {
        private const val TAG = "BrightnessCtrl"
        private const val DIMMED_LEVEL: Float = 0f
    }
}
