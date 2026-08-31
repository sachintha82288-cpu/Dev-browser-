package com.devbrowser.app.model;

import android.webkit.WebView;

import java.util.ArrayList;
import java.util.List;

/** One browsing tab: WebView + state. */
public class Tab {

    public interface Listener {
        void onLog(Tab tab);
    }

    public final WebView web;
    public final boolean incognito;
    public final List<String> logs = new ArrayList<>();

    public Listener listener;
    public String title = "";
    public String url = "";
    public int progress = 100;
    public long created = System.currentTimeMillis();

    public Tab(WebView web, boolean incognito) {
        this.web = web;
        this.incognito = incognito;
    }

    /** Neat tab strip title. */
    public String chipTitle() {
        if (incognito) return "Private";
        String t = title;
        if (t == null || t.isEmpty()) t = url;
        if (t == null || t.isEmpty()) t = "New tab";
        return t.length() > 18 ? t.substring(0, 18) + "…" : t;
    }

    public void log(String level, String message) {
        String line = "[" + level + "] " + message;
        logs.add(line);
        if (logs.size() > 400) logs.remove(0);
    }
}
