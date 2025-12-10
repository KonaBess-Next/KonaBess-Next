package com.ireddragonicy.konabessnext.fragments;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import com.ireddragonicy.konabessnext.BuildConfig;
import com.ireddragonicy.konabessnext.KonaBessStr;
import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.SettingsActivity;
import com.ireddragonicy.konabessnext.adapters.SettingsAdapter;

public class SettingsFragment extends Fragment {
    private RecyclerView recyclerView;
    private SettingsAdapter adapter;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        prefs = requireContext().getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);

        recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setPadding(0, 8, 0, 16);
        recyclerView.setClipToPadding(false);

        loadSettings();
        return recyclerView;
    }

    private void loadSettings() {
        ArrayList<SettingsAdapter.SettingItem> items = new ArrayList<>();

        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_dark_mode,
                getString(R.string.theme),
                getString(R.string.theme_description),
                getCurrentThemeName()));

        // Dynamic Color Toggle
        boolean isDynamicColor = prefs.getBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, true);
        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_tune,
                "Dynamic Color",
                "Use wallpaper-based colors (Material You)",
                isDynamicColor));

        // Color Palette (Only relevant if Dynamic Color is OFF)
        if (!isDynamicColor) {
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
                getString(R.string.freq_unit_description),
                getCurrentFreqUnitName()));

        // Auto-save GPU freq table toggle
        boolean isAutoSave = prefs.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false);
        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_save,
                getString(R.string.auto_save_gpu_freq_table),
                getString(R.string.auto_save_gpu_freq_table_desc),
                isAutoSave));

        items.add(new SettingsAdapter.SettingItem(
                R.drawable.ic_help,
                getString(R.string.help),
                "About and documentation",
                "Version " + BuildConfig.VERSION_NAME));

        adapter = new SettingsAdapter(items, requireContext());
        adapter.setOnItemClickListener(position -> {
            SettingsAdapter.SettingItem item = items.get(position);

            if (item.title.equals(getString(R.string.theme))) {
                showThemeDialog();
            } else if (item.title.equals("Dynamic Color")) {
                toggleDynamicColor();
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
    }

    private void showThemeDialog() {
        String[] themes = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };

        int currentTheme = prefs.getInt(SettingsActivity.KEY_THEME, SettingsActivity.THEME_SYSTEM);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.theme))
                .setSingleChoiceItems(themes, currentTheme, (dialog, which) -> {
                    prefs.edit().putInt(SettingsActivity.KEY_THEME, which).apply();
                    adapter.updateItem(0, getCurrentThemeName());
                    dialog.dismiss();
                    SettingsActivity.applyThemeFromSettings(requireContext());
                    requireActivity().recreate();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create().show();
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
                SettingsActivity.PALETTE_PURPLE,
                SettingsActivity.PALETTE_BLUE,
                SettingsActivity.PALETTE_GREEN,
                SettingsActivity.PALETTE_PINK,
                SettingsActivity.PALETTE_AMOLED
        };

        int currentPalette = prefs.getInt(SettingsActivity.KEY_COLOR_PALETTE, SettingsActivity.PALETTE_PURPLE);
        int initialSelection = 0;
        for (int i = 0; i < paletteIds.length; i++) {
            if (paletteIds[i] == currentPalette) {
                initialSelection = i;
                break;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Color Palette")
                .setSingleChoiceItems(palettes, initialSelection, (dialog, which) -> {
                    prefs.edit().putInt(SettingsActivity.KEY_COLOR_PALETTE, paletteIds[which]).apply();
                    adapter.updateItem(2, getCurrentColorPaletteName()); // Note: Index might vary, best to recreate
                    dialog.dismiss();
                    requireActivity().recreate();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create().show();
    }

    private void showLanguageDialog() {
        String[] languages = {
                getString(R.string.english),
                getString(R.string.german),
                getString(R.string.chinese),
                getString(R.string.indonesian)
        };

        String[] languageCodes = {
                SettingsActivity.LANGUAGE_ENGLISH,
                SettingsActivity.LANGUAGE_GERMAN,
                SettingsActivity.LANGUAGE_CHINESE,
                SettingsActivity.LANGUAGE_INDONESIAN
        };

        int currentIndex = getCurrentLanguageIndex();

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.language))
                .setSingleChoiceItems(languages, currentIndex, (dialog, which) -> {
                    prefs.edit().putString(SettingsActivity.KEY_LANGUAGE, languageCodes[which]).apply();
                    adapter.updateItem(2, getCurrentLanguageName());
                    dialog.dismiss();
                    requireActivity().recreate();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create().show();
    }

    private void showFreqUnitDialog() {
        String[] units = {
                getString(R.string.hz),
                getString(R.string.mhz),
                getString(R.string.ghz)
        };

        int currentUnit = prefs.getInt(SettingsActivity.KEY_FREQ_UNIT, SettingsActivity.FREQ_UNIT_MHZ);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.gpu_freq_unit))
                .setSingleChoiceItems(units, currentUnit, (dialog, which) -> {
                    prefs.edit().putInt(SettingsActivity.KEY_FREQ_UNIT, which).apply();
                    adapter.updateItem(3, getCurrentFreqUnitName());
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create().show();
    }

    private void showHelpDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.help))
                .setMessage(KonaBessStr.generic_help(requireActivity()))
                .setPositiveButton(getString(R.string.ok), null)
                .setNeutralButton(getString(R.string.about),
                        (dialog, which) -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                                requireContext())
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
                                                setData(Uri.parse("https://github.com/ireddragonicy"));
                                            }
                                        }))
                                .create().show())
                .create().show();
    }

    private void toggleAutoSave() {
        boolean currentState = prefs.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false);
        boolean newState = !currentState;

        prefs.edit().putBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, newState).apply();
        requireActivity().recreate();

        // Show toast to confirm the change
        String message = newState ? getString(R.string.auto_save_enabled_toast)
                : getString(R.string.auto_save_disabled_toast);
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private String getAutoSaveStatusText() {
        boolean isEnabled = prefs.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false);
        return isEnabled ? getString(R.string.common_on) : getString(R.string.common_off);
    }

    private void toggleDynamicColor() {
        boolean currentState = prefs.getBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, true);
        prefs.edit().putBoolean(SettingsActivity.KEY_DYNAMIC_COLOR, !currentState).apply();
        requireActivity().recreate();
    }

    private String getCurrentThemeName() {
        String[] names = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };
        return names[prefs.getInt(SettingsActivity.KEY_THEME, SettingsActivity.THEME_SYSTEM)];
    }

    private String getCurrentLanguageName() {
        String[] names = {
                getString(R.string.english),
                getString(R.string.german),
                getString(R.string.chinese),
                getString(R.string.indonesian)
        };
        String[] codes = {
                SettingsActivity.LANGUAGE_ENGLISH,
                SettingsActivity.LANGUAGE_GERMAN,
                SettingsActivity.LANGUAGE_CHINESE,
                SettingsActivity.LANGUAGE_INDONESIAN
        };

        String currentLang = prefs.getString(SettingsActivity.KEY_LANGUAGE, SettingsActivity.LANGUAGE_ENGLISH);
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(currentLang)) {
                return names[i];
            }
        }
        return names[0];
    }

    private String getCurrentFreqUnitName() {
        String[] names = {
                getString(R.string.hz),
                getString(R.string.mhz),
                getString(R.string.ghz)
        };
        return names[prefs.getInt(SettingsActivity.KEY_FREQ_UNIT, SettingsActivity.FREQ_UNIT_MHZ)];
    }

    private String getCurrentColorPaletteName() {
        int palette = prefs.getInt(SettingsActivity.KEY_COLOR_PALETTE, SettingsActivity.PALETTE_PURPLE);
        switch (palette) {
            case SettingsActivity.PALETTE_PURPLE:
                return "Purple & Teal";
            case SettingsActivity.PALETTE_BLUE:
                return "Blue & Orange";
            case SettingsActivity.PALETTE_GREEN:
                return "Green & Red";
            case SettingsActivity.PALETTE_PINK:
                return "Pink & Cyan";
            case SettingsActivity.PALETTE_AMOLED:
                return "Pure AMOLED (Black)";
            default:
                return "Purple & Teal";
        }
    }

    private int getCurrentLanguageIndex() {
        String[] codes = {
                SettingsActivity.LANGUAGE_ENGLISH,
                SettingsActivity.LANGUAGE_GERMAN,
                SettingsActivity.LANGUAGE_CHINESE,
                SettingsActivity.LANGUAGE_INDONESIAN
        };
        String currentLang = prefs.getString(SettingsActivity.KEY_LANGUAGE, SettingsActivity.LANGUAGE_ENGLISH);

        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(currentLang)) {
                return i;
            }
        }
        return 0;
    }
}
