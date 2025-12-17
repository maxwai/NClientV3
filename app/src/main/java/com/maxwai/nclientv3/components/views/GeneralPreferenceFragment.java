package com.maxwai.nclientv3.components.views;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.JsonWriter;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.CopyToClipboardActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.SettingsActivity;
import com.maxwai.nclientv3.StatusManagerActivity;
import com.maxwai.nclientv3.async.MetadataFetcher;
import com.maxwai.nclientv3.async.VersionChecker;
import com.maxwai.nclientv3.components.activities.CrashApplication;
import com.maxwai.nclientv3.components.launcher.LauncherCalculator;
import com.maxwai.nclientv3.components.launcher.LauncherReal;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class GeneralPreferenceFragment extends PreferenceFragmentCompat {
    private SettingsActivity act;

    public void setAct(SettingsActivity act) {
        this.act = act;
    }

    public void setType(SettingsActivity.Type type) {
        switch (type) {
            case MAIN:
                mainMenu();
                break;
            case COLUMN:
                columnMenu();
                break;
            case DATA:
                dataMenu();
                break;
        }
    }

    private void dataMenu() {
        addPreferencesFromResource(R.xml.settings_data);
        SeekBarPreference mobile = Objects.requireNonNull(findPreference(getString(R.string.key_mobile_usage)));
        SeekBarPreference wifi = Objects.requireNonNull(findPreference(getString(R.string.key_wifi_usage)));
        mobile.setOnPreferenceChangeListener((preference, newValue) -> {
            mobile.setTitle(getDataUsageString((Integer) newValue));
            return true;
        });
        wifi.setOnPreferenceChangeListener((preference, newValue) -> {
            wifi.setTitle(getDataUsageString((Integer) newValue));
            return true;
        });
        mobile.setTitle(getDataUsageString(mobile.getValue()));
        wifi.setTitle(getDataUsageString(wifi.getValue()));
        mobile.setUpdatesContinuously(true);
        wifi.setUpdatesContinuously(true);
    }

    private int getDataUsageString(int val) {
        switch (val) {
            case 0:
                return R.string.data_usage_no;
            case 1:
                return R.string.data_usage_thumb;
            case 2:
                return R.string.data_usage_full;
        }
        return R.string.data_usage_full;
    }

    private LocaleListCompat getLocaleListFromXml() {
        List<CharSequence> tagsList = new ArrayList<>();
        try {
            @SuppressLint("DiscouragedApi") int id = getResources().getIdentifier(
                "_generated_res_locale_config",
                "xml",
                requireContext().getPackageName()
            );
            XmlPullParser xpp = getResources().getXml(id);
            while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                    if (Objects.equals(xpp.getName(), "locale")) {
                        tagsList.add(xpp.getAttributeValue(0));
                    }
                }
                xpp.next();
            }
        } catch (XmlPullParserException | IOException e) {
            LogUtility.w("Problem parsing locales xml", e);
        }
        return LocaleListCompat.forLanguageTags(tagsList.stream()
            .reduce((a, b) -> a + "," + b)
            .orElse("")
            .toString());
    }

    private void fillRoba() {
        LocaleListCompat setLocaleList = AppCompatDelegate.getApplicationLocales();
        Locale actualLocale = setLocaleList.isEmpty() ? Locale.ENGLISH : Objects.requireNonNull(setLocaleList.get(0));

        ListPreference preference = Objects.requireNonNull(findPreference(getString(R.string.preference_key_language)));
        LocaleListCompat localeList = getLocaleListFromXml();

        String[] languagesEntry = new String[localeList.size() + 1];
        String[] languagesNames = new String[localeList.size() + 1];
        // System language
        languagesEntry[0] = getString(R.string.key_default_value);
        languagesNames[0] = Character.toUpperCase(getString(R.string.system_default).charAt(0))
            + getString(R.string.system_default).substring(1);
        // Other languages
        for (int i = 0; i < localeList.size(); i++) {
            Locale locale = Objects.requireNonNull(localeList.get(i));
            languagesEntry[i + 1] = locale.toLanguageTag();
            languagesNames[i + 1] = Character.toUpperCase(locale.getDisplayName(actualLocale).charAt(0))
                + locale.getDisplayName(actualLocale).substring(1);
        }

        preference.setEntryValues(languagesEntry);
        preference.setEntries(languagesNames);

    }

    @SuppressLint("ApplySharedPref")
    private void mainMenu() {
        addPreferencesFromResource(R.xml.settings);

        fillRoba();

        {
            Preference statusScreen = Objects.requireNonNull(findPreference(getString(R.string.preference_key_status_screen)));
            statusScreen.setOnPreferenceClickListener(preference -> {
                Intent i = new Intent(act, StatusManagerActivity.class);
                act.runOnUiThread(() -> act.startActivity(i));
                return false;
            });
        }
        {
            Preference colScreen = Objects.requireNonNull(findPreference(getString(R.string.preference_key_col_screen)));
            colScreen.setOnPreferenceClickListener(preference -> {
                Intent i = new Intent(act, SettingsActivity.class);
                i.putExtra(act.getPackageName() + ".TYPE", SettingsActivity.Type.COLUMN.ordinal());
                act.runOnUiThread(() -> act.startActivity(i));
                return false;
            });
        }
        {
            Preference dataScreen = Objects.requireNonNull(findPreference(getString(R.string.preference_key_data_screen)));
            dataScreen.setOnPreferenceClickListener(preference -> {
                Intent i = new Intent(act, SettingsActivity.class);
                i.putExtra(act.getPackageName() + ".TYPE", SettingsActivity.Type.DATA.ordinal());
                act.runOnUiThread(() -> act.startActivity(i));
                return false;
            });
        }
        {
            Preference fetchMetadata = Objects.requireNonNull(findPreference(getString(R.string.preference_key_fetch_metadata)));
            fetchMetadata.setVisible(Global.hasStoragePermission(act));
            fetchMetadata.setOnPreferenceClickListener(preference -> {
                new Thread(new MetadataFetcher(act)).start();
                return true;
            });
        }
        {
            Preference fakeIcon = Objects.requireNonNull(findPreference(getString(R.string.preference_key_fake_icon)));
            fakeIcon.setOnPreferenceChangeListener((preference, newValue) -> {
                PackageManager pm = act.getPackageManager();
                ComponentName name1 = new ComponentName(act, LauncherReal.class);
                ComponentName name2 = new ComponentName(act, LauncherCalculator.class);
                if ((boolean) newValue) {
                    changeLauncher(pm, name1, false);
                    changeLauncher(pm, name2, true);
                } else {
                    changeLauncher(pm, name1, true);
                    changeLauncher(pm, name2, false);
                }
                return true;
            });
        }
        {
            Preference useAccountTag = Objects.requireNonNull(findPreference(getString(R.string.preference_key_use_account_tag)));
            useAccountTag.setEnabled(Login.isLogged());
        }

        {
            Preference themeSelect = Objects.requireNonNull(findPreference(getString(R.string.preference_key_theme_select)));
            themeSelect.setOnPreferenceChangeListener((preference, newValue) -> {
                String newTheme = (String) newValue;
                String[] availableThemes = getResources().getStringArray(R.array.theme_data);
                assert availableThemes.length == 3;
                if (Arrays.stream(availableThemes).noneMatch(newTheme::equals)) {
                    return false;
                }
                act.getSharedPreferences("Settings", 0)
                    .edit()
                    .putBoolean(getString(R.string.preference_key_black_theme), newTheme.equals(availableThemes[2])) // black
                    .apply();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    int theme;
                    if (newTheme.equals(availableThemes[0])) { // light
                        theme = UiModeManager.MODE_NIGHT_NO;
                    } else if (newTheme.equals(availableThemes[1]) || newTheme.equals(availableThemes[2])) { // dark / black
                        theme = UiModeManager.MODE_NIGHT_YES;
                    } else {
                        return false;
                    }
                    UiModeManager uim = (UiModeManager) act.getSystemService(Context.UI_MODE_SERVICE);
                    uim.setApplicationNightMode(theme);
                } else {
                    CrashApplication.setDarkLightTheme(newTheme, act);
                }
                return true;
            });
        }
        {
            Preference keyLanguage = Objects.requireNonNull(findPreference(getString(R.string.preference_key_language)));
            keyLanguage.setOnPreferenceChangeListener((preference, newValue) -> {
                LocaleListCompat newLocale;
                if (newValue.equals(getString(R.string.key_default_value))) {
                    newLocale = LocaleListCompat.getEmptyLocaleList();
                } else {
                    newLocale = LocaleListCompat.forLanguageTags((String) newValue);
                }
                ContextCompat.getMainExecutor(act).execute(() -> AppCompatDelegate.setApplicationLocales(newLocale));
                return true;
            });
        }
        {
            Preference enableBeta = Objects.requireNonNull(findPreference(getString(R.string.preference_key_enable_beta)));
            enableBeta.setOnPreferenceChangeListener((preference, newValue) -> {
                //Instant update to allow search for updates
                Global.setEnableBeta((Boolean) newValue);
                return true;
            });
        }
        {
            Preference hasCredentials = Objects.requireNonNull(findPreference(getString(R.string.preference_key_has_credentials)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hasCredentials.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue.equals(Boolean.TRUE)) {
                        BiometricManager biometricManager = BiometricManager.from(act);
                        switch (biometricManager.canAuthenticate(BIOMETRIC_WEAK | DEVICE_CREDENTIAL)) {
                            case BiometricManager.BIOMETRIC_SUCCESS:
                                break;
                            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                                // Prompts the user to create credentials that your app accepts.
                                final Intent enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                                enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                    BIOMETRIC_WEAK | DEVICE_CREDENTIAL);
                                startActivity(enrollIntent);
                            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
                            case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
                                return false;
                        }
                    }
                    return true;
                });
            } else {
                hasCredentials.setEnabled(false);
                hasCredentials.setSummary(R.string.setting_device_credentials_low_sdk);
            }
        }

        {
            Preference keyVersion = Objects.requireNonNull(findPreference(getString(R.string.preference_key_version)));
            keyVersion.setTitle(getString(R.string.app_version_format, Global.getVersionName(act)));
        }
        {
            ListPreference savePath = Objects.requireNonNull(findPreference(getString(R.string.preference_key_save_path)));
            initStoragePaths(savePath);
            savePath.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!newValue.equals(getString(R.string.custom_path))) return true;
                manageCustomPath();
                return false;
            });
        }
        {
            //clear cache if pressed
            double cacheSize = Global.recursiveSize(act.getCacheDir()) / ((double) (1 << 20));
            Preference cache = Objects.requireNonNull(findPreference(getString(R.string.preference_key_cache)));
            cache.setSummary(getString(R.string.cache_size_formatted, cacheSize));
            cache.setOnPreferenceClickListener(preference -> {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(act);
                builder.setTitle(R.string.clear_cache);
                builder.setPositiveButton(R.string.yes, (dialog, which) -> {
                    Global.recursiveDelete(act.getCacheDir());
                    act.runOnUiThread(() -> {
                        Toast.makeText(act, act.getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show();
                        double cSize = Global.recursiveSize(act.getCacheDir()) / ((double) (2 << 20));
                        cache.setSummary(getString(R.string.cache_size_formatted, cSize));
                    });

                }).setNegativeButton(R.string.no, null).setCancelable(true);
                builder.show();

                return true;
            });
        }
        {
            Preference cookie = Objects.requireNonNull(findPreference(getString(R.string.preference_key_cookie)));
            cookie.setOnPreferenceClickListener(preference -> {
                Login.clearCookies(act);
                CookieManager.getInstance().removeAllCookies(null);
                return true;
            });
        }
        {
            Preference update = Objects.requireNonNull(findPreference(getString(R.string.preference_key_update)));
            update.setOnPreferenceClickListener(preference -> {
                new VersionChecker(act, false);
                return true;
            });
        }
        {
            Preference bug = Objects.requireNonNull(findPreference(getString(R.string.preference_key_bug)));
            bug.setOnPreferenceClickListener(preference -> {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/maxwai/NClientV3/issues/new"));
                startActivity(i);
                return true;
            });
        }
        {
            Preference bug = Objects.requireNonNull(findPreference(getString(R.string.preference_key_copy_logs)));
            bug.setOnPreferenceClickListener(preference -> {
                act.exportLogs();
                return true;
            });
        }
        {
            Preference copySettings = Objects.requireNonNull(findPreference(getString(R.string.preference_key_copy_settings)));
            copySettings.setOnPreferenceClickListener(preference -> {
                try {
                    CopyToClipboardActivity.copyTextToClipboard(act, getDataSettings(act));
                } catch (IOException e) {
                    LogUtility.e("Error copying settings into clipboard", e);
                    Toast.makeText(act, R.string.clipboard_settings_error, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
        {
            Preference export = Objects.requireNonNull(findPreference(getString(R.string.preference_key_export)));
            export.setOnPreferenceClickListener(preference -> {
                act.exportSettings();
                return true;
            });
        }
        {
            Preference _import = Objects.requireNonNull(findPreference(getString(R.string.preference_key_import)));
            _import.setOnPreferenceClickListener(preference -> {
                act.importSettings();
                return true;
            });
        }

        {
            ListPreference mirror = Objects.requireNonNull(findPreference(getString(R.string.preference_key_site_mirror)));
            mirror.setSummary(
                act.getSharedPreferences("Settings", Context.MODE_PRIVATE)
                    .getString(getString(R.string.preference_key_site_mirror), Utility.ORIGINAL_URL)
            );
            mirror.setOnPreferenceChangeListener((preference, newValue) -> {
                preference.setSummary(newValue.toString());
                return true;
            });
        }
    }

    public void manageCustomPath() {
        if (!Global.isExternalStorageManager()) {
            act.requestStorageManager();
            return;
        }
        final String key = getString(R.string.preference_key_save_path);
        Preference savePathPreference = Objects.requireNonNull(findPreference(key));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(act);
        AppCompatAutoCompleteTextView edit = (AppCompatAutoCompleteTextView) View.inflate(act, R.layout.autocomplete_entry, null);
        edit.setHint(R.string.insert_path);
        builder.setView(edit);
        builder.setTitle(R.string.insert_path);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            act.getSharedPreferences("Settings", Context.MODE_PRIVATE).edit().putString(key, edit.getText().toString()).apply();
            savePathPreference.setSummary(edit.getText().toString());
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void changeLauncher(PackageManager pm, ComponentName name, boolean enabled) {
        int enableState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(name, enableState, PackageManager.DONT_KILL_APP);
    }


    private void initStoragePaths(ListPreference storagePreference) {
        if (!Global.hasStoragePermission(act)) {
            storagePreference.setVisible(false);
            return;
        }
        List<File> files = Global.getUsableFolders(act);
        List<CharSequence> strings = new ArrayList<>(files.size() + 1);
        for (File f : files) {
            if (f != null)
                strings.add(f.getAbsolutePath());
        }
        strings.add(getString(R.string.custom_path));
        storagePreference.setEntries(strings.toArray(new CharSequence[0]));
        storagePreference.setEntryValues(strings.toArray(new CharSequence[0]));
        storagePreference.setSummary(
            act.getSharedPreferences("Settings", Context.MODE_PRIVATE)
                .getString(getString(R.string.preference_key_save_path), Global.MAINFOLDER.getParent())
        );
        storagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(newValue.toString());
            return true;
        });
    }

    private String getDataSettings(Context context) throws IOException {
        String[] names = new String[]{"Settings", "ScrapedTags"};
        try (StringWriter sw = new StringWriter();
             JsonWriter writer = new JsonWriter(sw)) {
            writer.setIndent("\t");

            writer.beginObject();
            for (String name : names)
                processSharedFromName(writer, context, name);
            writer.endObject();

            writer.flush();
            String settings = sw.toString();
            LogUtility.d(settings);
            return settings;
        }
    }

    private void processSharedFromName(JsonWriter writer, Context context, String name) throws IOException {
        writer.name(name);
        writer.beginObject();
        SharedPreferences preferences = context.getSharedPreferences(name, 0);
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            writeEntry(writer, entry);
        }
        writer.endObject();
    }

    private void writeEntry(JsonWriter writer, Map.Entry<String, ?> entry) throws IOException {
        writer.name(entry.getKey());
        if (entry.getValue() instanceof Integer) writer.value((Integer) entry.getValue());
        else if (entry.getValue() instanceof Boolean) writer.value((Boolean) entry.getValue());
        else if (entry.getValue() instanceof String) writer.value((String) entry.getValue());
        else if (entry.getValue() instanceof Long) writer.value((Long) entry.getValue());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName("Settings");
    }

    private void columnMenu() {
        addPreferencesFromResource(R.xml.settings_column);
    }

}
