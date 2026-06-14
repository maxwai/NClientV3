package com.maxwai.nclientv3.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.components.Ranges;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.SortType;
import com.maxwai.nclientv3.api.enums.TagStatus;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class SearchApiEndpoint {
    private static final String PARAMETER_QUERY = "query";
    private static final String PARAMETER_PAGE = "page";
    private static final String PARAMETER_SORT = "sort";

    @NonNull
    private final ApiRateLimiter rateLimiter;

    SearchApiEndpoint(@NonNull ApiRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @NonNull
    public Response execute(@NonNull Context context, @NonNull OkHttpClient client, @Nullable String query,
                            @Nullable Collection<Tag> tags, @Nullable Ranges ranges, int page,
                            @Nullable SortType sortType) throws IOException, ApiRateLimiter.RateLimitException {
        return rateLimiter.execute(context, client, buildParameters(query, tags, ranges, page, sortType));
    }

    @NonNull
    public String buildUrl(@Nullable String query, @Nullable Collection<Tag> tags, @Nullable Ranges ranges,
                           int page, @Nullable SortType sortType) {
        return rateLimiter.buildUrl(buildParameters(query, tags, ranges, page, sortType));
    }

    @NonNull
    public Map<String, String> buildParameters(@Nullable String query, @Nullable Collection<Tag> tags,
                                               @Nullable Ranges ranges, int page, @Nullable SortType sortType) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(PARAMETER_QUERY, buildSearchQuery(query, tags, ranges));
        parameters.put(PARAMETER_PAGE, Integer.toString(page));
        if (sortType != null && sortType.getUrlAddition() != null) {
            parameters.put(PARAMETER_SORT, sortType.getUrlAddition());
        }
        return parameters;
    }

    @NonNull
    private String buildSearchQuery(@Nullable String query, @Nullable Collection<Tag> tags, @Nullable Ranges ranges) {
        StringBuilder builder = new StringBuilder(query == null ? "" : query);
        if (tags != null) {
            for (Tag tag : tags) {
                if (builder.toString().contains(tag.toQueryTag(TagStatus.ACCEPTED))) continue;
                appendSearchToken(builder, tag.toQueryTag());
            }
        }
        if (ranges != null) {
            appendSearchToken(builder, ranges.toQuery());
        }
        return builder.toString();
    }

    private void appendSearchToken(@NonNull StringBuilder builder, @Nullable String token) {
        if (token == null || token.isEmpty()) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(token);
    }
}
