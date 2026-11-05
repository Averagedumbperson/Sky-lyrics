package com.skylyrics.core;

/** Snapshot of what Spotify was playing at {@code sampledAtNanos} (System.nanoTime clock). */
public record NowPlaying(String trackId, String title, String artist, String album,
                         long durationMs, long progressMs, boolean playing, long sampledAtNanos) {

    /** Estimated current playback position, extrapolated from the last sample. */
    public long positionMs(long nowNanos) {
        if (!playing) {
            return progressMs;
        }
        long pos = progressMs + (nowNanos - sampledAtNanos) / 1_000_000L;
        if (durationMs > 0 && pos > durationMs) {
            pos = durationMs;
        }
        return Math.max(0L, pos);
    }
}
