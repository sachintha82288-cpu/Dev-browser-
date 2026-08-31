package com.devbrowser.app.dl;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.devbrowser.app.DevBrowserApp;
import com.devbrowser.app.model.DownloadItem;
import com.devbrowser.app.network.ProxyManager;
import com.devbrowser.app.network.TorManager;
import com.devbrowser.app.util.Prefs;
import com.devbrowser.app.util.U;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small, self-contained download engine (no DownloadManager permission dance).
 * Streams into the app's Downloads folder so APK installs work everywhere,
 * and honours the configured proxy (incl. the Tor preset).
 */
public class Downloads {

    private static final String TAG = "Downloads";
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final List<DownloadItem> active = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Listener {
        void onChanged();
    }

    private static Listener listener;

    public static void setListener(Listener l) {
        listener = l;
    }

    public static List<DownloadItem> active() {
        synchronized (active) {
            return new ArrayList<>(active);
        }
    }

    /** Scan the download folder for finished files. */
    public static List<DownloadItem> files(Context c) {
        List<DownloadItem> out = new ArrayList<>();
        File dir = DevBrowserApp.get().downloadDir();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) out.add(DownloadItem.done(f));
            }
        }
        out.sort((a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    /** Enqueue a download for the given URL. */
    public static void enqueue(Context context, String url, String suggestedName) {
        final DownloadItem item = new DownloadItem(url, suggestedName);
        synchronized (active) {
            active.add(item);
        }
        notifyChanged();
        POOL.execute(() -> run(context, item));
    }

    private static void run(Context context, DownloadItem item) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(item.url);
            Proxy proxy = Proxy.NO_PROXY;
            if (Prefs.proxyEnabled()) {
                proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(Prefs.proxyHost(), Prefs.proxyPort()));
            }
            conn = (HttpURLConnection) u.openConnection(proxy);
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) DevBrowser/1.0");
            Log.d(TAG, item.url);
            int code = conn.getResponseCode();
            item.state = DownloadItem.STATE_DOWNLOADING;
            item.total = conn.getContentLengthLong();
            String name = item.name;
            if (name == null || name.isEmpty()) name = U.fileNameFromPath(conn.getURL().toString());
            File target = new File(DevBrowserApp.get().downloadDir(), name);
            item.path = target.getAbsolutePath();
            if (code < 400) {
                byte[] buf = new byte[32 * 1024];
                try (InputStream in = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(target)) {
                    long done = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        done += n;
                        item.done = done;
                    }
                }
                item.state = DownloadItem.STATE_DONE;
                item.time = System.currentTimeMillis();
            } else {
                item.state = DownloadItem.STATE_ERROR;
                item.error = "HTTP " + code;
            }
        } catch (Exception e) {
            Log.w(TAG, "download failed", e);
            item.state = DownloadItem.STATE_ERROR;
            item.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (item.path != null) new File(item.path).delete();
        } finally {
            if (conn != null) conn.disconnect();
        }
        synchronized (active) {
            active.remove(item);
        }
        notifyChanged();
    }

    private static void notifyChanged() {
        MAIN.post(() -> {
            if (listener != null) listener.onChanged();
        });
    }
}
