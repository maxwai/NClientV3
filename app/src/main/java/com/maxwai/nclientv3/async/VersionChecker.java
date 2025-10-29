package com.maxwai.nclientv3.async;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.maxwai.nclientv3.BuildConfig;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class VersionChecker {
    private static final String RELEASE_API_URL = "https://api.github.com/repos/maxwai/NClientV3/releases";
    private static final String LATEST_RELEASE_URL = "https://github.com/maxwai/NClientV3/releases/latest";
    private static String latest = null;
    private final AppCompatActivity context;
    private String downloadUrl;

    public VersionChecker(AppCompatActivity context, final boolean silent) {
        boolean withPrerelease = Global.isEnableBeta();
        this.context = context;
        if (latest != null && Global.hasStoragePermission(context)) {
            downloadVersion(latest);
            latest = null;
            return;
        }
        String actualVersionName = Global.getVersionName(context);
        LogUtility.d("ACTUAL VERSION: " + actualVersionName);
        Global.getClient(context).newCall(new Request.Builder().url(RELEASE_API_URL).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                context.runOnUiThread(() -> {
                    LogUtility.e(e.getLocalizedMessage(), e);
                    if (!silent)
                        Toast.makeText(context, R.string.error_retrieving, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                GitHubRelease release;
                try (JsonReader jr = new JsonReader(response.body().charStream())) {
                    release = parseVersionJson(jr, withPrerelease);
                }
                if (release == null) {
                    release = new GitHubRelease();
                    release.versionCode = actualVersionName;
                }
                downloadUrl = release.downloadUrl;
                GitHubRelease finalRelease = release;
                context.runOnUiThread(() -> {
                    boolean newer;
                    try {
                        newer = finalRelease.isNewerThenVersion(actualVersionName);
                    } catch (IllegalStateException ignored) {
                        newer = false;
                    }
                    if (downloadUrl == null || !newer) {
                        if (!silent)
                            Toast.makeText(context, R.string.no_updates_found, Toast.LENGTH_SHORT).show();
                    } else {
                        LogUtility.d("Executing false");
                        createDialog(actualVersionName, finalRelease);
                    }
                });
            }
        });
    }

    private static GitHubRelease parseVersionJson(JsonReader jr, boolean withPrerelease) throws IOException {
        try {
            GitHubRelease release;
            jr.beginArray();
            while (jr.hasNext()) {
                release = parseVersion(jr, withPrerelease);
                if (release != null)
                    return release;
            }
        } catch (IllegalStateException ignore) {
        }
        return null;
    }

    private static GitHubRelease parseVersion(JsonReader jr, boolean withPrerelease) throws IOException {
        GitHubRelease release = new GitHubRelease();
        boolean invalid = false;
        jr.beginObject();

        while (jr.peek() != JsonToken.END_OBJECT) {
            switch (jr.nextName()) {
                case "tag_name":
                    release.versionCode = jr.nextString();
                    break;
                case "body":
                    release.body = jr.nextString();
                    break;
                case "prerelease":
                    release.beta = jr.nextBoolean();
                    if (release.beta && !withPrerelease)
                        invalid = true;
                    break;
                case "assets":
                    jr.beginArray();
                    while (jr.hasNext()) {
                        if (release.downloadUrl != null) {
                            jr.skipValue();
                            continue;
                        }
                        release.downloadUrl = getDownloadUrl(jr);
                        //noinspection ConstantValue
                        if ((BuildConfig.FLAVOR.equals("pre28") && !release.downloadUrl.contains("pre28")) ||
                            (BuildConfig.FLAVOR.equals("post28") && release.downloadUrl.contains("pre28")))
                            release.downloadUrl = null;
                    }
                    jr.endArray();
                    break;
                default:
                    jr.skipValue();
                    break;
            }
        }
        jr.endObject();

        return invalid ? null : release;
    }

    private static String getDownloadUrl(JsonReader jr) throws IOException {
        String url = null;
        jr.beginObject();
        while (jr.peek() != JsonToken.END_OBJECT) {
            if ("browser_download_url".equals(jr.nextName()))
                url = jr.nextString();
            else jr.skipValue();
        }
        jr.endObject();
        return url;
    }

    private void createDialog(String versionName, GitHubRelease release) {
        String finalBody = release.body;
        String latestVersion = release.versionCode;
        boolean beta = release.beta;
        if (finalBody == null) return;
        finalBody = finalBody
            .replace("\r\n", "\n")//Remove ugly newline
            .replace("NClientV3 " + latestVersion, "")//remove version header
            .replaceAll("(\\s*\n\\s*)+", "\n")//remove multiple newline
            .replaceAll("\\(.*\\)", "").trim();//remove things between ()
        LogUtility.d("Evaluated: " + finalBody);
        LogUtility.d("Creating dialog");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        LogUtility.d("" + context);
        builder.setTitle(beta ? R.string.new_beta_version_found : R.string.new_version_found);
        builder.setIcon(R.drawable.ic_file);
        builder.setMessage(context.getString(R.string.update_version_format, versionName, latestVersion, finalBody));
        builder.setPositiveButton(R.string.install, (dialog, which) -> {
            if (Global.hasStoragePermission(context)) downloadVersion(latestVersion);
            else {
                latest = latestVersion;
                context.runOnUiThread(() -> context.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 2));
            }
        }).setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.github, (dialog, which) -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_RELEASE_URL));
                context.startActivity(browserIntent);
            });
        if (!context.isFinishing()) builder.show();
    }

    private void downloadVersion(String latestVersion) {
        final File f = new File(Global.UPDATEFOLDER, "NClientV3_" + latestVersion + ".apk");
        if (f.exists()) {
            if (context.getSharedPreferences("Settings", 0).getBoolean("downloaded", false)) {
                installApp(f);
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        if (downloadUrl == null) return;
        LogUtility.d(f.getAbsolutePath());
        Global.getClient(context).newCall(new Request.Builder().url(downloadUrl).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                context.runOnUiThread(() -> Toast.makeText(context, R.string.download_update_failed, Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                context.getSharedPreferences("Settings", 0).edit().putBoolean("downloaded", false).apply();
                if (Global.UPDATEFOLDER == null) {
                    Global.initStorage(context);
                }
                //noinspection ResultOfMethodCallIgnored
                Global.UPDATEFOLDER.mkdirs();
                //noinspection ResultOfMethodCallIgnored
                f.createNewFile();
                try (FileOutputStream stream = new FileOutputStream(f);
                     InputStream stream1 = response.body().byteStream()) {
                    int read;
                    byte[] bytes = new byte[1024];
                    while ((read = stream1.read(bytes)) != -1) {
                        stream.write(bytes, 0, read);
                    }
                    stream.flush();
                }
                context.getSharedPreferences("Settings", 0).edit().putBoolean("downloaded", true).apply();
                installApp(f);
            }
        });
    }

    private void installApp(File f) {
        try {
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            @SuppressLint("RequestInstallPackagesPolicy") Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(apkUri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (IllegalArgumentException ignore) {
            context.runOnUiThread(() -> Toast.makeText(context, context.getString(R.string.downloaded_update_at, f.getAbsolutePath()), Toast.LENGTH_SHORT).show());

        }
    }


    public static class GitHubRelease {
        // regex to get version in the form: MM.mm.pp(-suffix)
        private final static String regex = "(\\d+)\\.(\\d+)\\.(\\d+)(?:-((?:pre|rc)\\d))?";
        String versionCode, body, downloadUrl;
        boolean beta;

        public boolean isNewerThenVersion(String currentVersion) throws IllegalArgumentException {
            Matcher matcher = Pattern.compile(regex).matcher(currentVersion);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Current Version not in format");
            }
            int[] curNumbers = new int[3];
            String curSuffix;
            try {
                curNumbers[0] = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
                curNumbers[1] = Integer.parseInt(Objects.requireNonNull(matcher.group(2)));
                curNumbers[2] = Integer.parseInt(Objects.requireNonNull(matcher.group(3)));
                curSuffix = matcher.group(4);
            } catch (NumberFormatException ignored) {
                throw new IllegalStateException("Current Version not in format");
            }

            matcher = Pattern.compile(regex).matcher(versionCode);
            if (!matcher.find()) {
                throw new IllegalArgumentException("New Version not in format");
            }
            int[] newNumbers = new int[3];
            String newSuffix;
            try {
                newNumbers[0] = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
                newNumbers[1] = Integer.parseInt(Objects.requireNonNull(matcher.group(2)));
                newNumbers[2] = Integer.parseInt(Objects.requireNonNull(matcher.group(3)));
                newSuffix = matcher.group(4);
            } catch (NumberFormatException ignored) {
                throw new IllegalStateException("New Version not in format");
            }

            for (int i = 0; i < curNumbers.length; i++) {
                if (newNumbers[i] < curNumbers[i]) {
                    return false;
                } else if (newNumbers[i] > curNumbers[i]) {
                    return true;
                }
            }
            // At this point only the suffix may be different

            if (curSuffix == null && newSuffix == null) {
                return false;
            } else if (curSuffix == null) { // newSuffix != null
                // our current version doesn't have a suffix but the newest has one.
                // as suffixes are only for pre release, we have a newer version
                return false;
            } else if (newSuffix == null) { // curSuffix != null
                // our current version has a suffix but the newest has none.
                // as suffixes are only for pre release, there is a newer version
                return true;
            }

            // Hierarchy of pre release: preX -> rcX
            if (curSuffix.startsWith("pre") && newSuffix.startsWith("rc")) {
                return true;
            } else if (curSuffix.startsWith("rc") && newSuffix.startsWith("pre")) {
                return false;
            }

            if (curSuffix.startsWith("pre") && newSuffix.startsWith("pre")) {
                int curPreRelease, newPreRelease;
                try {
                    curPreRelease = Integer.parseInt(curSuffix.substring(3));
                    newPreRelease = Integer.parseInt(newSuffix.substring(3));
                } catch (NumberFormatException ignored) {
                    throw new IllegalStateException("New Version not in format");
                }
                return newPreRelease > curPreRelease;
            }

            if (curSuffix.startsWith("rc") && newSuffix.startsWith("rc")) {
                int curPreRelease, newPreRelease;
                try {
                    curPreRelease = Integer.parseInt(curSuffix.substring(2));
                    newPreRelease = Integer.parseInt(newSuffix.substring(2));
                } catch (NumberFormatException ignored) {
                    throw new IllegalStateException("New Version not in format");
                }
                return newPreRelease > curPreRelease;
            }

            throw new IllegalStateException("Suffix was not formated as expected");
        }
    }
}
