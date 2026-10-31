package com.maxwai.nclientv3.api;

import java.io.IOException;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Checks transport errors before handing data to a JSON parser. */
public final class ApiResponse {
    private ApiResponse() { }

    public static final class Failure extends IOException {
        public final int status;
        public Failure(String message) { this(message, 0); }
        public Failure(String message, int status) { super(message); this.status = status; }
    }

    public static String read(Response response) throws IOException {
        if (!response.isSuccessful()) {
            String hint;
            switch (response.code()) {
                case 401: hint = "Check your API key in Settings."; break;
                case 403: hint = "The site denied access. Open Connection to check site verification."; break;
                case 404: hint = "The gallery or API endpoint was not found."; break;
                case 429: hint = "The site is limiting requests. Wait before retrying."; break;
                default: hint = "Try again later.";
            }
            throw new Failure("API request failed (HTTP " + response.code() + "). " + hint, response.code());
        }
        ResponseBody body = response.body();
        if (body == null) throw new Failure("API returned an empty response");
        String type = response.header("Content-Type", "");
        if (type.toLowerCase(java.util.Locale.ROOT).contains("text/html"))
            throw new Failure("The site returned a web page instead of API data. Open Connection to complete site verification.");
        body.source().request(8 * 1024 * 1024 + 1L);
        if (body.source().getBuffer().size() > 8 * 1024 * 1024)
            throw new Failure("The API response is too large to read safely.");
        String text = body.string().trim();
        if (text.trim().isEmpty()) throw new Failure("API returned an empty response");
        if (text.startsWith("<"))
            throw new Failure("The site returned a web page instead of API data. Open Connection to complete site verification.");
        return text;
    }
}
