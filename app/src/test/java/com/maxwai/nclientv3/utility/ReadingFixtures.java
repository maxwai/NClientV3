package com.maxwai.nclientv3.utility;

import android.content.Context;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.enums.TitleType;
import com.maxwai.nclientv3.settings.Global;
import org.json.*;

final class ReadingFixtures {
    static void initialize(Context context) throws Exception {
        Global.initStorage(context);
        field("titleType", TitleType.PRETTY);
        field("usageWifi", Global.DataUsageType.NONE);
        field("usageMobile", Global.DataUsageType.NONE);
        field("offscreenLimit", 1);
        field("maxHistory", 100);
        field("columnCount", 2);
        field("defaultZoom", 100);
        field("mirror", "nhentai.net");
    }
    private static void field(String name, Object value) throws Exception {
        java.lang.reflect.Field field = Global.class.getDeclaredField(name);
        field.setAccessible(true); field.set(null, value);
    }
    static Gallery gallery(Context context, int id) throws Exception {
        JSONArray pages = new JSONArray();
        for (int i = 1; i <= 8; i++) pages.put(new JSONObject()
            .put("path", "/galleries/fixture-" + id + "/opaque-" + i + ".webp?revision=2")
            .put("thumbnail", "/galleries/fixture-" + id + "/thumb-" + i + ".webp")
            .put("width", 800).put("height", 1200));
        JSONObject image = new JSONObject().put("path", "/galleries/fixture-" + id + "/cover.webp").put("width", 400).put("height", 600);
        JSONObject object = new JSONObject().put("id", id).put("media_id", Integer.toString(1000 + id))
            .put("title", new JSONObject().put("pretty", "After the Rain " + id).put("english", "After the Rain " + id).put("japanese", ""))
            .put("cover", image).put("thumbnail", image).put("num_pages", 8).put("pages", pages)
            .put("tags", new JSONArray()).put("upload_date", 1).put("num_favorites", 0);
        return new Gallery(context, object.toString(), null, false);
    }
}
