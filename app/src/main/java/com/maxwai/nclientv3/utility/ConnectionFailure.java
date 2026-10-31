package com.maxwai.nclientv3.utility;

import com.maxwai.nclientv3.api.ApiResponse;
import com.maxwai.nclientv3.api.InspectorV3;

/** Safe explanations without raw URLs, cookies, or response bodies. */
public final class ConnectionFailure {
    private ConnectionFailure() { }
    public static String describe(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiResponse.Failure) return cause.getMessage();
            if (cause instanceof java.net.UnknownHostException)
                return "Could not find the site address (DNS). Check your connection or Private DNS settings.";
            if (cause instanceof javax.net.ssl.SSLException)
                return "A secure connection could not be established. Check your phone’s date and time and network. Certificate verification stays enabled.";
            if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.io.InterruptedIOException)
                return "The site did not respond in time. Try again when the connection is stable.";
            if (cause instanceof java.net.ConnectException)
                return "The connection to the site failed. The site or your network may be unavailable.";
            if (cause instanceof InspectorV3.InvalidResponseException || cause instanceof org.json.JSONException)
                return "The site responded, but its API data could not be read. Open Connection to check the response.";
        }
        return "The request could not be completed. Open Connection for a fresh check.";
    }
}
