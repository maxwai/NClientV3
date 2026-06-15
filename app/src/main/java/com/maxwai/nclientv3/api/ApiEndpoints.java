package com.maxwai.nclientv3.api;

public final class ApiEndpoints {
    public static final GalleryApiEndpoint GALLERY = new GalleryApiEndpoint(
        new ApiRateLimiter(
            "GET",
            "/api/v2/galleries/{gallery_id}",
            ApiLimitConstants.GALLERIES_GALLERY_ID_GET_UNAUTHENTICATED_REQUEST_LIMIT,
            ApiLimitConstants.GALLERIES_GALLERY_ID_GET_AUTHENTICATED_REQUEST_LIMIT,
            ApiLimitConstants.GALLERIES_GALLERY_ID_GET_LIMIT_WINDOW_MS
        )
    );

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
