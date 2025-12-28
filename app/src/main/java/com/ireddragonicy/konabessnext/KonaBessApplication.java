package com.ireddragonicy.konabessnext;

import android.app.Application;
import android.content.Context;

import com.ireddragonicy.konabessnext.utils.LocaleUtil;
import com.ireddragonicy.konabessnext.utils.RootHelper;
import com.ireddragonicy.konabessnext.ui.SettingsActivity;
import com.topjohnwu.superuser.Shell;

public class KonaBessApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtil.wrap(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SettingsActivity.applyThemeFromSettings(this);

        // Ensure RootHelper static initializer runs, then pre-cache shell
        try {
            Class.forName(RootHelper.class.getName());
            Shell.getShell(shell -> {
            });
        } catch (Exception ignored) {
        }
    }
}
