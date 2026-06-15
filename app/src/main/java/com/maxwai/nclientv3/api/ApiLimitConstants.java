package com.maxwai.nclientv3.api;

/**
 * Rate limit constant names are built from the API path and HTTP method.
 * Strip the /api/v2/ prefix, replace / with _, capitalize the path and append the HTTP method.
 * For example, /api/v2/part1/part2 with METHOD uses the prefix PART1_PART2_METHOD_.
 * <p/>
 * Each Method needs 3 constants:
 * <ul>
 *  <li>_LIMIT_WINDOW_MS</li>
 *  <li>_UNAUTHENTICATED_REQUEST_LIMIT</li>
 *  <li>_AUTHENTICATED_REQUEST_LIMIT</li>
 * </ul>
 */
public final class ApiLimitConstants {
    public static final int SEARCH_GET_LIMIT_WINDOW_MS = 60_000;
    public static final int SEARCH_GET_UNAUTHENTICATED_REQUEST_LIMIT = 10;
    public static final int SEARCH_GET_AUTHENTICATED_REQUEST_LIMIT = 20;

    public static final int GALLERIES_GALLERY_ID_GET_LIMIT_WINDOW_MS = 60_000;
    public static final int GALLERIES_GALLERY_ID_GET_UNAUTHENTICATED_REQUEST_LIMIT = 20;
    public static final int GALLERIES_GALLERY_ID_GET_AUTHENTICATED_REQUEST_LIMIT = 45;

    private ApiLimitConstants() {
    }
}
