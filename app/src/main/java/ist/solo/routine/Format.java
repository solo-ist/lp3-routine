package ist.solo.routine;

/** Durations as the player and summary show them. */
final class Format {
    private Format() {}

    /** 4:05, 12:00, 1:02:03. Rounds up so a timer never shows 0:00 while time remains. */
    static String clock(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return hms(s);
    }

    /** Elapsed time, rounded down: a count-up shows 0:00 until a full second has passed. */
    static String elapsed(long ms) {
        return hms(Math.max(0, ms / 1000));
    }

    /** "+2:14" for time over. */
    static String over(long overMs) {
        return "+" + elapsed(overMs);
    }

    /** "25 min", "1 h 5 min", "45 s" for plans and totals. */
    static String minutes(long ms) {
        long s = Math.round(ms / 1000.0);
        if (s < 60) return s + " s";
        long m = Math.round(s / 60.0);
        if (m < 60) return m + " min";
        long h = m / 60, rest = m % 60;
        return rest == 0 ? h + " h" : h + " h " + rest + " min";
    }

    private static String hms(long s) {
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
                ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
    }
}
