package io.github.sachintha82288.devbrowser.util

import android.net.Uri
import android.util.Patterns
import java.util.Locale

/** URL / search-input heuristics for the address bar. */
object UrlUtils {

    private val COMMON_TLDS = setOf(
        "com", "org", "net", "io", "dev", "lk", "co", "uk", "edu", "gov", "app",
        "info", "xyz", "me", "sh", "so", "ai", "js", "to", "gg", "gl", "is",
    )

    private val SCHEMES = listOf("http://", "https://", "file://", "view-source:", "about:", "data:", "blob:")

    private val EXTERNAL_SCHEMES = setOf("tel", "mailto", "sms", "geo", "market", "intent")

    /** True when the string should be loaded directly instead of searched for. */
    fun looksLikeUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.US)
        if (SCHEMES.any { lower.startsWith(it) }) return true
        if (trimmed.contains(' ') || trimmed.contains('\n')) return false
        if (!trimmed.contains('.')) return false
        // e.g. "localhost:8080" or "192.168.1.4:8000"
        if (lower.startsWith("localhost")) return true
        val hostPart = trimmed.substringBefore('/').substringBefore(':')
        if (Patterns.IP_ADDRESS.matcher(hostPart).matches()) return true
        val tld = hostPart.substringAfterLast('.', "")
        return tld.length in 2..24 && (COMMON_TLDS.contains(tld) || hostPart.contains('.'))
    }

    /** Normalize raw user input into a loadable URL. */
    fun toUrl(input: String): String {
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.US)
        if (SCHEMES.any { lower.startsWith(it) }) return trimmed
        if (looksLikeUrl(trimmed)) return "https://$trimmed"
        return trimmed
    }

    /**
     * Stage A: removes common campaign/click identifiers before a link is
     * opened. Unknown query parameters and fragments are preserved.
     */
    fun stripTrackingParams(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return url
            if (scheme != "http" && scheme != "https") return url
            val names = uri.queryParameterNames
            if (names.isEmpty()) return url
            val dropped = names.filter { isTrackingParam(it) }
            if (dropped.isEmpty()) return url
            val builder = uri.buildUpon().clearQuery()
            names.forEach { name ->
                if (isTrackingParam(name)) return@forEach
                uri.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
            }
            builder.build().toString()
        } catch (_: Exception) {
            url
        }
    }

    private val TRACKING_PARAMS = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "twclid",
        "igshid", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "vero_id", "wickedid",
        "oly_anon_id", "oly_enc_id", "rb_clickid", "s_cid", "mkt_tok", "trk",
        "sc_cid", "ref_src", "ref_url", "cmpid", "campaign_id",
    )

    private fun isTrackingParam(name: String): Boolean {
        val key = name.lowercase(Locale.US)
        return key.startsWith("utm_") || TRACKING_PARAMS.contains(key)
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
