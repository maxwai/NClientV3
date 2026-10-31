package com.maxwai.nclientv3.api;

import com.maxwai.nclientv3.RandomActivity;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.utility.ImageDownloadUtility;

import java.util.ArrayList;
import java.util.List;

public class RandomLoader {
    private static final int MAXLOADED = 1;
    private final List<Gallery> galleries;
    private final RandomActivity activity;
    private boolean galleryHasBeenRequested;
    private boolean loading;

    private final InspectorV3.InspectorResponse response = new InspectorV3.DefaultInspectorResponse() {
        @Override
        public void onFailure(Exception e) {
            activity.runOnUiThread(() -> {
                loading = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                com.google.android.material.snackbar.Snackbar.make(activity.findViewById(android.R.id.content),
                    com.maxwai.nclientv3.utility.ConnectionFailure.describe(e), com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                    .setTextMaxLines(4).setAction(com.maxwai.nclientv3.R.string.site_connection,
                        v -> activity.openSiteConnection(RandomLoader.this::requestGallery)).show();
            });
        }

        @Override
        public void onSuccess(List<GenericGallery> galleryList) {
            activity.runOnUiThread(() -> accept(galleryList));
        }

        private void accept(List<GenericGallery> galleryList) {
            loading = false;
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (galleryList.isEmpty() || !galleryList.get(0).isValid()) {
                onFailure(new InspectorV3.InvalidResponseException());
                return;
            }
            Gallery gallery = (Gallery) galleryList.get(0);
            galleries.add(gallery);
            ImageDownloadUtility.preloadImage(activity, gallery.getCover());
            if (galleryHasBeenRequested)
                requestGallery();//requestGallery will call loadRandomGallery
            else if (galleries.size() < MAXLOADED) loadRandomGallery();
        }
    };

    public RandomLoader(RandomActivity activity) {
        this.activity = activity;
        galleries = new ArrayList<>(MAXLOADED);
        galleryHasBeenRequested = RandomActivity.loadedGallery == null;
        loadRandomGallery();
    }

    private void loadRandomGallery() {
        if (loading || activity.isFinishing() || activity.isDestroyed() || galleries.size() >= MAXLOADED) return;
        loading = true;
        InspectorV3.randomInspector(activity, response, false).start();
    }

    public void requestGallery() {
        galleryHasBeenRequested = true;
        for (int i = 0; i < galleries.size(); i++) {
            if (galleries.get(i) == null)
                galleries.remove(i--);
        }
        if (!galleries.isEmpty()) {
            Gallery gallery = galleries.remove(0);
            activity.runOnUiThread(() -> activity.loadGallery(gallery));
            galleryHasBeenRequested = false;
        }
        loadRandomGallery();
    }
}
