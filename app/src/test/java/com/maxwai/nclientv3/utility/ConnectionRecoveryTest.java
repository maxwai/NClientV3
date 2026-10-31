package com.maxwai.nclientv3.utility;

import android.app.Application;
import android.content.Context;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;
import com.maxwai.nclientv3.components.CustomCookieJar;
import com.maxwai.nclientv3.settings.*;
import com.maxwai.nclientv3.api.ApiResponse;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, application = Application.class)
public class ConnectionRecoveryTest {
    private Context context;
    private CustomCookieJar jar;
    private OkHttpClient previous;
    private static final String CONFIG = "{\"image_servers\":[\"https://images.example\"],\"thumb_servers\":[\"https://thumbs.example\"]}";
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        jar = new CustomCookieJar(new SetCookieCache(), new SharedPrefsCookiePersistor(context.getSharedPreferences("Login", 0)));
        previous = Global.client;
        Global.client = new OkHttpClient.Builder().cookieJar(jar).build();
    }
    @After public void cleanup() { Global.client = previous; }
    @Test public void cookiesRespectHostPathSecureAndExpiry() {
        HttpUrl site = HttpUrl.get("https://nhentai.net/");
        jar.saveFromResponse(site, Arrays.asList(
            new Cookie.Builder().name("cf_clearance").value("fixture").hostOnlyDomain(site.host()).secure().path("/api/").build(),
            new Cookie.Builder().name("expired").value("fixture").hostOnlyDomain(site.host()).expiresAt(1).build()));
        assertEquals(1, jar.loadForRequest(HttpUrl.get("https://nhentai.net/api/v2/cdn")).size());
        for (String url : new String[]{"https://github.com/api/v2/cdn", "https://i1.nhentai.net/api/v2/cdn", "http://nhentai.net/api/v2/cdn", "https://nhentai.net/other"})
            assertTrue(url, jar.loadForRequest(HttpUrl.get(url)).isEmpty());
    }
    @Test public void verificationImportsOnlyChallengeCookiesAndKeepsBrowserUserAgent() {
        SiteSession.importVerification(context, "sessionid=private; cf_clearance=fixture; __cf_bm=bm; account_token=private", "Browser NClient/test");
        List<Cookie> cookies = jar.loadForRequest(HttpUrl.get("https://nhentai.net/api/v2/galleries"));
        assertEquals(2, cookies.size());
        for (Cookie cookie : cookies) {
            assertTrue(SiteConnectionPolicy.isVerificationCookie(cookie.name()));
            assertTrue(cookie.secure()); assertTrue(cookie.hostOnly()); assertTrue(cookie.httpOnly());
        }
        assertEquals("Browser NClient/test", SiteSession.userAgent(context));
        assertFalse(AuthStore.hasCredentials(context));
    }
    @Test public void cookieClearAlsoClearsHttpClientAndPersistedSession() {
        SiteSession.importVerification(context, "cf_clearance=fixture", "Browser NClient/test");
        SiteSession.clear(context);
        assertTrue(jar.loadForRequest(HttpUrl.get("https://nhentai.net/")).isEmpty());
        assertTrue(context.getSharedPreferences("Login", 0).getAll().isEmpty());
        assertTrue(context.getSharedPreferences("SiteSession", 0).getAll().isEmpty());
    }
    @Test public void publicApiDoesNotNeedOrInventCredentials() throws Exception {
        ApiAuthInterceptor auth = new ApiAuthInterceptor(context, true);
        Request original = new Request.Builder().url("https://nhentai.net/api/v2/galleries").build();
        try (Response ignored = auth.intercept(TestChain.create(original, null, request -> {
            assertNull(request.header("Authorization"));
            assertEquals("application/json", request.header("Accept"));
            assertTrue(request.header("User-Agent").contains("NClient/"));
            return TestChain.response(request, 200, "{\"result\":[]}");
        }))) { }
    }
    @Test public void credentialsNeverAttachToOtherOriginsOrMedia() throws Exception {
        AuthStore.saveApiKey(context, "test-only-not-a-real-key", true);
        ApiAuthInterceptor auth = new ApiAuthInterceptor(context, false);
        for (String url : new String[]{"https://other.example/api/v2/galleries", "http://nhentai.net/api/v2/galleries", "https://nhentai.net:444/api/v2/galleries", "https://nhentai.net/image.jpg", "https://i1.nhentai.net/api/v2/cdn"}) {
            Request original = new Request.Builder().url(url).build();
            try (Response ignored = auth.intercept(TestChain.create(original, null, request -> {
                assertNull(request.header("Authorization")); return TestChain.response(request, 200, "{}");
            }))) { }
        }
        Request original = new Request.Builder().url("https://nhentai.net/api/v2/user").build();
        try (Response ignored = auth.intercept(TestChain.create(original, null, request -> {
            assertEquals("Key test-only-not-a-real-key", request.header("Authorization"));
            return TestChain.response(request, 200, "{}");
        }))) { }
    }
    @Test public void verificationNavigationRejectsUnsafeDestinations() {
        assertTrue(SiteConnectionPolicy.isSite("https://nhentai.net/"));
        for (String url : new String[]{"http://nhentai.net/", "https://nhentai.net.evil.example/", "https://evil.example/?next=https://nhentai.net", "file:///data/private", "content://private", "intent://test", "https://user:secret@nhentai.net/", "https://nhentai.net:444/"})
            assertFalse(url, SiteConnectionPolicy.isSite(url));
    }
    @Test public void cdnDiscoveryRecoversWithVerifiedSessionWithoutReusingCachedFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new ApiAuthInterceptor(context, false))
            .addInterceptor(chain -> {
                calls.incrementAndGet();
                if (!SiteSession.userAgent(context).equals("Browser NClient/test")) return TestChain.response(chain.request(), 403, "{}");
                assertEquals("Browser NClient/test", chain.request().header("User-Agent"));
                assertNull(chain.request().header("Authorization"));
                return TestChain.response(chain.request(), 200, CONFIG);
            }).build();
        CdnRepository repository = new CdnRepository(() -> client, () -> "https://nhentai.net/api/v2/cdn", "NClient/test");
        try { repository.get(); fail(); } catch (CdnRepository.Unavailable expected) { }
        SiteSession.importVerification(context, "cf_clearance=fixture", "Browser NClient/test");
        repository.verificationCompleted();
        assertNotNull(repository.get()); assertEquals(2, calls.get());
    }
    @Test public void diagnosticMessagesDoNotEchoSecretsOrUrls() {
        String secret = "secret-cookie-value";
        assertFalse(ConnectionFailure.describe(new java.io.IOException(secret)).contains(secret));
        assertTrue(ConnectionFailure.describe(new java.net.UnknownHostException(secret)).contains("DNS"));
        assertTrue(ConnectionFailure.describe(new javax.net.ssl.SSLException(secret)).contains("secure"));
        assertTrue(ConnectionFailure.describe(new java.net.SocketTimeoutException(secret)).contains("time"));
    }
}
