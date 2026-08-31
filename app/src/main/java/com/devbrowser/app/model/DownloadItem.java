package com.devbrowser.app.model;

import com.devbrowser.app.util.U;

import java.io.File;

/** State of one download (active or finished). */
public class DownloadItem {

    public static final int STATE_QUEUED = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_ERROR = 3;

    public final String url;
    public String name;
    public String path;
    public int state = STATE_QUEUED;
    public long total = -1;
    public long done = 0;
    public long time;
    public String error;

    public DownloadItem(String url, String name) {
        this.url = url;
        this.name = name;
    }

    public static DownloadItem done(File f) {
        DownloadItem i = new DownloadItem("file://" + f.getAbsolutePath(), f.getName());
        i.path = f.getAbsolutePath();
        i.state = STATE_DONE;
        i.total = f.length();
        i.done = f.length();
        i.time = f.lastModified();
        return i;
    }

    public boolean isApk() {
        String n = name == null ? path : name;
        return n != null && n.toLowerCase().endsWith(".apk");
    }

    public String display() {
        if (state == STATE_DONE || state == STATE_ERROR) {
            return name == null ? "" : name;
        }
        return (name == null ? "…" : name) + "  " + progressText();
    }

    public String progressText() {
        if (state == STATE_ERROR) return "error: " + error;
        if (state == STATE_DONE) return U.humanSize(done);
        String size = total > 0 ? U.humanSize(done) + " / " + U.humanSize(total) : U.humanSize(done);
        return size;
    }
}
