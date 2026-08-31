package com.devbrowser.app;

/**
 * Hand-off slot for large strings (page source) between activities without
 * serialising megabytes through Intent extras.
 */
public class VolatileSource {
    private static String source = "";

    public static void set(String s) {
        source = s == null ? "" : s;
    }

    public static String get() {
        return source;
    }
}
