package com.maxwai.nclientv3.settings;

import android.content.Context;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.components.CookieInterceptor;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.IOException;
import java.net.HttpURLConnection;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class ApiAuthInterceptor implements Interceptor {
    private final boolean logRequests;
    @NonNull
    private final Context context;

    public ApiAuthInterceptor(@NonNull Context context, boolean logRequests) {
        this.context = context.getApplicationContext();
        this.logRequests = logRequests;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (logRequests)
            LogUtility.d("Requested url: " + request.url());
        if (request.header("Authorization") != null || !request.url().encodedPath().startsWith("/api/v2/")) {
            return chain.proceed(request);
        }

        Request.Builder r = request.newBuilder();
        r.addHeader("User-Agent", "NClient/" + Global.getVersionName(context) + " (https://github.com/maxwai/NClientV3)");

        if (!AuthStore.hasValidApiKey(context)) return chain.proceed(r.build());

        String authorization = AuthStore.getAuthorizationHeader(context);
        if (authorization == null) return chain.proceed(r.build());

        Request authenticated =r.header("Authorization", authorization)
            .build();
        Response response = chain.proceed(authenticated);
        if (response.code() == 401 || response.code() == 403) {
            AuthStore.setApiKeyValidation(context, false);
        } else if (response.isSuccessful()) {
            AuthStore.setApiKeyValidation(context, true);
        }
        return response;
    }
}
