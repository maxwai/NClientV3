package com.maxwai.nclientv3.utility;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.maxwai.nclientv3.*;

/** Shared presentation and top-level navigation; gallery/API behavior stays with each screen. */
public final class PremiumUi {
    public static final int DOWNLOAD_PERMISSION = 4246;
    private PremiumUi() { }

    public static int columns(int requested, int widthDp, boolean pages) {
        int minimum = pages ? 104 : 144;
        return Math.max(1, Math.min(requested, Math.max(1, (widthDp - 48) / minimum)));
    }

    public static int columns(int requested, int widthDp, int heightDp, boolean pages) {
        int count = columns(requested, widthDp, pages);
        return !pages && heightDp < 500 ? Math.min(count, Math.max(1, (widthDp - 48) / 320)) : count;
    }

    public static void configure(Activity activity) {
        boolean browse = activity instanceof MainActivity;
        boolean primary = browse || activity instanceof FavoriteActivity || activity instanceof LocalActivity
            || activity instanceof HistoryActivity || activity instanceof BookmarkActivity || activity instanceof SettingsActivity;
        boolean rootBrowse = browse && ((MainActivity) activity).isRootBrowse();
        int heading = R.string.nav_explore, subtitle = R.string.explore_subtitle, selected = R.id.nav_explore;
        if (activity instanceof FavoriteActivity) {
            heading = R.string.nav_library; subtitle = R.string.library_subtitle; selected = R.id.nav_library;
        } else if (activity instanceof LocalActivity) {
            heading = R.string.nav_downloads; subtitle = R.string.downloads_subtitle; selected = R.id.nav_downloads;
        } else if (activity instanceof SettingsActivity) {
            heading = R.string.settings; subtitle = R.string.settings_subtitle; selected = R.id.nav_settings;
        } else if (activity instanceof HistoryActivity) {
            heading = R.string.history; subtitle = R.string.reading_history_subtitle; selected = R.id.nav_history;
        } else if (activity instanceof BookmarkActivity) {
            heading = R.string.manage_bookmarks; subtitle = R.string.library_subtitle; selected = R.id.nav_library;
        }
        TextView title = activity.findViewById(R.id.screen_heading), body = activity.findViewById(R.id.screen_subtitle);
        if (title != null) title.setText(heading);
        if (body != null) body.setText(subtitle);
        View header = activity.findViewById(R.id.shelf_header);
        if (header != null) header.setVisibility(primary && (!browse || rootBrowse)
            && activity.getResources().getConfiguration().screenHeightDp >= 500 ? View.VISIBLE : View.GONE);
        View tools = activity.findViewById(R.id.browse_tools);
        if (tools != null) tools.setVisibility(rootBrowse ? View.VISIBLE : View.GONE);
        BottomNavigationView navigation = activity.findViewById(R.id.bottom_navigation);
        if (navigation != null) {
            navigation.setVisibility(!primary || (browse && !rootBrowse) ? View.GONE : View.VISIBLE);
            navigation.setOnItemSelectedListener(null);
            navigation.setSelectedItemId(selected);
            navigation.setOnItemSelectedListener(item -> {
                Class<? extends Activity> target;
                if (item.getItemId() == R.id.nav_library) target = FavoriteActivity.class;
                else if (item.getItemId() == R.id.nav_history) target = HistoryActivity.class;
                else if (item.getItemId() == R.id.nav_downloads) target = LocalActivity.class;
                else if (item.getItemId() == R.id.nav_settings) target = SettingsActivity.class;
                else target = MainActivity.class;
                if (activity.getClass() == target) return true;
                if (target == LocalActivity.class && !com.maxwai.nclientv3.settings.Global.hasStoragePermission(activity)) {
                    activity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE}, DOWNLOAD_PERMISSION);
                    return false;
                }
                Intent intent = new Intent(activity, target);
                if (target == MainActivity.class) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                // Keep Explore as the root; switching tabs does not build an ever-growing back stack.
                if (!(activity instanceof MainActivity)) activity.finish();
                return true;
            });
        }
        RecyclerView recycler = activity.findViewById(R.id.recycler);
        SwipeRefreshLayout refresh = activity.findViewById(R.id.refresher);
        if (refresh != null) {
            refresh.setColorSchemeColors(AccentTheme.color(activity));
            refresh.setProgressBackgroundColorSchemeColor(AppearanceTheme.color(activity, com.google.android.material.R.attr.colorSurface));
        }
        if (recycler != null) {
            recycler.setItemAnimator(null);
            if (primary) watchEmptyState(activity, recycler, refresh);
        }
        AppearanceTheme.decorate(activity);
    }

    private static void watchEmptyState(Activity activity, RecyclerView recycler, SwipeRefreshLayout refresh) {
        View empty = activity.findViewById(R.id.list_empty);
        if (empty == null || recycler.getTag(R.id.premium_observer) != null) return;
        RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
            private void update() {
                recycler.post(() -> {
                    RecyclerView.Adapter<?> adapter = recycler.getAdapter();
                    boolean show = adapter != null && adapter.getItemCount() == 0 && (refresh == null || !refresh.isRefreshing());
                    int visibility = show ? View.VISIBLE : View.GONE;
                    if (empty.getVisibility() != visibility) empty.setVisibility(visibility);
                });
            }
            @Override public void onChanged() { update(); }
            @Override public void onItemRangeInserted(int start, int count) { update(); }
            @Override public void onItemRangeRemoved(int start, int count) { update(); }
        };
        recycler.setTag(R.id.premium_observer, observer);
        final RecyclerView.Adapter<?>[] registered = {null};
        recycler.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (registered[0] != recycler.getAdapter()) {
                if (registered[0] != null) registered[0].unregisterAdapterDataObserver(observer);
                registered[0] = recycler.getAdapter();
                if (registered[0] != null) registered[0].registerAdapterDataObserver(observer);
            }
            observer.onChanged();
        });
    }

    public static void status(View card, int color) {
        View indicator = card.findViewById(R.id.status_accent);
        if (indicator != null) {
            indicator.setBackgroundColor(color);
            indicator.setVisibility(color == 0 ? View.GONE : View.VISIBLE);
        }
    }
}
