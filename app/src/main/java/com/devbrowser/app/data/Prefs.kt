package com.devbrowser.app.data

import android.content.Context
import android.content.SharedPreferences

/** Central, typed access to persisted settings. */
object Prefs {

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("devbrowser", Context.MODE_PRIVATE)
    }

    var homePage: String
        get() = sp.getString(KEY_HOME, "https://duckduckgo.com")!!
        set(v) = sp.edit().putString(KEY_HOME, v).apply()

    var searchEngine: String
        get() = sp.getString(KEY_SEARCH, "https://duckduckgo.com/?q=%s")!!
        set(v) = sp.edit().putString(KEY_SEARCH, v).apply()

    /** none | manual | tor */
    var proxyMode: String
        get() = sp.getString(KEY_PROXY_MODE, MODE_NONE)!!
        set(v) = sp.edit().putString(KEY_PROXY_MODE, v).apply()

    /** socks | http */
    var proxyType: String
        get() = sp.getString(KEY_PROXY_TYPE, "socks")!!
        set(v) = sp.edit().putString(KEY_PROXY_TYPE, v).apply()

    var proxyHost: String
        get() = sp.getString(KEY_PROXY_HOST, "127.0.0.1")!!
        set(v) = sp.edit().putString(KEY_PROXY_HOST, v).apply()

    var proxyPort: Int
        get() = sp.getInt(KEY_PROXY_PORT, 9050)
        set(v) = sp.edit().putInt(KEY_PROXY_PORT, v).apply()

    /** Route DNS through the SOCKS proxy (prevents DNS leaks). */
    var proxyDns: Boolean
        get() = sp.getBoolean(KEY_PROXY_DNS, true)
        set(v) = sp.edit().putBoolean(KEY_PROXY_DNS, v).apply()

    var desktopMode: Boolean
        get() = sp.getBoolean(KEY_DESKTOP, false)
        set(v) = sp.edit().putBoolean(KEY_DESKTOP, v).apply()

    var javascriptEnabled: Boolean
        get() = sp.getBoolean(KEY_JS, true)
        set(v) = sp.edit().putBoolean(KEY_JS, v).apply()

    var remoteDebugging: Boolean
        get() = sp.getBoolean(KEY_REMOTE_DEBUG, false)
        set(v) = sp.edit().putBoolean(KEY_REMOTE_DEBUG, v).apply()

    var trackingProtection: Boolean
        get() = sp.getBoolean(KEY_TP, true)
        set(v) = sp.edit().putBoolean(KEY_TP, v).apply()

    var customUserAgent: String
        get() = sp.getString(KEY_UA, "")!!
        set(v) = sp.edit().putString(KEY_UA, v).apply()

    var accentColor: Int
        get() = sp.getInt(KEY_ACCENT, 0xFFFF1B4B.toInt())
        set(v) = sp.edit().putInt(KEY_ACCENT, v).apply()

    var installedExtensions: Set<String>
        get() = sp.getStringSet(KEY_EXT, emptySet())!!
        set(v) = sp.edit().putStringSet(KEY_EXT, v).apply()

    fun bookmarks(): List<String> =
        sp.getString(KEY_BOOKMARKS, "")!!.split("\n").filter { it.isNotBlank() }

    fun addBookmark(url: String) {
        val list = bookmarks().toMutableList()
        if (!list.contains(url)) list.add(url)
        sp.edit().putString(KEY_BOOKMARKS, list.joinToString("\n")).apply()
    }

    fun removeBookmark(url: String) {
        sp.edit().putString(KEY_BOOKMARKS, bookmarks().filter { it != url }.joinToString("\n")).apply()
    }

    const val MODE_NONE = "none"
    const val MODE_MANUAL = "manual"
    const val MODE_TOR = "tor"

    private const val KEY_HOME = "home_page"
    private const val KEY_SEARCH = "search_engine"
    private const val KEY_PROXY_MODE = "proxy_mode"
    private const val KEY_PROXY_TYPE = "proxy_type"
    private const val KEY_PROXY_HOST = "proxy_host"
    private const val KEY_PROXY_PORT = "proxy_port"
    private const val KEY_PROXY_DNS = "proxy_dns"
    private const val KEY_DESKTOP = "desktop_mode"
    private const val KEY_JS = "javascript"
    private const val KEY_REMOTE_DEBUG = "remote_debugging"
    private const val KEY_TP = "tracking_protection"
    private const val KEY_UA = "user_agent"
    private const val KEY_ACCENT = "accent_color"
    private const val KEY_EXT = "extensions"
    private const val KEY_BOOKMARKS = "bookmarks"
}
