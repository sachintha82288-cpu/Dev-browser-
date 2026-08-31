package com.devbrowser.app;

import android.app.Application;
import android.os.Environment;
import android.util.Log;

import com.devbrowser.app.util.Prefs;

import java.io.File;

/** Application entry: initialises preferences and the download folder. */
public class DevBrowserApp extends Application {

    private static final String TAG = "DevBrowser";
    private static DevBrowserApp instance;

    public static final String APP_ID = "com.devbrowser.app";
    public static final String FILE_AUTHORITY = APP_ID + ".files";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Prefs.init(this);
        ensureDownloadDir();
        Log.i(TAG, "DevBrowser started v1.1.0");
    }

    public static DevBrowserApp get() {
        return instance;
    }

    /** App-specific public download folder (no extra storage permissions needed). */
    public File downloadDir() {
        File dir = new File(getExternalFilesDir(null), "Downloads");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void ensureDownloadDir() {
        downloadDir();
    }

    /** Fallback stable status line for display. */
    public static String storageHint() {
        return Environment.getExternalStorageState();
    }
}
