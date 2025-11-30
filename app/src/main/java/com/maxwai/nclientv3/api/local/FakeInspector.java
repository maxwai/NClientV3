package com.maxwai.nclientv3.api.local;

import com.maxwai.nclientv3.LocalActivity;
import com.maxwai.nclientv3.adapters.LocalAdapter;
import com.maxwai.nclientv3.components.ThreadAsyncTask;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.File;
import java.util.ArrayList;

public class FakeInspector extends ThreadAsyncTask<LocalActivity, LocalActivity, LocalActivity> {
    private final ArrayList<LocalGallery> galleries;
    private final ArrayList<String> invalidPaths;
    private LocalAdapter localAdapter;
    private final File folder;

    public FakeInspector(LocalActivity activity, File folder) {
        super(activity);
        this.folder = new File(folder, "Download");
        galleries = new ArrayList<>();
        invalidPaths = new ArrayList<>();
    }


    @Override
    protected LocalActivity doInBackground(LocalActivity activity) {
        localAdapter = new LocalAdapter(activity, new ArrayList<>());
        activity.setAdapter(localAdapter);
        if (!this.folder.exists()) return activity;
        publishProgress(activity);
        File parent = this.folder;
        //noinspection ResultOfMethodCallIgnored
        parent.mkdirs();
        File[] files = parent.listFiles();
        if (files == null) return activity;
        for (File f : files) if (f.isDirectory()) createGallery(f);
        for (String x : invalidPaths) LogUtility.d("Invalid path: " + x);
        localAdapter.addGalleries(galleries);
        galleries.clear();
        return activity;
    }

    @Override
    protected void onProgressUpdate(LocalActivity values) {
        values.getRefresher().setRefreshing(true);
    }

    @Override
    protected void onPostExecute(LocalActivity activity) {
        activity.getRefresher().setRefreshing(false);
    }

    private void createGallery(final File file) {
        LocalGallery lg = new LocalGallery(file, false);
        if (lg.isValid()) {
            galleries.add(lg);
            if (galleries.size() == 50){
                localAdapter.addGalleries(galleries);
                galleries.clear();
            }
        } else {
            LogUtility.e(lg);
            invalidPaths.add(file.getAbsolutePath());
        }
    }
}
