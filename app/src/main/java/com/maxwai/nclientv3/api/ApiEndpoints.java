package com.maxwai.nclientv3.api;

public final class ApiEndpoints {
    public static final SearchApiEndpoint SEARCH = new SearchApiEndpoint(
        new ApiRateLimiter(
            "GET",
            "/api/v2/search",
            ApiLimitConstants.SEARCH_GET_UNAUTHENTICATED_REQUEST_LIMIT,
            ApiLimitConstants.SEARCH_GET_AUTHENTICATED_REQUEST_LIMIT,
            ApiLimitConstants.SEARCH_GET_LIMIT_WINDOW_MS
        )
    );

    private ApiEndpoints() {
    }
}
