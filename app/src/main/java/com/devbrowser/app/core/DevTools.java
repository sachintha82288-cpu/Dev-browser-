package com.devbrowser.app.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.devbrowser.app.model.Tab;

import java.io.File;
import java.io.FileOutputStream;

/** Developer helpers: in-page JS bridge, screenshots, view-source capture. */
public class DevTools {

    public interface SourceCallback {
        void onSource(String source);
    }

    // ------------------------------------------------------------------
    //  JS bridge available to every page as window.DevBrowser
    // ------------------------------------------------------------------
    public static class JsBridge {
        private final Tab tab;

        public JsBridge(Tab tab) {
            this.tab = tab;
        }

        @JavascriptInterface
        public String getTitle() {
            return tab.title == null ? "" : tab.title;
        }

        @JavascriptInterface
        public String getUrl() {
            return tab.url == null ? "" : tab.url;
        }

        @JavascriptInterface
        public String getVersion() {
            return "DevBrowser/1.1.0";
        }

        @JavascriptInterface
        public String getHost() {
            return com.devbrowser.app.util.U.hostOf(tab.url);
        }
    }

    /** Install the DevBrowser object on a page. */
    public static void installBridge(WebView web, Tab tab) {
        web.addJavascriptInterface(new JsBridge(tab), "DevBrowser");
    }

    /** Grab document HTML. Results arrive on the UI thread via callback. */
    public static void viewSource(WebView web, final SourceCallback cb) {
        web.evaluateJavascript(
                "(function(){" +
                        "try{var d=document.documentElement.outerHTML;" +
                        "var s=document.doctype?new XMLSerializer().serializeToString(document.doctype)+'\\n':'';" +
                        "var out=s+document.documentElement.outerHTML;" +
                        "return out;}catch(e){return '[source error] '+e.message;}" +
                        "})()",
                value -> cb.onSource(value == null ? "" : stripQuotes(value)));
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return unescapeJs(v);
    }

    private static String unescapeJs(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 < s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (Exception e) {
                                sb.append(n);
                            }
                        } else sb.append(n);
                        break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Render the WebView into an offscreen bitmap and save it as PNG. */
    public static File screenshot(WebView web, File dir, String name) {
        try {
            int w = web.getWidth();
            int h = web.getHeight();
            if (w <= 0 || h <= 0) return null;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            web.draw(canvas);
            File out = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            bmp.recycle();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** Evaluate an expression and log the result to the tab's console. */
    public static void evaluateAndLog(WebView web, Tab tab, String js) {
        web.evaluateJavascript(js, value -> {
            String v = stripQuotes(value == null ? "undefined" : value);
            tab.log("eval", v);
            if (tab.listener != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> tab.listener.onLog(tab));
            }
        });
    }
}
