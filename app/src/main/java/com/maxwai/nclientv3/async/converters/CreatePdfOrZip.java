package com.maxwai.nclientv3.async.converters;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.local.LocalGallery;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.NotificationSettings;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CreatePdfOrZip extends Worker {
    private static final String GALLERY_ID_KEY = "GALLERY_ID";
    private static final String PDF_OR_ZIP_KEY = "PDF_OR_ZIP";
    private static final Map<Integer, LocalGallery> galleryMap = Collections.synchronizedMap(new HashMap<>());
    private int notId;
    private int totalPage;
    private NotificationCompat.Builder notification;

    public CreatePdfOrZip(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void startWork(Context context, LocalGallery gallery, boolean pdf) {
        int id;
        do {
            id = new Random().nextInt(Integer.MAX_VALUE);
        } while (galleryMap.containsKey(id));
        galleryMap.put(id, gallery);
        WorkRequest createPdfOrZipWorkRequest = new OneTimeWorkRequest.Builder(CreatePdfOrZip.class)
            .setInputData(new Data.Builder()
                .putInt(GALLERY_ID_KEY, id)
                .putBoolean(PDF_OR_ZIP_KEY, pdf)
                .build())
            .build();
        WorkManager.getInstance(context).enqueue(createPdfOrZipWorkRequest);
    }

    public static boolean hasPDFCapabilities() {
        try {
            Class.forName("android.graphics.pdf.PdfDocument");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        notId = NotificationSettings.getNotificationId();
        System.gc();
        if (!getInputData().hasKeyWithValueOfType(PDF_OR_ZIP_KEY, Boolean.class)) {
            return Result.failure();
        }
        boolean pdf = getInputData().getBoolean(PDF_OR_ZIP_KEY, false);
        if (pdf && !hasPDFCapabilities()) {
            return Result.failure();
        }
        int galleryId = getInputData().getInt(GALLERY_ID_KEY, -1);
        if (galleryId == -1) {
            return Result.failure();
        }
        LocalGallery gallery = galleryMap.remove(galleryId);
        if (gallery == null) {
            return Result.failure();
        }
        totalPage = gallery.getPageCount();
        preExecute(gallery.getDirectory(), pdf);
        if (pdf) {
            PdfDocument document = new PdfDocument();
            try {
                File page;
                for (int a = 1; a <= gallery.getPageCount(); a++) {
                    page = gallery.getPage(a);
                    if (page == null) continue;
                    Bitmap bitmap = BitmapFactory.decodeFile(page.getAbsolutePath());
                    if (bitmap != null) {
                        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(bitmap.getWidth(), bitmap.getHeight(), a).create();
                        PdfDocument.Page p = document.startPage(info);
                        p.getCanvas().drawBitmap(bitmap, 0f, 0f, null);
                        document.finishPage(p);
                        bitmap.recycle();
                    }
                    notification.setProgress(totalPage - 1, a + 1, false);
                    NotificationSettings.notify(getApplicationContext(), notId, notification.build());

                }
                notification.setContentText(getApplicationContext().getString(R.string.writing_pdf));
                notification.setProgress(totalPage, 0, true);
                NotificationSettings.notify(getApplicationContext(), notId, notification.build());
                try {
                    File finalPath = Global.PDFFOLDER;
                    //noinspection ResultOfMethodCallIgnored
                    finalPath.mkdirs();
                    finalPath = new File(finalPath, gallery.getTitle() + ".pdf");
                    //noinspection ResultOfMethodCallIgnored
                    finalPath.createNewFile();
                    LogUtility.d("Generating PDF at: " + finalPath);
                    try (FileOutputStream out = new FileOutputStream(finalPath)) {
                        document.writeTo(out);
                    }
                    notification.setProgress(0, 0, false);
                    notification.setContentTitle(getApplicationContext().getString(R.string.created_pdf));
                    notification.setContentText(gallery.getTitle());
                    createIntentOpen(finalPath, true);
                    NotificationSettings.notify(getApplicationContext(), notId, notification.build());
                    LogUtility.d(finalPath.getAbsolutePath());
                } catch (IOException e) {
                    notification.setContentTitle(getApplicationContext().getString(R.string.error_pdf));
                    notification.setContentText(getApplicationContext().getString(R.string.failed));
                    notification.setProgress(0, 0, false);
                    NotificationSettings.notify(getApplicationContext(), notId, notification.build());
                    LogUtility.e(new RuntimeException("Error generating file", e));
                    return Result.failure();
                }
            } finally {
                document.close();
            }
        } else {
            try {
                File file = new File(Global.ZIPFOLDER, gallery.getTitle() + ".zip");
                FileOutputStream o = new FileOutputStream(file);
                try (ZipOutputStream out = new ZipOutputStream(o)) {
                    out.setLevel(Deflater.BEST_COMPRESSION);
                    File actual;
                    int read;
                    byte[] buffer = new byte[1024];
                    for (int i = 1; i <= gallery.getPageCount(); i++) {
                        actual = gallery.getPage(i);
                        if (actual == null) continue;
                        ZipEntry entry = new ZipEntry(actual.getName());
                        try (FileInputStream in = new FileInputStream(actual)) {
                            out.putNextEntry(entry);
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }
                        out.closeEntry();
                        notification.setProgress(gallery.getPageCount(), i, false);
                        NotificationSettings.notify(getApplicationContext(), notId, notification.build());
                    }
                    out.flush();
                }
                postExecutePdf(true, gallery, null, file);
            } catch (IOException e) {
                LogUtility.e(e.getLocalizedMessage(), e);
                postExecutePdf(false, gallery, e.getLocalizedMessage(), null);
                return Result.failure();
            }
        }
        return Result.success();
    }

    private void postExecutePdf(boolean success, LocalGallery gallery, String localizedMessage, File file) {
        notification.setProgress(0, 0, false)
            .setContentTitle(success ? getApplicationContext().getString(R.string.created_zip) : getApplicationContext().getString(R.string.failed_zip));
        if (!success) {
            notification.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(gallery.getTitle())
                .setSummaryText(localizedMessage));
        } else {
            createIntentOpen(file, false);
        }
        NotificationSettings.notify(getApplicationContext(), notId, notification.build());
    }

    private void createIntentOpen(File finalPath, boolean pdf) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri apkURI = FileProvider.getUriForFile(
                getApplicationContext(), getApplicationContext().getPackageName() + ".provider", finalPath);
            i.setDataAndType(apkURI, pdf ? "application/pdf" : "application/zip");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            List<ResolveInfo> resInfoList = getApplicationContext().getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                getApplicationContext().grantUriPermission(packageName, apkURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            notification.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, i, PendingIntent.FLAG_MUTABLE));
            LogUtility.d(apkURI.toString());
        } catch (IllegalArgumentException ignore) {//sometimes the uri isn't available

        }
    }

    private void preExecute(File file, boolean pdf) {
        notification = new NotificationCompat.Builder(getApplicationContext(), pdf ? Global.CHANNEL_ID2 : Global.CHANNEL_ID3);
        notification.setSmallIcon(pdf ? R.drawable.ic_pdf : R.drawable.ic_archive)
            .setOnlyAlertOnce(true)
            .setContentText(pdf ? getApplicationContext().getString(R.string.parsing_pages) : file.getName())
            .setContentTitle(getApplicationContext().getString(pdf ? R.string.channel2_title : R.string.channel3_title))
            .setProgress(pdf ? (totalPage - 1) : 1, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS);
        if (pdf) {
            notification.setStyle(new NotificationCompat.BigTextStyle().bigText(file.getName()));
        }
        NotificationSettings.notify(getApplicationContext(), notId, notification.build());
    }
}
