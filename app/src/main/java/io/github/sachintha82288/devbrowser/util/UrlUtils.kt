package io.github.sachintha82288.devbrowser.util

import android.net.Uri
import java.util.Locale

/** URL / search-input heuristics for the address bar. */
object UrlUtils {

    private val COMMON_TLDS = setOf(
        "com", "org", "net", "io", "dev", "lk", "co", "uk", "edu", "gov", "app",
        "info", "xyz", "me", "sh", "so", "ai", "js", "to", "gg", "gl", "is",
    )

    private val SCHEMES = listOf("http://", "https://", "file://", "view-source:", "about:", "data:", "blob:")

    private val EXTERNAL_SCHEMES = setOf("tel", "mailto", "sms", "geo", "market", "intent")

    private const val LOCALHOST = "localhost"
    private const val IPV6_LOOPBACK = "[::1]"

    // android.util.Patterns.IP_ADDRESS is deprecated, so the address bar does
    // its own IPv4 check instead.
    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** True when the string should be loaded directly instead of searched for. */
    fun looksLikeUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.US)
        if (SCHEMES.any { lower.startsWith(it) }) return true
        if (trimmed.contains(' ') || trimmed.contains('\n')) return false

        val hostPart = inputHost(trimmed)
        // Development targets carry no dot to key off, so they have to be
        // recognised before the dotted-host heuristic below: "localhost:8080",
        // a bracketed IPv6 literal such as "[::1]:3000", or "192.168.1.4:8000".
        if (isLocalTarget(hostPart)) return true
        if (trimmed.startsWith("[") && trimmed.contains(']')) return true
        if (isIpv4Address(hostPart)) return true

        if (!trimmed.contains('.')) return false
        val tld = hostPart.substringAfterLast('.', "")
        return tld.length in 2..24 && (COMMON_TLDS.contains(tld) || hostPart.contains('.'))
    }

    /** Normalize raw user input into a loadable URL. */
    fun toUrl(input: String): String {
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.US)
        if (SCHEMES.any { lower.startsWith(it) }) return trimmed
        if (!looksLikeUrl(trimmed)) return trimmed
        // A dev server almost never has a TLS certificate, so the hosts a phone
        // gets pointed at (localhost, loopback, the private ranges) are loaded
        // over plain http; everywhere else stays https.
        val scheme = if (isLocalTarget(inputHost(trimmed))) "http://" else "https://"
        return "$scheme$trimmed"
    }

    /** Host part of raw address-bar input: no port, no path, no scheme. */
    private fun inputHost(input: String): String {
        val trimmed = input.trim()
        // A bracketed IPv6 literal keeps its brackets: the host itself has colons.
        if (trimmed.startsWith("[")) return trimmed.substringBefore(']') + "]"
        return trimmed.substringBefore('/').substringBefore(':')
    }

    private fun ipv4Octets(host: String): List<Int>? =
        IPV4.matchEntire(host)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: -1 }

    private fun isIpv4Address(host: String): Boolean =
        ipv4Octets(host)?.all { it in 0..255 } == true

    /** localhost, loopback, link-local and the RFC 1918 private ranges. */
    private fun isLocalTarget(host: String): Boolean {
        if (host.equals(LOCALHOST, ignoreCase = true) || host == IPV6_LOOPBACK) return true
        val octets = ipv4Octets(host) ?: return false
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (first, second) = octets[0] to octets[1]
        return first == 127 || first == 10 || first == 0 ||
            (first == 192 && second == 168) ||
            (first == 172 && second in 16..31) ||
            (first == 169 && second == 254)
    }

    fun searchUrl(query: String, template: String): String =
        template.replace("%s", Uri.encode(query))

    /** Host name for display, or null for non-http(s) URLs. */
    fun hostOf(url: String?): String? {
        if (url == null) return null
        return try {
            val uri = Uri.parse(url)
            if (uri.scheme == "http" || uri.scheme == "https") uri.host else null
        } catch (_: Exception) {
            null
        }
    }

    fun isExternalScheme(url: String): Boolean {
        val scheme = try {
            Uri.parse(url).scheme
        } catch (_: Exception) {
            null
        } ?: return false
        return EXTERNAL_SCHEMES.contains(scheme.lowercase(Locale.US))
    }

    /** Derive a readable page title from a URL. */
    fun titleFromUrl(url: String?): String {
        if (url == null) return ""
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return url
            val path = uri.path?.trim('/')
            if (path.isNullOrEmpty()) host else "${host}/…/${path.takeLast(30)}"
        } catch (_: Exception) {
            url
        }
    }
}
