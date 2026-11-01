package com.maxwai.nclientv3.utility;

import android.app.Application;
import android.content.*;
import android.os.Looper;
import android.view.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.maxwai.nclientv3.*;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.async.database.*;
import com.maxwai.nclientv3.settings.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, application = Application.class, qualifiers = "w412dp-h892dp-night-mdpi")
public class ReadingFlowTest {
    private Context context;
    private DatabaseHelper database;
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication(); ReadingFixtures.initialize(context);
        org.robolectric.util.ReflectionHelpers.setStaticField(ReadingHistoryStore.class, "instance", null);
        database = new DatabaseHelper(context); database.getWritableDatabase();
    }
    private void drain() throws Exception {
        ReadingHistoryStore.IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @After public void close() throws Exception { drain(); ReadingHistoryStore.get(context).close(); database.close(); }
    private View measure(android.app.Activity activity) {
        View root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        root.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(892, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 412, 892); Shadows.shadowOf(Looper.getMainLooper()).idle(); return root;
    }
    @Test public void readerSavesTurningPagesAndSurvivesActivityRecreation() throws Exception {
        Gallery gallery = ReadingFixtures.gallery(context, 1);
        Intent intent = new Intent(context, ZoomActivity.class).putExtra(context.getPackageName()+".GALLERY", gallery)
            .putExtra(context.getPackageName()+".PAGE", 3);
        ActivityController<ZoomActivity> controller = Robolectric.buildActivity(ZoomActivity.class, intent);
        controller.get().setTheme(R.style.AppTheme);
        try (ActivityController<ZoomActivity> running = controller.setup()) {
            measure(running.get()); drain();
            assertEquals(3, ReadingHistoryStore.get(context).list().get(0).page);
            ((ViewPager2) running.get().findViewById(R.id.container)).setCurrentItem(5, false);
            drain(); assertEquals(6, ReadingHistoryStore.get(context).list().get(0).page);
            assertEquals(6, Queries.ResumeTable.pageFromId(1));
            running.recreate(); measure(running.get()); drain();
            assertEquals(5, running.get().getCurrentPage());
            assertEquals(6, ReadingHistoryStore.get(context).list().get(0).page);
        }
    }
    @Test public void historyTapStartsReaderAtSavedPageWithoutFetchingMetadata() throws Exception {
        ReadingHistoryStore.get(context).record(ReadingFixtures.gallery(context, 1), 6);
        ReadingHistoryStore.get(context).record(ReadingFixtures.gallery(context, 2), 3);
        Global.client = new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> { throw new AssertionError("History must use saved gallery metadata"); }).build();
        ActivityController<HistoryActivity> controller = Robolectric.buildActivity(HistoryActivity.class);
        controller.get().setTheme(R.style.AppTheme);
        try (ActivityController<HistoryActivity> running = controller.setup()) {
            drain(); measure(running.get());
            RecyclerView recycler = running.get().findViewById(R.id.recycler);
            assertEquals(2, recycler.getAdapter().getItemCount());
            recycler.getChildAt(0).performClick(); drain();
            Intent launched = Shadows.shadowOf(running.get()).getNextStartedActivity();
            assertEquals(ZoomActivity.class.getName(), launched.getComponent().getClassName());
            assertEquals(3, launched.getIntExtra(context.getPackageName()+".PAGE", -1));
            assertEquals(2, ((Gallery) launched.getParcelableExtra(context.getPackageName()+".GALLERY")).getId());
        }
    }
    @Test public void legacyEntriesRemainVisibleAlongsideNewReadingProgress() throws Exception {
        Queries.HistoryTable.addGallery(ReadingFixtures.gallery(context, 1).toSimpleGallery());
        Queries.ResumeTable.insert(1, 4);
        ReadingHistoryStore.get(context).record(ReadingFixtures.gallery(context, 2), 6);
        ActivityController<HistoryActivity> controller = Robolectric.buildActivity(HistoryActivity.class);
        controller.get().setTheme(R.style.AppTheme);
        try (ActivityController<HistoryActivity> running = controller.setup()) {
            drain(); measure(running.get());
            assertEquals(2, running.get().getRecycler().getAdapter().getItemCount());
            assertTrue(((android.widget.TextView) running.get().getRecycler().getChildAt(1).findViewById(R.id.reading_position)).getText().toString().contains("4"));
        }
    }
    @Test public void galleryReadActionAllowsCachedReadingWhenNetworkImagesAreDisabled() throws Exception {
        Intent intent = new Intent(context, GalleryActivity.class).putExtra(context.getPackageName()+".GALLERY", ReadingFixtures.gallery(context, 1));
        ActivityController<GalleryActivity> controller = Robolectric.buildActivity(GalleryActivity.class, intent);
        controller.get().setTheme(R.style.AppTheme);
        try (ActivityController<GalleryActivity> running = controller.setup()) {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(running.get(), running.get().findViewById(R.id.toolbar));
            assertTrue(running.get().onOptionsItemSelected(menu.getMenu().add(0, R.id.read_gallery, 0, "Read")));
            Intent launched = Shadows.shadowOf(running.get()).getNextStartedActivity();
            assertNotNull(launched); assertEquals(ZoomActivity.class.getName(), launched.getComponent().getClassName());
            assertTrue(ReadingHistoryStore.get(context).list().isEmpty()); // Opening details alone is not a reading visit.
        }
    }
}
