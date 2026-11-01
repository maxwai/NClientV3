package com.maxwai.nclientv3.settings;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.maxwai.nclientv3.api.components.*;
import com.maxwai.nclientv3.api.local.LocalGallery;
import com.maxwai.nclientv3.utility.ImageUrlResolver;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.*;

/** Private, bounded reading history; independent of favorites and downloaded files. */
public final class ReadingHistoryStore extends SQLiteOpenHelper {
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final Context context;
    private static ReadingHistoryStore instance;
    public static synchronized ReadingHistoryStore get(Context context) {
        if (instance == null) instance = new ReadingHistoryStore(context.getApplicationContext());
        return instance;
    }
    public ReadingHistoryStore(Context context) {
        super(context, "ReadingHistory.db", null, 1);
        this.context = context.getApplicationContext();
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE reading (entry_key TEXT PRIMARY KEY, gallery_id INTEGER, title TEXT, cover TEXT, page INTEGER, total INTEGER, updated INTEGER, visited INTEGER NOT NULL, snapshot TEXT, local_path TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    public static String key(GenericGallery gallery) {
        return gallery instanceof LocalGallery ? "local:" + ((LocalGallery) gallery).getDirectory().getAbsolutePath() : "online:" + gallery.getId();
    }
    public static int clampPage(int page, int total) { return Math.max(1, Math.min(page, Math.max(1, total))); }
    public void record(GenericGallery gallery, int page) throws IOException, JSONException {
        if (gallery.getPageCount() < 1) return;
        String local = gallery instanceof LocalGallery ? ((LocalGallery) gallery).getDirectory().getAbsolutePath() : "";
        String snapshot = gallery instanceof Gallery ? snapshot((Gallery) gallery) : "";
        String cover = gallery instanceof Gallery ? ((Gallery) gallery).getThumbnail().toString()
            : android.net.Uri.fromFile(((LocalGallery) gallery).getPage(((LocalGallery) gallery).getMin())).toString();
        ContentValues row = new ContentValues();
        row.put("entry_key", key(gallery)); row.put("gallery_id", gallery.getId()); row.put("title", gallery.getTitle());
        row.put("cover", cover); row.put("page", clampPage(page, gallery.getPageCount())); row.put("total", gallery.getPageCount());
        row.put("updated", System.currentTimeMillis()); row.put("snapshot", snapshot); row.put("local_path", local);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Order visits independently of clock changes and same-millisecond page turns.
            row.put("visited", android.database.DatabaseUtils.longForQuery(db, "SELECT COALESCE(MAX(visited),0)+1 FROM reading", null));
            db.insertWithOnConflict("reading", null, row, SQLiteDatabase.CONFLICT_REPLACE);
            int limit = Math.max(1, Math.min(200, context.getSharedPreferences("Settings", 0)
                .getInt(context.getString(com.maxwai.nclientv3.R.string.preference_key_max_history_size), 100)));
            db.execSQL("DELETE FROM reading WHERE entry_key NOT IN (SELECT entry_key FROM reading ORDER BY visited DESC LIMIT ?)", new Object[]{limit});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void updatePage(String key, int page) {
        getWritableDatabase().execSQL("UPDATE reading SET page=MAX(1, MIN(?,total)), updated=?, visited=(SELECT COALESCE(MAX(visited),0)+1 FROM reading) WHERE entry_key=?",
            new Object[]{page, System.currentTimeMillis(), key});
    }
    public List<Entry> list() {
        List<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT entry_key,gallery_id,title,cover,page,total,updated,snapshot,local_path FROM reading ORDER BY visited DESC", null)) {
            while (c.moveToNext()) result.add(new Entry(c.getString(0),c.getInt(1),c.getString(2),c.getString(3),c.getInt(4),c.getInt(5),c.getLong(6),c.getString(7),c.getString(8)));
        }
        return result;
    }
    public void remove(String key) { getWritableDatabase().delete("reading", "entry_key=?", new String[]{key}); }
    public void clear() { getWritableDatabase().delete("reading", null, null); }
    private static String path(android.net.Uri uri) {
        String value = uri.toString();
        return ImageUrlResolver.isManaged(value) ? ImageUrlResolver.rawPath(value) : value;
    }
    private static JSONObject image(Page page) throws JSONException {
        JSONObject object = new JSONObject().put("path", path(page.getImagePath()))
            .put("width", page.getSize().getWidth()).put("height", page.getSize().getHeight());
        if (!page.getThumbnailPath().equals(page.getImagePath())) object.put("thumbnail", path(page.getThumbnailPath()));
        return object;
    }
    private static String snapshot(Gallery gallery) throws IOException, JSONException {
        StringWriter writer = new StringWriter(); gallery.jsonWrite(writer);
        JSONObject object = new JSONObject(writer.toString());
        object.put("cover", image(gallery.getGalleryData().getCover()));
        object.put("thumbnail", image(gallery.getGalleryData().getThumbnail()));
        JSONArray pages = new JSONArray();
        for (Page page : gallery.getGalleryData().getPages()) pages.put(image(page));
        object.put("pages", pages).put("num_pages", gallery.getPageCount());
        return object.toString();
    }
    public static final class Entry {
        public final String key, title, cover, snapshot, localPath;
        public final int id, page, total;
        public final long updated;
        public Entry(String key, int id, String title, String cover, int page, int total, long updated, String snapshot, String localPath) {
            this.key=key; this.id=id; this.title=title; this.cover=cover; this.page=page; this.total=total;
            this.updated=updated; this.snapshot=snapshot; this.localPath=localPath;
        }
        public GenericGallery restore(Context context) throws IOException {
            if (!localPath.isEmpty()) {
                LocalGallery local = new LocalGallery(new File(localPath));
                if (!local.isValid() || local.getPageCount() < 1) throw new IOException("Downloaded gallery is no longer available");
                return local;
            }
            return snapshot.isEmpty() ? null : new Gallery(context, snapshot, null, false);
        }
    }
}
