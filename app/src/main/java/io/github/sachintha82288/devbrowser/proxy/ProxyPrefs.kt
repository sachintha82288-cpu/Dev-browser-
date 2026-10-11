package io.github.sachintha82288.devbrowser.proxy

import io.github.sachintha82288.devbrowser.data.AppSettings
import io.github.sachintha82288.devbrowser.data.DohMode
import io.github.sachintha82288.devbrowser.data.ProxyMode

/**
 * Maps DevBrowser proxy / DNS settings to the Firefox (Gecko) preference keys
 * that control the network stack.
 */
object ProxyPrefs {

    const val PREF_PROXY_TYPE = "network.proxy.type"
    const val PREF_HTTP_HOST = "network.proxy.http"
    const val PREF_HTTP_PORT = "network.proxy.http_port"
    const val PREF_SSL_HOST = "network.proxy.ssl"
    const val PREF_SSL_PORT = "network.proxy.ssl_port"
    const val PREF_SHARE_PROXY = "network.proxy.share_proxy_settings"
    const val PREF_SOCKS_HOST = "network.proxy.socks"
    const val PREF_SOCKS_PORT = "network.proxy.socks_port"
    const val PREF_SOCKS_VERSION = "network.proxy.socks_version"
    const val PREF_SOCKS_REMOTE_DNS = "network.proxy.socks_remote_dns"
    const val PREF_TRR_MODE = "network.trr.mode"
    const val PREF_TRR_URI = "network.trr.uri"

    // network.proxy.type values (nsIProtocolProxyService)
    private const val PROXY_TYPE_NONE = 0
    private const val PROXY_TYPE_MANUAL = 1
    private const val PROXY_TYPE_SYSTEM = 5

    private const val DEFAULT_DOH_RESOLVER = "https://mozilla.cloudflare-dns.com/dns-query"

    const val TOR_SOCKS_HOST = "127.0.0.1"
    const val TOR_SOCKS_PORT = 9050

    /** Preference map applied at engine startup (written to the GeckoView YAML config). */
    fun startupPrefs(settings: AppSettings): Map<String, Any> = buildMap {
        putAll(proxyPrefs(settings))
        putAll(dohPrefs(settings))
    }

    fun proxyPrefs(settings: AppSettings): Map<String, Any> = buildMap {
        when (settings.proxyMode) {
            ProxyMode.DIRECT -> put(PREF_PROXY_TYPE, PROXY_TYPE_NONE)
            ProxyMode.SYSTEM -> put(PREF_PROXY_TYPE, PROXY_TYPE_SYSTEM)
            ProxyMode.TOR -> {
                put(PREF_PROXY_TYPE, PROXY_TYPE_MANUAL)
                put(PREF_SOCKS_HOST, TOR_SOCKS_HOST)
                put(PREF_SOCKS_PORT, TOR_SOCKS_PORT)
                put(PREF_SOCKS_VERSION, 5)
                put(PREF_SOCKS_REMOTE_DNS, true)
            }
            ProxyMode.MANUAL -> {
                val port = settings.proxyPort.toIntOrNull() ?: 0
                put(PREF_PROXY_TYPE, PROXY_TYPE_MANUAL)
                if (settings.proxyUseSocks) {
                    put(PREF_SOCKS_HOST, settings.proxyHost)
                    put(PREF_SOCKS_PORT, port)
                    put(PREF_SOCKS_VERSION, settings.proxySocksVersion)
                    put(PREF_SOCKS_REMOTE_DNS, settings.proxyRemoteDns)
                } else {
                    put(PREF_HTTP_HOST, settings.proxyHost)
                    put(PREF_HTTP_PORT, port)
                    put(PREF_SSL_HOST, settings.proxyHost)
                    put(PREF_SSL_PORT, port)
                    put(PREF_SHARE_PROXY, true)
                }
            }
        }
    }

    fun dohPrefs(settings: AppSettings): Map<String, Any> = buildMap {
        when (settings.dohMode) {
            DohMode.OFF -> put(PREF_TRR_MODE, 5) // 5 = explicitly disabled
            DohMode.DEFAULT -> {
                put(PREF_TRR_MODE, 2) // TRR first, native fallback
                put(PREF_TRR_URI, DEFAULT_DOH_RESOLVER)
            }
            DohMode.CUSTOM -> {
                put(PREF_TRR_MODE, 2)
                put(PREF_TRR_URI, settings.dohUrl.ifBlank { DEFAULT_DOH_RESOLVER })
            }
        }
    }
}
