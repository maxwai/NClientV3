package com.maxwai.nclientv3.settings;

import android.content.Context;
import com.maxwai.nclientv3.BuildConfig;
import com.maxwai.nclientv3.utility.SiteConnectionPolicy;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

/** Imports only site-verification cookies, never website account credentials. */
public final class SiteSession {
    private SiteSession() { }
    public static String userAgent(Context context) {
        return context.getSharedPreferences("SiteSession", 0).getString("user_agent",
            "NClient/" + BuildConfig.VERSION_NAME + " (https://github.com/Seraphene/NClientV3)");
    }
    public static void importVerification(Context context, String cookieHeader, String userAgent) {
        if (cookieHeader == null) return;
        HttpUrl site = HttpUrl.get("https://nhentai.net/");
        List<Cookie> cookies = new ArrayList<>();
        for (String part : cookieHeader.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2 || !SiteConnectionPolicy.isVerificationCookie(pair[0])) continue;
            try {
                cookies.add(new Cookie.Builder().name(pair[0]).value(pair[1]).hostOnlyDomain(site.host())
                    .path("/").secure().httpOnly().expiresAt(System.currentTimeMillis() + 60 * 60_000L).build());
            } catch (IllegalArgumentException ignored) { }
        }
        if (cookies.isEmpty()) return;
        Global.getClient(context).cookieJar().saveFromResponse(site, cookies);
        context.getSharedPreferences("SiteSession", 0).edit().putString("user_agent", userAgent).apply();
    }
    public static void clear(Context context) {
        if (Global.getClient(context).cookieJar() instanceof com.maxwai.nclientv3.components.CustomCookieJar)
            ((com.maxwai.nclientv3.components.CustomCookieJar) Global.getClient(context).cookieJar()).clear();
        context.getSharedPreferences("SiteSession", 0).edit().clear().apply();
    }
}
