package com.maxwai.nclientv3.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AuthStore {
    private static final String AUTH_PREFERENCES = "Auth";
    private static final String KEY_TYPE = "type";
    private static final String KEY_SECRET = "secret";

    private AuthStore() {
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getSharedPreferences(AUTH_PREFERENCES, Context.MODE_PRIVATE);
    }

    public static void saveApiKey(@NonNull Context context, @NonNull String apiKey) {
        String normalized = apiKey.trim();
        getPreferences(context).edit()
            .putString(KEY_TYPE, AuthCredentials.Type.API_KEY.name())
            .putString(KEY_SECRET, normalized)
            .apply();
    }

    public static void clear(@NonNull Context context) {
        getPreferences(context).edit().clear().apply();
    }

    @Nullable
    public static AuthCredentials getCredentials(@NonNull Context context) {
        SharedPreferences preferences = getPreferences(context);
        String typeName = preferences.getString(KEY_TYPE, null);
        String secret = preferences.getString(KEY_SECRET, null);
        if (typeName == null || secret == null || secret.trim().isEmpty()) return null;
        try {
            return new AuthCredentials(AuthCredentials.Type.valueOf(typeName), secret.trim());
        } catch (IllegalArgumentException ignore) {
            clear(context);
            return null;
        }
    }

    public static boolean hasCredentials(@NonNull Context context) {
        return getCredentials(context) != null;
    }

    @Nullable
    public static String getAuthorizationHeader(@NonNull Context context) {
        AuthCredentials credentials = getCredentials(context);
        return credentials == null ? null : credentials.toAuthorizationHeader();
    }
}
