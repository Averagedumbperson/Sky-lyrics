package com.skylyrics.core;

/**
 * Turns raw readings into a NowPlaying with a usable playback position.
 * If the player reports a position, it is trusted; if not (or if it looks stale right after a track change),
 * the position is estimated from the wall clock, assuming the track started when we first saw it.
 */
public final class LocalPositionTracker {
    /** After a track change, a reported position this far past "time since change" is a stale leftover. */
    private static final long SETTLE_MS = 4000L;

    private String lastKey;
    private boolean transition;      // true when the current track replaced a previous one
    private long keySinceNanos;
    private double estMs;
    private long lastNanos;

    public synchronized void reset() {
        lastKey = null;
        transition = false;
    }

    public synchronized NowPlaying apply(LocalSample s, long nowNanos) {
        String key = s.title() + "|" + s.artist();
        boolean changed = !key.equals(lastKey);
        if (changed) {
            transition = lastKey != null;
            lastKey = key;
            keySinceNanos = nowNanos;
            estMs = 0;
            lastNanos = nowNanos;
        }
        double dtMs = (nowNanos - lastNanos) / 1_000_000.0;
        lastNanos = nowNanos;
        if (!changed && s.playing()) {
            estMs += dtMs;
        }

        long sinceChangeMs = (nowNanos - keySinceNanos) / 1_000_000L;
        boolean trust = s.positionMs() >= 0;
        if (trust && transition && sinceChangeMs < SETTLE_MS && s.positionMs() > sinceChangeMs + SETTLE_MS) {
            trust = false; // old track's position still being reported
        }
        long progress;
        if (trust) {
            progress = s.positionMs();
            estMs = progress;
        } else {
            progress = (long) estMs;
        }
        return new NowPlaying(key, s.title(), s.artist(), s.album(), s.durationMs(), progress, s.playing(), nowNanos);
    }
}
