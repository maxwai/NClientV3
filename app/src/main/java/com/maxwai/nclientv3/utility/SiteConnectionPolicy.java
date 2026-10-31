package com.maxwai.nclientv3.utility;

import okhttp3.HttpUrl;

/** Verification and credentials are limited to the official HTTPS origin. */
public final class SiteConnectionPolicy {
    private SiteConnectionPolicy() { }
    public static boolean isSite(String value) {
        HttpUrl url = HttpUrl.parse(value);
        return url != null && url.isHttps() && url.port() == 443
            && url.host().equals("nhentai.net") && url.username().isEmpty() && url.password().isEmpty();
    }
    public static boolean isVerificationCookie(String name) {
        return "cf_clearance".equals(name) || "__cf_bm".equals(name);
    }
}
