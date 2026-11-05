package com.skylyrics.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Minimal HTTP client built on java.base only (HttpURLConnection).
 * Minecraft ships a trimmed Java runtime, so we avoid optional JDK modules such as java.net.http.
 */
public final class Http {
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 10000;

    /** @param retryAfterSeconds value of the Retry-After header, or 5 if absent/unparseable */
    public record Response(int status, String body, long retryAfterSeconds) {
    }

    private Http() {
    }

    public static Response get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = open(url, headers);
        c.setRequestMethod("GET");
        return execute(c);
    }

    public static Response postForm(String url, String formBody) throws IOException {
        HttpURLConnection c = open(url, Map.of("Content-Type", "application/x-www-form-urlencoded"));
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = c.getOutputStream()) {
            os.write(bytes);
        }
        return execute(c);
    }

    private static HttpURLConnection open(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            c.setRequestProperty(e.getKey(), e.getValue());
        }
        return c;
    }

    private static Response execute(HttpURLConnection c) throws IOException {
        try {
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = in == null ? "" : readAll(in);
            long retry = 5L;
            String ra = c.getHeaderField("Retry-After");
            if (ra != null) {
                try {
                    retry = Long.parseLong(ra.trim());
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
            return new Response(status, body, retry);
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
