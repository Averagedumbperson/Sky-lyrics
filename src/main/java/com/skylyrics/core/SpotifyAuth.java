package com.skylyrics.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Spotify "Authorization Code with PKCE" login (no client secret needed).
 * A tiny local web server on 127.0.0.1 (plain ServerSocket, java.base only) receives the redirect after you
 * approve access in your browser.
 */
public final class SpotifyAuth {
    private static final Logger LOG = LoggerFactory.getLogger("skylyrics");
    private static final String AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SCOPES = "user-read-currently-playing user-read-playback-state";
    private static final long LOGIN_TIMEOUT_MS = 180_000L;

    public record Tokens(String accessToken, String refreshToken, long expiresAtMs) {
    }

    private final Config config;
    private final Path tokenFile;
    private final AtomicBoolean loginRunning = new AtomicBoolean(false);
    private volatile Tokens tokens;

    public SpotifyAuth(Config config, Path tokenFile) {
        this.config = config;
        this.tokenFile = tokenFile;
        this.tokens = loadTokens();
    }

    public boolean isConnected() {
        Tokens t = tokens;
        return t != null && t.refreshToken() != null && !t.refreshToken().isBlank();
    }

    public boolean isLoginRunning() {
        return loginRunning.get();
    }

    /** A valid access token, refreshing it first if it is about to expire. */
    public synchronized String accessToken() throws IOException {
        Tokens t = tokens;
        if (t == null) {
            throw new IOException("Not connected to Spotify");
        }
        if (System.currentTimeMillis() < t.expiresAtMs() - 30_000L) {
            return t.accessToken();
        }
        return refresh(t).accessToken();
    }

    /** Forces the next accessToken() call to refresh (used after a 401). */
    public synchronized void invalidateAccessToken() {
        Tokens t = tokens;
        if (t != null) {
            tokens = new Tokens(t.accessToken(), t.refreshToken(), 0L);
        }
    }

    public synchronized void logout() {
        tokens = null;
        try {
            Files.deleteIfExists(tokenFile);
        } catch (IOException e) {
            LOG.warn("Could not delete {}", tokenFile, e);
        }
    }

    // ---- login ---------------------------------------------------------------------------------

    /** Starts the browser login. Progress/result messages are passed to {@code status}. */
    public void beginLogin(Consumer<String> status) {
        config.reloadCredentialsFromDisk();
        final String clientId = config.spotifyClientId == null ? "" : config.spotifyClientId.trim();
        if (clientId.isEmpty()) {
            status.accept("Add your Spotify Client ID to " + config.file() + " first");
            return;
        }
        if (!loginRunning.compareAndSet(false, true)) {
            status.accept("A login is already in progress");
            return;
        }

        final ServerSocket server;
        try {
            server = new ServerSocket(config.redirectPort, 8, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            loginRunning.set(false);
            status.accept("Could not start login (is port " + config.redirectPort + " in use?): " + e.getMessage());
            return;
        }

        final String verifier = Pkce.newVerifier();
        final String challenge = Pkce.challenge(verifier);
        final String state = Pkce.newVerifier().substring(0, 24);
        final String redirect = "http://127.0.0.1:" + config.redirectPort + "/callback";
        final AtomicBoolean done = new AtomicBoolean(false);

        Thread acceptor = new Thread(() -> {
            long deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS;
            try {
                server.setSoTimeout(1000);
                while (!done.get() && System.currentTimeMillis() < deadline) {
                    Socket socket;
                    try {
                        socket = server.accept();
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                    // One thread per connection: browsers sometimes open idle "preconnect" sockets.
                    Thread handler = new Thread(() -> {
                        if (handleConnection(socket, state, verifier, redirect, clientId, status)) {
                            done.set(true);
                        }
                    }, "SkyLyrics-login-conn");
                    handler.setDaemon(true);
                    handler.start();
                }
            } catch (IOException e) {
                LOG.debug("Login server stopped: {}", e.getMessage());
            } finally {
                try {
                    server.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
                loginRunning.set(false);
            }
        }, "SkyLyrics-login");
        acceptor.setDaemon(true);
        acceptor.start();

        String url = AUTH_URL + "?" + form(
                "response_type", "code",
                "client_id", clientId,
                "scope", SCOPES,
                "redirect_uri", redirect,
                "state", state,
                "code_challenge_method", "S256",
                "code_challenge", challenge);
        LOG.info("Spotify login URL: {}", url);
        if (BrowserOpener.open(url)) {
            status.accept("Waiting for you to approve access in your browser...");
        } else {
            status.accept("Could not open a browser - the login link is in the game log (latest.log)");
        }
    }

    /** @return true when the Spotify redirect (/callback) was handled, so the login attempt is finished */
    private boolean handleConnection(Socket socket, String state, String verifier, String redirect,
                                     String clientId, Consumer<String> status) {
        try (socket) {
            socket.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String requestLine = in.readLine();
            if (requestLine == null) {
                return false;
            }
            String header;
            do {
                header = in.readLine(); // skip the rest of the request headers
            } while (header != null && !header.isEmpty());

            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0])) {
                respond(socket, 405, "Method Not Allowed");
                return false;
            }
            String target = parts[1];
            int qm = target.indexOf('?');
            String path = qm < 0 ? target : target.substring(0, qm);
            if (!"/callback".equals(path)) {
                respond(socket, 404, "Not found");
                return false; // e.g. /favicon.ico
            }

            Map<String, String> q = parseQuery(qm < 0 ? null : target.substring(qm + 1));
            String message;
            if (q.containsKey("error")) {
                message = "Spotify returned an error: " + q.get("error");
            } else if (!state.equals(q.get("state")) || q.get("code") == null) {
                message = "Invalid response from Spotify. Please click Connect again.";
            } else {
                try {
                    exchangeCode(q.get("code"), verifier, redirect, clientId);
                    message = "Connected to Spotify. You can close this tab and go back to Minecraft.";
                } catch (Exception e) {
                    LOG.warn("Spotify token exchange failed", e);
                    message = "Login failed: " + e.getMessage();
                }
            }
            respond(socket, 200, message);
            status.accept(message);
            return true;
        } catch (IOException e) {
            LOG.debug("Login connection error: {}", e.getMessage());
            return false;
        }
    }

    private static void respond(Socket socket, int code, String message) throws IOException {
        String reason = switch (code) {
            case 200 -> "OK";
            case 404 -> "Not Found";
            default -> "Method Not Allowed";
        };
        byte[] body = ("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\">"
                + "<h2>Sky Lyrics</h2><p>" + escapeHtml(message) + "</p></body></html>")
                .getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private void exchangeCode(String code, String verifier, String redirect, String clientId) throws IOException {
        String body = form(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", redirect,
                "client_id", clientId,
                "code_verifier", verifier);
        JsonObject json = postToken(body);
        Tokens fresh = new Tokens(
                requireString(json, "access_token"),
                requireString(json, "refresh_token"),
                System.currentTimeMillis() + optLong(json, "expires_in", 3600L) * 1000L);
        synchronized (this) {
            tokens = fresh;
            saveTokens(fresh);
        }
    }

    private Tokens refresh(Tokens old) throws IOException {
        String body = form(
                "grant_type", "refresh_token",
                "refresh_token", old.refreshToken(),
                "client_id", config.spotifyClientId.trim());
        try {
            JsonObject json = postToken(body);
            String refreshToken = json.has("refresh_token") && !json.get("refresh_token").isJsonNull()
                    ? json.get("refresh_token").getAsString() : old.refreshToken();
            Tokens fresh = new Tokens(
                    requireString(json, "access_token"),
                    refreshToken,
                    System.currentTimeMillis() + optLong(json, "expires_in", 3600L) * 1000L);
            tokens = fresh;
            saveTokens(fresh);
            return fresh;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("invalid_grant")) {
                logout(); // refresh token revoked/expired: need to log in again
            }
            throw e;
        }
    }

    private JsonObject postToken(String formBody) throws IOException {
        Http.Response res = Http.postForm(TOKEN_URL, formBody);
        if (res.status() != 200) {
            throw new IOException("Spotify token request failed (HTTP " + res.status() + "): " + res.body());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    // ---- token storage -------------------------------------------------------------------------

    private Tokens loadTokens() {
        try {
            if (!Files.exists(tokenFile)) {
                return null;
            }
            JsonObject o = JsonParser.parseString(Files.readString(tokenFile, StandardCharsets.UTF_8)).getAsJsonObject();
            String refresh = optString(o, "refresh_token", "");
            if (refresh.isBlank()) {
                return null;
            }
            return new Tokens(optString(o, "access_token", ""), refresh, 0L); // expired -> refreshed on first use
        } catch (Exception e) {
            LOG.warn("Could not read {}", tokenFile, e);
            return null;
        }
    }

    private void saveTokens(Tokens t) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("access_token", t.accessToken());
            o.addProperty("refresh_token", t.refreshToken());
            Files.createDirectories(tokenFile.getParent());
            Files.writeString(tokenFile, o.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Could not save {}", tokenFile, e);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    static String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(enc(kv[i])).append('=').append(enc(kv[i + 1]));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String requireString(JsonObject o, String key) throws IOException {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            throw new IOException("Spotify response is missing '" + key + "'");
        }
        return e.getAsString();
    }

    static String optString(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? def : e.getAsString();
    }

    static long optLong(JsonObject o, String key, long def) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? def : e.getAsLong();
    }
}
