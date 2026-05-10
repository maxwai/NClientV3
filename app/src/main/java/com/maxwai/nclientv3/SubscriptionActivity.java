package com.maxwai.nclientv3;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;
import com.maxwai.nclientv3.adapters.ListAdapter;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.activities.BaseActivity;
import com.maxwai.nclientv3.components.classes.Bookmark;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SubscriptionActivity extends BaseActivity {
    private static final int MAX_PARALLEL_REQUESTS = 2;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_INITIAL_FAILURES = 5;
    private static final long RETRY_DELAY_MS = 200;

    private ListAdapter adapter;
    private TextView progressText;
    private ExecutorService executor;
    private int loadId = 0;
    private Snackbar snackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscription);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(R.string.subscriptions);

        masterLayout = findViewById(R.id.master_layout);
        progressText = findViewById(R.id.progress_text);
        refresher = findViewById(R.id.refresher);
        recycler = findViewById(R.id.recycler);
        adapter = new ListAdapter(this);
        recycler.setAdapter(adapter);
        recycler.setHasFixedSize(true);
        changeLayout(getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
        refresher.setOnRefreshListener(this::loadSubscriptions);
        loadSubscriptions();
    }

    private void loadSubscriptions() {
        int actualLoad = ++loadId;
        hideError();
        if (executor != null) executor.shutdownNow();

        List<Bookmark> bookmarks = Queries.BookmarkTable.getSubscribedBookmarks();
        if (bookmarks.isEmpty()) {
            adapter.restartDataset(new ArrayList<>());
            updateProgress(0, 0);
            refresher.setRefreshing(false);
            return;
        }

        refresher.setRefreshing(true);
        updateProgress(0, bookmarks.size());
        executor = Executors.newFixedThreadPool(MAX_PARALLEL_REQUESTS);
        ConcurrentHashMap<Integer, GenericGallery> galleries = new ConcurrentHashMap<>();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger initialFailures = new AtomicInteger(0);
        AtomicBoolean hasSuccess = new AtomicBoolean(false);
        AtomicBoolean aborted = new AtomicBoolean(false);

        for (Bookmark bookmark : bookmarks) {
            executor.execute(() -> {
                List<GenericGallery> result = loadBookmark(bookmark, hasSuccess, initialFailures, aborted, actualLoad);
                if (actualLoad != loadId || isFinishing()) return;
                if (aborted.get()) return;
                if (result == null) {
                    failed.incrementAndGet();
                } else {
                    hasSuccess.set(true);
                    for (GenericGallery gallery : result) {
                        if (gallery != null && gallery.isValid())
                            galleries.put(gallery.getId(), gallery);
                    }
                    updateGalleries(galleries);
                }

                int done = completed.incrementAndGet();
                updateProgress(done, bookmarks.size());
                if (done == bookmarks.size()) {
                    finishRefresh(failed.get(), bookmarks.size(), actualLoad);
                }
            });
        }
    }

    private List<GenericGallery> loadBookmark(Bookmark bookmark, AtomicBoolean hasSuccess, AtomicInteger initialFailures, AtomicBoolean aborted, int actualLoad) {
        for (int attempt = 0; attempt < MAX_RETRIES && !aborted.get(); attempt++) {
            try {
                InspectorV3 inspector = bookmark.createInspector(this, null);
                if (inspector == null) return null;
                inspector.setPage(1);
                if (!inspector.createDocument()) throw new IOException("Subscription request failed");
                inspector.parseDocument();
                return inspector.getGalleries();
            } catch (Exception e) {
                LogUtility.e("Subscription request failed", e);
                if (!hasSuccess.get() && initialFailures.incrementAndGet() >= MAX_INITIAL_FAILURES) {
                    aborted.set(true);
                    showUnableToConnect(actualLoad);
                    return null;
                }
                Utility.threadSleep(RETRY_DELAY_MS);
            }
        }
        return null;
    }

    private void updateGalleries(ConcurrentHashMap<Integer, GenericGallery> galleryMap) {
        List<GenericGallery> sorted = new ArrayList<>(galleryMap.values());
        sorted.sort((a, b) -> Integer.compare(b.getId(), a.getId()));
        runOnUiThread(() -> adapter.restartDataset(sorted));
    }

    private void updateProgress(int done, int total) {
        runOnUiThread(() -> {
            progressText.setText(getString(R.string.subscription_progress_format, done, total));
        });
    }

    private void finishRefresh(int failed, int total, int actualLoad) {
        runOnUiThread(() -> {
            if (actualLoad != loadId || isFinishing()) return;
            if (executor != null) executor.shutdown();
            refresher.setRefreshing(false);
            updateProgress(total - failed, total);
            if (failed == total) {
                showError(R.string.unable_to_connect_to_the_site, v -> loadSubscriptions());
            }
        });
    }

    private void showUnableToConnect(int actualLoad) {
        runOnUiThread(() -> {
            if (actualLoad != loadId || isFinishing()) return;
            if (executor != null) executor.shutdownNow();
            refresher.setRefreshing(false);
            showError(R.string.unable_to_connect_to_the_site, v -> loadSubscriptions());
        });
    }

    private void hideError() {
        runOnUiThread(() -> {
            if (snackbar != null && snackbar.isShown()) {
                snackbar.dismiss();
                snackbar = null;
            }
        });
    }

    private void showError(int text, View.OnClickListener listener) {
        if (listener == null) {
            snackbar = Snackbar.make(masterLayout, text, Snackbar.LENGTH_SHORT);
        } else {
            snackbar = Snackbar.make(masterLayout, text, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(R.string.retry, listener);
        }
        snackbar.show();
    }

    @Override
    protected int getPortraitColumnCount() {
        return Global.getColPortMain();
    }

    @Override
    protected int getLandscapeColumnCount() {
        return Global.getColLandMain();
    }

    @Override
    protected void onDestroy() {
        loadId++;
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
