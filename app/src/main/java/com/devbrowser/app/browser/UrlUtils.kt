package com.devbrowser.app.browser

import android.net.Uri
import java.net.URLEncoder

object UrlUtils {

    private val SCHEMES = listOf(
        "http://", "https://", "about:", "file://", "data:", "view-source:",
        "resource://", "chrome://", "javascript:", "ftp://"
    )

    /** Turns whatever the user typed into a loadable URI, or a search query. */
    fun normalize(input: String, searchTemplate: String): String {
        val text = input.trim()
        if (text.isEmpty()) return "about:blank"
        if (SCHEMES.any { text.startsWith(it, ignoreCase = true) }) return text
        if (text.startsWith("localhost") || text.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$"))) {
            return "http://$text"
        }
        if (looksLikeDomain(text)) return "https://$text"
        return searchTemplate.replace("%s", URLEncoder.encode(text, "UTF-8"))
    }

    private fun looksLikeDomain(text: String): Boolean {
        if (text.contains(' ')) return false
        val host = text.substringBefore('/').substringBefore('?')
        if (host.endsWith(".onion")) return true
        return host.contains('.') && host.substringAfterLast('.').length >= 2
    }

    fun host(url: String): String = try {
        Uri.parse(url).host ?: url
    } catch (t: Throwable) {
        url
    }

    fun baseDomain(url: String): String {
        val h = host(url)
        val parts = h.split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else h
    }
}
