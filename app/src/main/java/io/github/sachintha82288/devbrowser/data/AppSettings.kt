package io.github.sachintha82288.devbrowser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class SearchEngine(val label: String, val queryTemplate: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s"),
    WIKIPEDIA("Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search=%s"),
}

enum class TrackingProtectionLevel { NONE, STANDARD, STRICT }

enum class DohMode { OFF, DEFAULT, CUSTOM }

enum class ProxyMode { DIRECT, SYSTEM, MANUAL, TOR }

/**
 * Application settings persisted in [SharedPreferences], exposed as Compose state so
 * the UI updates immediately when a value changes.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("devbrowser_settings", Context.MODE_PRIVATE)

    // --- General ---------------------------------------------------------

    var searchEngine: SearchEngine
        get() = _searchEngine
        set(value) { _searchEngine = value; prefs.edit().putString("search_engine", value.name).apply() }

    var restoreTabs: Boolean
        get() = _restoreTabs
        set(value) { _restoreTabs = value; prefs.edit().putBoolean("restore_tabs", value).apply() }

    // --- Privacy & security ----------------------------------------------

    var trackingProtection: TrackingProtectionLevel
        get() = _trackingProtection
        set(value) { _trackingProtection = value; prefs.edit().putString("tracking_protection", value.name).apply() }

    var httpsOnly: Boolean
        get() = _httpsOnly
        set(value) { _httpsOnly = value; prefs.edit().putBoolean("https_only", value).apply() }

    var blockPopups: Boolean
        get() = _blockPopups
        set(value) { _blockPopups = value; prefs.edit().putBoolean("block_popups", value).apply() }

    var javascriptEnabled: Boolean
        get() = _javascriptEnabled
        set(value) { _javascriptEnabled = value; prefs.edit().putBoolean("javascript", value).apply() }

    var webFontsEnabled: Boolean
        get() = _webFonts
        set(value) { _webFonts = value; prefs.edit().putBoolean("web_fonts", value).apply() }

    var dohMode: DohMode
        get() = _dohMode
        set(value) { _dohMode = value; prefs.edit().putString("doh_mode", value.name).apply() }

    var dohUrl: String
        get() = _dohUrl
        set(value) { _dohUrl = value; prefs.edit().putString("doh_url", value).apply() }

    // --- Developer --------------------------------------------------------

    var remoteDebugging: Boolean
        get() = _remoteDebugging
        set(value) { _remoteDebugging = value; prefs.edit().putBoolean("remote_debugging", value).apply() }

    var consoleOutput: Boolean
        get() = _consoleOutput
        set(value) { _consoleOutput = value; prefs.edit().putBoolean("console_output", value).apply() }

    var aboutConfig: Boolean
        get() = _aboutConfig
        set(value) { _aboutConfig = value; prefs.edit().putBoolean("about_config", value).apply() }

    /**
     * Writes `xpinstall.signatures.required=false` (and friends) into the engine
     * startup config so self-built / unsigned .xpi files can be installed.
     */
    var allowUnsignedExtensions: Boolean
        get() = _allowUnsignedExtensions
        set(value) {
            _allowUnsignedExtensions = value
            prefs.edit().putBoolean("allow_unsigned_extensions", value).apply()
        }

    // --- Appearance -------------------------------------------------------

    var amoledBlack: Boolean
        get() = _amoledBlack
        set(value) { _amoledBlack = value; prefs.edit().putBoolean("amoled_black", value).apply() }

    // --- Proxy ------------------------------------------------------------

    var proxyMode: ProxyMode
        get() = _proxyMode
        set(value) { _proxyMode = value; prefs.edit().putString("proxy_mode", value.name).apply() }

    var proxyHost: String
        get() = _proxyHost
        set(value) { _proxyHost = value; prefs.edit().putString("proxy_host", value).apply() }

    var proxyPort: String
        get() = _proxyPort
        set(value) { _proxyPort = value; prefs.edit().putString("proxy_port", value).apply() }

    /** For MANUAL mode: true = SOCKS, false = HTTP. */
    var proxyUseSocks: Boolean
        get() = _proxyUseSocks
        set(value) { _proxyUseSocks = value; prefs.edit().putBoolean("proxy_use_socks", value).apply() }

    var proxySocksVersion: Int
        get() = _proxySocksVersion
        set(value) { _proxySocksVersion = value; prefs.edit().putInt("proxy_socks_version", value).apply() }

    var proxyRemoteDns: Boolean
        get() = _proxyRemoteDns
        set(value) { _proxyRemoteDns = value; prefs.edit().putBoolean("proxy_remote_dns", value).apply() }

    private var _searchEngine by mutableStateOf(enumPref("search_engine", SearchEngine.DUCKDUCKGO, SearchEngine.entries))
    private var _restoreTabs by mutableStateOf(prefs.getBoolean("restore_tabs", true))

    private var _trackingProtection by mutableStateOf(enumPref("tracking_protection", TrackingProtectionLevel.STANDARD, TrackingProtectionLevel.entries))
    private var _httpsOnly by mutableStateOf(prefs.getBoolean("https_only", false))
    private var _blockPopups by mutableStateOf(prefs.getBoolean("block_popups", true))
    private var _javascriptEnabled by mutableStateOf(prefs.getBoolean("javascript", true))
    private var _webFonts by mutableStateOf(prefs.getBoolean("web_fonts", true))
    private var _dohMode by mutableStateOf(enumPref("doh_mode", DohMode.OFF, DohMode.entries))
    private var _dohUrl by mutableStateOf(prefs.getString("doh_url", "") ?: "")

    private var _remoteDebugging by mutableStateOf(prefs.getBoolean("remote_debugging", false))
    private var _consoleOutput by mutableStateOf(prefs.getBoolean("console_output", true))
    private var _aboutConfig by mutableStateOf(prefs.getBoolean("about_config", true))
    private var _allowUnsignedExtensions by mutableStateOf(prefs.getBoolean("allow_unsigned_extensions", true))

    private var _amoledBlack by mutableStateOf(prefs.getBoolean("amoled_black", false))

    private var _proxyMode by mutableStateOf(enumPref("proxy_mode", ProxyMode.DIRECT, ProxyMode.entries))
    private var _proxyHost by mutableStateOf(prefs.getString("proxy_host", "") ?: "")
    private var _proxyPort by mutableStateOf(prefs.getString("proxy_port", "") ?: "")
    private var _proxyUseSocks by mutableStateOf(prefs.getBoolean("proxy_use_socks", true))
    private var _proxySocksVersion by mutableStateOf(prefs.getInt("proxy_socks_version", 5))
    private var _proxyRemoteDns by mutableStateOf(prefs.getBoolean("proxy_remote_dns", true))

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T, values: Collection<T>): T {
        val name = prefs.getString(key, null) ?: return default
        return values.firstOrNull { it.name == name } ?: default
    }
}
