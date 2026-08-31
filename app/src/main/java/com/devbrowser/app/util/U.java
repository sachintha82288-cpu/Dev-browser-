package com.devbrowser.app.util;

import android.net.Uri;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/** Small helpers: URL normalisation, date formatting, MIME guessing. */
public class U {

    private static final Pattern HAS_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
    private static final Pattern LOOKS_LIKE_URL = Pattern.compile(
            "^(([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}|(\\d{1,3}\\.){3}\\d{1,3}|localhost)(:\\d+)?(/.*)?$");

    /** Normalise what the user typed into a URL (or a search query). */
    public static String smartUrl(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) return "";
        if (HAS_SCHEME.matcher(s).matches()) {
            return s.startsWith("about:") ? s : s;
        }
        if (LOOKS_LIKE_URL.matcher(s).matches()) {
            return "https://" + s;
        }
        return Prefs.searchUrl(s);
    }

    public static String hostOf(String url) {
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            return host == null ? "" : host;
        } catch (Exception e) {
            return "";
        }
    }

    public static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    public static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    public static String time(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    public static String day(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(millis));
    }

    public static String mimeFor(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".xml")) return "text/xml";
        if (n.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    public static String fileNameFromPath(String url) {
        try {
            Uri u = Uri.parse(url);
            String path = u.getPath();
            if (path != null && path.length() > 1) {
                String n = path.substring(path.lastIndexOf('/') + 1);
                if (!n.isEmpty()) return n;
            }
        } catch (Exception ignored) {
        }
        return "download_" + System.currentTimeMillis();
    }
}
