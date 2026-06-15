package com.maxwai.nclientv3.api;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class GalleryApiEndpoint {
    private static final String PATH_PARAMETER_GALLERY_ID = "gallery_id";
    private static final String PARAMETER_INCLUDE = "include";
    private static final String INCLUDE_RELATED_FAVORITE = "related,favorite";

    @NonNull
    private final ApiRateLimiter rateLimiter;

    GalleryApiEndpoint(@NonNull ApiRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @NonNull
    public Response execute(@NonNull Context context, @NonNull OkHttpClient client, int galleryId,
                            boolean includeRelatedFavorite) throws IOException, ApiRateLimiter.RateLimitException {
        return rateLimiter.execute(context, client, buildPathParameters(galleryId), buildParameters(includeRelatedFavorite), null);
    }

    @NonNull
    public String buildUrl(int galleryId, boolean includeRelatedFavorite) {
        return rateLimiter.buildUrl(buildPathParameters(galleryId), buildParameters(includeRelatedFavorite));
    }

    @NonNull
    private Map<String, String> buildPathParameters(int galleryId) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(PATH_PARAMETER_GALLERY_ID, Integer.toString(galleryId));
        return parameters;
    }

    @NonNull
    private Map<String, String> buildParameters(boolean includeRelatedFavorite) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (includeRelatedFavorite) parameters.put(PARAMETER_INCLUDE, INCLUDE_RELATED_FAVORITE);
        return parameters;
    }
}
