package com.maxwai.nclientv3.components;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.Excludes;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.maxwai.nclientv3.utility.ImageHttpClient;
import java.io.InputStream;

@GlideModule
@Excludes(OkHttpLibraryGlideModule.class)
public final class ReaderGlideModule extends AppGlideModule {
    @Override public void applyOptions(@NonNull Context context, @NonNull com.bumptech.glide.GlideBuilder builder) {
        com.bumptech.glide.load.engine.cache.MemorySizeCalculator sizes = new com.bumptech.glide.load.engine.cache.MemorySizeCalculator.Builder(context)
            .setMemoryCacheScreens(2).setBitmapPoolScreens(1).build();
        builder.setDiskCache(new com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory(context, com.maxwai.nclientv3.utility.ImageCache.MAX_DISK_BYTES));
        builder.setMemoryCache(new com.bumptech.glide.load.engine.cache.LruResourceCache(Math.min(sizes.getMemoryCacheSize(), 64L * 1024 * 1024)));
        builder.setBitmapPool(new com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool(Math.min(sizes.getBitmapPoolSize(), 32L * 1024 * 1024)));
    }
    @Override public boolean isManifestParsingEnabled() { return false; }

    @Override public void registerComponents(@NonNull Context context, @NonNull Glide glide,
        @NonNull Registry registry) {
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(
            ImageHttpClient.get()));
    }
}
