package com.maxwai.nclientv3.async.database.export;

import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.SettingsActivity;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.IOException;

public class Manager extends Thread {
    @NonNull
    private final Uri file;
    @NonNull
    private final SettingsActivity context;
    private final boolean export;
    private final Runnable end;

    public Manager(@NonNull Uri file, @NonNull SettingsActivity context, boolean export, Runnable end) {
        this.file = file;
        this.context = context;
        this.export = export;
        this.end = end;
    }


    @Override
    public void run() {
        try {
            if (export) Exporter.exportData(context, file);
            else Importer.importData(context, file);
            context.runOnUiThread(end);
        } catch (IOException e) {
            LogUtility.e(e, e);
            context.runOnUiThread(() -> Toast.makeText(context, R.string.failed, Toast.LENGTH_SHORT).show());
        }
    }
}
