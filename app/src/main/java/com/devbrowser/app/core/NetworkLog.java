package com.devbrowser.app.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory ring buffer capturing every WebView request via
 * shouldInterceptRequest plus page/download lifecycle. Worked from the
 * WebView's network thread, so all access is synchronized.
 */
public class NetworkLog {

    public static final int MAX = 600;

    public static class Entry {
        public final long id;
        public final long start;
        public final String url;
        public final String type;
        public boolean blocked;
        public boolean failed;
        public long end = -1;
        public long size = -1;

        Entry(long id, long start, String url, String type) {
            this.id = id;
            this.start = start;
            this.url = url;
            this.type = type;
        }

        public int status() {
            if (blocked) return 2;   // blocked
            if (failed) return 3;    // error
            return 1;                // ok
        }

        public long duration() {
            return end > start ? end - start : 0;
        }
    }

    private static final Deque<Entry> log = new ArrayDeque<>();
    private static long seq = 0;

    public static synchronized Entry begin(String url, String type) {
        Entry e = new Entry(++seq, System.currentTimeMillis(), url == null ? "" : url, type);
        log.addFirst(e);
        while (log.size() > MAX) log.removeLast();
        return e;
    }

    public static synchronized void end(Entry e, long size) {
        if (e != null) {
            e.end = System.currentTimeMillis();
            e.size = size;
        }
    }

    public static synchronized void markBlocked(Entry e) {
        if (e != null) {
            e.blocked = true;
            e.end = System.currentTimeMillis();
        }
    }

    public static synchronized void markFailed(Entry e) {
        if (e != null) {
            e.failed = true;
            e.end = System.currentTimeMillis();
        }
    }

    /** Snapshot (newest first), optionally filtered by type keyword. */
    public static synchronized List<Entry> snapshot(String filter) {
        List<Entry> out = new ArrayList<>();
        String f = filter == null ? "all" : filter.toLowerCase();
        for (Entry e : log) {
            if (f.equals("all")) out.add(e);
            else if (f.equals("blocked") && e.blocked) out.add(e);
            else if (f.equals("failed") && e.failed) out.add(e);
            else if (f.equals("other") && !knownType(e.type)) out.add(e);
            else if (e.type.equalsIgnoreCase(f)) out.add(e);
        }
        return out;
    }

    private static boolean knownType(String t) {
        return t.equals("document") || t.equals("script") || t.equals("style")
                || t.equals("image") || t.equals("media") || t.equals("xhr")
                || t.equals("fetch") || t.equals("download");
    }

    public static synchronized void clear() {
        log.clear();
    }

    public static synchronized int size() {
        return log.size();
    }

    /** Guess resource type from the URL for interception logging. */
    public static String typeOf(String url) {
        String u = url == null ? "" : url.toLowerCase();
        if (u.endsWith(".js")) return "script";
        if (u.endsWith(".css")) return "style";
        if (u.endsWith(".png") || u.endsWith(".jpg") || u.endsWith(".jpeg")
                || u.endsWith(".gif") || u.endsWith(".webp") || u.endsWith(".svg")
                || u.endsWith(".ico")) return "image";
        if (u.endsWith(".mp4") || u.endsWith(".webm") || u.endsWith(".mp3")
                || u.endsWith(".ogg") || u.endsWith(".m3u8")) return "media";
        if (u.startsWith("data:")) return "other";
        return "other";
    }
}
