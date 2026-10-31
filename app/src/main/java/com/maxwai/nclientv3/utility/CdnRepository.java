package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import okhttp3.*;

/** One public discovery request is shared by concurrent reader/thumbnail loads. */
public final class CdnRepository {
    public static final class Unavailable extends IOException {
        public Unavailable(String message, Throwable cause) { super(message, cause); }
    }
    private final Supplier<OkHttpClient> client;
    private final Supplier<String> endpoint;
    private final String userAgent;
    private final LongSupplier clock;
    private volatile CdnServers cached;
    private volatile String cachedEndpoint;
    private long validUntil, lastFetch, retryAfter;
    private IOException lastFailure;
    private volatile String preferredImage, preferredThumb;
    public CdnRepository(OkHttpClient client, Supplier<String> endpoint, String userAgent) {
        this(client, endpoint, userAgent, System::currentTimeMillis);
    }
    public CdnRepository(OkHttpClient client, Supplier<String> endpoint, String userAgent, LongSupplier clock) {
        this.client = () -> client; this.endpoint = endpoint; this.userAgent = userAgent; this.clock = clock;
    }
    public CdnRepository(Supplier<OkHttpClient> client, Supplier<String> endpoint, String userAgent) {
        this.client = client; this.endpoint = endpoint; this.userAgent = userAgent; this.clock = System::currentTimeMillis;
    }
    public synchronized CdnServers get() throws IOException {
        long now = clock.getAsLong();
        String address = endpoint.get();
        if (!address.equals(cachedEndpoint)) { cached = null; lastFailure = null; retryAfter = 0; preferredImage = preferredThumb = null; cachedEndpoint = address; }
        if (cached != null && now < validUntil) return cached;
        if (lastFailure != null && now < retryAfter) throw lastFailure;
        try (Response response = client.get().newCall(new Request.Builder().url(address)
            .header("User-Agent", userAgent).header("Accept", "application/json").build()).execute()) {
            CdnServers servers = CdnServers.parse(com.maxwai.nclientv3.api.ApiResponse.read(response));
            cached = servers; lastFetch = clock.getAsLong(); validUntil = lastFetch + 10 * 60_000L;
            lastFailure = null; retryAfter = 0;
            return servers;
        } catch (IOException | IllegalArgumentException error) {
            lastFailure = new Unavailable("Unable to load image-server configuration", error);
            retryAfter = clock.getAsLong() + 15_000;
            throw lastFailure;
        }
    }
    public synchronized java.util.List<String> urlsFor(String managedUrl) throws IOException {
        return get().resolve(managedUrl, ImageUrlResolver.isThumbnail(managedUrl) ? preferredThumb : preferredImage);
    }
    /** UI diagnostics must not wait on a discovery request holding the repository lock. */
    public java.util.List<String> cachedUrls(String managedUrl) {
        CdnServers snapshot = cached;
        if (snapshot == null || !endpoint.get().equals(cachedEndpoint)) return java.util.Collections.emptyList();
        return snapshot.resolve(managedUrl, ImageUrlResolver.isThumbnail(managedUrl) ? preferredThumb : preferredImage);
    }
    public synchronized void succeeded(String managedUrl, String resolvedUrl) {
        if (cached == null) return;
        String base = cached.baseFor(managedUrl, resolvedUrl);
        if (ImageUrlResolver.isThumbnail(managedUrl)) preferredThumb = base;
        else preferredImage = base;
    }
    public synchronized void invalidate() {
        validUntil = Math.min(validUntil, lastFetch + 30_000);
    }
    public synchronized void verificationCompleted() {
        validUntil = 0;
        lastFailure = null;
        retryAfter = 0;
    }
}
