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

    public ApiAuthInterceptor(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (request.header("Authorization") != null || !request.url().encodedPath().startsWith("/api/v2/")) {
            return chain.proceed(request);
        }

        if (!AuthStore.hasValidApiKey(context)) return chain.proceed(request);

        String authorization = AuthStore.getAuthorizationHeader(context);
        if (authorization == null) return chain.proceed(request);

        Request authenticated = request.newBuilder()
            .header("Authorization", authorization)
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
