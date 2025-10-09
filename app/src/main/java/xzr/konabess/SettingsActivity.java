package xzr.konabess;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

import xzr.konabess.adapters.ParamAdapter;
import xzr.konabess.utils.DialogUtil;

public class SettingsActivity extends Activity {
    private static final String PREFS_NAME = "KonaBessSettings";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_FREQ_UNIT = "freq_unit";
    
    public static final int FREQ_UNIT_HZ = 0;
    public static final int FREQ_UNIT_MHZ = 1;
    public static final int FREQ_UNIT_GHZ = 2;
    
    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_GERMAN = "de";
    public static final String LANGUAGE_CHINESE = "zh-rCN";
    public static final String LANGUAGE_INDONESIAN = "in";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved language
        applyLanguage();
        
        setTitle(getString(R.string.settings));
        showSettingsView();
    }

    private void showSettingsView() {
        LinearLayout mainView = new LinearLayout(this);
        mainView.setOrientation(LinearLayout.VERTICAL);
        setContentView(mainView);

        ListView listView = new ListView(this);
        ArrayList<ParamAdapter.item> items = new ArrayList<>();

        // Language setting
        items.add(new ParamAdapter.item() {{
            title = getString(R.string.language);
            subtitle = getCurrentLanguageName();
        }});

        // GPU Frequency Unit setting
        items.add(new ParamAdapter.item() {{
            title = getString(R.string.gpu_freq_unit);
            subtitle = getCurrentFreqUnitName();
        }});

        // Help
        items.add(new ParamAdapter.item() {{
            title = getString(R.string.help);
            subtitle = "View help and about information";
        }});

        listView.setAdapter(new ParamAdapter(items, this));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0:
                    showLanguageDialog();
                    break;
                case 1:
                    showFreqUnitDialog();
                    break;
                case 2:
                    showHelpDialog();
                    break;
            }
        });

        mainView.addView(listView);
    }

    private void showLanguageDialog() {
        String[] languages = {
            getString(R.string.english),
            getString(R.string.german),
            getString(R.string.chinese),
            getString(R.string.indonesian)
        };

        String[] languageCodes = {LANGUAGE_ENGLISH, LANGUAGE_GERMAN, LANGUAGE_CHINESE, LANGUAGE_INDONESIAN};
        int currentIndex = getCurrentLanguageIndex();

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.language))
                .setSingleChoiceItems(languages, currentIndex, (dialog, which) -> {
                    String selectedLanguage = languageCodes[which];
                    saveLanguage(selectedLanguage);
                    dialog.dismiss();
                    recreate(); // Restart activity to apply language change
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

        int currentUnit = getSavedFreqUnit();

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.gpu_freq_unit))
                .setSingleChoiceItems(units, currentUnit, (dialog, which) -> {
                    saveFreqUnit(which);
                    dialog.dismiss();
                    showSettingsView(); // Refresh the view
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create().show();
    }


    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.help))
                .setMessage(KonaBessStr.generic_help(this))
                .setPositiveButton(getString(R.string.ok), null)
                .setNeutralButton(getString(R.string.about),
                        (dialog, which) -> new AlertDialog.Builder(this)
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

    private String getCurrentLanguageName() {
        String currentLang = getSavedLanguage();
        switch (currentLang) {
            case LANGUAGE_ENGLISH:
                return getString(R.string.english);
            case LANGUAGE_GERMAN:
                return getString(R.string.german);
            case LANGUAGE_CHINESE:
                return getString(R.string.chinese);
            case LANGUAGE_INDONESIAN:
                return getString(R.string.indonesian);
            default:
                return getString(R.string.english);
        }
    }

    private String getCurrentFreqUnitName() {
        int unit = getSavedFreqUnit();
        switch (unit) {
            case FREQ_UNIT_HZ:
                return getString(R.string.hz);
            case FREQ_UNIT_MHZ:
                return getString(R.string.mhz);
            case FREQ_UNIT_GHZ:
                return getString(R.string.ghz);
            default:
                return getString(R.string.mhz);
        }
    }

    private int getCurrentLanguageIndex() {
        String currentLang = getSavedLanguage();
        switch (currentLang) {
            case LANGUAGE_ENGLISH:
                return 0;
            case LANGUAGE_GERMAN:
                return 1;
            case LANGUAGE_CHINESE:
                return 2;
            case LANGUAGE_INDONESIAN:
                return 3;
            default:
                return 0;
        }
    }

    private void saveLanguage(String language) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    private String getSavedLanguage() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH);
    }

    private void saveFreqUnit(int unit) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_FREQ_UNIT, unit).apply();
    }

    private int getSavedFreqUnit() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_FREQ_UNIT, FREQ_UNIT_MHZ);
    }

    private void applyLanguage() {
        String language = getSavedLanguage();
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
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
}
