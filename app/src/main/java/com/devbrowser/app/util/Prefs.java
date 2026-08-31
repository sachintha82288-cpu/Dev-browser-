package com.devbrowser.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Thin wrapper over SharedPreferences for all browser settings. */
public class Prefs {

    public static final int UA_DEFAULT = 0;
    public static final int UA_DESKTOP = 1;
    public static final int UA_ANDROID = 2;
    public static final int UA_CUSTOM = 3;

    public static final int SEARCH_DUCKDUCKGO = 0;
    public static final int SEARCH_GOOGLE = 1;
    public static final int SEARCH_BING = 2;

    public static final String DEFAULT_HOMEPAGE = "about:devhome";
    public static final String DEFAULT_SEARCH = "https://duckduckgo.com/?q=";

    private static SharedPreferences sp;

    public static void init(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("devbrowser", Context.MODE_PRIVATE);
    }

    private static SharedPreferences p() {
        if (sp == null) throw new IllegalStateException("Prefs not initialised");
        return sp;
    }

    // ----- browser -----
    public static String homepage() { return p().getString("homepage", DEFAULT_HOMEPAGE); }
    public static void setHomepage(String v) { p().edit().putString("homepage", v).apply(); }

    public static int searchEngine() { return p().getInt("search_engine", SEARCH_DUCKDUCKGO); }
    public static void setSearchEngine(int v) { p().edit().putInt("search_engine", v).apply(); }

    public static int uaMode() { return p().getInt("ua_mode", UA_DEFAULT); }
    public static void setUaMode(int v) { p().edit().putInt("ua_mode", v).apply(); }

    public static String customUa() { return p().getString("custom_ua", ""); }
    public static void setCustomUa(String v) { p().edit().putString("custom_ua", v).apply(); }

    public static boolean jsEnabled() { return p().getBoolean("js", true); }
    public static void setJsEnabled(boolean v) { p().edit().putBoolean("js", v).apply(); }

    public static boolean blockImages() { return p().getBoolean("block_images", false); }
    public static void setBlockImages(boolean v) { p().edit().putBoolean("block_images", v).apply(); }

    public static boolean savePrivateHistory() { return p().getBoolean("save_private_history", false); }
    public static void setSavePrivateHistory(boolean v) { p().edit().putBoolean("save_private_history", v).apply(); }

    public static boolean thirdPartyCookiesAllowed() { return p().getBoolean("allow_3p_cookies", true); }
    public static void setThirdPartyCookiesAllowed(boolean v) { p().edit().putBoolean("allow_3p_cookies", v).apply(); }

    // ----- ad blocking -----
    public static boolean adblockEnabled() { return p().getBoolean("adblock_enabled", true); }
    public static void setAdblockEnabled(boolean v) { p().edit().putBoolean("adblock_enabled", v).apply(); }

    public static String adblockUserRules() { return p().getString("adblock_user_rules", ""); }
    public static void setAdblockUserRules(String v) { p().edit().putString("adblock_user_rules", v).apply(); }

    // ----- in-app Tor proxy (SOCKS) -----
    public static boolean localProxyEnabled() { return p().getBoolean("local_proxy_enabled", false); }
    public static void setLocalProxyEnabled(boolean v) { p().edit().putBoolean("local_proxy_enabled", v).apply(); }

    public static String socksHost() { return p().getString("socks_host", "127.0.0.1"); }
    public static void setSocksHost(String v) { p().edit().putString("socks_host", v).apply(); }

    public static int socksPort() { return p().getInt("socks_port", 9050); }
    public static void setSocksPort(int v) { p().edit().putInt("socks_port", v).apply(); }

    // ----- workspace -----
    public static String workspaceHtml() { return p().getString("workspace_html", ""); }
    public static void setWorkspaceHtml(String v) { p().edit().putString("workspace_html", v).apply(); }

    public static String workspaceCss() { return p().getString("workspace_css", ""); }
    public static void setWorkspaceCss(String v) { p().edit().putString("workspace_css", v).apply(); }

    public static String workspaceJs() { return p().getString("workspace_js", ""); }
    public static void setWorkspaceJs(String v) { p().edit().putString("workspace_js", v).apply(); }

    // ----- proxy / tor -----
    public static boolean proxyEnabled() { return p().getBoolean("proxy_enabled", false); }
    public static void setProxyEnabled(boolean v) { p().edit().putBoolean("proxy_enabled", v).apply(); }

    public static String proxyHost() { return p().getString("proxy_host", "127.0.0.1"); }
    public static void setProxyHost(String v) { p().edit().putString("proxy_host", v).apply(); }

    public static int proxyPort() { return p().getInt("proxy_port", 8118); }
    public static void setProxyPort(int v) { p().edit().putInt("proxy_port", v).apply(); }

    /** Build a user agent string for the current mode. */
    public static String resolveUserAgent(String defaultUa) {
        switch (uaMode()) {
            case UA_DESKTOP:
                return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
            case UA_ANDROID:
                return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
            case UA_CUSTOM:
                return customUa();
            default:
                return defaultUa;
        }
    }

    public static String searchUrl(String query) {
        switch (searchEngine()) {
            case SEARCH_GOOGLE:  return "https://www.google.com/search?q=" + U.enc(query);
            case SEARCH_BING:    return "https://www.bing.com/search?q=" + U.enc(query);
            case SEARCH_DUCKDUCKGO:
            default:             return "https://duckduckgo.com/?q=" + U.enc(query);
        }
    }
}
