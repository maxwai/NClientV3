package com.maxwai.nclientv3.components.activities;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.async.ScrapeTags;
import com.maxwai.nclientv3.async.database.DatabaseHelper;
import com.maxwai.nclientv3.async.downloader.DownloadGalleryV2;
import com.maxwai.nclientv3.settings.Database;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.TagV2;
import com.maxwai.nclientv3.utility.network.NetworkUtil;

public class CrashApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Global.initStorage(this);
        //noinspection resource
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
        registerActivityLifecycleCallbacks(new CustomActivityLifecycleCallback());
    }

    private void afterUpdateChecks(SharedPreferences preferences, String oldVersion) {
        SharedPreferences.Editor editor = preferences.edit();
        removeOldUpdates();
        //update tags
        ScrapeTags.startWork(this);
        if ("0.0.0".equals(oldVersion))
            editor.putBoolean(getString(R.string.preference_key_check_update), true);
        editor.apply();
        Global.setLastVersion(this);
    }


    private void removeOldUpdates() {
        if (!Global.hasStoragePermission(this)) return;
        Global.recursiveDelete(Global.UPDATEFOLDER);
        //noinspection ResultOfMethodCallIgnored
        Global.UPDATEFOLDER.mkdir();
    }

    private static class CustomActivityLifecycleCallback implements ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            View rootView = activity.getWindow().getDecorView().getRootView();
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                    barsInsets.left,
                    barsInsets.top,
                    barsInsets.right,
                    barsInsets.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {

        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {

        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {

        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {

        }
    }
}
