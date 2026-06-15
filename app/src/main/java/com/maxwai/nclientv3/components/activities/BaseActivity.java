package com.maxwai.nclientv3.components.activities;

import android.content.res.Configuration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.ApiRateLimiter;
import com.maxwai.nclientv3.components.widgets.CustomGridLayoutManager;

public abstract class BaseActivity extends GeneralActivity {
    protected RecyclerView recycler;
    protected SwipeRefreshLayout refresher;
    protected ViewGroup masterLayout;

    protected abstract int getPortraitColumnCount();

    protected abstract int getLandscapeColumnCount();


    public SwipeRefreshLayout getRefresher() {
        return refresher;
    }

    public RecyclerView getRecycler() {
        return recycler;
    }

    public ViewGroup getMasterLayout() {
        return masterLayout;
    }

    @NonNull
    public String getRequestFailureMessage(@NonNull Exception e) {
        if (e instanceof ApiRateLimiter.RateLimitException) {
            long retryAfterMs = ((ApiRateLimiter.RateLimitException) e).getRetryAfterMs();
            long retryAfterSeconds = Math.max(1, (retryAfterMs + 999) / 1000);
            return getString(R.string.rate_limited_retry_after, retryAfterSeconds);
        }
        return getString(R.string.unable_to_connect_to_the_site);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            changeLayout(true);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            changeLayout(false);
        }
    }

    protected void changeLayout(boolean landscape) {
        CustomGridLayoutManager manager = (CustomGridLayoutManager) recycler.getLayoutManager();
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        int count = landscape ? getLandscapeColumnCount() : getPortraitColumnCount();
        int position = 0;

        if (manager != null)
            position = manager.findFirstCompletelyVisibleItemPosition();
        CustomGridLayoutManager gridLayoutManager = new CustomGridLayoutManager(this, count);
        recycler.setLayoutManager(gridLayoutManager);
        recycler.setAdapter(adapter);
        recycler.scrollToPosition(position);
    }
}
