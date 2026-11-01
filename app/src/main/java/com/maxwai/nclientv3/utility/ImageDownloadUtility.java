package com.maxwai.nclientv3.utility;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.Rotate;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.components.GlideX;
import com.maxwai.nclientv3.settings.Global;

import java.io.File;
import java.util.function.Supplier;

public class ImageDownloadUtility {


    public static void preloadImage(Context context, Uri url) {
        if (Global.getDownloadPolicy() == Global.DataUsageType.NONE) return;
        RequestManager manager = GlideX.with(context);
        LogUtility.d("Requested url glide: " + url);
        if (manager != null) manager.load(url).apply(ImageCache.options(url)).preload();
    }

    public static void loadImageOp(Context context, ImageView view, File file, int angle) {
        RequestManager glide = GlideX.with(context);
        if (glide == null) return;
        Drawable logo = Global.getLogo(context.getResources());
        glide.load(file).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE).transform(new Rotate(angle)).error(logo).placeholder(logo).into(view);
        LogUtility.d("Requested file glide: " + file);
    }

    public static void loadImageOp(Context context, ImageView view, Gallery gallery, int page, int angle) {
        loadImageOp(context, view, gallery, page, angle, true);
    }

    public static void downloadPage(Activity activity, ImageView imageView, Gallery gallery, int page, boolean shouldFull) {
        shouldFull = ImageUrlResolver.filePath(gallery.getHighPage(page).toString()).endsWith("gif") || shouldFull;
        loadImageOp(activity, imageView, gallery, page, 0, shouldFull);
    }

    public static void loadImageOp(Context context, ImageView imageView, Gallery gallery, int page, int angle, boolean shouldFull) {
        loadImageOp(context, imageView, gallery, () -> getUrlForGallery(gallery, page, shouldFull), angle, false);
    }

    private static void loadImageOp(Context context, ImageView view, @Nullable Gallery gallery, Supplier<Uri> url, int angle, boolean priority) {
        RequestManager glide = GlideX.with(view);
        if (glide == null) return;
        Uri imageUrl = url.get();
        RequestBuilder<Drawable> request = glide.load(imageUrl).apply(ImageCache.options(imageUrl))
            .onlyRetrieveFromCache(Global.getDownloadPolicy() == Global.DataUsageType.NONE && ImageCache.isRemote(imageUrl));
        if (angle != 0) request = request.transform(new Rotate(angle));
        android.view.ViewGroup parent = view.getParent() instanceof android.view.ViewGroup
            ? (android.view.ViewGroup) view.getParent() : null;
        android.view.View progress = parent == null ? null : parent.findViewById(com.maxwai.nclientv3.R.id.page_loading);
        android.widget.TextView status = parent == null ? null : parent.findViewById(com.maxwai.nclientv3.R.id.page_load_status);
        if (progress != null) progress.setVisibility(android.view.View.VISIBLE);
        if (status != null) status.setVisibility(android.view.View.GONE);
        request.placeholder(com.maxwai.nclientv3.R.drawable.cover_placeholder)
            .error(com.maxwai.nclientv3.R.drawable.ic_refresh)
            .addListener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model,
                    @NonNull Target<Drawable> target, boolean first) {
                    LogUtility.e("Image load failed", e);
                    if (progress != null) progress.setVisibility(android.view.View.GONE);
                    if (status != null) {
                        status.setVisibility(android.view.View.VISIBLE);
                        status.setText(context.getString(com.maxwai.nclientv3.R.string.page_load_retry)
                            + "\n" + ImageFailure.describe(e));
                        status.setOnClickListener(v -> loadImageOp(context, view, gallery, url, angle, priority));
                    }
                    return false;
                }
                @Override
                public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                    Target<Drawable> target, @NonNull DataSource source, boolean first) {
                    if (progress != null) progress.setVisibility(android.view.View.GONE);
                    if (status != null) status.setVisibility(android.view.View.GONE);
                    return false;
                }
            }).into(view);
    }

    private static Uri getUrlForGallery(Gallery gallery, int page, boolean shouldFull) {
        return shouldFull ? gallery.getPageUrl(page) : gallery.getLowPage(page);
    }

    private static void loadLogo(ImageView imageView) {
        imageView.setImageDrawable(Global.getLogo(imageView.getResources()));
    }

    public static void loadImage(Activity activity, Uri url, ImageView imageView) {
        loadImageOp(activity, imageView, null, () -> url, 0, false);
    }

    public static void loadImage(Activity activity, File file, ImageView imageView) {
        loadImage(activity, file == null ? null : Uri.fromFile(file), imageView);
    }

    /**
     * Load Resource using id
     */
    public static void loadImage(@DrawableRes int resource, ImageView imageView) {
        imageView.setImageResource(resource);
    }
}
