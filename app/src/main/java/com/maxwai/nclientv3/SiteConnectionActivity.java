package com.maxwai.nclientv3;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.SiteSession;
import com.maxwai.nclientv3.utility.*;
import com.maxwai.nclientv3.api.ApiResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.*;
import org.json.JSONObject;

public class SiteConnectionActivity extends GeneralActivity {
    private static final String SITE = "https://nhentai.net/";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Call currentCall;
    private WebView web;
    private TextView status;
    private MaterialButton check, verify;
    private boolean verified;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_site_connection);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.site_connection);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        status = findViewById(R.id.connection_status);
        check = findViewById(R.id.connection_check);
        verify = findViewById(R.id.connection_verify);
        check.setOnClickListener(v -> { if (verified) { setResult(RESULT_OK); finish(); } else checkConnection(); });
        verify.setOnClickListener(v -> openVerification());
        checkConnection();
    }

    private void checkConnection() {
        if (!check.isEnabled()) return;
        if (web != null) SiteSession.importVerification(this, CookieManager.getInstance().getCookie(SITE), web.getSettings().getUserAgentString());
        check.setEnabled(false); verify.setEnabled(false);
        status.setText(R.string.connection_checking);
        worker.execute(() -> {
            String message; boolean success = false;
            try {
                JSONObject galleries = new JSONObject(fetch("api/v2/galleries?per_page=1"));
                if (galleries.optJSONArray("result") == null) throw new ApiResponse.Failure("The gallery API returned an unsupported response.");
                CdnServers.parse(fetch("api/v2/cdn"));
                ImageHttpClient.verificationCompleted();
                success = true; message = getString(R.string.connection_verified);
            } catch (Exception error) { message = ConnectionFailure.describe(error); }
            final boolean result = success; final String text = message;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                verified = result; status.setText(text);
                check.setEnabled(true); verify.setEnabled(true);
                check.setText(result ? R.string.connection_continue : R.string.connection_check);
            });
        });
    }

    private String fetch(String path) throws java.io.IOException {
        Call call = Global.getClient(this).newCall(new Request.Builder().url(SITE + path).build());
        currentCall = call;
        if (worker.isShutdown()) { call.cancel(); throw new java.io.InterruptedIOException(); }
        try (Response response = call.execute()) { return ApiResponse.read(response); }
        finally { currentCall = null; }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void openVerification() {
        verified = false; check.setText(R.string.connection_check);
        if (web == null) {
            web = new WebView(this);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setSupportMultipleWindows(false);
            settings.setUserAgentString(settings.getUserAgentString() + " NClient/" + BuildConfig.VERSION_NAME + " (https://github.com/Seraphene/NClientV3)");
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return blocked(request.getUrl().toString());
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return blocked(url); }
                private boolean blocked(String url) {
                    if (SiteConnectionPolicy.isSite(url)) return false;
                    status.setText(R.string.connection_site_only); return true;
                }
                @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                    handler.cancel(); status.setText(ConnectionFailure.describe(new javax.net.ssl.SSLException("verification")));
                }
                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) status.setText(R.string.connection_web_error);
                }
                @Override public void onPageFinished(WebView view, String url) {
                    if (SiteConnectionPolicy.isSite(url)) {
                        CookieManager.getInstance().flush();
                        SiteSession.importVerification(SiteConnectionActivity.this, CookieManager.getInstance().getCookie(SITE), view.getSettings().getUserAgentString());
                    }
                }
            });
            FrameLayout container = findViewById(R.id.verification_container);
            container.addView(web, new FrameLayout.LayoutParams(-1, -1));
            container.setVisibility(View.VISIBLE);
        }
        web.loadUrl(SITE);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        Call call = currentCall; if (call != null) call.cancel();
        if (web != null) { web.stopLoading(); ((FrameLayout) web.getParent()).removeView(web); web.destroy(); }
        super.onDestroy();
    }
}
