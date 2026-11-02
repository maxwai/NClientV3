package com.maxwai.nclientv3.utility;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.maxwai.nclientv3.*;
import com.maxwai.nclientv3.settings.Global;

/** Remembers the preferred reading library, not a device-wide network setting. */
public final class ReadingSource {
    public static final int PERMISSION = 4247;
    private ReadingSource() { }
    public static boolean downloadedOnly(Context context) { return context.getSharedPreferences("Settings", 0).getBoolean("downloaded_reading", false); }
    public static void remember(Context context, boolean downloaded) { context.getSharedPreferences("Settings", 0).edit().putBoolean("downloaded_reading", downloaded).apply(); }
    public static Class<? extends Activity> startup(Context context) {
        return downloadedOnly(context) && Global.hasStoragePermission(context) ? LocalActivity.class : MainActivity.class;
    }
    public static void downloads(Activity activity) {
        if (!Global.hasStoragePermission(activity)) {
            activity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION);
            return;
        }
        remember(activity, true);
        if (activity instanceof LocalActivity) activity.recreate();
        else { activity.startActivity(new Intent(activity, LocalActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); activity.finish(); }
    }
    public static void online(Activity activity) {
        remember(activity, false);
        activity.startActivity(new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        if (!(activity instanceof MainActivity)) activity.finish();
    }
}
