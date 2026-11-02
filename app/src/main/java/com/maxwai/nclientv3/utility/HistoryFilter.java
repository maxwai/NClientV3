package com.maxwai.nclientv3.utility;

import com.maxwai.nclientv3.settings.ReadingHistoryStore.Entry;
import java.util.Locale;
import org.json.*;

/** Search only metadata already stored on this device. */
public final class HistoryFilter {
    private HistoryFilter() { }
    public static String searchText(Entry entry) {
        StringBuilder text = new StringBuilder(entry.title).append(' ').append(entry.id);
        try {
            JSONObject snapshot = new JSONObject(entry.snapshot);
            JSONObject titles = snapshot.optJSONObject("title");
            if (titles != null) text.append(' ').append(titles.optString("english")).append(' ').append(titles.optString("japanese"));
            JSONArray tags = snapshot.optJSONArray("tags");
            if (tags != null) for (int i=0; i<tags.length(); i++) {
                JSONObject tag = tags.optJSONObject(i);
                if (tag != null && "artist".equals(tag.optString("type"))) text.append(' ').append(tag.optString("name"));
            }
        } catch (JSONException ignored) { }
        return text.toString().toLowerCase(Locale.ROOT);
    }
    public static boolean matches(Entry entry, String text, String query, int filter) {
        boolean finished = entry.total > 0 && entry.page >= entry.total;
        if (filter == 1 && finished || filter == 2 && !finished) return false;
        for (String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) if (!text.contains(word)) return false;
        return true;
    }
}
