package com.maxwai.nclientv3.utility;

import android.app.Application;
import android.webkit.*;
import android.widget.TextView;
import com.maxwai.nclientv3.*;
import com.maxwai.nclientv3.settings.Global;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, application = Application.class)
public class SiteConnectionActivityTest {
    private OkHttpClient previous;
    @Before public void setup() {
        previous = Global.client;
        Global.initStorage(RuntimeEnvironment.getApplication());
    }
    @After public void cleanup() { Global.client = previous; }
    private ActivityController<SiteConnectionActivity> launch() {
        ActivityController<SiteConnectionActivity> controller = Robolectric.buildActivity(SiteConnectionActivity.class);
        controller.get().setTheme(R.style.AppTheme);
        return controller.setup();
    }
    private void awaitCheck(SiteConnectionActivity activity) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!activity.findViewById(R.id.connection_check).isEnabled() && System.nanoTime() < end) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertTrue("Connection check must finish", activity.findViewById(R.id.connection_check).isEnabled());
    }
    @Test public void successfulPublicApiAndCdnCheckReturnsToCaller() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Global.client = new OkHttpClient.Builder().addInterceptor(chain -> {
            calls.incrementAndGet();
            assertNull(chain.request().header("Authorization"));
            return TestChain.response(chain.request(), 200, chain.request().url().encodedPath().endsWith("/cdn")
                ? "{\"image_servers\":[\"https://images.example\"],\"thumb_servers\":[\"https://thumbs.example\"]}"
                : "{\"result\":[]}");
        }).build();
        try (ActivityController<SiteConnectionActivity> controller = launch()) {
            SiteConnectionActivity activity = controller.get(); awaitCheck(activity);
            assertEquals(2, calls.get());
            assertEquals(activity.getString(R.string.connection_verified), ((TextView) activity.findViewById(R.id.connection_status)).getText().toString());
            activity.findViewById(R.id.connection_check).performClick();
            assertEquals(android.app.Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        }
    }
    @Test public void accessDeniedStopsAfterOneRequestAndCanRetryManually() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Global.client = new OkHttpClient.Builder().addInterceptor(chain -> {
            calls.incrementAndGet(); return TestChain.response(chain.request(), 403, "{}");
        }).build();
        try (ActivityController<SiteConnectionActivity> controller = launch()) {
            SiteConnectionActivity activity = controller.get(); awaitCheck(activity);
            assertEquals(1, calls.get());
            assertTrue(((TextView) activity.findViewById(R.id.connection_status)).getText().toString().contains("403"));
            activity.findViewById(R.id.connection_check).performClick(); awaitCheck(activity);
            assertEquals(2, calls.get());
        }
    }
    @Test public void dnsFailureIsIdentifiedAndDoesNotPretendVerificationCanRepairIt() throws Exception {
        Global.client = new OkHttpClient.Builder().addInterceptor(chain -> { throw new java.net.UnknownHostException("fixture"); }).build();
        try (ActivityController<SiteConnectionActivity> controller = launch()) {
            SiteConnectionActivity activity = controller.get(); awaitCheck(activity);
            assertTrue(((TextView) activity.findViewById(R.id.connection_status)).getText().toString().contains("DNS"));
            assertEquals(activity.getString(R.string.connection_check), ((TextView) activity.findViewById(R.id.connection_check)).getText().toString());
        }
    }
    @Test public void verificationWindowDisablesLocalFilesAndBlocksExternalNavigation() throws Exception {
        Global.client = new OkHttpClient.Builder().addInterceptor(chain -> TestChain.response(chain.request(), 403, "{}")).build();
        try (ActivityController<SiteConnectionActivity> controller = launch()) {
            SiteConnectionActivity activity = controller.get(); awaitCheck(activity);
            activity.findViewById(R.id.connection_verify).performClick();
            android.widget.FrameLayout container = activity.findViewById(R.id.verification_container);
            WebView web = (WebView) container.getChildAt(0);
            assertEquals("https://nhentai.net/", web.getUrl());
            assertFalse(web.getSettings().getAllowFileAccess()); assertFalse(web.getSettings().getAllowContentAccess());
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.getSettings().getMixedContentMode());
            assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(web));
            WebViewClient client = Shadows.shadowOf(web).getWebViewClient();
            assertTrue(client.shouldOverrideUrlLoading(web, "https://outside.example/"));
            assertTrue(client.shouldOverrideUrlLoading(web, "file:///data/private"));
            assertFalse(client.shouldOverrideUrlLoading(web, "https://nhentai.net/"));
        }
    }
}
