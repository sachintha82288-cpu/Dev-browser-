package com.devbrowser.app.network;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

/** Launches/detects Orbot and offers the standard local Tor HTTP proxy preset. */
public class TorManager {

    public static final String ORBOT_PACKAGE = "org.torproject.android";
    public static final String TOR_PROXY = "127.0.0.1:8118";

    public static boolean isOrbotInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(ORBOT_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Opens Orbot if installed, otherwise opens its F-Droid page. Returns true if launched. */
    public static boolean openOrbot(Context ctx) {
        try {
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ORBOT_PACKAGE);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(launch);
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            Intent store = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://f-droid.org/packages/org.torproject.android/"));
            store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(store);
        } catch (Exception ignored) {
        }
        return false;
    }
}
