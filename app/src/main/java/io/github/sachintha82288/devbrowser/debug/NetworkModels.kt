package io.github.sachintha82288.devbrowser.debug

/** Request categories used by the filter row (feature 12). */
enum class RequestType(val js: String) {
    DOC("doc"),
    CSS("css"),
    JS("js"),
    XHR("xhr"),
    FETCH("fetch"),
    IMG("img"),
    MEDIA("media"),
    FONT("font"),
    WS("ws"),
    OTHER("other"),
    ;

    companion object {
        fun from(raw: String?): RequestType =
            entries.firstOrNull { it.js == raw } ?: OTHER
    }
}

data class HeaderPair(val name: String, val value: String)

/** Resource-timing phases in milliseconds (feature 16). */
data class NetTiming(
    val dns: Long = 0,
    val connect: Long = 0,
    val tls: Long = 0,
    val ttfb: Long = 0,
    val download: Long = 0,
) {
    val total: Long get() = dns + connect + tls + ttfb + download
    val isEmpty: Boolean get() = total <= 0
}

/** One network request observed in the current tab. */
data class NetworkEntry(
    val id: Long,
    val url: String,
    val method: String,
    val status: Int,
    val type: RequestType,
    val size: Long,
    val durationMs: Long,
    val startedAt: Long,
    val requestHeaders: List<HeaderPair> = emptyList(),
    val responseHeaders: List<HeaderPair> = emptyList(),
    val requestCookies: List<String> = emptyList(),
    val setCookies: List<String> = emptyList(),
    val body: String = "",
    val bodyTruncated: Boolean = false,
    val mimeType: String = "",
    val timing: NetTiming = NetTiming(),
    val blocked: Boolean = false,
) {
    val host: String
        get() = url.substringAfter("://").substringBefore('/').substringBefore('?')

    val path: String
        get() {
            val afterHost = url.substringAfter("://", "")
            val slash = afterHost.indexOf('/')
            return if (slash > 0) afterHost.substring(slash) else "/"
        }

    val isFailed: Boolean get() = blocked || status == 0 || status >= 400

    /** Pretty JSON body when the response parses as JSON (feature 21). */
    fun prettyJson(): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        return try {
            val value: Any = if (trimmed.startsWith("{")) {
                org.json.JSONObject(trimmed)
            } else {
                org.json.JSONArray(trimmed)
            }
            value.toString(2)
        } catch (_: Exception) {
            null
        }
    }

    val isJson: Boolean get() = prettyJson() != null

    /** Feature 19: a copyable cURL command. */
    fun asCurl(): String {
        val builder = StringBuilder("curl -X $method '").append(url).append("'")
        requestHeaders.forEach { header ->
            builder.append(" \\\n  -H '").append(header.name).append(": ")
                .append(header.value.replace("'", "'\\''")).append("'")
        }
        if (requestCookies.isNotEmpty()) {
            builder.append(" \\\n  -H 'Cookie: ")
                .append(requestCookies.joinToString("; ").replace("'", "'\\''")).append("'")
        }
        return builder.toString()
    }

    /** Feature 20: a copyable fetch() snippet. */
    fun asFetch(): String {
        val headerLines = requestHeaders.joinToString(",\n    ") { header ->
            "      '${header.name}': '${header.value.replace("'", "\\'")}'"
        }
        val headers = if (requestHeaders.isEmpty()) "" else ",\n    headers: {\n$headerLines\n    }"
        return "fetch('${url.replace("'", "\\'")}', {\n  method: '$method'$headers\n})" +
            "\n  .then(r => r.text())\n  .then(console.log);"
    }
}

/** Live WebSocket record for feature 100. */
data class WebSocketRecord(
    val url: String,
    val openedAt: Long,
    val messages: List<WebSocketMessage>,
)

data class WebSocketMessage(
    val direction: String,
    val payload: String,
    val timestamp: Long,
)
