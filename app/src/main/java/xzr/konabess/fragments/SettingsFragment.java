package xzr.konabess.fragments;

import android.app.AlertDialog;
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

import java.util.ArrayList;

import xzr.konabess.BuildConfig;
import xzr.konabess.KonaBessStr;
import xzr.konabess.R;
import xzr.konabess.SettingsActivity;
import xzr.konabess.adapters.SettingsAdapter;

public class SettingsFragment extends Fragment {
    private RecyclerView recyclerView;
    private SettingsAdapter adapter;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
            getCurrentThemeName()
        ));

        items.add(new SettingsAdapter.SettingItem(
            R.drawable.ic_tune,
            "Color Palette",
            "Choose your color scheme",
            getCurrentColorPaletteName()
        ));

        items.add(new SettingsAdapter.SettingItem(
            R.drawable.ic_language,
            getString(R.string.language),
            "Select your preferred language",
            getCurrentLanguageName()
        ));

        items.add(new SettingsAdapter.SettingItem(
            R.drawable.ic_frequency,
            getString(R.string.gpu_freq_unit),
            getString(R.string.freq_unit_description),
            getCurrentFreqUnitName()
        ));

        // Auto-save GPU freq table toggle
        items.add(new SettingsAdapter.SettingItem(
            R.drawable.ic_save,
            getString(R.string.auto_save_gpu_freq_table),
            getString(R.string.auto_save_gpu_freq_table_desc),
            getAutoSaveStatusText()
        ));

        items.add(new SettingsAdapter.SettingItem(
            R.drawable.ic_help,
            getString(R.string.help),
            "About and documentation",
            "Version " + BuildConfig.VERSION_NAME
        ));

        adapter = new SettingsAdapter(items, requireContext());
        adapter.setOnItemClickListener(position -> {
            switch (position) {
                case 0:
                    showThemeDialog();
                    break;
                case 1:
                    showColorPaletteDialog();
                    break;
                case 2:
                    showLanguageDialog();
                    break;
                case 3:
                    showFreqUnitDialog();
                    break;
                case 4:
                    toggleAutoSave();
                    break;
                case 5:
                    showHelpDialog();
                    break;
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

        new AlertDialog.Builder(requireContext())
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
            "Dynamic (Material You)",
            "Purple & Teal",
            "Blue & Orange",
            "Green & Red",
            "Pink & Cyan",
            "Pure AMOLED (Black)"
        };

    int currentPalette = prefs.getInt(SettingsActivity.KEY_COLOR_PALETTE, 0);

        new AlertDialog.Builder(requireContext())
                .setTitle("Color Palette")
                .setSingleChoiceItems(palettes, currentPalette, (dialog, which) -> {
                    prefs.edit().putInt(SettingsActivity.KEY_COLOR_PALETTE, which).apply();
                    adapter.updateItem(1, getCurrentColorPaletteName());
                    dialog.dismiss();
                    // Apply color palette by recreating activity
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

        new AlertDialog.Builder(requireContext())
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

        new AlertDialog.Builder(requireContext())
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
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.help))
                .setMessage(KonaBessStr.generic_help(requireActivity()))
                .setPositiveButton(getString(R.string.ok), null)
                .setNeutralButton(getString(R.string.about),
                        (dialog, which) -> new AlertDialog.Builder(requireContext())
                                .setTitle(getString(R.string.about))
                                .setMessage(getResources().getString(R.string.author) + " " +
                                        "xzr467706992 (LibXZR)\n" + getResources().getString(R.string.release_at) + " www.akr-developers.com\n")
                                .setPositiveButton(getString(R.string.ok), null)
                                .setNegativeButton("Github",
                                        (dialog1, which1) -> startActivity(new Intent() {{
                                            setAction(Intent.ACTION_VIEW);
                                            setData(Uri.parse("https://github.com/xzr467706992/KonaBess"));
                                        }}))
                                .setNeutralButton(getString(R.string.visit_akr),
                                        (dialog1, which1) -> startActivity(new Intent() {{
                                            setAction(Intent.ACTION_VIEW);
                                            setData(Uri.parse("https://www.akr-developers.com/d/441"));
                                        }})).create().show())
                .create().show();
    }

    private void toggleAutoSave() {
        boolean currentState = prefs.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false);
        boolean newState = !currentState;
        
        prefs.edit().putBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, newState).apply();
        adapter.updateItem(4, getAutoSaveStatusText());
        
        // Show toast to confirm the change
        String message = newState ? 
            getString(R.string.auto_save_enabled_toast) : 
            getString(R.string.auto_save_disabled_toast);
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private String getAutoSaveStatusText() {
        boolean isEnabled = prefs.getBoolean(SettingsActivity.KEY_AUTO_SAVE_GPU_TABLE, false);
        return isEnabled ? getString(R.string.common_on) : getString(R.string.common_off);
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
    int palette = prefs.getInt(SettingsActivity.KEY_COLOR_PALETTE, 0);
        switch (palette) {
            case 0: return "Dynamic (Material You)";
            case 1: return "Purple & Teal";
            case 2: return "Blue & Orange";
            case 3: return "Green & Red";
            case 4: return "Pink & Cyan";
            case 5: return "Pure AMOLED (Black)";
            default: return "Dynamic (Material You)";
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


