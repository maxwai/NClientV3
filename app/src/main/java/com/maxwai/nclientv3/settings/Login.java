package com.maxwai.nclientv3.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.loginapi.LoadTags;
import com.maxwai.nclientv3.loginapi.User;
import com.maxwai.nclientv3.utility.Utility;

import okhttp3.HttpUrl;

public class Login {
    public static HttpUrl BASE_HTTP_URL;
    private static User user;
    private static boolean accountTag;
    private static Context appContext;

    public static void initLogin(@NonNull Context context) {
        appContext = context.getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences("Settings", 0);
        accountTag = preferences.getBoolean(context.getString(R.string.preference_key_use_account_tag), false);
        BASE_HTTP_URL = HttpUrl.get(Utility.getBaseUrl());
    }

    public static boolean useAccountTag() {
        return accountTag;
    }

    public static void clearOnlineTags() {
        Queries.TagTable.removeAllBlacklisted();
    }

    public static void addOnlineTag(Tag tag) {
        Queries.TagTable.insert(tag);
        Queries.TagTable.updateBlacklistedTag(tag, true);
    }

    public static void removeOnlineTag(Tag tag) {
        Queries.TagTable.updateBlacklistedTag(tag, false);
    }

    public static boolean isLogged(@Nullable Context context) {
        Context authContext = context != null ? context.getApplicationContext() : appContext;
        if (authContext != null && AuthStore.hasCredentials(authContext)) {
            if (context != null && user == null) {
                User.createUser(context, user -> {
                    if (user != null) {
                        new LoadTags(context).start();
                    }
                });
            }
            return true;
        }
        return false;
    }

    public static boolean isLogged() {
        return isLogged(null);
    }


    public static User getUser() {
        return user;
    }

    public static void updateUser(User user) {
        Login.user = user;
    }


    public static boolean isOnlineTags(Tag tag) {
        return Queries.TagTable.isBlackListed(tag);
    }

}
