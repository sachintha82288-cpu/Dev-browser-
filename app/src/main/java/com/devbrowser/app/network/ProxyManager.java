package com.devbrowser.app.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Best-effort WebView proxy override.
 *
 * The public androidx.webkit.ProxyController boundary is provided by the WebView
 * provider (SupportLibWebViewChromiumFactory.getProxyController(), whose methods
 * use the support-lib interface signatures). We reach it through the platform
 * WebViewFactory instead of depending on androidx, keeping the app dependency-free.
 *
 * Works on modern Google WebView (Chromium); on OEM WebView versions without the
 * support library the call fails gracefully and the UI reports the limitation.
 */
public class ProxyManager {

    private static final String TAG = "ProxyManager";
    public static final String TOR_HOST = "127.0.0.1";
    public static final int TOR_HTTP_PORT = 8118;

    public interface Result {
        void onResult(boolean ok, String detail);
    }

    /** Apply an HTTP/HTTPS proxy to the app's WebView. */
    public static void setProxy(String host, int port, Result result) {
        runVoid("setProxyOverride", new Runnable() {
            @Override
            public void run() {
                try {
                    Object adapter = getAdapter();
                    Method m = findMethod(adapter, "setProxyOverride", 4);
                    if (m == null) throw new NoSuchMethodException("setProxyOverride");
                    // proxyRules = {{scheme, "host:port"}, ...}
                    String[][] rules = new String[][]{
                            {"http", host + ":" + port},
                            {"https", host + ":" + port},
                    };
                    String[] bypass = {"<local>"};
                    m.invoke(adapter, rules, bypass, (Runnable) () -> {
                    }, (Executor) command -> command.run());
                    result.onResult(true, host + ":" + port);
                } catch (Throwable t) {
                    Log.w(TAG, "setProxy failed", t);
                    result.onResult(false, t.toString());
                }
            }
        });
    }

    /** Remove any WebView proxy override. */
    public static void clearProxy(Result result) {
        runVoid("clearProxyOverride", new Runnable() {
            @Override
            public void run() {
                try {
                    Object adapter = getAdapter();
                    Method m = findMethod(adapter, "clearProxyOverride", 2);
                    if (m == null) throw new NoSuchMethodException("clearProxyOverride");
                    m.invoke(adapter, (Runnable) () -> {
                    }, (Executor) command -> command.run());
                    result.onResult(true, "cleared");
                } catch (Throwable t) {
                    Log.w(TAG, "clearProxy failed", t);
                    result.onResult(false, t.toString());
                }
            }
        });
    }

    private static void runVoid(String what, Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }

    private static Object getAdapter() throws Exception {
        Class<?> factoryClass = Class.forName("android.webkit.WebViewFactory");
        Object provider = factoryClass.getMethod("getProvider").invoke(null);
        return provider.getClass().getMethod("getProxyController").invoke(provider);
    }

    private static Method findMethod(Object adapter, String name, int argc) {
        for (Method m : adapter.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        }
        return null;
    }
}
