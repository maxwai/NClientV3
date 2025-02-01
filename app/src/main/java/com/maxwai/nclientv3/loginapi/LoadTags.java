package com.maxwai.nclientv3.loginapi;

import android.util.JsonReader;

import androidx.annotation.Nullable;

import com.maxwai.nclientv3.adapters.TagsAdapter;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;

public class LoadTags extends Thread {
    @Nullable
    private final TagsAdapter adapter;

    public LoadTags(@Nullable TagsAdapter adapter) {
        this.adapter = adapter;
    }

    private Elements getScripts(String url) throws IOException {

        try (Response response = Global.getClient().newCall(new Request.Builder().url(url).build()).execute()) {
            return Jsoup.parse(response.body().byteStream(), null, Utility.getBaseUrl()).getElementsByTag("script");
        }
    }

    private String extractArray(Element e) throws StringIndexOutOfBoundsException {
        String t = e.toString();
        return t.substring(t.indexOf('['), t.indexOf(';'));
    }

    private void readTags(JsonReader reader) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            Tag tt = new Tag(reader);
            if (tt.getType() != TagType.LANGUAGE && tt.getType() != TagType.CATEGORY) {
                Login.addOnlineTag(tt);
                if (adapter != null) adapter.addItem();
            }
        }
    }

    @Override
    public void run() {
        super.run();
        if (Login.getUser() == null) return;
        String url = String.format(Locale.US, Utility.getBaseUrl() + "users/%s/%s/blacklist",
            Login.getUser().getId(), Login.getUser().getCodename()
        );
        LogUtility.d(url);
        try {
            Elements scripts = getScripts(url);
            analyzeScripts(scripts);
        } catch (IOException | StringIndexOutOfBoundsException e) {
            LogUtility.e("Error getting blacklisted Tags from website", e);
        }

    }

    private void analyzeScripts(Elements scripts) throws IOException, StringIndexOutOfBoundsException {
        if (!scripts.isEmpty()) {
            Login.clearOnlineTags();
            String array = Utility.unescapeUnicodeString(extractArray(scripts.last()));
            try (JsonReader reader = new JsonReader(new StringReader(array))) {
                readTags(reader);
            }
        }
    }
}
