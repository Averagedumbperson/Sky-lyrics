package com.skylyrics.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings, stored as config/skylyrics.json. */
public final class Config {
    private static final Logger LOG = LoggerFactory.getLogger("skylyrics");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String[] COLOR_NAMES = {"White", "Lavender", "Pink", "Cyan", "Gold", "Mint"};
    public static final int[] COLOR_RGB = {0xFFFFFF, 0xC9B8FF, 0xFF9EC8, 0x8EE8FF, 0xFFD66B, 0x9CFFCB};
    public static final String[] SIZE_NAMES = {"Small", "Medium", "Large", "Huge"};
    /** Text height as an angle of the sky, so size stays consistent at any resolution/FOV. */
    public static final float[] SIZE_DEG = {4.5f, 6.5f, 9.0f, 12.0f};

    /**
     * false (default): read what's playing from this computer, no setup needed.
     * true: use a Spotify account instead (needs the Client ID below).
     */
    public boolean useSpotifyAccount = false;
    /** Set after the one-time welcome message has been shown. */
    public boolean welcomeShown = false;

    /** Only for "Spotify account" mode: your Spotify app's Client ID (see README). */
    public String spotifyClientId = "";
    /** Must match the redirect URI registered in your Spotify app: http://127.0.0.1:PORT/callback */
    public int redirectPort = 8888;

    public boolean showLyrics = true;
    public boolean showPill = true;
    /** true: lyrics float in front of you wherever you look. false: they stay where they appeared in the sky. */
    public boolean followView = true;
    public boolean wordByWord = true;
    public int colorIndex = 1;
    public int sizeIndex = 1;
    /** Shifts lyric timing (positive = lyrics appear later). */
    public int offsetMs = 0;

    private transient Path file;

    public static Config load(Path file) {
        Config cfg = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, Config.class);
            } catch (Exception e) {
                LOG.warn("Could not read {} - using defaults", file, e);
            }
        }
        if (cfg == null) {
            cfg = new Config();
        }
        cfg.file = file;
        cfg.sanitize();
        cfg.save();
        return cfg;
    }

    /** Re-reads only the Spotify credentials from disk, so editing the file doesn't need a game restart. */
    public synchronized void reloadCredentialsFromDisk() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Config fresh = GSON.fromJson(r, Config.class);
            if (fresh != null) {
                fresh.sanitize();
                this.spotifyClientId = fresh.spotifyClientId;
                this.redirectPort = fresh.redirectPort;
            }
        } catch (Exception e) {
            LOG.warn("Could not re-read {}", file, e);
        }
    }

    private void sanitize() {
        if (spotifyClientId == null) {
            spotifyClientId = "";
        }
        spotifyClientId = spotifyClientId.trim();
        if (redirectPort < 1024 || redirectPort > 65535) {
            redirectPort = 8888;
        }
        colorIndex = Math.floorMod(colorIndex, COLOR_NAMES.length);
        sizeIndex = Math.floorMod(sizeIndex, SIZE_NAMES.length);
        offsetMs = Math.max(-5000, Math.min(5000, offsetMs));
    }

    public Path file() {
        return file;
    }

    public synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Could not save {}", file, e);
        }
    }
}
