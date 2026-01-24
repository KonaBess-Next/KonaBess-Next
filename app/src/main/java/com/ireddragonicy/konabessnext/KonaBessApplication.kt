package com.ireddragonicy.konabessnext

import android.app.Application
import android.content.Context
import com.ireddragonicy.konabessnext.utils.LocaleUtil
import com.ireddragonicy.konabessnext.utils.RootHelper
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KonaBessApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtil.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        SettingsActivity.applyThemeFromSettings(this)

        // Ensure RootHelper static initializer runs (or object is initialized), then pre-cache shell
        try {
            // Configure Shell builder before creating the shell
            Shell.enableVerboseLogging = false
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10))
            
            // Accessing the object instance to force initialization if needed
            Shell.getShell { }
        } catch (ignored: Exception) {
        }
    }
}
