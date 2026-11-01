package com.maxwai.nclientv3.utility;

import android.app.Application;
import android.content.Context;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.local.LocalGallery;
import com.maxwai.nclientv3.settings.ReadingHistoryStore;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, application = Application.class)
public class ReadingHistoryTest {
    private Context context;
    private ReadingHistoryStore store;
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication(); ReadingFixtures.initialize(context);
        store = new ReadingHistoryStore(context);
    }
    @After public void close() { store.close(); }
    @Test public void restartPreservesIndependentPagesAndExactApiPaths() throws Exception {
        Gallery first = ReadingFixtures.gallery(context, 1), second = ReadingFixtures.gallery(context, 2);
        store.record(first, 3); store.record(second, 7);
        // A clock rollback and equal timestamps must not override actual reading order.
        store.getWritableDatabase().execSQL("UPDATE reading SET updated=?", new Object[]{System.currentTimeMillis() + 60000});
        store.updatePage(ReadingHistoryStore.key(first), 5);
        store.close(); store = new ReadingHistoryStore(context);
        assertEquals(2, store.list().size());
        ReadingHistoryStore.Entry entry = store.list().get(0);
        assertEquals(1, entry.id); assertEquals(5, entry.page); assertEquals(7, store.list().get(1).page);
        Gallery restored = (Gallery) entry.restore(context);
        assertEquals(first.getTitle(), restored.getTitle()); assertEquals(8, restored.getPageCount());
        for (int i = 0; i < 8; i++) {
            assertEquals(first.getHighPage(i), restored.getHighPage(i));
            assertEquals(first.getGalleryData().getPage(i).getThumbnailPath(), restored.getGalleryData().getPage(i).getThumbnailPath());
        }
    }
    @Test public void reopeningUpdatesOneEntryAndBoundsSavedPage() throws Exception {
        Gallery gallery = ReadingFixtures.gallery(context, 1);
        store.record(gallery, 100); assertEquals(8, store.list().get(0).page);
        store.record(gallery, -1); assertEquals(1, store.list().size()); assertEquals(1, store.list().get(0).page);
        store.updatePage(ReadingHistoryStore.key(gallery), 900); assertEquals(8, store.list().get(0).page);
        store.updatePage(ReadingHistoryStore.key(gallery), -30); assertEquals(1, store.list().get(0).page);
    }
    @Test public void configuredLimitEvictsOldestWithoutDiscardingRecentProgress() throws Exception {
        context.getSharedPreferences("Settings", 0).edit().putInt(context.getString(R.string.preference_key_max_history_size), 2).commit();
        store.record(ReadingFixtures.gallery(context, 1), 1); store.record(ReadingFixtures.gallery(context, 2), 4);
        store.record(ReadingFixtures.gallery(context, 3), 7);
        assertEquals(2, store.list().size()); assertEquals(3, store.list().get(0).id); assertEquals(2, store.list().get(1).id);
        assertEquals(4, store.list().get(1).page);
    }
    @Test public void localHistoryRestoresFolderAndDeletingHistoryPreservesDownloads() throws Exception {
        File folder = new File(context.getFilesDir(), "Downloaded story"); assertTrue(folder.mkdir());
        File image = new File(folder, "1.png"); assertTrue(image.createNewFile());
        LocalGallery local = new LocalGallery(folder);
        store.record(local, 1);
        assertTrue(store.list().get(0).restore(context) instanceof LocalGallery);
        store.remove(ReadingHistoryStore.key(local)); assertTrue(store.list().isEmpty()); assertTrue(image.isFile());
        store.record(local, 1); store.clear(); assertTrue(store.list().isEmpty()); assertTrue(image.isFile());
        store.record(local, 1); assertTrue(image.delete());
        try { store.list().get(0).restore(context); fail("Missing downloads must not open an empty reader"); }
        catch (java.io.IOException expected) { }
    }
}
