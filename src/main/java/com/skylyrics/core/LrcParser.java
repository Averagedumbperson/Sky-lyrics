package com.skylyrics.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses LRC ("[mm:ss.xx] text") synced lyrics. */
public final class LrcParser {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TAG = Pattern.compile("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>");

    private LrcParser() {
    }

    public static List<LyricLine> parse(String lrc) {
        List<LyricLine> out = new ArrayList<>();
        if (lrc == null) {
            return out;
        }
        for (String raw : lrc.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.charAt(0) != '[') {
                continue;
            }
            List<Long> times = new ArrayList<>();
            int idx = 0;
            Matcher m = TIMESTAMP.matcher(line);
            while (idx < line.length()) {
                m.region(idx, line.length());
                if (!m.lookingAt()) {
                    break;
                }
                times.add(toMillis(m));
                idx = m.end();
            }
            if (times.isEmpty()) {
                continue; // metadata tag such as [ar:Artist]
            }
            String text = WORD_TAG.matcher(line.substring(idx)).replaceAll("").strip();
            for (long t : times) {
                out.add(new LyricLine(t, text));
            }
        }
        out.sort(Comparator.comparingLong(LyricLine::timeMs));
        return out;
    }

    private static long toMillis(Matcher m) {
        long min = Long.parseLong(m.group(1));
        long sec = Long.parseLong(m.group(2));
        long ms = 0;
        String frac = m.group(3);
        if (frac != null) {
            ms = Long.parseLong(frac);
            if (frac.length() == 1) {
                ms *= 100;
            } else if (frac.length() == 2) {
                ms *= 10;
            }
        }
        return min * 60_000L + sec * 1000L + ms;
    }

    /** Index of the last line whose time is <= posMs, or -1 if playback is before the first line. */
    public static int indexAt(List<LyricLine> lines, long posMs) {
        int lo = 0;
        int hi = lines.size() - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs() <= posMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
