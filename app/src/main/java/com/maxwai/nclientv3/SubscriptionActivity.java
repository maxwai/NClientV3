package com.maxwai.nclientv3;

import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SubscriptionActivity extends BaseActivity {
    private static final int MAX_PARALLEL_REQUESTS = 2;
    private static final int BATCH_REQUEST_LIMIT = 8;
    private static final int MAX_FAILURES_PER_BOOKMARK = 2;
    private static final int MAX_CONSECUTIVE_FAILURES = 8;
    private static final long REQUEST_DELAY_MS = 500;
    private static final long BATCH_DELAY_MS = 60_000;

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
            updateProgress(0, 0, 0);
            refresher.setRefreshing(false);
            return;
        }

        refresher.setRefreshing(true);
        updateProgress(0, 0, bookmarks.size());
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> loadSubscriptionBatches(bookmarks, actualLoad));
    }

    private void loadSubscriptionBatches(List<Bookmark> bookmarks, int actualLoad) {
        List<SubscriptionTask> tasks = new ArrayList<>(bookmarks.size());
        for (Bookmark bookmark : bookmarks) tasks.add(new SubscriptionTask(bookmark));

        ConcurrentHashMap<Integer, GenericGallery> galleries = new ConcurrentHashMap<>();
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger consecutiveFailures = new AtomicInteger(0);
        AtomicBoolean aborted = new AtomicBoolean(false);

        while (actualLoad == loadId && !isFinishing() && !aborted.get()) {
            hideError();
            int requestsUsed = runSubscriptionWave(tasks, galleries, successful, failed, consecutiveFailures, aborted, bookmarks.size(), actualLoad);
            if (aborted.get() || actualLoad != loadId || isFinishing()) break;
            if (requestsUsed == 0) break;
            if (isSubscriptionLoadComplete(tasks)) break;
            showRateLimitPause(actualLoad);
            Utility.threadSleep(BATCH_DELAY_MS);
        }

        if (!aborted.get()) finishRefresh(failed.get(), bookmarks.size(), actualLoad);
    }

    private int runSubscriptionWave(List<SubscriptionTask> tasks, ConcurrentHashMap<Integer, GenericGallery> galleries,
                                    AtomicInteger successful, AtomicInteger failed, AtomicInteger consecutiveFailures,
                                    AtomicBoolean aborted, int total, int actualLoad) {
        int requestsUsed = 0;
        while (requestsUsed < BATCH_REQUEST_LIMIT && actualLoad == loadId && !isFinishing() && !aborted.get()) {
            List<SubscriptionTask> batch = createNextBatch(tasks, BATCH_REQUEST_LIMIT - requestsUsed);
            if (batch.isEmpty()) break;
            runSubscriptionBatch(batch, galleries, successful, failed, consecutiveFailures, aborted, total, actualLoad);
            requestsUsed += batch.size();
        }
        return requestsUsed;
    }

    private List<SubscriptionTask> createNextBatch(List<SubscriptionTask> tasks, int limit) {
        List<SubscriptionTask> batch = new ArrayList<>(Math.min(limit, MAX_PARALLEL_REQUESTS));
        addTasksToBatch(batch, tasks, 0, limit);
        addTasksToBatch(batch, tasks, 1, limit);
        return batch;
    }

    private void addTasksToBatch(List<SubscriptionTask> batch, List<SubscriptionTask> tasks, int failures, int limit) {
        for (SubscriptionTask task : tasks) {
            if (batch.size() >= limit || batch.size() >= MAX_PARALLEL_REQUESTS) return;
            if (!task.finished && task.failures == failures) batch.add(task);
        }
    }

    private void runSubscriptionBatch(List<SubscriptionTask> batch, ConcurrentHashMap<Integer, GenericGallery> galleries,
                                      AtomicInteger successful, AtomicInteger failed, AtomicInteger consecutiveFailures,
                                      AtomicBoolean aborted, int total, int actualLoad) {
        CountDownLatch latch = new CountDownLatch(batch.size());
        ExecutorService batchExecutor = Executors.newFixedThreadPool(MAX_PARALLEL_REQUESTS);
        try {
            for (SubscriptionTask task : batch) {
                if (aborted.get() || actualLoad != loadId || isFinishing()) {
                    latch.countDown();
                    continue;
                }
                batchExecutor.execute(() -> {
                    try {
                        List<GenericGallery> result = loadBookmark(task.bookmark);
                        if (actualLoad != loadId || isFinishing() || aborted.get()) return;
                        handleSubscriptionResult(task, result, galleries, successful, failed, consecutiveFailures, aborted, total, actualLoad);
                    } finally {
                        latch.countDown();
                    }
                });
                Utility.threadSleep(REQUEST_DELAY_MS);
            }
            latch.await();
        } catch (InterruptedException e) {
            LogUtility.d("Subscription batch interrupted", e);
        } finally {
            batchExecutor.shutdownNow();
        }
    }

    private List<GenericGallery> loadBookmark(Bookmark bookmark) {
        try {
            InspectorV3 inspector = bookmark.createInspector(this, null);
            if (inspector == null) return null;
            inspector.setPage(1);
            if (!inspector.createDocument()) throw new IOException("Subscription request failed");
            inspector.parseDocument();
            return inspector.getGalleries();
        } catch (Exception e) {
            LogUtility.e("Subscription request failed", e);
            return null;
        }
    }

    private void handleSubscriptionResult(SubscriptionTask task, List<GenericGallery> result, ConcurrentHashMap<Integer, GenericGallery> galleries,
                                          AtomicInteger successful, AtomicInteger failed, AtomicInteger consecutiveFailures,
                                          AtomicBoolean aborted, int total, int actualLoad) {
        if (result == null) {
            int currentConsecutiveFailures = consecutiveFailures.incrementAndGet();
            task.failures++;
            if (task.failures >= MAX_FAILURES_PER_BOOKMARK) {
                task.finished = true;
                failed.incrementAndGet();
                updateProgress(successful.get(), failed.get(), total);
            }
            if (currentConsecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                aborted.set(true);
                showUnableToConnect(actualLoad);
            }
            return;
        }

        consecutiveFailures.set(0);
        task.finished = true;
        successful.incrementAndGet();
        for (GenericGallery gallery : result) {
            if (gallery != null && gallery.isValid()) galleries.put(gallery.getId(), gallery);
        }
        updateGalleries(galleries);
        updateProgress(successful.get(), failed.get(), total);
    }

    private boolean isSubscriptionLoadComplete(List<SubscriptionTask> tasks) {
        for (SubscriptionTask task : tasks) {
            if (!task.finished) return false;
        }
        return true;
    }

    private void updateGalleries(ConcurrentHashMap<Integer, GenericGallery> galleryMap) {
        List<GenericGallery> sorted = new ArrayList<>(galleryMap.values());
        sorted.sort((a, b) -> Integer.compare(b.getId(), a.getId()));
        runOnUiThread(() -> adapter.restartDataset(sorted));
    }

    private void updateProgress(int successful, int failed, int total) {
        runOnUiThread(() -> {
            String text = getString(R.string.subscription_progress_format, successful, failed, total);
            SpannableString spannable = new SpannableString(text);
            String successfulText = String.valueOf(successful);
            String failedText = String.valueOf(failed);
            String totalText = String.valueOf(total);
            int successfulStart = text.indexOf(successfulText);
            int failedStart = text.indexOf(failedText, successfulStart + successfulText.length());
            int totalStart = text.lastIndexOf(totalText);
            setProgressSpan(spannable, successfulStart, successfulText.length(), Color.GREEN);
            setProgressSpan(spannable, failedStart, failedText.length(), Color.RED);
            setProgressSpan(spannable, totalStart, totalText.length(), Color.WHITE);
            progressText.setText(spannable);
        });
    }

    private void setProgressSpan(SpannableString spannable, int start, int length, int color) {
        if (start < 0) return;
        spannable.setSpan(new ForegroundColorSpan(color), start, start + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void finishRefresh(int failed, int total, int actualLoad) {
        runOnUiThread(() -> {
            if (actualLoad != loadId || isFinishing()) return;
            if (executor != null) executor.shutdown();
            refresher.setRefreshing(false);
            updateProgress(total - failed, failed, total);
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

    private void showRateLimitPause(int actualLoad) {
        runOnUiThread(() -> {
            if (actualLoad != loadId || isFinishing()) return;
            snackbar = Snackbar.make(masterLayout, R.string.subscription_rate_limit_pause, Snackbar.LENGTH_INDEFINITE);
            TextView snackbarText = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            snackbarText.setSingleLine(false);
            snackbarText.setMaxLines(3);
            snackbarText.setEllipsize(null);
            snackbar.show();
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

    private static class SubscriptionTask {
        final Bookmark bookmark;
        int failures = 0;
        boolean finished = false;

        SubscriptionTask(Bookmark bookmark) {
            this.bookmark = bookmark;
        }
    }
}
