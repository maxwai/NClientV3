package com.maxwai.nclientv3.utility;

import com.maxwai.nclientv3.BuildConfig;
import java.io.IOException;
import okhttp3.OkHttpClient;

public final class ImageHttpClient {
    private static final String USER_AGENT = "NClient/" + BuildConfig.VERSION_NAME + " (https://github.com/Seraphene/NClientV3)";
    private static final CdnRepository CDN = new CdnRepository(com.maxwai.nclientv3.settings.Global::getClient,
        () -> Utility.getApiBaseUrl() + "cdn", USER_AGENT);
    private static final OkHttpClient CLIENT = NetworkClients.images(Utility::getHost, USER_AGENT).newBuilder()
        .addInterceptor(new CdnRoutingInterceptor(CDN, Utility::getHost, USER_AGENT)).build();
    private ImageHttpClient() { }
    public static OkHttpClient get() { return CLIENT; }
    public static void verificationCompleted() { CDN.verificationCompleted(); }
    public static java.util.List<String> cachedUrls(String value) { return CDN.cachedUrls(value); }
    public static String resolveForBrowser(String value) throws IOException {
        if (!ImageUrlResolver.isManaged(value)) return value;
        return CDN.urlsFor(value).get(0);
    }
}
