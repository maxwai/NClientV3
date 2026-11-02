package com.maxwai.nclientv3;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.maxwai.nclientv3.adapters.HanimeCommunityAdapter;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.components.HanimeCommunityGallery;
import com.maxwai.nclientv3.components.activities.GeneralActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HanimeImagesActivity extends GeneralActivity {
    private int page = 1;
    private boolean loading;
    private HanimeCommunityAdapter adapter;
    private SwipeRefreshLayout refresher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hanime_images);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);

        RecyclerView recycler = findViewById(R.id.recycler);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recycler.setLayoutManager(layoutManager);
        recycler.setHasFixedSize(false);
        adapter = new HanimeCommunityAdapter(this);
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dy > 0 && !loading && layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 5)
                    loadPage(page + 1, false);
            }
        });

        refresher = findViewById(R.id.refresher);
        refresher.setOnRefreshListener(() -> {
            page = 1;
            loadPage(1, true);
        });
        loadPage(1, true);
    }

    private void loadPage(int requestedPage, boolean reset) {
        loading = true;
        InspectorV3.hanimeCommunityInspector(this, requestedPage, new InspectorV3.DefaultInspectorResponse() {
            @Override
            public void onSuccess(List<GenericGallery> galleries) {
                ArrayList<HanimeCommunityGallery> images = new ArrayList<>();
                for (GenericGallery gallery : galleries)
                    if (gallery instanceof HanimeCommunityGallery)
                        images.add((HanimeCommunityGallery) gallery);
                runOnUiThread(() -> {
                    page = requestedPage;
                    if (reset) adapter.reset(images);
                    else adapter.add(images);
                });
            }

            @Override
            public void onFailure(Exception e) {
                super.onFailure(e);
            }

            @Override
            public void onEnd() {
                runOnUiThread(() -> {
                    loading = false;
                    refresher.setRefreshing(false);
                });
            }
        }).start();
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
