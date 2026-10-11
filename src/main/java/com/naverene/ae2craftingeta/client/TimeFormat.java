package com.naverene.ae2craftingeta.client;

public final class TimeFormat {

    private TimeFormat() {}

    /** Formats a duration as "1h 4m", "4m 12s" or "8.5s". */
    public static String format(double seconds) {
        if (seconds < 10) {
            return String.format("%.1fs", seconds);
        }
        long total = Math.round(seconds);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long secs = total % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    /** Formats whole seconds with the seconds always shown, as "1h 4m 12s", "4m 12s" or "12s", so it ticks down. */
    public static String formatCountdown(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + secs + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }
}
