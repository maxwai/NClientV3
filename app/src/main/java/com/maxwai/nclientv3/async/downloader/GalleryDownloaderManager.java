package com.maxwai.nclientv3.async.downloader;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

import com.maxwai.nclientv3.GalleryActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.NotificationSettings;

import java.util.ConcurrentModificationException;
import java.util.Locale;

public class GalleryDownloaderManager {
    private final int notificationId = NotificationSettings.getNotificationId();
    private final GalleryDownloaderV2 downloaderV2;
    private final Context context;
    private NotificationCompat.Builder notification;
    private Gallery gallery;

    private final DownloadObserver observer = new DownloadObserver() {
        @Override
        public void triggerStartDownload(GalleryDownloaderV2 downloader) {
            gallery = downloader.getGallery();
            prepareNotification();
            notificationUpdate();
        }

        @Override
        public void triggerUpdateProgress(GalleryDownloaderV2 downloader, int reach, int total) {
            setPercentage(reach, total);
            notificationUpdate();
        }

        @Override
        public void triggerEndDownload(GalleryDownloaderV2 downloader) {
            endNotification();
            addClickListener();
            notificationUpdate();
            DownloadQueue.remove(downloader, false);
        }

        @Override
        public void triggerCancelDownload(GalleryDownloaderV2 downloader) {
            cancelNotification();
            Global.recursiveDelete(downloader.getFolder());
        }

        @Override
        public void triggerPauseDownload(GalleryDownloaderV2 downloader) {
            notificationUpdate();
        }
    };

    public GalleryDownloaderManager(Context context, Gallery gallery, int start, int end) {
        this.context = context;
        this.gallery = gallery;
        this.downloaderV2 = new GalleryDownloaderV2(context, gallery, start, end);
        this.downloaderV2.addObserver(observer);
    }

    public GalleryDownloaderManager(Context context, String title, Uri thumbnail, int id) {
        this.context = context;
        this.downloaderV2 = new GalleryDownloaderV2(context, title, thumbnail, id);
        this.downloaderV2.addObserver(observer);
    }

    private void cancelNotification() {
        NotificationSettings.cancel(notificationId);
    }

    private void addClickListener() {
        Intent notifyIntent = new Intent(context, GalleryActivity.class);
        notifyIntent.putExtra(context.getPackageName() + ".GALLERY", downloaderV2.localGallery());
        notifyIntent.putExtra(context.getPackageName() + ".ISLOCAL", true);
        // Create the PendingIntent

        PendingIntent notifyPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            notifyPendingIntent = PendingIntent.getActivity(
                context, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );
        } else {
            notifyPendingIntent = PendingIntent.getActivity(
                context, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT
            );
        }
        notification.setContentIntent(notifyPendingIntent);
    }

    public GalleryDownloaderV2 downloader() {
        return downloaderV2;
    }

    private void endNotification() {
        //notification=new NotificationCompat.Builder(context.getApplicationContext(), Global.CHANNEL_ID1);
        //notification.setOnlyAlertOnce(true).setSmallIcon(R.drawable.ic_check).setAutoCancel(true);
        hidePercentage();
        if (downloaderV2.getStatus() != GalleryDownloaderV2.Status.CANCELED) {
            notification.setSmallIcon(R.drawable.ic_check);
            notification.setContentTitle(String.format(Locale.US, context.getString(R.string.completed_format), gallery.getTitle()));
        } else {
            notification.setSmallIcon(R.drawable.ic_close);
            notification.setContentTitle(String.format(Locale.US, context.getString(R.string.cancelled_format), gallery.getTitle()));
        }
    }

    private void hidePercentage() {
        setPercentage(0, 0);
    }

    private void setPercentage(int reach, int total) {
        notification.setProgress(total, reach, false);
    }

    private void prepareNotification() {
        notification = new NotificationCompat.Builder(context.getApplicationContext(), Global.CHANNEL_ID1);
        notification.setOnlyAlertOnce(true)

            .setContentTitle(String.format(Locale.US, context.getString(R.string.downloading_format), gallery.getTitle()))
            .setProgress(gallery.getPageCount(), 0, false)
            .setSmallIcon(R.drawable.ic_file);
        setPercentage(0, 1);
    }


    private synchronized void notificationUpdate() {
        try {
            NotificationSettings.notify(context, notificationId, notification.build());
        } catch (NullPointerException | ConcurrentModificationException ignore) {
        }
    }

}
