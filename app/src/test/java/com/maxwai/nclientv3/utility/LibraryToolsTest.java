package com.maxwai.nclientv3.utility;

import android.app.Application;
import android.content.*;
import android.os.Looper;
import android.view.*;
import androidx.appcompat.widget.SearchView;
import com.maxwai.nclientv3.*;
import com.maxwai.nclientv3.settings.*;
import com.maxwai.nclientv3.settings.ReadingHistoryStore.Entry;
import com.maxwai.nclientv3.async.database.DatabaseHelper;
import com.maxwai.nclientv3.async.downloader.*;
import com.maxwai.nclientv3.api.local.*;
import com.maxwai.nclientv3.adapters.LocalAdapter;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=31, application=Application.class, qualifiers="w320dp-h640dp-night-mdpi")
public class LibraryToolsTest {
    private Context context;
    private DatabaseHelper database;
    @Before public void setup() throws Exception {
        context=RuntimeEnvironment.getApplication(); ReadingFixtures.initialize(context);
        org.robolectric.util.ReflectionHelpers.setStaticField(ReadingHistoryStore.class,"instance",null);
        org.robolectric.util.ReflectionHelpers.setStaticField(Global.class,"localSortType",new LocalSortType(0));
        database=new DatabaseHelper(context); database.getWritableDatabase();
        context.getSharedPreferences("Settings",0).edit().remove("downloaded_reading").commit();
    }
    private void drain() throws Exception { ReadingHistoryStore.IO.submit(()->{}).get(5,TimeUnit.SECONDS); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    @After public void close() throws Exception { drain(); ReadingHistoryStore.get(context).close(); database.close(); }
    @Test public void titleArtistAndIdSearchUsesSavedMetadataAndAllWords() throws Exception {
        ReadingHistoryStore.get(context).record(ReadingFixtures.gallery(context,42,true),3);
        Entry e=ReadingHistoryStore.get(context).list().get(0); String text=HistoryFilter.searchText(e);
        assertTrue(HistoryFilter.matches(e,text," RAIN   artist 42 ",0));
        assertFalse(HistoryFilter.matches(e,text,"rain missing",0));
        Entry legacy=new Entry("old",7,"Legacy Story","",1,0,0,"","");
        assertTrue(HistoryFilter.matches(legacy,HistoryFilter.searchText(legacy),"legacy 7",0));
    }
    @Test public void progressFiltersDoNotTreatUnknownTotalsAsFinished() {
        Entry ongoing=new Entry("a",1,"A","",3,8,0,"","");
        Entry finished=new Entry("b",2,"B","",8,8,0,"","");
        Entry unknown=new Entry("c",3,"C","",4,0,0,"","");
        assertTrue(HistoryFilter.matches(ongoing,"","",1)); assertFalse(HistoryFilter.matches(finished,"","",1));
        assertTrue(HistoryFilter.matches(finished,"","",2)); assertFalse(HistoryFilter.matches(unknown,"","",2));
    }
    @Test public void historySearchAndFilterSurviveRecreation() throws Exception {
        ReadingHistoryStore.get(context).record(ReadingFixtures.gallery(context,1,true),8);
        ReadingHistoryStore.get(context).record(ReadingFixtures.gallery(context,2),3);
        ActivityController<HistoryActivity> controller=Robolectric.buildActivity(HistoryActivity.class);
        controller.get().setTheme(R.style.AppTheme);
        try(ActivityController<HistoryActivity> running=controller.setup()) {
            drain(); HistoryActivity a=running.get();
            androidx.appcompat.widget.PopupMenu popup=new androidx.appcompat.widget.PopupMenu(a,a.findViewById(R.id.toolbar));
            a.onCreateOptionsMenu(popup.getMenu());
            ((SearchView)popup.getMenu().findItem(R.id.history_search).getActionView()).setQuery("Fixture Artist",false);
            assertEquals(1,a.getRecycler().getAdapter().getItemCount());
            a.onOptionsItemSelected(popup.getMenu().findItem(R.id.history_filter));
            androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
            dialog.getListView().performItemClick(null,2,2);
            running.recreate(); drain();
            assertEquals(1,running.get().getRecycler().getAdapter().getItemCount());
            androidx.appcompat.widget.PopupMenu recreated=new androidx.appcompat.widget.PopupMenu(running.get(),running.get().findViewById(R.id.toolbar));
            running.get().onCreateOptionsMenu(recreated.getMenu());
            assertEquals("Fixture Artist",((SearchView)recreated.getMenu().findItem(R.id.history_search).getActionView()).getQuery().toString());
            assertEquals(context.getString(R.string.history_finished),recreated.getMenu().findItem(R.id.history_filter).getTitle().toString());
        }
    }
    @Test public void downloadedSwitchPersistsAndLauncherSkipsOnlineBrowsing() throws Exception {
        ActivityController<HistoryActivity> controller=Robolectric.buildActivity(HistoryActivity.class); controller.get().setTheme(R.style.AppTheme);
        try(ActivityController<HistoryActivity> running=controller.setup()) {
            drain(); running.get().findViewById(R.id.reading_source).performClick();
            Intent launched=Shadows.shadowOf(running.get()).getNextStartedActivity();
            assertEquals(LocalActivity.class.getName(),launched.getComponent().getClassName());
            assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
            assertTrue(ReadingSource.downloadedOnly(context)); assertEquals(LocalActivity.class,ReadingSource.startup(context));
        }
        ActivityController<PINActivity> pin=Robolectric.buildActivity(PINActivity.class); pin.get().setTheme(R.style.AppTheme);
        try(ActivityController<PINActivity> running=pin.setup()) {
            assertEquals(LocalActivity.class.getName(),Shadows.shadowOf(running.get()).getNextStartedActivity().getComponent().getClassName());
        }
    }
    @Test public void returnOnlineClearsRememberedLibrary() {
        ReadingSource.remember(context,true);
        try(ActivityController<android.app.Activity> c=Robolectric.buildActivity(android.app.Activity.class).setup()) {
            ReadingSource.online(c.get()); assertFalse(ReadingSource.downloadedOnly(context));
            assertEquals(MainActivity.class,ReadingSource.startup(context));
            assertEquals(MainActivity.class.getName(),Shadows.shadowOf(c.get()).getNextStartedActivity().getComponent().getClassName());
        }
    }
    @Test public void downloadedLibraryHidesQueuedDownloadsButPreservesSavedStories() throws Exception {
        File folder=new File(context.getFilesDir(),"Saved story"); assertTrue(folder.mkdir()); assertTrue(new File(folder,"1.png").createNewFile());
        ArrayList<LocalGallery> stories=new ArrayList<>(); stories.add(new LocalGallery(folder));
        GalleryDownloaderManager manager=new GalleryDownloaderManager(context,"Pending",android.net.Uri.parse("https://example.invalid/thumb"),2);
        DownloadQueue.add(manager);
        ActivityController<LocalActivity> c=Robolectric.buildActivity(LocalActivity.class);
        LocalAdapter offline=null, online=null;
        try {
            ReadingSource.remember(context,true); offline=new LocalAdapter(c.get(),stories); assertEquals(1,offline.getItemCount());
            java.util.concurrent.atomic.AtomicInteger notifiedCount=new java.util.concurrent.atomic.AtomicInteger(-1);
            LocalAdapter observed=offline;
            offline.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                @Override public void onChanged() { notifiedCount.set(observed.getItemCount()); }
                @Override public void onItemRangeInserted(int start,int count) { notifiedCount.set(observed.getItemCount()); }
            });
            File second=new File(context.getFilesDir(),"Another saved story"); assertTrue(second.mkdir()); assertTrue(new File(second,"1.png").createNewFile());
            offline.addGalleries(Collections.singletonList(new LocalGallery(second))); Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(2,notifiedCount.get());
            ReadingSource.remember(context,false); online=new LocalAdapter(c.get(),stories); assertEquals(2,online.getItemCount());
        } finally {
            if(offline!=null)offline.removeObserver(); if(online!=null)online.removeObserver(); DownloadQueue.remove(manager.downloader(),false);
        }
    }
}
