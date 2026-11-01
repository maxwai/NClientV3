package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.net.Uri;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import java.io.File;

public final class ImageCache {
    public static final long MAX_DISK_BYTES = 512L * 1024 * 1024;
    private ImageCache() { }
    public static RequestOptions options(Uri uri) {
        return new RequestOptions().diskCacheStrategy(isRemote(uri) ? DiskCacheStrategy.DATA : DiskCacheStrategy.NONE);
    }
    public static boolean isRemote(Uri uri) { return uri != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())); }
    // Reuse Glide's existing directory so upgrades retain already cached images.
    public static File directory(Context context) { return new File(context.getCacheDir(), com.bumptech.glide.load.engine.cache.DiskCache.Factory.DEFAULT_DISK_CACHE_DIR); }
    /** Invoked on main; Glide manages synchronization with active disk writes. */
    public static void clear(Context context, Runnable completed) {
        Context app = context.getApplicationContext();
        Glide.get(app).clearMemory();
        com.maxwai.nclientv3.settings.ReadingHistoryStore.IO.execute(() -> {
            Glide.get(app).clearDiskCache();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(completed);
        });
    }
}
