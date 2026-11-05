package com.skylyrics.core;

import java.util.List;
import java.util.Random;

/** Timing, fade, placement and word-reveal helpers for lyric lines. Pure logic. */
public final class LyricAnim {
    private static final long MAX_HOLD_MS = 7000L;
    private static final float FADE_IN_MS = 220f;
    private static final float FADE_OUT_MS = 450f;

    private LyricAnim() {
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    /** When a line "ends": the next line's start, but never held longer than MAX_HOLD_MS. */
    public static long lineEnd(List<LyricLine> lines, int i, long durationMs) {
        long start = lines.get(i).timeMs();
        long next = i + 1 < lines.size() ? lines.get(i + 1).timeMs() : Long.MAX_VALUE;
        long end = Math.min(next, start + MAX_HOLD_MS);
        if (durationMs > 0) {
            end = Math.min(end, durationMs);
        }
        return Math.max(end, start + 400L);
    }

    /** Fade in after start, full until end, then fade out (it lingers briefly while the next line appears). */
    public static float alpha(long posMs, long startMs, long endMs) {
        if (posMs < startMs) {
            return 0f;
        }
        float in = clamp01((posMs - startMs) / FADE_IN_MS);
        float out = posMs <= endMs ? 1f : clamp01(1f - (posMs - endMs) / FADE_OUT_MS);
        return in * out;
    }

    /** Small "pop" scale when a line appears: 0.88 -> 1.0 with ease-out. */
    public static float pop(long sinceStartMs) {
        float t = clamp01(sinceStartMs / 280f);
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        return 0.88f + 0.12f * e;
    }

    /**
     * Where a line appears relative to the view: {yawOffsetDeg, degreesAboveCenter}.
     * Deterministic per (track, line) and alternates left/right so neighbouring lines don't overlap.
     */
    public static float[] placement(long trackSeed, int lineIndex) {
        Random r = new Random(trackSeed * 31L + lineIndex);
        float side = (lineIndex % 2 == 0) ? -1f : 1f;
        float yaw = side * (6f + r.nextFloat() * 20f);
        float up = 9f + r.nextFloat() * 17f;
        return new float[]{yaw, up};
    }

    /**
     * Reveal time (ms) for each word. LRCLIB only has line-level timing, so words are spread over the first
     * part of the line, weighted by word length (an approximation of how the line is sung).
     */
    public static long[] wordRevealMs(String[] words, long startMs, long endMs) {
        long[] out = new long[words.length];
        if (words.length == 0) {
            return out;
        }
        long span = Math.min((long) ((endMs - startMs) * 0.8), 4000L);
        double total = 0;
        for (String w : words) {
            total += w.length() + 1;
        }
        double acc = 0;
        for (int k = 0; k < words.length; k++) {
            out[k] = startMs + (long) (span * (acc / total));
            acc += words[k].length() + 1;
        }
        return out;
    }
}
