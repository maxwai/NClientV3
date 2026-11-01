package com.maxwai.nclientv3.components.views;


import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Priority;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.Rotate;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.ImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.ZoomActivity;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.components.GlideX;
import com.maxwai.nclientv3.files.GalleryFolder;
import com.maxwai.nclientv3.files.PageFile;
import com.maxwai.nclientv3.github.chrisbanes.photoview.PhotoView;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;

public class ZoomFragment extends Fragment {

    public interface OnZoomChangeListener {
        void onZoomChange(View v, float zoomLevel);
    }

    private static final float MAX_SCALE = 8f;
    private static final float CHANGE_PAGE_THRESHOLD = .2f;
    private PhotoView photoView = null;
    private ImageButton retryButton;
    private View loadingIndicator;
    private android.widget.TextView loadStatus;
    private PageFile pageFile = null;
    private Uri url;
    private int degree = 0;
    private boolean completedDownload = false;
    private boolean requestInFlight = false;
    private android.widget.Button retryAction, browserAction, detailsAction;
    private View statePanel;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable slowNotice = () -> {
        if (requestInFlight && loadStatus != null) loadStatus.setText(R.string.reader_loading_slow);
    };
    private String failureReason = "";
    private View.OnClickListener clickListener;
    private OnZoomChangeListener zoomChangeListener;
    private ImageViewTarget<Drawable> target = null;


    public ZoomFragment() {
    }

    public static ZoomFragment newInstance(GenericGallery gallery, int page, @Nullable GalleryFolder directory) {
        Bundle args = new Bundle();
        args.putString("URL", gallery.isLocal() ? null : (Global.getDownloadPolicy() == Global.DataUsageType.THUMBNAIL
            ? ((Gallery) gallery).getGalleryData().getPage(page).getThumbnailPath()
            : ((Gallery) gallery).getHighPage(page)).toString());
        args.putInt("PAGE", page);
        args.putParcelable("FOLDER", directory == null ? null : directory.getPage(page + 1));
        ZoomFragment fragment = new ZoomFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public void setClickListener(View.OnClickListener clickListener) {
        this.clickListener = clickListener;
    }


    public void setZoomChangeListener(OnZoomChangeListener zoomChangeListener) {
        this.zoomChangeListener = zoomChangeListener;
    }

    private float calculateScaleFactor(int width, int height) {
        FragmentActivity activity = getActivity();
        if (height < width * 2) return Global.getDefaultZoom();
        float finalSize =
            ((float) Global.getDeviceWidth(activity) * height) /
                ((float) Global.getDeviceHeight(activity) * width);
        finalSize = Math.max(finalSize, Global.getDefaultZoom());
        finalSize = Math.min(finalSize, MAX_SCALE);
        LogUtility.d("Final scale: " + finalSize);
        return (float) Math.floor(finalSize);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_zoom, container, false);
        ZoomActivity activity = (ZoomActivity) getActivity();
        assert getArguments() != null;
        assert activity != null;
        //find views
        photoView = rootView.findViewById(R.id.image);
        retryButton = rootView.findViewById(R.id.imageView);
        loadingIndicator = rootView.findViewById(R.id.reader_loading);
        statePanel = rootView.findViewById(R.id.reader_state);
        retryAction = rootView.findViewById(R.id.reader_retry);
        browserAction = rootView.findViewById(R.id.reader_browser);
        detailsAction = rootView.findViewById(R.id.reader_details);
        loadStatus = rootView.findViewById(R.id.reader_status);
        //read arguments
        String str = getArguments().getString("URL");
        url = str == null ? null : Uri.parse(str);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pageFile = getArguments().getParcelable("FOLDER", PageFile.class);
        } else {
            pageFile = getArguments().getParcelable("FOLDER");
        }
        photoView.setAllowParentInterceptOnEdge(true);
        photoView.setOnPhotoTapListener((view, x, y) -> {
            boolean prev = x < CHANGE_PAGE_THRESHOLD;
            boolean next = x > 1f - CHANGE_PAGE_THRESHOLD;
            if ((prev || next) && Global.isButtonChangePage()) {
                activity.changeClosePage(next);
            } else if (clickListener != null) {
                clickListener.onClick(view);
            }
            LogUtility.d(view, x, y, prev, next);
        });

        photoView.setOnScaleChangeListener((float scaleFactor, float focusX, float focusY)->{
            if(this.zoomChangeListener!=null) {
                this.zoomChangeListener.onZoomChange(rootView, photoView.getScale());
            }
            });

        photoView.setMaximumScale(MAX_SCALE);
        retryAction.setOnClickListener(v -> loadImage(Priority.IMMEDIATE));
        browserAction.setOnClickListener(v -> openImageInBrowser());
        detailsAction.setOnClickListener(v -> showFailureDetails());
        rootView.setOnClickListener(v -> { if (clickListener != null) clickListener.onClick(v); });
        createTarget();
        int distance = Math.abs(activity.getCurrentPage() - getArguments().getInt("PAGE"));
        if (distance <= activity.getRequestWindow())
            loadImage(distance == 0 ? Priority.IMMEDIATE : Priority.LOW);
        return rootView;
    }

    private void createTarget() {
        target = new ImageViewTarget<Drawable>(photoView) {

            @Override
            protected void setResource(@Nullable Drawable resource) {
                photoView.setImageDrawable(resource);
            }

            void applyDrawable(ImageView toShow, ImageView toHide, Drawable drawable) {
                toShow.setVisibility(View.VISIBLE);
                toHide.setVisibility(View.GONE);
                toShow.setImageDrawable(drawable);
                if (toShow instanceof PhotoView)
                    scalePhoto(drawable);
            }

            @Override
            public void onLoadStarted(@Nullable Drawable placeholder) {
                requestInFlight = true;
                super.onLoadStarted(placeholder);
                applyDrawable(photoView, retryButton, placeholder);
                statePanel.setVisibility(View.VISIBLE);
                retryAction.setVisibility(View.GONE);
                browserAction.setVisibility(View.GONE);
                detailsAction.setVisibility(View.GONE);
                loadingIndicator.setVisibility(View.VISIBLE);
                uiHandler.removeCallbacks(slowNotice);
                uiHandler.postDelayed(slowNotice, 8000);
                loadStatus.setText(R.string.page_loading);
                loadStatus.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                super.onLoadFailed(errorDrawable);
                applyDrawable(retryButton, photoView, errorDrawable);
                loadingIndicator.setVisibility(View.GONE);
                uiHandler.removeCallbacks(slowNotice);
                statePanel.setVisibility(View.VISIBLE);
                retryAction.setVisibility(View.VISIBLE);
                browserAction.setVisibility(url == null ? View.GONE : View.VISIBLE);
                detailsAction.setVisibility(View.VISIBLE);
                loadStatus.setText(failureReason.equals("Image request timed out") ? R.string.reader_timeout
                    : failureReason.equals("HTTP 429") ? R.string.reader_rate_limited : R.string.page_load_failed);
                loadStatus.setOnClickListener(null);
                loadStatus.setVisibility(View.VISIBLE);
            }

            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                applyDrawable(photoView, retryButton, resource);
                loadingIndicator.setVisibility(View.GONE);
                loadStatus.setVisibility(View.GONE);
                statePanel.setVisibility(View.GONE);
                uiHandler.removeCallbacks(slowNotice);
                if (resource instanceof Animatable)
                    ((Animatable) resource).start();
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                requestInFlight = false;
                uiHandler.removeCallbacks(slowNotice);
                statePanel.setVisibility(View.GONE);
                super.onLoadCleared(placeholder);
                applyDrawable(photoView, retryButton, placeholder);
            }
        };
    }

    private void scalePhoto(Drawable drawable) {
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) return;
        photoView.setScale(calculateScaleFactor(
            drawable.getIntrinsicWidth(),
            drawable.getIntrinsicHeight()
        ), 0, 0, false);
    }

    public void loadImage() {
        loadImage(Priority.NORMAL);
    }

    public void loadImage(Priority priority) {
        if (completedDownload || requestInFlight || photoView == null) return;
        failureReason = "";
        loadStatus.setOnClickListener(null);
        cancelRequest();
        RequestBuilder<Drawable> dra = loadPage();
        if (dra == null) return;
        requestInFlight = true;
        dra
            .transform(new com.bumptech.glide.load.MultiTransformation<>(
                java.util.Arrays.asList(new com.bumptech.glide.load.resource.bitmap.FitCenter(), new Rotate(degree))))
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_refresh)
            .priority(priority)
            .addListener(new RequestListener<>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                    requestInFlight = false;
                    uiHandler.removeCallbacks(slowNotice);
                    LogUtility.e("Reader image load failed", e);
                    failureReason = com.maxwai.nclientv3.utility.ImageFailure.describe(e);
                    if (pageFile != null && url != null) {
                        pageFile = null;
                        photoView.post(() -> { if (isAdded() && photoView != null) loadImage(); });
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                    requestInFlight = false;
                    completedDownload = true;
                    return false;
                }
            })
            .into(target);
    }

    @Nullable
    private RequestBuilder<Drawable> loadPage() {
        RequestBuilder<Drawable> request;
        RequestManager glide = GlideX.with(photoView);
        if (glide == null) return null;
        if (pageFile != null && pageFile.isFile() && pageFile.length() > 0) {
            request = glide.load(pageFile).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE);
            LogUtility.d("Requested file glide: " + pageFile);
        } else {
            if (url == null) request = glide.load(R.mipmap.ic_launcher);
            else {
                LogUtility.d("Requested url glide: " + url);
                request = glide.load(url).apply(com.maxwai.nclientv3.utility.ImageCache.options(url))
                    .onlyRetrieveFromCache(Global.getDownloadPolicy() == Global.DataUsageType.NONE);
            }
        }
        android.util.DisplayMetrics display = getResources().getDisplayMetrics();
        return request.override(Math.min(2048, Math.max(1, display.widthPixels * 2)),
            Math.min(4096, Math.max(1, display.heightPixels * 2)));
    }

    private void openImageInBrowser() {
        if (url == null || !isAdded()) return;
        browserAction.setEnabled(false);
        new Thread(() -> {
            try {
                String resolved = com.maxwai.nclientv3.utility.ImageHttpClient.resolveForBrowser(url.toString());
                uiHandler.post(() -> {
                    if (!isAdded() || browserAction == null) return;
                    browserAction.setEnabled(true);
                    try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(resolved))); }
                    catch (android.content.ActivityNotFoundException ignored) {
                        android.widget.Toast.makeText(requireContext(), R.string.failed, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (java.io.IOException | IllegalArgumentException error) {
                uiHandler.post(() -> {
                    if (!isAdded() || browserAction == null) return;
                    browserAction.setEnabled(true);
                    android.widget.Toast.makeText(requireContext(), R.string.failed, android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        }, "resolve-browser-image").start();
    }

    private void showFailureDetails() {
        if (!isAdded()) return;
        String address = url == null ? "Local file" : url.buildUpon().clearQuery().fragment(null).build().toString();
        if (url != null && com.maxwai.nclientv3.utility.ImageUrlResolver.isManaged(url.toString())) {
            String raw = com.maxwai.nclientv3.utility.ImageUrlResolver.rawPath(url.toString());
            int query = raw.indexOf('?');
            address = "API path: " + (query < 0 ? raw : raw.substring(0, query));
            for (String resolved : com.maxwai.nclientv3.utility.ImageHttpClient.cachedUrls(url.toString()))
                address += "\n" + Uri.parse(resolved).buildUpon().clearQuery().fragment(null).build();
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.failed)
                .setMessage(failureReason + "\n\n" + address)
                .setPositiveButton(R.string.retry, (dialog, which) -> loadImage())
                .setNegativeButton(android.R.string.cancel, null);
        if (url != null) builder.setNeutralButton(R.string.open_in_browser,
            (dialog, which) -> openImageInBrowser());
        androidx.appcompat.app.AlertDialog dialog = builder.show();
        android.widget.TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextIsSelectable(true);
    }

    public Drawable getDrawable() {
        return photoView.getDrawable();
    }

    public void cancelRequest() {
        if (completedDownload) return;
        requestInFlight = false;
        uiHandler.removeCallbacks(slowNotice);
        if (photoView != null && target != null) {
            RequestManager manager = GlideX.with(photoView);
            if (manager != null) manager.clear(target);
        }
    }

    public void releaseImage() {
        completedDownload = false;
        cancelRequest();
        if (photoView != null) photoView.setImageDrawable(null);
    }

    private void updateDegree() {
        degree = (degree + 270) % 360;
        completedDownload = false;
        cancelRequest();
        loadImage(Priority.IMMEDIATE);
    }

    @Override
    public void onDestroyView() {
        uiHandler.removeCallbacks(slowNotice);
        requestInFlight = false;
        if (photoView != null && target != null) {
            RequestManager manager = GlideX.with(photoView);
            if (manager != null) manager.clear(target);
        }
        completedDownload = false;
        target = null;
        photoView = null;
        retryButton = null;
        loadingIndicator = null;
        loadStatus = null;
        statePanel = null;
        retryAction = null;
        browserAction = null;
        detailsAction = null;
        super.onDestroyView();
    }

    public void rotate() {
        updateDegree();
    }
}
