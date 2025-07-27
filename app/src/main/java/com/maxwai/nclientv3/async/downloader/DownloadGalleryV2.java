package com.maxwai.nclientv3.async.downloader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.maxwai.nclientv3.api.SimpleGallery;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class DownloadGalleryV2 extends Worker {
    private static final ReentrantLock lock = new ReentrantLock();

    public DownloadGalleryV2(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void downloadGallery(Context context, GenericGallery gallery) {
        if (gallery.isValid() && gallery instanceof Gallery)
            downloadGallery(context, (Gallery) gallery);
        if (gallery.getId() > 0) {
            if (gallery instanceof SimpleGallery) {
                SimpleGallery simple = (SimpleGallery) gallery;
                downloadGallery(context, gallery.getTitle(), simple.getThumbnail(), simple.getId());
            } else downloadGallery(context, null, null, gallery.getId());
        }
    }

    private static void downloadGallery(Context context, String title, Uri thumbnail, int id) {
        if (id < 1) return;
        DownloadQueue.add(new GalleryDownloaderManager(context, title, thumbnail, id));
        startWork(context);
    }

    private static void downloadGallery(Context context, Gallery gallery) {
        downloadGallery(context, gallery, 0, gallery.getPageCount() - 1);
    }

    private static void downloadGallery(Context context, Gallery gallery, int start, int end) {
        DownloadQueue.add(new GalleryDownloaderManager(context, gallery, start, end));
        startWork(context);
    }

    public static void loadDownloads(Context context) {
        try {
            List<GalleryDownloaderManager> g = Queries.DownloadTable.getAllDownloads(context);
            for (GalleryDownloaderManager gg : g) {
                gg.downloader().setStatus(GalleryDownloaderV2.Status.PAUSED);
                DownloadQueue.add(gg);
            }
            new PageChecker().start();
            startWork(context);
        } catch (IOException e) {
            LogUtility.e(e, e);
        }
    }

    public static void downloadRange(Context context, Gallery gallery, int start, int end) {
        downloadGallery(context, gallery, start, end);
    }

    public static void startWork(@Nullable Context context) {
        if (context != null) {
            WorkRequest DownloadGalleryWorkRequest = new OneTimeWorkRequest.Builder(DownloadGalleryV2.class).build();
            WorkManager.getInstance(context).enqueue(DownloadGalleryWorkRequest);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        lock.lock();
        try {
            obtainData();
            GalleryDownloaderManager entry = DownloadQueue.fetch();
            if (entry != null) {
                LogUtility.d("Downloading: " + entry.downloader().getId());
                if (entry.downloader().downloadGalleryData()) {
                    entry.downloader().download();
                }
            }
        } finally {
            lock.unlock();
        }
        return Result.success();
    }

    private void obtainData() {
        GalleryDownloaderV2 downloader = DownloadQueue.fetchForData();
        while (downloader != null) {
            downloader.downloadGalleryData();
            Utility.threadSleep(100);
            downloader = DownloadQueue.fetchForData();
        }
    }


}
