package com.devbrowser.app.proxy

import android.content.Context
import android.content.Intent
import android.util.Log
import com.devbrowser.app.browser.GeckoPrefs
import com.devbrowser.app.data.Prefs
import org.mozilla.geckoview.GeckoRuntime

/**
 * Applies proxy configuration to the Gecko networking stack.
 *
 * network.proxy.type values:
 *   0 = direct, 1 = manual, 4 = auto-detect, 5 = system
 */
object ProxyController {

    private const val TAG = "ProxyController"

    /** Orbot's default SOCKS5 listener. */
    const val TOR_HOST = "127.0.0.1"
    const val TOR_SOCKS_PORT = 9050
    const val TOR_HTTP_PORT = 8118

    fun apply(runtime: GeckoRuntime) {
        when (Prefs.proxyMode) {
            Prefs.MODE_NONE -> direct(runtime)
            Prefs.MODE_TOR -> socks(runtime, TOR_HOST, TOR_SOCKS_PORT, dns = true)
            Prefs.MODE_MANUAL -> {
                if (Prefs.proxyType == "socks") {
                    socks(runtime, Prefs.proxyHost, Prefs.proxyPort, Prefs.proxyDns)
                } else {
                    http(runtime, Prefs.proxyHost, Prefs.proxyPort)
                }
            }
        }
    }

    private fun direct(runtime: GeckoRuntime) {
        GeckoPrefs.set(runtime, mapOf("network.proxy.type" to 0))
        Log.i(TAG, "Proxy disabled (direct connection)")
    }

    private fun socks(runtime: GeckoRuntime, host: String, port: Int, dns: Boolean) {
        GeckoPrefs.set(
            runtime,
            mapOf(
                "network.proxy.type" to 1,
                "network.proxy.socks" to host,
                "network.proxy.socks_port" to port,
                "network.proxy.socks_version" to 5,
                "network.proxy.socks_remote_dns" to dns,
                "network.proxy.allow_hijacking_localhost" to true,
                "network.dns.blockDotOnion" to false,
                // Never bypass the proxy, otherwise local addresses leak.
                "network.proxy.no_proxies_on" to ""
            )
        )
        Log.i(TAG, "SOCKS5 proxy -> $host:$port (remote dns=$dns)")
    }

    private fun http(runtime: GeckoRuntime, host: String, port: Int) {
        GeckoPrefs.set(
            runtime,
            mapOf(
                "network.proxy.type" to 1,
                "network.proxy.http" to host,
                "network.proxy.http_port" to port,
                "network.proxy.ssl" to host,
                "network.proxy.ssl_port" to port,
                "network.proxy.share_proxy_settings" to true,
                "network.proxy.no_proxies_on" to ""
            )
        )
        Log.i(TAG, "HTTP proxy -> $host:$port")
    }

    fun describe(): String = when (Prefs.proxyMode) {
        Prefs.MODE_TOR -> "Tor (SOCKS5 $TOR_HOST:$TOR_SOCKS_PORT)"
        Prefs.MODE_MANUAL -> "${Prefs.proxyType.uppercase()} ${Prefs.proxyHost}:${Prefs.proxyPort}"
        else -> "Direct"
    }

    fun isTor(): Boolean = Prefs.proxyMode == Prefs.MODE_TOR

    /** Asks Orbot (if installed) to start its Tor service. */
    fun requestOrbotStart(context: Context) {
        try {
            val intent = Intent("org.torproject.android.intent.action.START").apply {
                setPackage("org.torproject.android")
                putExtra("org.torproject.android.intent.extra.PACKAGE_NAME", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.sendBroadcast(intent)
            context.startService(
                Intent("org.torproject.android.intent.action.START")
                    .setPackage("org.torproject.android")
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Orbot not available: ${t.message}")
        }
    }

    fun isOrbotInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("org.torproject.android", 0)
        true
    } catch (t: Throwable) {
        false
    }
}
