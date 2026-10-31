package io.github.sachintha82288.devbrowser.debug

import org.json.JSONArray
import org.json.JSONObject

/** Parses the recorder's JSON payloads into [NetworkEntry] values. */
object NetworkParser {

    fun parseEntries(array: JSONArray?): List<NetworkEntry> {
        if (array == null) return emptyList()
        val out = ArrayList<NetworkEntry>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(parseEntry(obj))
        }
        return out
    }

    private fun parseEntry(obj: JSONObject): NetworkEntry {
        val timing = obj.optJSONObject("tm")
        return NetworkEntry(
            id = obj.optLong("i"),
            url = obj.optString("u"),
            method = obj.optString("m", "GET"),
            status = obj.optInt("s"),
            type = RequestType.from(obj.optString("ty")),
            size = obj.optLong("sz"),
            durationMs = obj.optLong("d"),
            startedAt = obj.optLong("t"),
            requestHeaders = parseHeaders(obj.optJSONArray("rh")),
            responseHeaders = parseHeaders(obj.optJSONArray("sh")),
            requestCookies = parseStrings(obj.optJSONArray("rc")),
            setCookies = parseStrings(obj.optJSONArray("sc")),
            body = obj.optString("b"),
            bodyTruncated = obj.optBoolean("bt"),
            mimeType = obj.optString("mt"),
            timing = NetTiming(
                dns = timing?.optLong("dns") ?: 0,
                connect = timing?.optLong("connect") ?: 0,
                tls = timing?.optLong("tls") ?: 0,
                ttfb = timing?.optLong("ttfb") ?: 0,
                download = timing?.optLong("dl") ?: 0,
            ),
            blocked = obj.optBoolean("bl"),
        )
    }

    private fun parseHeaders(array: JSONArray?): List<HeaderPair> {
        if (array == null) return emptyList()
        val out = ArrayList<HeaderPair>(array.length())
        for (i in 0 until array.length()) {
            val pair = array.optJSONArray(i) ?: continue
            out.add(HeaderPair(pair.optString(0), pair.optString(1)))
        }
        return out
    }

    private fun parseStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) out.add(value)
        }
        return out
    }

    fun parseWebSockets(array: JSONArray?): List<WebSocketRecord> {
        if (array == null) return emptyList()
        val out = ArrayList<WebSocketRecord>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val messages = ArrayList<WebSocketMessage>()
            val list = obj.optJSONArray("messages")
            if (list != null) {
                for (m in 0 until list.length()) {
                    val message = list.optJSONObject(m) ?: continue
                    messages.add(
                        WebSocketMessage(
                            direction = message.optString("d"),
                            payload = message.optString("m"),
                            timestamp = message.optLong("t"),
                        ),
                    )
                }
            }
            out.add(
                WebSocketRecord(
                    url = obj.optString("url"),
                    openedAt = obj.optLong("open"),
                    messages = messages,
                ),
            )
        }
        return out
    }
}
