package com.maxwai.nclientv3.components.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import com.maxwai.nclientv3.BuildConfig;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.async.ScrapeTags;
import com.maxwai.nclientv3.async.database.DatabaseHelper;
import com.maxwai.nclientv3.async.downloader.DownloadGalleryV2;
import com.maxwai.nclientv3.settings.Database;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.TagV2;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.network.NetworkUtil;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// TODO: remove or replace this Class
public class CrashApplication extends MultiDexApplication {
    private static final String SIGNATURE_GITHUB = "ce96fdbcc89991f083320140c148db5f";

    @Override
    public void onCreate() {
        super.onCreate();
        Global.initLanguage(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Global.initStorage(this);
        Database.setDatabase(new DatabaseHelper(getApplicationContext()).getWritableDatabase());
        String version = Global.getLastVersion(this), actualVersion = Global.getVersionName(this);
        SharedPreferences preferences = getSharedPreferences("Settings", 0);
        if (!actualVersion.equals(version))
            afterUpdateChecks(preferences, version);

        Global.initFromShared(this);
        NetworkUtil.initConnectivity(this);
        TagV2.initMinCount(this);
        TagV2.initSortByName(this);
        DownloadGalleryV2.loadDownloads(this);
    }

    private boolean signatureCheck() {
        try {
            @SuppressLint("PackageManagerGetSignatures")
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                getPackageName(), PackageManager.GET_SIGNATURES);
            //note sample just checks the first signature

            for (Signature signature : packageInfo.signatures) {
                // MD5 is used because it is not a secure data
                MessageDigest m = MessageDigest.getInstance("MD5");
                m.update(signature.toByteArray());
                String hash = new BigInteger(1, m.digest()).toString(16);
                LogUtility.d("Find signature: " + hash);
                if (SIGNATURE_GITHUB.equals(hash)) return true;
            }
        } catch (NullPointerException | PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            LogUtility.e("Error checking the signature", e);
        }
        return false;
    }

    private void afterUpdateChecks(SharedPreferences preferences, String oldVersion) {
        SharedPreferences.Editor editor = preferences.edit();
        removeOldUpdates();
        //update tags
        ScrapeTags.startWork(this);
        if ("0.0.0".equals(oldVersion))
            editor.putBoolean(getString(R.string.key_check_update), signatureCheck());
        editor.apply();
        Global.setLastVersion(this);
    }


    private void removeOldUpdates() {
        if (!Global.hasStoragePermission(this)) return;
        Global.recursiveDelete(Global.UPDATEFOLDER);
        Global.UPDATEFOLDER.mkdir();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder()
            .withBuildConfigClass(BuildConfig.class)
            .withReportContent(ReportField.PACKAGE_NAME,
                ReportField.BUILD_CONFIG,
                ReportField.APP_VERSION_CODE,
                ReportField.STACK_TRACE,
                ReportField.ANDROID_VERSION,
                ReportField.LOGCAT);

        ACRA.init(this, builder);
        ACRA.getErrorReporter().setEnabled(getSharedPreferences("Settings", 0).getBoolean(getString(R.string.key_send_report), false));
    }
}
