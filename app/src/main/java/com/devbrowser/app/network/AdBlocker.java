package com.devbrowser.app.network;

import android.content.Context;
import android.util.Log;

import com.devbrowser.app.util.Prefs;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Host-rule ad/tracker blocker used from WebViewClient.shouldInterceptRequest.
 *
 * Rules (assets/adblock/hosts.txt + user rules) use adblock-style syntax
 * subset: "||domain^" or "domain" pattern; matched as suffix against the
 * request hostname (with an optional leading wildcard).
 */
public class AdBlocker {

    private static final String TAG = "AdBlocker";
    private static AdBlocker instance;
    private final Set<String> suffixRules = new HashSet<>();
    private volatile boolean loaded = false;

    public static synchronized AdBlocker get(Context c) {
        if (instance == null) instance = new AdBlocker(c.getApplicationContext());
        return instance;
    }

    private AdBlocker(Context ctx) {
        reload(ctx);
    }

    /** Re-parse bundled + user rules after settings change. */
    public void reload(Context ctx) {
        synchronized (suffixRules) {
            suffixRules.clear();
            loadAsset(ctx, "adblock/hosts.txt");
            for (String line : Prefs.adblockUserRules().split("\n")) {
                addRule(line);
            }
            loaded = true;
        }
        Log.i(TAG, "rules loaded: " + suffixRules.size());
    }

    private void loadAsset(Context ctx, String asset) {
        try (InputStream in = ctx.getAssets().open(asset);
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) addRule(line);
        } catch (Exception e) {
            Log.w(TAG, "cannot load " + asset, e);
        }
    }

    private void addRule(String raw) {
        String r = raw == null ? "" : raw.trim().toLowerCase();
        if (r.isEmpty() || r.startsWith("#") || r.startsWith("!")) return;
        r = r.replace("||", "").replace("^", "").replace("|", "");
        if (r.startsWith("*.")) r = r.substring(2);
        if (r.startsWith(".")) r = r.substring(1);
        if (r.isEmpty() || r.length() < 3 || r.contains("/") || r.contains(" ")) return;
        suffixRules.add(r);
    }

    /** True if the request URL should be blocked. */
    public boolean shouldBlock(String url) {
        if (!Prefs.adblockEnabled()) return false;
        String host = com.devbrowser.app.util.U.hostOf(url);
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase();
        synchronized (suffixRules) {
            for (String rule : suffixRules) {
                if (h.equals(rule) || h.endsWith("." + rule)) return true;
            }
        }
        return false;
    }

    public int ruleCount() {
        synchronized (suffixRules) {
            return suffixRules.size();
        }
    }
}
