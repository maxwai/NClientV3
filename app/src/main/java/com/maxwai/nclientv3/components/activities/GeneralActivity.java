package com.maxwai.nclientv3.components.activities;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.views.CFTokenView;
import com.maxwai.nclientv3.settings.Global;

import java.lang.ref.WeakReference;

public abstract class GeneralActivity extends AppCompatActivity {
    private static WeakReference<GeneralActivity> lastActivity;
    private String appliedAccent;
    private String appliedAppearance;
    private boolean isFastScrollerApplied = false;
    private CFTokenView tokenView = null;
    private Runnable connectionRetry;
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> connectionLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            Runnable retry = connectionRetry;
            connectionRetry = null;
            if (result.getResultCode() == RESULT_OK && retry != null) retry.run();
        });

    public void openSiteConnection(Runnable retry) {
        connectionRetry = retry;
        connectionLauncher.launch(new android.content.Intent(this, com.maxwai.nclientv3.SiteConnectionActivity.class));
    }

    public static @Nullable
    CFTokenView getLastCFView() {
        if (lastActivity == null) return null;
        GeneralActivity activity = lastActivity.get();
        if (activity != null) {
            activity.runOnUiThread(activity::inflateWebView);
            return activity.tokenView;
        }
        return null;
    }

    private void inflateWebView() {
        if (tokenView == null) {
            Toast.makeText(this, R.string.fetching_cloudflare_token, Toast.LENGTH_SHORT).show();
            ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content).getRootView();
            ViewGroup v = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.cftoken_layout, rootView, false);
            tokenView = new CFTokenView(v);
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            tokenView.setVisibility(View.GONE);
            this.addContentView(v, params);
        }
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        com.maxwai.nclientv3.utility.PremiumUi.configure(this);
    }

    @Override
    public void onConfigurationChanged(@androidx.annotation.NonNull android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        com.maxwai.nclientv3.utility.PremiumUi.configure(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, @androidx.annotation.NonNull String[] permissions,
                                          @androidx.annotation.NonNull int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == com.maxwai.nclientv3.utility.PremiumUi.DOWNLOAD_PERMISSION && results.length > 0
            && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Global.initStorage(this);
            startActivity(new android.content.Intent(this, com.maxwai.nclientv3.LocalActivity.class));
            if (!(this instanceof com.maxwai.nclientv3.MainActivity)) finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Global.hideMultitask())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        Global.initActivity(this);
        appliedAccent = com.maxwai.nclientv3.utility.AccentTheme.selected(this);
        appliedAppearance = com.maxwai.nclientv3.utility.AppearanceTheme.selected(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedAccent != null && (!appliedAccent.equals(com.maxwai.nclientv3.utility.AccentTheme.selected(this))
            || !appliedAppearance.equals(com.maxwai.nclientv3.utility.AppearanceTheme.selected(this)))) {
            recreate();
            return;
        }
        lastActivity = new WeakReference<>(this);
        com.maxwai.nclientv3.utility.PremiumUi.configure(this);
        if (!isFastScrollerApplied) {
            isFastScrollerApplied = true;
            Global.applyFastScroller(findViewById(R.id.recycler));
        }
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        SharedPreferences preferences = getSharedPreferences("Settings", 0);
        if (preferences.getBoolean(getString(R.string.preference_key_black_theme), false)) {
            theme.applyStyle(R.style.AppTheme_Black, true);
        }
        com.maxwai.nclientv3.utility.AccentTheme.apply(theme, com.maxwai.nclientv3.utility.AccentTheme.selected(this));
        com.maxwai.nclientv3.utility.AppearanceTheme.apply(theme, com.maxwai.nclientv3.utility.AppearanceTheme.selected(this));
        return theme;
    }
}
