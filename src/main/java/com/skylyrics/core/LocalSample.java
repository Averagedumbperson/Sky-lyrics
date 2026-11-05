package com.skylyrics.core;

/**
 * One reading from the operating system's "now playing" info.
 * Every platform script prints one tab-separated line per second:
 * title, artist, album, duration, position, status.
 */
public record LocalSample(String title, String artist, String album, long durationMs, long positionMs, boolean playing) {

    /** Means "nothing is playing". */
    public static final LocalSample NOTHING = new LocalSample("", "", "", 0L, -1L, false);

    public boolean isNothing() {
        return title.isEmpty();
    }

    /**
     * @param unitDivisor 1 if duration/position are in milliseconds, 1000 if they are in microseconds
     * @return NOTHING for a blank line or a stopped player; null if the line is not a valid reading (ignored);
     *         otherwise the sample. A negative or missing position becomes -1 ("unknown").
     */
    public static LocalSample parse(String rawLine, int unitDivisor) {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.replace("\uFEFF", "").stripTrailing();
        if (line.isBlank()) {
            return NOTHING;
        }
        String[] f = line.split("\t", -1);
        if (f.length < 6) {
            return null;
        }
        String title = f[0].strip();
        String status = f[5].strip();
        if (title.isEmpty() || status.equalsIgnoreCase("stopped") || status.equalsIgnoreCase("closed")) {
            return NOTHING;
        }
        long duration = toMillis(f[3], unitDivisor);
        long position = toMillis(f[4], unitDivisor);
        return new LocalSample(title, f[1].strip(), f[2].strip(),
                Math.max(0L, duration), position < 0 ? -1L : position, status.equalsIgnoreCase("playing"));
    }

    private static long toMillis(String s, int divisor) {
        try {
            double v = Double.parseDouble(s.strip().replace(',', '.')); // some locales print "12,5"
            return Math.round(v / Math.max(1, divisor));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
