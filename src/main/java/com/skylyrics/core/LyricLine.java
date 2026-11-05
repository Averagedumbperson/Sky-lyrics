package com.skylyrics.core;

/** One synced lyric line. An empty text marks a pause/instrumental gap. */
public record LyricLine(long timeMs, String text) {
}
