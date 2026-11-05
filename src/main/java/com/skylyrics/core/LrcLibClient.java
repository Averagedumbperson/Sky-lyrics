package com.skylyrics.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fetches time-synced lyrics from LRCLIB (https://lrclib.net), a free community lyrics database. */
public final class LrcLibClient {
    private static final String BASE = "https://lrclib.net/api";
    private static final String USER_AGENT = "SkyLyrics/0.1.0 (Minecraft Fabric mod)";

    public record Result(List<LyricLine> lines, String note) {
    }

    private final Map<String, Result> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
            return size() > 40;
        }
    };

    public Result fetch(NowPlaying np) throws IOException {
        synchronized (cache) {
            Result cached = cache.get(np.trackId());
            if (cached != null) {
                return cached;
            }
        }
        Result result = lookup(np);
        synchronized (cache) {
            cache.put(np.trackId(), result);
        }
        return result;
    }

    private Result lookup(NowPlaying np) throws IOException {
        String artist = firstArtist(np.artist());
        long durationSec = Math.round(np.durationMs() / 1000.0);

        // 1) exact match
        String exactUrl = BASE + "/get?track_name=" + enc(np.title()) + "&artist_name=" + enc(artist)
                + (np.album().isEmpty() ? "" : "&album_name=" + enc(np.album()))
                + (durationSec > 0 ? "&duration=" + durationSec : "");
        Http.Response res = get(exactUrl);
        if (res.status() == 200) {
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            if (isInstrumental(o)) {
                return new Result(List.of(), "Instrumental");
            }
            String synced = syncedOf(o);
            if (synced != null) {
                return resultFrom(synced);
            }
        }

        // 2) fuzzy search with a cleaned-up title
        String searchUrl = BASE + "/search?track_name=" + enc(cleanTitle(np.title())) + "&artist_name=" + enc(artist);
        res = get(searchUrl);
        if (res.status() == 200) {
            JsonElement parsed = JsonParser.parseString(res.body());
            if (parsed.isJsonArray()) {
                String best = pickBest(parsed.getAsJsonArray(), durationSec);
                if (best != null) {
                    return resultFrom(best);
                }
            }
        }
        return new Result(List.of(), "No synced lyrics found");
    }

    private static Result resultFrom(String synced) {
        List<LyricLine> lines = LrcParser.parse(synced);
        return lines.isEmpty() ? new Result(List.of(), "No synced lyrics found") : new Result(lines, lines.size() + " lines");
    }

    /** Prefers a result whose duration is within 5 s of the track; otherwise the first with synced lyrics. */
    private static String pickBest(JsonArray arr, long durationSec) {
        String fallback = null;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String synced = syncedOf(o);
            if (synced == null) {
                continue;
            }
            if (fallback == null) {
                fallback = synced;
            }
            JsonElement d = o.get("duration");
            if (durationSec > 0 && d != null && !d.isJsonNull() && Math.abs(d.getAsDouble() - durationSec) <= 5.0) {
                return synced;
            }
        }
        return fallback;
    }

    private static String syncedOf(JsonObject o) {
        JsonElement s = o.get("syncedLyrics");
        if (s == null || s.isJsonNull()) {
            return null;
        }
        String text = s.getAsString();
        return text.isBlank() ? null : text;
    }

    private static boolean isInstrumental(JsonObject o) {
        JsonElement i = o.get("instrumental");
        return i != null && !i.isJsonNull() && i.getAsBoolean();
    }

    private static Http.Response get(String url) throws IOException {
        return Http.get(url, Map.of("User-Agent", USER_AGENT));
    }

    static String firstArtist(String artists) {
        int comma = artists.indexOf(',');
        return (comma < 0 ? artists : artists.substring(0, comma)).trim();
    }

    /** "Song - Remastered 2011" / "Song (feat. X)" -> "Song". Only used for the fuzzy fallback search. */
    static String cleanTitle(String title) {
        String t = title.replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "");
        int dash = t.indexOf(" - ");
        if (dash > 0) {
            t = t.substring(0, dash);
        }
        t = t.trim();
        return t.isEmpty() ? title : t;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
