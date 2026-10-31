package com.maxwai.nclientv3.settings;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class ApiAuthInterceptor implements Interceptor {
    @NonNull
    private final Context context;

    public ApiAuthInterceptor(@NonNull Context context, boolean logRequests) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!com.maxwai.nclientv3.utility.SiteConnectionPolicy.isSite(request.url().toString())
            || !request.url().encodedPath().startsWith("/api/v2/")) {
            return chain.proceed(request);
        }

        Request.Builder r = request.newBuilder();
        r.header("User-Agent", SiteSession.userAgent(context));
        r.header("Accept", "application/json");

        if (request.header("Authorization") != null) return chain.proceed(r.build());
        if (!AuthStore.hasValidApiKey(context)) return chain.proceed(r.build());

        String authorization = AuthStore.getAuthorizationHeader(context);
        if (authorization == null) return chain.proceed(r.build());

        Request authenticated =r.header("Authorization", authorization)
            .build();
        Response response = chain.proceed(authenticated);
        if (response.code() == 401) {
            AuthStore.setApiKeyValidation(context, false);
        } else if (response.isSuccessful()) {
            AuthStore.setApiKeyValidation(context, true);
        }
        return response;
    }
}
