package com.maxwai.nclientv3.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.CustomCookieJar;
import com.maxwai.nclientv3.loginapi.LoadTags;
import com.maxwai.nclientv3.loginapi.User;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import java.util.List;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class Login {
    public static final String LOGIN_COOKIE = "sessionid";
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

    /**
     * @noinspection SameParameterValue
     */
    private static void removeCookie(String cookieName) {
        CustomCookieJar cookieJar = (CustomCookieJar) Global.client.cookieJar();
        cookieJar.removeCookie(cookieName);
    }


    public static void logout() {
        if (appContext != null) AuthStore.clear(appContext);
        CustomCookieJar cookieJar = (CustomCookieJar) Global.client.cookieJar();
        removeCookie(LOGIN_COOKIE);
        cookieJar.clearSession();
        updateUser(null);//remove user
        clearOnlineTags();//remove online tags
        clearWebViewCookies();//clear webView cookies
    }

    public static void clearWebViewCookies() {
        try {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } catch (Throwable ignore) {
        }//catch InvocationTargetException randomly thrown
    }

    public static void clearOnlineTags() {
        Queries.TagTable.removeAllBlacklisted();
    }

    public static void clearCookies(@NonNull Context context) {
        CustomCookieJar cookieJar = (CustomCookieJar) Global.getClient(context).cookieJar();
        cookieJar.clear();
        cookieJar.clearSession();
    }

    public static void addOnlineTag(Tag tag) {
        Queries.TagTable.insert(tag);
        Queries.TagTable.updateBlacklistedTag(tag, true);
    }

    public static void removeOnlineTag(Tag tag) {
        Queries.TagTable.updateBlacklistedTag(tag, false);
    }

    public static boolean hasCookie(String name) {
        List<Cookie> cookies = Global.client.cookieJar().loadForRequest(BASE_HTTP_URL);
        for (Cookie c : cookies) {
            if (c.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasLegacySession() {
        return hasCookie(LOGIN_COOKIE);
    }

    public static boolean isLogged(@Nullable Context context) {
        Context authContext = context != null ? context.getApplicationContext() : appContext;
        List<Cookie> cookies = Global.client.cookieJar().loadForRequest(BASE_HTTP_URL);
        LogUtility.d("Cookies: " + cookies);
        if (authContext != null && AuthStore.hasCredentials(authContext)) {
            return true;
        }
        if (hasLegacySession()) {
            if (context != null && user == null) User.createUser(context, user -> {
                if (user != null) {
                    new LoadTags(context).start();
                }
            });
            return true;
        }
        if (context != null) logout();
        return false;
        //return sessionId!=null;
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
