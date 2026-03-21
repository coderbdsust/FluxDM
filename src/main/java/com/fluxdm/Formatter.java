package com.fluxdm;

public class Formatter {
    public static String bytes(long b) {
        if (b <= 0) return "0 B";
        String[] u = {"B","KB","MB","GB","TB"};
        int i = (int)(Math.log(b) / Math.log(1024));
        i = Math.min(i, u.length - 1);
        return String.format("%.1f %s", b / Math.pow(1024, i), u[i]);
    }
    public static String speed(double bps) {
        if (bps <= 0) return "—";
        String[] u = {"B/s","KB/s","MB/s","GB/s"};
        int i = (int)(Math.log(bps) / Math.log(1024));
        i = Math.min(Math.max(i, 0), u.length - 1);
        return String.format("%.1f %s", bps / Math.pow(1024, i), u[i]);
    }
    public static String eta(double bps, long remaining) {
        if (bps <= 0 || remaining <= 0) return "—";
        double secs = remaining / bps;
        if (!Double.isFinite(secs) || secs > 86400) return "—";
        int s = (int) secs, m = s / 60, h = m / 60;
        m %= 60; s %= 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
    public static String elapsed(long millis) {
        if (millis <= 0) return "0:00";
        int totalSec = (int) (millis / 1000);
        int h = totalSec / 3600, m = (totalSec % 3600) / 60, s = totalSec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
    public static String icon(DownloadTask.FileType t) {
        return switch (t) {
            case YOUTUBE   -> "▶";
            case VIDEO     -> "🎬";
            case AUDIO     -> "🎵";
            case ARCHIVE   -> "📦";
            case IMAGE     -> "🖼";
            case DOCUMENT  -> "📄";
            case EXECUTABLE-> "⚙";
            default        -> "📁";
        };
    }
}
