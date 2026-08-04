package dev.schemtocreate.util;

import java.util.Locale;

/** Human readable formatting for log and CLI output. */
public final class Formats {

    private static final String[] BYTE_UNITS = {"B", "KiB", "MiB", "GiB", "TiB"};

    private Formats() {
    }

    public static String bytes(long value) {
        if (value < 1024) {
            return value + " B";
        }
        double scaled = value;
        int unit = 0;
        while (scaled >= 1024 && unit < BYTE_UNITS.length - 1) {
            scaled /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.2f %s", scaled, BYTE_UNITS[unit]);
    }

    public static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static String duration(long nanos) {
        double millis = nanos / 1_000_000.0;
        if (millis < 1000) {
            return String.format(Locale.ROOT, "%.1f ms", millis);
        }
        double seconds = millis / 1000.0;
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%.2f s", seconds);
        }
        long minutes = (long) (seconds / 60);
        return String.format(Locale.ROOT, "%d m %.1f s", minutes, seconds - minutes * 60);
    }

    public static String rate(long items, long nanos) {
        if (nanos <= 0) {
            return "n/a";
        }
        double perSecond = items / (nanos / 1_000_000_000.0);
        return String.format(Locale.ROOT, "%,.0f/s", perSecond);
    }
}
