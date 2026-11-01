package com.maxwai.nclientv3;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.maxwai.nclientv3.api.*;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.activities.BaseActivity;
import com.maxwai.nclientv3.settings.ReadingHistoryStore;
import com.maxwai.nclientv3.settings.ReadingHistoryStore.Entry;
import com.maxwai.nclientv3.utility.*;
import java.util.*;

public class HistoryActivity extends BaseActivity {
    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> opening = new HashSet<>();
    private final HistoryCards adapter = new HistoryCards();
    private int generation;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.app_bar_main);
        Toolbar toolbar = findViewById(R.id.toolbar); setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.history);
        recycler = findViewById(R.id.recycler); masterLayout = findViewById(R.id.master_layout);
        refresher = findViewById(R.id.refresher);
        recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        refresher.setOnRefreshListener(this::reload);
        ((TextView) findViewById(R.id.empty_heading)).setText(R.string.history);
        ((TextView) findViewById(R.id.empty_subtitle)).setText(R.string.reading_history_empty);
    }
    @Override protected void onResume() { super.onResume(); if (recycler != null) reload(); }
    private void reload() {
        final int request = ++generation;
        refresher.setRefreshing(true);
        ReadingHistoryStore.IO.execute(() -> {
            List<Entry> rows = ReadingHistoryStore.get(this).list();
            Set<Integer> known = new HashSet<>();
            for (Entry entry : rows) if (entry.id > 0) known.add(entry.id);
            for (SimpleGallery gallery : Queries.HistoryTable.getHistory()) {
                if (known.add(gallery.getId())) rows.add(new Entry("online:" + gallery.getId(), gallery.getId(), gallery.getTitle(),
                    gallery.getThumbnail().toString(), Math.max(1, Queries.ResumeTable.pageFromId(gallery.getId())), 0, 0, "", ""));
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || request != generation) return;
                entries.clear(); entries.addAll(rows); adapter.notifyDataSetChanged(); refresher.setRefreshing(false);
            });
        });
    }
    private void open(Entry entry) { open(entry, false); }
    private void open(Entry entry, boolean details) {
        if (!opening.add(entry.key)) return;
        adapter.notifyDataSetChanged();
        ReadingHistoryStore.IO.execute(() -> {
            try {
                GenericGallery gallery = entry.restore(this);
                if (gallery != null) launch(entry, gallery, details);
                else InspectorV3.galleryInspector(this, entry.id, new InspectorV3.DefaultInspectorResponse() {
                    @Override public void onSuccess(List<GenericGallery> galleries) {
                        if (galleries.size() == 1) launch(entry, galleries.get(0), details); else failed(entry, new java.io.IOException(), details);
                    }
                    @Override public void onFailure(Exception error) { failed(entry, error, details); }
                }).start();
            } catch (Exception error) { failed(entry, error, details); }
        });
    }
    private void launch(Entry entry, GenericGallery gallery, boolean details) {
        runOnUiThread(() -> {
            opening.remove(entry.key); adapter.notifyDataSetChanged();
            if (isFinishing() || isDestroyed()) return;
            Intent intent = new Intent(this, details ? GalleryActivity.class : ZoomActivity.class);
            intent.putExtra(getPackageName() + ".GALLERY", gallery);
            intent.putExtra(getPackageName() + ".ISLOCAL", gallery.isLocal());
            intent.putExtra(getPackageName() + ".EXPAND_DETAILS", details);
            intent.putExtra(getPackageName() + ".PAGE", ReadingHistoryStore.clampPage(entry.page, gallery.getPageCount()));
            startActivity(intent);
        });
    }
    private void failed(Entry entry, Exception error, boolean details) {
        runOnUiThread(() -> {
            opening.remove(entry.key);
            if (isFinishing() || isDestroyed()) return;
            adapter.notifyDataSetChanged();
            Snackbar.make(masterLayout, entry.localPath.isEmpty() ? ConnectionFailure.describe(error) : getString(R.string.reading_file_missing), Snackbar.LENGTH_LONG)
                .setTextMaxLines(4).setAction(R.string.retry, v -> open(entry, details)).show();
        });
    }
    private void remove(Entry entry) {
        ReadingHistoryStore.IO.execute(() -> {
            ReadingHistoryStore.get(this).remove(entry.key); Queries.HistoryTable.remove(entry.id);
            runOnUiThread(this::reload);
        });
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.history, menu); Utility.tintMenu(this, menu); return true; }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.cancelAll) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.history).setMessage(R.string.reading_clear_confirm)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (d, w) -> ReadingHistoryStore.IO.execute(() -> {
                    ReadingHistoryStore.get(this).clear(); Queries.HistoryTable.emptyHistory(); runOnUiThread(this::reload);
                })).show(); return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override protected int getPortraitColumnCount() { return 1; }
    @Override protected int getLandscapeColumnCount() { return 1; }
    @Override protected void changeLayout(boolean landscape) { }
    private class HistoryCards extends RecyclerView.Adapter<HistoryCard> {
        @NonNull @Override public HistoryCard onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new HistoryCard(getLayoutInflater().inflate(R.layout.reading_history_card, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull HistoryCard holder, int position) {
            Entry entry = entries.get(position);
            ((TextView) holder.itemView.findViewById(R.id.title)).setText(entry.title);
            String progress = entry.total > 0 ? getString(R.string.page_format, entry.page, entry.total) : getString(R.string.continue_page, entry.page);
            ((TextView) holder.itemView.findViewById(R.id.reading_position)).setText(progress);
            ProgressBar bar = holder.itemView.findViewById(R.id.reading_progress);
            bar.setMax(Math.max(1, entry.total)); bar.setProgress(entry.page); bar.setVisibility(entry.total > 0 ? View.VISIBLE : View.GONE);
            ((TextView) holder.itemView.findViewById(R.id.reading_date)).setText(entry.updated == 0 ? getString(R.string.resume)
                : android.text.format.DateUtils.getRelativeTimeSpanString(entry.updated, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS));
            holder.itemView.findViewById(R.id.reading_opening).setVisibility(opening.contains(entry.key) ? View.VISIBLE : View.GONE);
            ImageDownloadUtility.loadImage(HistoryActivity.this, Uri.parse(entry.cover), holder.itemView.findViewById(R.id.image));
            holder.itemView.setOnClickListener(v -> open(entry));
            holder.itemView.findViewById(R.id.history_details).setOnClickListener(v -> open(entry, true));
            holder.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(HistoryActivity.this).setTitle(entry.title).setMessage(progress)
                    .setPositiveButton(R.string.resume, (d, w) -> open(entry))
                    .setNeutralButton(R.string.delete, (d, w) -> remove(entry)).setNegativeButton(R.string.cancel, null).show(); return true;
            });
        }
        @Override public int getItemCount() { return entries.size(); }
    }
    private static class HistoryCard extends RecyclerView.ViewHolder { HistoryCard(View view) { super(view); } }
}
