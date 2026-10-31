package com.maxwai.nclientv3.api;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import okhttp3.*;

public class ApiResponseTest {
    private Response response(int status, String type, String text) {
        return new Response.Builder().request(new Request.Builder().url("https://example.org/api/v2/galleries").build())
            .protocol(Protocol.HTTP_1_1).code(status).message("test")
            .header("Content-Type", type).body(ResponseBody.create(text, MediaType.get(type))).build();
    }
    @Test public void jsonPassesThrough() throws Exception {
        try (Response r = response(200, "application/json", "{\"result\":[]}")) {
            assertEquals("{\"result\":[]}", ApiResponse.read(r));
        }
    }
    @Test public void httpFailuresKeepStatus() {
        for (int status : new int[]{401,403,404,429,500}) {
            try (Response r = response(status, "application/json", "{}")) {
                ApiResponse.read(r); fail("Must reject HTTP " + status);
            } catch (IOException e) { assertTrue(e.getMessage().contains("HTTP " + status)); }
        }
    }
    @Test(expected=IOException.class) public void htmlChallengeIsRejected() throws Exception {
        try (Response r = response(200, "text/html", "<html>challenge</html>")) { ApiResponse.read(r); }
    }
    @Test(expected=IOException.class) public void emptyResponseIsRejected() throws Exception {
        try (Response r = response(200, "application/json", " ")) { ApiResponse.read(r); }
    }
    @Test(expected=IOException.class) public void htmlWithoutContentTypeDoesNotReachJsonParser() throws Exception {
        try (Response r = response(200, "text/plain", "  <!DOCTYPE html><html>verification</html>")) { ApiResponse.read(r); }
    }
    @Test(expected=IOException.class) public void oversizedResponseIsRejected() throws Exception {
        try (Response r = response(200, "application/json", "x".repeat(8 * 1024 * 1024 + 1))) { ApiResponse.read(r); }
    }
}
