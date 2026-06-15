package com.maxwai.nclientv3.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.settings.AuthStore;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiRateLimiter {
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    @NonNull
    private final Object lock = new Object();
    @NonNull
    private final ArrayDeque<Long> recentRequests = new ArrayDeque<>();
    @NonNull
    private final String method;
    @NonNull
    private final String path;
    private final int unauthenticatedRequestLimit;
    private final int authenticatedRequestLimit;
    private final long windowMs;
    @Nullable
    private Date retryAfterUntil;

    ApiRateLimiter(@NonNull String method, @NonNull String path, int unauthenticatedRequestLimit,
                   int authenticatedRequestLimit, long windowMs) {
        this.method = method;
        this.path = path;
        this.unauthenticatedRequestLimit = unauthenticatedRequestLimit;
        this.authenticatedRequestLimit = authenticatedRequestLimit;
        this.windowMs = windowMs;
    }

    @NonNull
    public Response execute(@NonNull Context context, @NonNull OkHttpClient client,
                            @NonNull Map<String, String> parameters)
        throws IOException, RateLimitException {
        return execute(context, client, new LinkedHashMap<>(), parameters, null);
    }

    @NonNull
    public Response execute(@NonNull Context context, @NonNull OkHttpClient client,
                            @NonNull Map<String, String> parameters, @Nullable RequestBody body)
        throws IOException, RateLimitException {
        return execute(context, client, new LinkedHashMap<>(), parameters, body);
    }

    @NonNull
    public Response execute(@NonNull Context context, @NonNull OkHttpClient client,
                            @NonNull Map<String, String> pathParameters,
                            @NonNull Map<String, String> parameters, @Nullable RequestBody body)
        throws IOException, RateLimitException {
        Request request = buildRequest(pathParameters, parameters, body);
        long timestamp = reserveRequest(context);
        try {
            Response response = client.newCall(request).execute();
            if (response.code() == HTTP_TOO_MANY_REQUESTS) {
                removeRequest(timestamp);
                long retryAfterMs = getLocalRetryAfterMs();
                logRateLimitState(context, "response 429", retryAfterMs);
                setRetryAfterUntil(retryAfterMs);
                response.close();
                throw new RateLimitException(retryAfterMs);
            }
            return response;
        } catch (IOException e) {
            removeRequest(timestamp);
            throw e;
        }
    }

    @NonNull
    public Call executeAsync(@NonNull Context context, @NonNull OkHttpClient client,
                             @NonNull Map<String, String> parameters, @Nullable RequestBody body,
                             @NonNull Consumer<Response> onSuccess,
                             @NonNull Consumer<RateLimitException> onRateLimited,
                             @NonNull Consumer<IOException> onFailure, @NonNull Runnable onCancelled) {
        return executeAsync(context, client, new LinkedHashMap<>(), parameters, body, onSuccess, onRateLimited, onFailure, onCancelled);
    }

    @NonNull
    public Call executeAsync(@NonNull Context context, @NonNull OkHttpClient client,
                             @NonNull Map<String, String> pathParameters,
                             @NonNull Map<String, String> parameters, @Nullable RequestBody body,
                             @NonNull Consumer<Response> onSuccess,
                             @NonNull Consumer<RateLimitException> onRateLimited,
                             @NonNull Consumer<IOException> onFailure, @NonNull Runnable onCancelled) {
        Request request = buildRequest(pathParameters, parameters, body);
        long timestamp;
        try {
            timestamp = reserveRequest(context);
        } catch (RateLimitException e) {
            Call call = client.newCall(request);
            call.cancel();
            onRateLimited.accept(e);
            return call;
        }

        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                removeRequest(timestamp);
                if (call.isCanceled()) onCancelled.run();
                else onFailure.accept(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.code() == HTTP_TOO_MANY_REQUESTS) {
                    removeRequest(timestamp);
                    long retryAfterMs = getLocalRetryAfterMs();
                    logRateLimitState(context, "async response 429", retryAfterMs);
                    setRetryAfterUntil(retryAfterMs);
                    onRateLimited.accept(new RateLimitException(retryAfterMs));
                    response.close();
                    return;
                }
                onSuccess.accept(response);
            }
        });
        return call;
    }

    @NonNull
    public Request buildRequest(@NonNull Map<String, String> parameters, @Nullable RequestBody body) {
        return buildRequest(new LinkedHashMap<>(), parameters, body);
    }

    @NonNull
    public Request buildRequest(@NonNull Map<String, String> pathParameters,
                                @NonNull Map<String, String> parameters, @Nullable RequestBody body) {
        return new Request.Builder().url(buildUrl(pathParameters, parameters)).method(method, body).build();
    }

    @NonNull
    public String buildUrl(@NonNull Map<String, String> parameters) {
        return buildUrl(new LinkedHashMap<>(), parameters);
    }

    @NonNull
    public String buildUrl(@NonNull Map<String, String> pathParameters, @NonNull Map<String, String> parameters) {
        HttpUrl.Builder builder = HttpUrl.get(Utility.getBaseUrl()).newBuilder();
        String normalizedPath = fillPathParameters(path, pathParameters);
        if (normalizedPath.startsWith("/")) normalizedPath = normalizedPath.substring(1);
        builder.addPathSegments(normalizedPath);
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            builder.addQueryParameter(parameter.getKey(), parameter.getValue());
        }
        return builder.build().toString();
    }

    @NonNull
    private String fillPathParameters(@NonNull String path, @NonNull Map<String, String> pathParameters) {
        String resolved = path;
        for (Map.Entry<String, String> parameter : pathParameters.entrySet()) {
            resolved = resolved.replace("{" + parameter.getKey() + "}", parameter.getValue());
        }
        return resolved;
    }

    private long reserveRequest(@NonNull Context context) throws RateLimitException {
        synchronized (lock) {
            Date now = new Date();
            if (retryAfterUntil != null) {
                if (retryAfterUntil.after(now)) {
                    long retryAfterMs = retryAfterUntil.getTime() - now.getTime();
                    logRateLimitStateLocked(context, "retryAfterUntil block", retryAfterMs);
                    throw new RateLimitException(retryAfterMs);
                }
                retryAfterUntil = null;
            }

            cleanWindowLocked();
            int requestLimit = AuthStore.hasValidApiKey(context) ? authenticatedRequestLimit : unauthenticatedRequestLimit;
            if (recentRequests.size() >= requestLimit) {
                long retryAfterMs = getRetryAfterMsLocked();
                logRateLimitStateLocked(context, "local window block", retryAfterMs);
                throw new RateLimitException(retryAfterMs);
            }
            long timestamp = System.currentTimeMillis();
            recentRequests.addLast(timestamp);
            return timestamp;
        }
    }

    private void removeRequest(long timestamp) {
        synchronized (lock) {
            recentRequests.remove(timestamp);
        }
    }

    private void cleanWindowLocked() {
        long threshold = System.currentTimeMillis() - windowMs;
        //noinspection DataFlowIssue
        while (!recentRequests.isEmpty() && recentRequests.peekFirst() <= threshold) {
            recentRequests.removeFirst();
        }
    }

    private long getRetryAfterMsLocked() {
        cleanWindowLocked();
        Long firstRequest = recentRequests.peekFirst();
        if (firstRequest == null) return windowMs;
        return Math.max(0, firstRequest + windowMs - System.currentTimeMillis());
    }

    private void setRetryAfterUntil(long retryAfterMs) {
        synchronized (lock) {
            retryAfterUntil = new Date(System.currentTimeMillis() + retryAfterMs);
        }
    }

    private long getLocalRetryAfterMs() {
        synchronized (lock) {
            return getRetryAfterMsLocked();
        }
    }

    private void logRateLimitState(@NonNull Context context, @NonNull String event, long retryAfterMs) {
        synchronized (lock) {
            logRateLimitStateLocked(context, event, retryAfterMs);
        }
    }

    private void logRateLimitStateLocked(@NonNull Context context, @NonNull String event, long retryAfterMs) {
        cleanWindowLocked();
        int requestLimit = AuthStore.hasValidApiKey(context) ? authenticatedRequestLimit : unauthenticatedRequestLimit;
        String retryUntil = retryAfterUntil == null ? "null" : Long.toString(retryAfterUntil.getTime() - System.currentTimeMillis());
        LogUtility.d("ApiRateLimiter " + path
            + " event=" + event
            + " size=" + recentRequests.size()
            + " limit=" + requestLimit
            + " retryAfterUntilMs=" + retryUntil
            + " retryAfterMs=" + retryAfterMs);
    }

    public static class RateLimitException extends Exception {
        private final long retryAfterMs;

        RateLimitException(long retryAfterMs) {
            super("API rate limited");
            this.retryAfterMs = retryAfterMs;
        }

        public long getRetryAfterMs() {
            return retryAfterMs;
        }
    }
}
