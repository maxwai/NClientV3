package com.maxwai.nclientv3.api;

/**
 * Rate limit constant names are built from the API path and HTTP method.
 * Strip the /api/v2/ prefix, replace / with _, capitalize the path and append the HTTP method.
 * For example, /api/v2/part1/part2 with METHOD uses the prefix PART1_PART2_METHOD_.
 */
public final class ApiLimitConstants {
    public static final long SEARCH_GET_LIMIT_WINDOW_MS = 60_000;
    public static final int SEARCH_GET_UNAUTHENTICATED_REQUEST_LIMIT = 10;
    public static final int SEARCH_GET_AUTHENTICATED_REQUEST_LIMIT = 20;

    private ApiLimitConstants() {
    }
}
