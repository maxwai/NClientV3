package com.maxwai.nclientv3.utility;

import android.app.Application;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.maxwai.nclientv3.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, application = Application.class, qualifiers = "w412dp-h892dp-night-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppearanceLayoutTest {
    private void historyFixture(AppCompatActivity activity) {
        activity.setContentView(R.layout.app_bar_main);
        ((TextView) activity.findViewById(R.id.screen_heading)).setText(R.string.history);
        ((TextView) activity.findViewById(R.id.screen_subtitle)).setText(R.string.reading_history_subtitle);
        ((androidx.appcompat.widget.Toolbar) activity.findViewById(R.id.toolbar)).setTitle(R.string.ui_brand);
        activity.findViewById(R.id.browse_tools).setVisibility(View.GONE);
        activity.findViewById(R.id.reading_source).setVisibility(View.VISIBLE);
        ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_history);
        RecyclerView list = activity.findViewById(R.id.recycler); list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(activity).inflate(R.layout.reading_history_card, parent, false)) {};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                String[] titles = {"After the Rain", "Letters from Kyoto", "The Quiet Hours"};
                ((TextView) holder.itemView.findViewById(R.id.title)).setText(titles[position]);
                ((TextView) holder.itemView.findViewById(R.id.reading_position)).setText("12 / 48");
                ((TextView) holder.itemView.findViewById(R.id.reading_date)).setText("2 hours ago");
                ((ProgressBar) holder.itemView.findViewById(R.id.reading_progress)).setProgress(25);
                GradientDrawable cover = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xff687C87 + position * 0x100600, 0xffBFB19D}); cover.setCornerRadius(10);
                ((ImageView) holder.itemView.findViewById(R.id.image)).setImageDrawable(cover);
            }
            @Override public int getItemCount() { return 3; }
        });
        AppearanceTheme.decorate(activity);
    }
    private void render(String theme, int width, int height) throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("Settings", 0).edit().putString("theme_select", theme).commit();
        ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class);
        controller.get().setTheme(R.style.AppTheme); AppearanceTheme.apply(controller.get().getTheme(), theme);
        try (ActivityController<AppCompatActivity> running = controller.setup()) {
            AppCompatActivity activity = running.get(); historyFixture(activity);
            View root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            RecyclerView list = activity.findViewById(R.id.recycler); assertTrue(list.getHeight() >= 200);
            View row = list.getChildAt(0); assertNotNull(row); assertTrue(row.getHeight() >= 160);
            TextView title = row.findViewById(R.id.title); assertTrue(title.getWidth() >= 140);
            assertTrue(row.findViewById(R.id.reading_position).getTop() >= title.getBottom() + 12);
            BottomNavigationView navigation = activity.findViewById(R.id.bottom_navigation);
            assertEquals(5, navigation.getMenu().size()); assertTrue(navigation.getBottom() <= height);
            int canvas = AppearanceTheme.color(activity, android.R.attr.colorBackground);
            for (String accent : new String[]{"gold", "jade", "ocean", "amethyst", "rose", "copper"}) {
                AccentTheme.apply(activity.getTheme(), accent);
                assertTrue(theme + "/" + accent, androidx.core.graphics.ColorUtils.calculateContrast(AccentTheme.color(activity), canvas) > 4.5);
            }
            File output = new File("build/ui-previews/" + BuildConfig.FLAVOR + "/history-" + theme + "-" + width + ".png");
            assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); Canvas drawing = new Canvas(bitmap);
            drawing.drawColor(canvas); root.draw(drawing);
            try (FileOutputStream stream = new FileOutputStream(output)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
            bitmap.recycle();
        }
    }
    @Test public void allDarkThemesHaveReadableCardsAndAccentChoices() throws Exception {
        for (String theme : new String[]{"dark", "midnight", "forest", "amethyst", "black", "glass"}) render(theme, 412, 892);
    }
    @Test @Config(qualifiers = "w320dp-h640dp-night-mdpi")
    public void glassHistoryFitsSmallScreens() throws Exception { render("glass", 320, 640); }
    @Test @Config(qualifiers = "w412dp-h892dp-notnight-mdpi")
    public void paperHistoryKeepsReadableContrast() throws Exception { render("light", 412, 892); }
    @Test public void settingsOffersAllSevenThemesAndPersistsGlass() throws Exception {
        com.maxwai.nclientv3.settings.Global.initStorage(RuntimeEnvironment.getApplication());
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class);
        controller.get().setTheme(R.style.AppTheme);
        try (ActivityController<SettingsActivity> running = controller.setup()) {
            androidx.preference.PreferenceFragmentCompat fragment = (androidx.preference.PreferenceFragmentCompat)
                running.get().getSupportFragmentManager().findFragmentById(R.id.fragment);
            androidx.preference.ListPreference appearance = fragment.findPreference("theme_select");
            assertNotNull(appearance); assertEquals(7, appearance.getEntryValues().length);
            assertTrue(appearance.callChangeListener("glass")); assertEquals("glass", AppearanceTheme.selected(running.get()));
        }
    }
}
