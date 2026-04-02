package com.maxwai.nclientv3;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.settings.AuthStore;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class LoginActivity extends GeneralActivity {
    private static final String API_KEY_SETTINGS_URL = "https://nhentai.net/user/settings#apikeys";
    private static final String VALIDATION_URL = "api/v2/favorites?page=1";

    private TextView statusText;
    private LinearLayout inputGroup;
    private EditText apiKeyInput;
    private ProgressBar progressBar;
    private MaterialButton openApiKeyPage;
    private MaterialButton clearApiKey;
    private MaterialButton pasteApiKey;
    private MaterialButton validateApiKey;
    private boolean validationInFlight = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.setting_api_key_title);
        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        statusText = findViewById(R.id.login_status_text);
        inputGroup = findViewById(R.id.login_input_group);
        apiKeyInput = findViewById(R.id.api_key_input);
        progressBar = findViewById(R.id.login_progress);
        openApiKeyPage = findViewById(R.id.open_api_key_page);
        clearApiKey = findViewById(R.id.clear_api_key);
        pasteApiKey = findViewById(R.id.paste_api_key);
        validateApiKey = findViewById(R.id.validate_api_key);

        setInputVisible(true);
        openApiKeyPage.setOnClickListener(v -> openApiKeyPage());
        clearApiKey.setOnClickListener(v -> clearSavedApiKey());
        pasteApiKey.setOnClickListener(v -> pasteApiKey());
        validateApiKey.setOnClickListener(v -> validateAndSaveApiKey());
        loadSavedApiKey();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedApiKey();
    }

    private void openApiKeyPage() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(API_KEY_SETTINGS_URL)));
    }

    private void updateStatusMessage(@NonNull String message) {
        statusText.setText(message);
    }

    private void setInputVisible(boolean visible) {
        inputGroup.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        if (text == null) return;
        apiKeyInput.setText(text.toString().trim());
        Toast.makeText(this, R.string.login_api_key_pasted_from_clipboard, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        validationInFlight = loading;
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        apiKeyInput.setEnabled(!loading);
        openApiKeyPage.setEnabled(!loading);
        clearApiKey.setEnabled(!loading && AuthStore.hasApiKey(this));
        pasteApiKey.setEnabled(!loading);
        validateApiKey.setEnabled(!loading);
        updateStatusMessage(getStatusMessage());
    }

    private void loadSavedApiKey() {
        String apiKey = AuthStore.getApiKey(this);
        if (apiKey != null && !validationInFlight) {
            apiKeyInput.setText(apiKey);
        }
        clearApiKey.setEnabled(!validationInFlight && AuthStore.hasApiKey(this));
        updateStatusMessage(getStatusMessage());
    }

    @NonNull
    private String getStatusMessage() {
        if (AuthStore.hasValidApiKey(this)) return getString(R.string.login_status_api_key_saved);
        if (AuthStore.hasApiKey(this)) return getString(R.string.login_status_api_key_invalid);
        return getString(R.string.login_api_key_intro_message);
    }

    private void clearSavedApiKey() {
        AuthStore.clear(this);
        Login.updateUser(null);
        apiKeyInput.setText("");
        clearApiKey.setEnabled(false);
        updateStatusMessage(getStatusMessage());
        Toast.makeText(this, R.string.login_api_key_removed, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(LoginActivity.this, R.string.unable_to_connect_to_the_site, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (response) {
                    if (response.isSuccessful()) {
                        AuthStore.saveApiKey(LoginActivity.this, apiKey, true);
                        Login.updateUser(null);
                        runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(LoginActivity.this, R.string.login_api_key_saved, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                        return;
                    }

                    LogUtility.w("API key validation rejected: " + response.code());
                    runOnUiThread(() -> {
                        setLoading(false);
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
}
