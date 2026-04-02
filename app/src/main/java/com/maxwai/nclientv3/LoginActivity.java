package com.maxwai.nclientv3;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.components.views.CFTokenView;
import com.maxwai.nclientv3.settings.AuthStore;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class LoginActivity extends GeneralActivity {
    private static final String LOGIN_URL = "https://nhentai.net/login/";
    private static final String API_KEY_SETTINGS_URL = "https://nhentai.net/user/settings#apikeys";
    private static final String VALIDATION_URL = "api/v2/favorites?page=1";

    private static final String SETTINGS_PATH = "/user/settings";
    private static final String LOGIN_PATH = "/login";
    private static final String EXTRACT_API_KEY_SCRIPT =
        "(function() {" +
            "const node = document.querySelector('code.key-value.svelte-x30ryz, code.key-value');" +
            "return node ? node.textContent.trim() : '';" +
        "})();";
    private static final String DETECT_LOGGED_IN_SCRIPT =
        "(function() {" +
            "const loggedIn = !!document.querySelector('a[href*=\"/logout\"], a[href*=\"/user/settings\"], a[href*=\"/favorites\"]');" +
            "const href = window.location.href || '';" +
            "return JSON.stringify({loggedIn: loggedIn, href: href});" +
        "})();";
    private static final Pattern API_KEY_PATTERN = Pattern.compile("nhk_[A-Za-z0-9_-]+");
    private static final int API_KEY_POLL_DELAY_MS = 1000;
    private static final int LOGIN_STATE_POLL_DELAY_MS = 1200;

    private CFTokenView.CFTokenWebView webView;
    private TextView statusText;
    private LinearLayout inputGroup;
    private EditText apiKeyInput;
    private ProgressBar progressBar;
    private MaterialButton openApiKeyPage;
    private MaterialButton openInBrowser;
    private MaterialButton pasteApiKey;
    private MaterialButton validateApiKey;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean redirectedToApiKeyPage = false;
    private boolean validationInFlight = false;
    private boolean apiKeyAutoDetected = false;
    private final Runnable pollForApiKeyRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || !redirectedToApiKeyPage || apiKeyAutoDetected || validationInFlight) {
                return;
            }
            webView.evaluateJavascript(EXTRACT_API_KEY_SCRIPT, LoginActivity.this::handleExtractedApiKey);
            handler.postDelayed(this, API_KEY_POLL_DELAY_MS);
        }
    };
    private final Runnable pollLoginStateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || redirectedToApiKeyPage) return;
            String currentUrl = webView.getUrl();
            if (shouldRedirectToApiKeyPage(currentUrl)) {
                redirectToApiKeyPage();
                return;
            }
            webView.evaluateJavascript(DETECT_LOGGED_IN_SCRIPT, LoginActivity.this::handleLoggedInDetection);
            handler.postDelayed(this, LOGIN_STATE_POLL_DELAY_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.title_activity_login);
        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        webView = findViewById(R.id.webView);
        statusText = findViewById(R.id.login_status_text);
        inputGroup = findViewById(R.id.login_input_group);
        apiKeyInput = findViewById(R.id.api_key_input);
        progressBar = findViewById(R.id.login_progress);
        openApiKeyPage = findViewById(R.id.open_api_key_page);
        openInBrowser = findViewById(R.id.open_in_browser);
        pasteApiKey = findViewById(R.id.paste_api_key);
        validateApiKey = findViewById(R.id.validate_api_key);

        webView.setWebViewClient(new LoginWebViewClient());
        openApiKeyPage.setOnClickListener(v -> openApiKeyPage());
        openInBrowser.setOnClickListener(v -> openCurrentPageInBrowser());
        pasteApiKey.setOnClickListener(v -> pasteApiKey());
        validateApiKey.setOnClickListener(v -> validateAndSaveApiKey());
        webView.loadUrl(LOGIN_URL);
        scheduleLoginStatePolling();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void openApiKeyPage() {
        redirectedToApiKeyPage = true;
        handler.removeCallbacks(pollLoginStateRunnable);
        setInputVisible(true);
        updateStatusMessage(getString(R.string.login_status_create_api_key));
        webView.loadUrl(API_KEY_SETTINGS_URL);
    }

    private void openCurrentPageInBrowser() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(API_KEY_SETTINGS_URL)));
    }

    private void updateStatusMessage(@NonNull String message) {
        statusText.setText(message);
    }

    private void setInputVisible(boolean visible) {
        inputGroup.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fillFromClipboardIfPossible(false);
    }

    private void pasteApiKey() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, R.string.login_clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            Toast.makeText(this, R.string.login_clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = clipData.getItemAt(0).coerceToText(this);
        if (text != null) apiKeyInput.setText(text.toString().trim());
    }

    private void fillFromClipboardIfPossible(boolean notifyUser) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) return;
        CharSequence text = clipData.getItemAt(0).coerceToText(this);
        if (text == null) return;
        String candidate = text.toString().trim();
        if (candidate.isEmpty() || candidate.equals(apiKeyInput.getText().toString().trim())) return;
        apiKeyInput.setText(candidate);
        if (notifyUser)
            Toast.makeText(this, R.string.login_api_key_pasted_from_clipboard, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        validationInFlight = loading;
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        apiKeyInput.setEnabled(!loading);
        openApiKeyPage.setEnabled(!loading);
        openInBrowser.setEnabled(!loading);
        pasteApiKey.setEnabled(!loading);
        validateApiKey.setEnabled(!loading);
        webView.setEnabled(!loading);
    }

    private void scheduleApiKeyPolling() {
        handler.removeCallbacks(pollForApiKeyRunnable);
        handler.postDelayed(pollForApiKeyRunnable, 300);
    }

    private void scheduleLoginStatePolling() {
        handler.removeCallbacks(pollLoginStateRunnable);
        handler.postDelayed(pollLoginStateRunnable, 600);
    }

    private boolean isSettingsUrl(@Nullable String url) {
        return url != null && (url.startsWith(API_KEY_SETTINGS_URL) || url.contains(SETTINGS_PATH));
    }

    private boolean isLoginUrl(@Nullable String url) {
        return url != null && url.contains(LOGIN_PATH);
    }

    private boolean isNhentaiUrl(@Nullable String url) {
        return url != null && url.startsWith("https://nhentai.net/");
    }

    private boolean shouldRedirectToApiKeyPage(@Nullable String url) {
        if (redirectedToApiKeyPage) return false;
        if (isSettingsUrl(url)) return true;
        if (hasSessionCookie()) return true;
        return isNhentaiUrl(url) && !isLoginUrl(url);
    }

    private void redirectToApiKeyPage() {
        if (redirectedToApiKeyPage) return;
        redirectedToApiKeyPage = true;
        handler.removeCallbacks(pollLoginStateRunnable);
        setInputVisible(true);
        updateStatusMessage(getString(R.string.login_status_redirecting_api_key));
        webView.loadUrl(API_KEY_SETTINGS_URL);
    }

    private void handleExtractedApiKey(String rawValue) {
        if (rawValue == null || rawValue.isEmpty() || "null".equals(rawValue)) return;
        String decoded = rawValue;
        if (decoded.length() >= 2 && decoded.charAt(0) == '"' && decoded.charAt(decoded.length() - 1) == '"') {
            decoded = decoded.substring(1, decoded.length() - 1);
        }
        decoded = decoded.replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim();

        Matcher matcher = API_KEY_PATTERN.matcher(decoded);
        if (!matcher.find()) return;

        String apiKey = matcher.group();
        if (apiKey.isEmpty() || apiKey.equals(apiKeyInput.getText().toString().trim())) return;
        apiKeyAutoDetected = true;
        copyApiKeyToClipboard(apiKey);
        apiKeyInput.setText(apiKey);
        updateStatusMessage(getString(R.string.login_status_validating_api_key));
        Toast.makeText(this, R.string.login_api_key_detected, Toast.LENGTH_SHORT).show();
        validateAndSaveApiKey();
    }

    private void handleLoggedInDetection(String rawValue) {
        if (redirectedToApiKeyPage || rawValue == null || rawValue.isEmpty() || "null".equals(rawValue)) return;
        String decoded = rawValue;
        if (decoded.length() >= 2 && decoded.charAt(0) == '"' && decoded.charAt(decoded.length() - 1) == '"') {
            decoded = decoded.substring(1, decoded.length() - 1);
        }
        decoded = decoded.replace("\\\"", "\"").replace("\\\\", "\\");
        if (!decoded.contains("\"loggedIn\":true")) return;
        redirectToApiKeyPage();
    }

    private void copyApiKeyToClipboard(@NonNull String apiKey) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("nhentai_api_key", apiKey));
    }

    private void validateAndSaveApiKey() {
        String apiKey = apiKeyInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, R.string.login_api_key_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        Request request = new Request.Builder()
            .url(Utility.getBaseUrl() + VALIDATION_URL)
            .header("Authorization", "Key " + apiKey)
            .build();
        Global.getClient(this).newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                LogUtility.e("API key validation failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    if (redirectedToApiKeyPage) scheduleApiKeyPolling();
                    Toast.makeText(LoginActivity.this, R.string.unable_to_connect_to_the_site, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (response) {
                    if (response.isSuccessful()) {
                        AuthStore.saveApiKey(LoginActivity.this, apiKey);
                        Login.updateUser(null);
                        runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(LoginActivity.this, R.string.login_api_key_saved, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                        return;
                    }

                    String message = response.body() == null ? null : response.body().string();
                    LogUtility.w("API key validation rejected: " + response.code() + ' ' + message);
                    runOnUiThread(() -> {
                        setLoading(false);
                        apiKeyAutoDetected = false;
                        if (redirectedToApiKeyPage) scheduleApiKeyPolling();
                        updateStatusMessage(getString(R.string.login_status_create_api_key));
                        Toast.makeText(LoginActivity.this, R.string.login_api_key_invalid, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }

    private boolean hasSessionCookie() {
        String cookies = CookieManager.getInstance().getCookie("https://nhentai.net/");
        return cookies != null && cookies.contains("sessionid=");
    }

    private final class LoginWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if (scheme == null || "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return false;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (url == null) return;

            if (isSettingsUrl(url)) {
                redirectedToApiKeyPage = true;
                handler.removeCallbacks(pollLoginStateRunnable);
                setInputVisible(true);
                updateStatusMessage(getString(R.string.login_status_create_api_key));
                fillFromClipboardIfPossible(true);
                scheduleApiKeyPolling();
                return;
            }

            if (shouldRedirectToApiKeyPage(url)) {
                redirectToApiKeyPage();
                return;
            }

            handler.removeCallbacks(pollForApiKeyRunnable);
            setInputVisible(false);
            updateStatusMessage(getString(R.string.login_status_login_first));
            scheduleLoginStatePolling();
        }
    }
}
