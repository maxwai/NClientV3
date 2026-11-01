package com.maxwai.nclientv3.utility;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.maxwai.nclientv3.R;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Native Android rendering of the production XML, using fictional fixture titles and covers. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class, qualifiers = "w412dp-h892dp-night-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PremiumLayoutTest {
    private static final String[] TITLES = {"After the Rain", "Letters from Kyoto", "The Quiet Hours", "A Season of Light"};
    private ActivityController<AppCompatActivity> activity() {
        ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class);
        controller.get().setTheme(R.style.AppTheme);
        return controller.setup();
    }
    private View measure(AppCompatActivity activity, int width, int height) {
        View content = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, width, height);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        return content;
    }
    private void screenshot(View view, String name) throws Exception {
        File directory = new File("build/ui-previews/" + com.maxwai.nclientv3.BuildConfig.FLAVOR);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(view.getContext().getColor(R.color.canvas));
        view.draw(canvas);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
    private static GradientDrawable cover(int index) {
        int[][] colors = {{0xff4D6055, 0xffB1A683}, {0xff6D5B53,0xffCFB290}, {0xff3C4549,0xff9BABB0}, {0xff6B7057,0xffD6C4A0}};
        GradientDrawable cover = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors[index % 4]);
        cover.setCornerRadius(18);
        return cover;
    }
    private void shelf(AppCompatActivity activity, boolean download) {
        activity.setContentView(R.layout.app_bar_main);
        ((androidx.appcompat.widget.Toolbar) activity.findViewById(R.id.toolbar)).setTitle(R.string.ui_brand);
        activity.findViewById(R.id.browse_tools).setVisibility(download ? View.GONE : View.VISIBLE);
        if (download) {
            ((TextView) activity.findViewById(R.id.screen_heading)).setText(R.string.nav_downloads);
            ((TextView) activity.findViewById(R.id.screen_subtitle)).setText(R.string.downloads_subtitle);
            ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_downloads);
        }
        RecyclerView recycler = activity.findViewById(R.id.recycler);
        recycler.setLayoutManager(new GridLayoutManager(activity, PremiumUi.columns(2, activity.getResources().getConfiguration().screenWidthDp, activity.getResources().getConfiguration().screenHeightDp, false)));
        recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                boolean single = activity.getResources().getConfiguration().screenHeightDp < 500
                    || ((GridLayoutManager) ((RecyclerView) parent).getLayoutManager()).getSpanCount() == 1;
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(download ? R.layout.entry_download_layout_compact : single ? R.layout.entry_layout_single : R.layout.entry_layout, parent, false)) { };
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ((TextView) holder.itemView.findViewById(R.id.title)).setText(TITLES[position]);
                ((ImageView) holder.itemView.findViewById(R.id.image)).setImageDrawable(cover(position));
                if (download) {
                    ((TextView) holder.itemView.findViewById(R.id.progress)).setText("48%");
                    ((android.widget.ProgressBar) holder.itemView.findViewById(R.id.progressBar)).setProgress(48);
                } else {
                    ((TextView) holder.itemView.findViewById(R.id.flag)).setText("ENGLISH");
                    holder.itemView.findViewById(R.id.pages).setVisibility(View.GONE);
                }
            }
            @Override public int getItemCount() { return 4; }
        });
    }
    @Test public void coversAndTitlesHaveSeparateSpaceAndBottomNavigationStaysReachable() throws Exception {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get(); shelf(activity, false);
            View root = measure(activity, 412, 892);
            RecyclerView recycler = activity.findViewById(R.id.recycler);
            assertTrue(recycler.getHeight() > 240);
            View card = recycler.getChildAt(0); assertNotNull(card);
            ImageView image = card.findViewById(R.id.image); TextView title = card.findViewById(R.id.title);
            assertTrue(image.getWidth() >= 144);
            assertTrue(title.getTop() >= image.getBottom() + 8);
            assertEquals(5, ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).getMenu().size());
            assertTrue(activity.findViewById(R.id.bottom_navigation).getBottom() <= 892);
            screenshot(root, "01-explore-dark");
        }
    }
    @Test public void galleryHasAReadableTitleAndLargePrimaryAndSecondaryActions() throws Exception {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get();
            androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(activity);
            View gallery = LayoutInflater.from(activity).inflate(R.layout.tags_layout, scroll, false);
            scroll.setPadding(12, 0, 12, 0); scroll.addView(gallery); activity.setContentView(scroll);
            ((TextView) gallery.findViewById(R.id.gallery_title)).setText("After the Rain\nA story for the quiet hours");
            ((ImageView) gallery.findViewById(R.id.gallery_cover)).setImageDrawable(cover(0));
            ((TextView) gallery.findViewById(R.id.page_count)).setText("48 pages");
            ((TextView) gallery.findViewById(R.id.favorite_count)).setText("240 favorites");
            ((TextView) gallery.findViewById(R.id.upload_date)).setText("October 2026");
            ((TextView) gallery.findViewById(R.id.id_num)).setText("#688486");
            View root = measure(activity, 412, 892);
            View read = gallery.findViewById(R.id.gallery_read), save = gallery.findViewById(R.id.gallery_save), download = gallery.findViewById(R.id.gallery_download);
            assertTrue(read.getHeight() >= 56); assertTrue(save.getHeight() >= 48);
            assertTrue(save.getRight() < download.getLeft());
            int[] readAt = new int[2], saveAt = new int[2];
            read.getLocationOnScreen(readAt); save.getLocationOnScreen(saveAt);
            assertTrue(readAt[1] + read.getHeight() < saveAt[1]);
            assertTrue(gallery.findViewById(R.id.gallery_title).getHeight() > 48);
            screenshot(root, "02-gallery-dark");
        }
    }
    @Test public void downloadsKeepControlsBelowCoverAndText() throws Exception {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get(); shelf(activity, true);
            View root = measure(activity, 412, 892);
            View card = ((RecyclerView) activity.findViewById(R.id.recycler)).getChildAt(0);
            View image = card.findViewById(R.id.image), title = card.findViewById(R.id.title);
            int[] imageLocation = new int[2], titleLocation = new int[2];
            image.getLocationOnScreen(imageLocation); title.getLocationOnScreen(titleLocation);
            assertTrue(titleLocation[1] >= imageLocation[1] + image.getHeight());
            assertTrue(card.findViewById(R.id.playButton).getWidth() >= 48);
            assertTrue(card.findViewById(R.id.cancelButton).getWidth() >= 48);
            screenshot(root, "03-downloads-dark");
        }
    }
    @Test public void readerChromeUsesTouchSizedControlsAndNoExtraSystemInset() throws Exception {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get(); activity.setContentView(R.layout.activity_zoom);
            ((androidx.appcompat.widget.Toolbar) activity.findViewById(R.id.toolbar)).setTitle("After the Rain");
            ((TextView) activity.findViewById(R.id.pages)).setText("12 / 48");
            ((android.widget.SeekBar) activity.findViewById(R.id.seekBar)).setProgress(25);
            View root = measure(activity, 412, 892);
            View footer = activity.findViewById(R.id.master_layout);
            assertTrue(footer.getBottom() <= 892 - 16);
            assertTrue(activity.findViewById(R.id.prev).getHeight() >= 48);
            assertTrue(activity.findViewById(R.id.seekBar).getHeight() >= 48);
            screenshot(root, "04-reader-dark");
        }
    }
    @Test @Config(qualifiers = "w320dp-h640dp-night-mdpi")
    public void smallScreensReduceColumnsInsteadOfSqueezingCards() throws Exception {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get(); shelf(activity, false);
            View root = measure(activity, 320, 640);
            assertEquals(1, ((GridLayoutManager) ((RecyclerView) activity.findViewById(R.id.recycler)).getLayoutManager()).getSpanCount());
            assertTrue(activity.findViewById(R.id.recycler).getHeight() > 150);
            RecyclerView list = activity.findViewById(R.id.recycler);
            assertTrue(list.getChildAt(0).getHeight() <= list.getHeight());
            View cover = list.getChildAt(0).findViewById(R.id.image);
            View title = list.getChildAt(0).findViewById(R.id.title);
            int[] coverPosition = new int[2], titlePosition = new int[2];
            cover.getLocationOnScreen(coverPosition); title.getLocationOnScreen(titlePosition);
            assertTrue(titlePosition[0] >= coverPosition[0] + cover.getWidth() + 16);
            screenshot(root, "05-explore-small");
        }
    }
    @Test @Config(qualifiers = "w412dp-h892dp-notnight-mdpi")
    public void theLightPaletteIsAlsoUsable() throws Exception {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get(); shelf(activity, false);
            View root = measure(activity, 412, 892);
            int background = activity.getColor(R.color.canvas), text = activity.getColor(R.color.text_primary);
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(text, background) > 7);
            screenshot(root, "06-explore-light");
        }
    }
    @Test @Config(qualifiers = "w892dp-h412dp-land-night-mdpi")
    public void landscapeKeepsNavigationAndListSpace() throws Exception {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get(); shelf(activity, false);
            activity.findViewById(R.id.shelf_header).setVisibility(View.GONE);
            View root = measure(activity, 892, 412);
            assertTrue(activity.findViewById(R.id.recycler).getHeight() >= 240);
            RecyclerView list = activity.findViewById(R.id.recycler);
            assertTrue(list.getChildAt(0).getHeight() <= list.getHeight());
            View cover = list.getChildAt(0).findViewById(R.id.image);
            View title = list.getChildAt(0).findViewById(R.id.title);
            int[] coverPosition = new int[2], titlePosition = new int[2];
            cover.getLocationOnScreen(coverPosition); title.getLocationOnScreen(titlePosition);
            assertTrue(titlePosition[0] >= coverPosition[0] + cover.getWidth() + 16);
            screenshot(root, "07-explore-landscape");
        }
    }
    @Test public void accountRemainsScrollableAtLargeText() throws Exception {
        RuntimeEnvironment.setFontScale(1.4f);
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get(); activity.setContentView(R.layout.activity_api_key);
            View root = measure(activity, 412, 892);
            assertTrue(activity.findViewById(R.id.api_key_input).getHeight() >= 48);
            assertTrue(activity.findViewById(R.id.api_key_input_group).getParent().getParent() instanceof androidx.core.widget.NestedScrollView);
            screenshot(root, "08-account-large-text");
        }
    }
    @Test public void settingsHaveRoomForWrappedSummariesAndSwitches() throws Exception {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get(); activity.setContentView(R.layout.activity_settings);
            androidx.preference.PreferenceFragmentCompat fragment = (androidx.preference.PreferenceFragmentCompat)
                activity.getSupportFragmentManager().findFragmentById(R.id.fragment);
            assertNotNull(fragment);
            androidx.preference.PreferenceScreen screen = fragment.getPreferenceManager().createPreferenceScreen(activity);
            fragment.setPreferenceScreen(screen);
            androidx.preference.PreferenceCategory category = new androidx.preference.PreferenceCategory(activity);
            category.setTitle("Personalize your space"); category.setLayoutResource(R.layout.preference_category_premium); screen.addPreference(category);
            for (String label : new String[]{"Appearance", "Reading preferences", "Your collection", "Storage and downloads"}) {
                androidx.preference.Preference preference = new androidx.preference.Preference(activity);
                preference.setTitle(label); preference.setSummary("Choose what feels right for your reading time.");
                preference.setLayoutResource(R.layout.preference_premium); category.addPreference(preference);
            }
            ((TextView) activity.findViewById(R.id.screen_heading)).setText(R.string.settings);
            ((TextView) activity.findViewById(R.id.screen_subtitle)).setText(R.string.settings_subtitle);
            ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_settings);
            View root = measure(activity, 412, 892);
            assertTrue(fragment.getListView().getHeight() > 400);
            assertNotNull(fragment.getListView().getChildAt(1));
            assertTrue(fragment.getListView().getChildAt(1).getHeight() >= 84);
            screenshot(root, "11-settings-row-fixture");
        }
    }
    @Test public void accentSelectionChangesNavigationButtonsAndContainers() throws Exception {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get();
            int original = AccentTheme.color(activity);
            for (String accent : new String[]{"jade", "ocean", "amethyst", "rose", "copper"}) {
                AccentTheme.apply(activity.getTheme(), accent);
                assertNotEquals(original, AccentTheme.color(activity));
                shelf(activity, false);
                View root = measure(activity, 412, 892);
                BottomNavigationView navigation = activity.findViewById(R.id.bottom_navigation);
                assertEquals(AccentTheme.color(activity), navigation.getItemIconTintList().getColorForState(new int[]{android.R.attr.state_checked}, 0));
                int background = activity.getColor(R.color.canvas);
                assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(AccentTheme.color(activity), background) > 4.5);
                if (accent.equals("jade")) screenshot(root, "10-explore-jade");
            }
        }
    }
    @Test public void theActualAccentSettingPersistsTheChoice() throws Exception {
        com.maxwai.nclientv3.settings.Global.initStorage(RuntimeEnvironment.getApplication());
        ActivityController<com.maxwai.nclientv3.SettingsActivity> controller = Robolectric.buildActivity(com.maxwai.nclientv3.SettingsActivity.class);
        controller.get().setTheme(R.style.AppTheme);
        try (ActivityController<com.maxwai.nclientv3.SettingsActivity> running = controller.setup()) {
            com.maxwai.nclientv3.SettingsActivity activity = running.get();
            androidx.preference.PreferenceFragmentCompat fragment = (androidx.preference.PreferenceFragmentCompat)
                activity.getSupportFragmentManager().findFragmentById(R.id.fragment);
            androidx.preference.ListPreference accent = fragment.findPreference("accent_color");
            assertNotNull(accent); assertEquals(6, accent.getEntryValues().length);
            screenshot(measure(activity, 412, 892), "09-settings-dark");
            assertTrue(accent.callChangeListener("ocean"));
            assertEquals("ocean", AccentTheme.selected(activity));
        }
    }
    @Test @Config(sdk = 31)
    public void android12CanInflateTheNewNavigationAndGalleryActions() {
        try (ActivityController<AppCompatActivity> controller = activity()) {
            AppCompatActivity activity = controller.get(); shelf(activity, false); measure(activity, 412, 892);
            assertTrue(activity.findViewById(R.id.bottom_navigation).getHeight() >= 64);
            activity.setContentView(R.layout.tags_layout); measure(activity, 412, 892);
            assertTrue(activity.findViewById(R.id.gallery_read).getHeight() >= 56);
        }
    }
    @Test public void readableGridBoundsCannotBecomeZeroOrOverflowTheScreen() {
        assertEquals(1, PremiumUi.columns(4, 320, false));
        assertEquals(2, PremiumUi.columns(4, 412, false));
        assertEquals(4, PremiumUi.columns(4, 892, false));
        assertEquals(2, PremiumUi.columns(2, 320, true));
        assertEquals(1, PremiumUi.columns(0, 0, false));
        assertEquals(2, PremiumUi.columns(4, 892, 412, false));
    }
}
