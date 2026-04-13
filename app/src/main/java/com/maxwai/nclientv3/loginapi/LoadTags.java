package com.maxwai.nclientv3.loginapi;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;

import okhttp3.Request;
import okhttp3.Response;

public class LoadTags extends Thread {
    @NonNull
    private final Context context;

    public LoadTags(@NonNull Context context) {
        this.context = context;
    }

    private void readTags(JsonReader jr) throws IOException {
        jr.beginObject();
        while (jr.peek() != JsonToken.END_OBJECT) {
            if (jr.nextName().equals("tags")) {
                jr.beginArray();
                while (jr.peek() != JsonToken.END_ARRAY) {
                    Tag tt = new Tag(jr);
                    if (tt.getType() != TagType.LANGUAGE && tt.getType() != TagType.CATEGORY) {
                        Login.addOnlineTag(tt);
                    }
                }
                jr.endArray();

            } else {
                jr.skipValue();
            }
        }
        jr.endObject();
    }

    @Override
    public void run() {
        super.run();
        if (Login.getUser() == null) return;
        String url = Utility.getApiBaseUrl() + "blacklist";
        LogUtility.d(url);
        try (Response response = Global.getClient(context).newCall(new Request.Builder().url(url).build()).execute()) {
            JsonReader json = new JsonReader(response.body().charStream());
            Login.clearOnlineTags();
            readTags(json);
        } catch (IOException | StringIndexOutOfBoundsException e) {
            LogUtility.e("Error getting blacklisted Tags from website", e);
        }

    }
}
