package com.ireddragonicy.konabessnext;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

import com.ireddragonicy.konabessnext.adapters.SettingsAdapter;
import com.ireddragonicy.konabessnext.utils.LocaleUtil;

public class SettingsActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "KonaBessSettings";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_FREQ_UNIT = "freq_unit";
    public static final String KEY_THEME = "theme";
    public static final String KEY_COLOR_PALETTE = "color_palette";
    public static final String KEY_AUTO_SAVE_GPU_TABLE = "auto_save_gpu_table";
    public static final String KEY_DYNAMIC_COLOR = "dynamic_color";

    public static final int FREQ_UNIT_HZ = 0;
    public static final int FREQ_UNIT_MHZ = 1;
    public static final int FREQ_UNIT_GHZ = 2;

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int PALETTE_DYNAMIC = 0;
    public static final int PALETTE_PURPLE = 1;
    public static final int PALETTE_BLUE = 2;
    public static final int PALETTE_GREEN = 3;
    public static final int PALETTE_PINK = 4;
    public static final int PALETTE_AMOLED = 5;

    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_GERMAN = "de";
    public static final String LANGUAGE_CHINESE = "zh-rCN";
    public static final String LANGUAGE_INDONESIAN = "in";

    private SharedPreferences prefs;
    private SettingsAdapter adapter;
    private RecyclerView recyclerView;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleUtil.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        applyTheme();
        super.onCreate(savedInstanceState);
        showSettingsView();
    }

    private void applyTheme() {
        // Apply color palette theme first
        boolean dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true);

        if (dynamicColor) {
            setTheme(R.style.Theme_KonaBess); // Material You Dynamic
        } else {
            int palette = prefs.getInt(KEY_COLOR_PALETTE, PALETTE_PURPLE);
            switch (palette) {
                case PALETTE_PURPLE:
                    setTheme(R.style.Theme_KonaBess_Purple);
                    break;
                case PALETTE_BLUE:
                    setTheme(R.style.Theme_KonaBess_Blue);
                    break;
                case PALETTE_GREEN:
                    setTheme(R.style.Theme_KonaBess_Green);
                    break;
                case PALETTE_PINK:
                    setTheme(R.style.Theme_KonaBess_Pink);
                    break;
                case PALETTE_AMOLED:
                    setTheme(R.style.Theme_KonaBess_AMOLED);
                    break;
                default:
                    setTheme(R.style.Theme_KonaBess_Purple);
                    break;
            }
        }
        // Then apply light/dark mode
        applyThemeMode(getSavedTheme());
    }

    private void showSettingsView() {
        LinearLayout mainView = new LinearLayout(this);
        mainView.setOrientation(LinearLayout.VERTICAL);
        setContentView(mainView);

        // Material Toolbar
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.settings));
        toolbar.setTitleTextColor(getColor(android.R.color.white));
        mainView.addView(toolbar);

        // RecyclerView for settings
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setPadding(0, 8, 0, 16);
        recyclerView.setClipToPadding(false);

        ArrayList<SettingsAdapter.SettingItem> items = new ArrayList<>();

        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_dark_mode,
                getString(R.string.theme),
                getString(R.string.theme_description),
                getCurrentThemeName()));

        // Dynamic Color Toggle
        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_tune,
                "Dynamic Color",
                "Use wallpaper-based colors (Material You)",
                isDynamicColorEnabled()));

        // Color Palette (Only relevant if Dynamic Color is OFF)
        if (!isDynamicColorEnabled()) {
            items.add(new SettingsAdapter.SettingItem(
                    R.drawable.ic_tune,
                    "Color Palette",
                    "Choose your color scheme",
                    getCurrentColorPaletteName()));
        }

        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_language,
                getString(R.string.language),
                "Select your preferred language",
                getCurrentLanguageName()));

        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_frequency,
                getString(R.string.gpu_freq_unit),
                "Choose frequency display unit",
                getCurrentFreqUnitName()));

        // Auto-save GPU freq table toggle
        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_save,
                getString(R.string.auto_save_gpu_freq_table),
                getString(R.string.auto_save_gpu_freq_table_desc),
                isAutoSaveEnabled()));

        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_help,
                getString(R.string.help),
                "About and documentation",
                "Version " + BuildConfig.VERSION_NAME));

        adapter = new SettingsAdapter(items, this);

        adapter.setOnItemClickListener(position -> {
            SettingsAdapter.SettingItem item = items.get(position);

            if (item.title.equals(getString(R.string.theme))) {
                showThemeDialog();
            } else if (item.title.equals("Dynamic Color")) {
                toggleDynamicColor();
                recreate();
            } else if (item.title.equals("Color Palette")) {
                showColorPaletteDialog();
            } else if (item.title.equals(getString(R.string.language))) {
                showLanguageDialog();
            } else if (item.title.equals(getString(R.string.gpu_freq_unit))) {
                showFreqUnitDialog();
            } else if (item.title.equals(getString(R.string.auto_save_gpu_freq_table))) {
                toggleAutoSave();
            } else if (item.title.equals(getString(R.string.help))) {
                showHelpDialog();
            }
        });

        recyclerView.setAdapter(adapter);
        mainView.addView(recyclerView);
    }

    public void showLanguageDialogPublic() {
        showLanguageDialog();
    }

    private void showLanguageDialog() {
        String[] languages = {
                getString(R.string.english),
                getString(R.string.german),
                getString(R.string.chinese),
                getString(R.string.indonesian)
        };

        String[] languageCodes = { LANGUAGE_ENGLISH, LANGUAGE_GERMAN, LANGUAGE_CHINESE, LANGUAGE_INDONESIAN };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.language))
                .setSingleChoiceItems(languages, getCurrentLanguageIndex(), (dialog, which) -> {
                    saveLanguage(languageCodes[which]);
                    adapter.updateItem(1, getCurrentLanguageName());
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create().show();
    }

    public void showThemeDialogPublic() {
        showThemeDialog();
    }

    private void showThemeDialog() {
        String[] themes = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.theme))
                .setSingleChoiceItems(themes, getSavedTheme(), (dialog, which) -> {
                    saveTheme(which);
                    adapter.updateItem(0, getCurrentThemeName());
                    dialog.dismiss();
                    applyTheme();
                    restartActivity();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create().show();
    }

    public void showFreqUnitDialogPublic() {
        showFreqUnitDialog();
    }

    private void showFreqUnitDialog() {
        String[] units = {
                getString(R.string.hz),
                getString(R.string.mhz),
                getString(R.string.ghz)
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.gpu_freq_unit))
                .setSingleChoiceItems(units, getSavedFreqUnit(), (dialog, which) -> {
                    saveFreqUnit(which);
                    adapter.updateItem(2, getCurrentFreqUnitName());
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create().show();
    }

    public void showHelpDialogPublic() {
        showHelpDialog();
    }

    private void showHelpDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.help))
                .setMessage(KonaBessStr.generic_help(this))
                .setPositiveButton(getString(R.string.ok), null)
                .setNeutralButton(getString(R.string.about),
                        (dialog, which) -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.about))
                                .setMessage(getResources().getString(R.string.author) + " " +
                                        "IRedDragonICY & xzr467706992 (LibXZR)\n"
                                        + getResources().getString(R.string.release_at) + " www.akr-developers.com\n")
                                .setPositiveButton(getString(R.string.ok), null)
                                .setNegativeButton("Github",
                                        (dialog1, which1) -> startActivity(new Intent() {
                                            {
                                                setAction(Intent.ACTION_VIEW);
                                                setData(Uri.parse("https://github.com/ireddragonicy/KonaBess"));
                                            }
                                        }))
                                .setNeutralButton(getString(R.string.visit_akr),
                                        (dialog1, which1) -> startActivity(new Intent() {
                                            {
                                                setAction(Intent.ACTION_VIEW);
                                                setData(Uri.parse("https://www.akr-developers.com/d/441"));
                                            }
                                        }))
                                .create().show())
                .create().show();
    }

    public String getCurrentLanguageNamePublic() {
        return getCurrentLanguageName();
    }

    private String getCurrentLanguageName() {
        String[] names = {
                getString(R.string.english),
                getString(R.string.german),
                getString(R.string.chinese),
                getString(R.string.indonesian)
        };
        String[] codes = { LANGUAGE_ENGLISH, LANGUAGE_GERMAN, LANGUAGE_CHINESE, LANGUAGE_INDONESIAN };

        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(getSavedLanguage())) {
                return names[i];
            }
        }
        return names[0];
    }

    public String getCurrentThemeNamePublic() {
        return getCurrentThemeName();
    }

    private String getCurrentThemeName() {
        String[] names = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };
        return names[getSavedTheme()];
    }

    public String getCurrentFreqUnitNamePublic() {
        return getCurrentFreqUnitName();
    }

    private String getCurrentFreqUnitName() {
        String[] names = {
                getString(R.string.hz),
                getString(R.string.mhz),
                getString(R.string.ghz)
        };
        return names[getSavedFreqUnit()];
    }

    private String getAutoSaveStatus() {
        return getString(isAutoSaveEnabled() ? R.string.common_on : R.string.common_off);
    }

    private int getCurrentLanguageIndex() {
        String[] codes = { LANGUAGE_ENGLISH, LANGUAGE_GERMAN, LANGUAGE_CHINESE, LANGUAGE_INDONESIAN };
        String currentLang = getSavedLanguage();

        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(currentLang)) {
                return i;
            }
        }
        return 0;
    }

    private void saveLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    private String getSavedLanguage() {
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH);
    }

    private void saveFreqUnit(int unit) {
        prefs.edit().putInt(KEY_FREQ_UNIT, unit).apply();
    }

    private int getSavedFreqUnit() {
        return prefs.getInt(KEY_FREQ_UNIT, FREQ_UNIT_MHZ);
    }

    private void toggleAutoSave() {
        boolean enabled = !isAutoSaveEnabled();
        prefs.edit().putBoolean(KEY_AUTO_SAVE_GPU_TABLE, enabled).apply();
        recreate();
        Toast.makeText(this, enabled ? R.string.auto_save_enabled_toast : R.string.auto_save_disabled_toast,
                Toast.LENGTH_SHORT).show();
    }

    private boolean isAutoSaveEnabled() {
        return prefs.getBoolean(KEY_AUTO_SAVE_GPU_TABLE, false);
    }

    private boolean isDynamicColorEnabled() {
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, true);
    }

    private void toggleDynamicColor() {
        boolean enabled = !isDynamicColorEnabled();
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply();
    }

    private String getCurrentColorPaletteName() {
        int palette = getSavedColorPalette();
        switch (palette) {
            case PALETTE_PURPLE:
                return "Purple & Teal";
            case PALETTE_BLUE:
                return "Blue & Orange";
            case PALETTE_GREEN:
                return "Green & Red";
            case PALETTE_PINK:
                return "Pink & Cyan";
            case PALETTE_AMOLED:
                return "Pure AMOLED (Black)";
            default:
                return "Purple & Teal";
        }
    }

    private void showColorPaletteDialog() {
        String[] palettes = {
                "Purple & Teal",
                "Blue & Orange",
                "Green & Red",
                "Pink & Cyan",
                "Pure AMOLED (Black)"
        };
        final int[] paletteIds = {
                PALETTE_PURPLE,
                PALETTE_BLUE,
                PALETTE_GREEN,
                PALETTE_PINK,
                PALETTE_AMOLED
        };

        int currentPalette = getSavedColorPalette();
        int initialSelection = 0;
        for (int i = 0; i < paletteIds.length; i++) {
            if (paletteIds[i] == currentPalette) {
                initialSelection = i;
                break;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Color Palette")
                .setSingleChoiceItems(palettes, initialSelection, (dialog, which) -> {
                    saveColorPalette(paletteIds[which]);
                    dialog.dismiss();
                    restartActivity();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create().show();
    }

    private void saveTheme(int theme) {
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }

    private int getSavedTheme() {
        return prefs.getInt(KEY_THEME, THEME_SYSTEM);
    }

    private void saveColorPalette(int palette) {
        prefs.edit().putInt(KEY_COLOR_PALETTE, palette).apply();
    }

    private int getSavedColorPalette() {
        return prefs.getInt(KEY_COLOR_PALETTE, PALETTE_DYNAMIC);
    }

    private void restartActivity() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    public static String formatFrequency(long frequencyHz, int unit) {
        switch (unit) {
            case FREQ_UNIT_HZ:
                return frequencyHz + " Hz";
            case FREQ_UNIT_MHZ:
                return (frequencyHz / 1000000L) + " MHz";
            case FREQ_UNIT_GHZ:
                return String.format("%.2f GHz", frequencyHz / 1000000000.0);
            default:
                return (frequencyHz / 1000000L) + " MHz";
        }
    }

    public static String formatFrequency(long frequencyHz, Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int unit = prefs.getInt(KEY_FREQ_UNIT, FREQ_UNIT_MHZ);
        return formatFrequency(frequencyHz, unit);
    }

    public static void applyThemeFromSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true);

        if (dynamicColor) {
            context.setTheme(R.style.Theme_KonaBess); // Material You Dynamic
        } else {
            int palette = prefs.getInt(KEY_COLOR_PALETTE, PALETTE_PURPLE);
            switch (palette) {
                case PALETTE_PURPLE:
                    context.setTheme(R.style.Theme_KonaBess_Purple);
                    break;
                case PALETTE_BLUE:
                    context.setTheme(R.style.Theme_KonaBess_Blue);
                    break;
                case PALETTE_GREEN:
                    context.setTheme(R.style.Theme_KonaBess_Green);
                    break;
                case PALETTE_PINK:
                    context.setTheme(R.style.Theme_KonaBess_Pink);
                    break;
                case PALETTE_AMOLED:
                    context.setTheme(R.style.Theme_KonaBess_AMOLED);
                    break;
                default:
                    context.setTheme(R.style.Theme_KonaBess_Purple);
                    break;
            }
        }

        int theme = prefs.getInt(KEY_THEME, THEME_SYSTEM);
        applyThemeMode(theme);
    }

    public static boolean isAutoSaveEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_SAVE_GPU_TABLE, false);
    }

    private static void applyThemeMode(int theme) {
        int[] modes = {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES
        };
        AppCompatDelegate.setDefaultNightMode(modes[theme]);
    }
}
