package io.github.sachintha82288.devbrowser.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Stage B: remembered per-site permission decisions.
 *
 * The permission dialog answer is stored for the host so the same site is not
 * asked again. The security centre lists every stored decision and can revoke
 * one host at a time (or everything).
 */
class PermissionStore(context: Context) {

    private val prefs = context.getSharedPreferences("devbrowser_permissions", Context.MODE_PRIVATE)

    /** Bumped on every change so Compose readers recompose. */
    var version by mutableStateOf(0)
        private set

    private val decisions: MutableMap<String, MutableMap<String, Boolean>> = load()

    data class Entry(val host: String, val kind: String, val granted: Boolean)

    fun decision(host: String?, kind: String): Boolean? {
        val key = normalize(host) ?: return null
        return decisions[key]?.get(kind)
    }

    fun remember(host: String?, kind: String, granted: Boolean) {
        val key = normalize(host) ?: return
        val entry = decisions.getOrPut(key) { mutableMapOf() }
        entry[kind] = granted
        persist()
    }

    fun revoke(host: String?, kind: String) {
        val key = normalize(host) ?: return
        val entry = decisions[key] ?: return
        entry.remove(kind)
        if (entry.isEmpty()) decisions.remove(key)
        persist()
    }

    fun revokeHost(host: String?) {
        val key = normalize(host) ?: return
        decisions.remove(key)
        persist()
    }

    fun clearAll() {
        decisions.clear()
        persist()
    }

    fun entries(): List<Entry> =
        decisions.flatMap { (host, map) ->
            map.map { (kind, granted) -> Entry(host, kind, granted) }
        }.sortedWith(compareBy({ it.host }, { it.kind }))

    fun hosts(): List<String> = decisions.keys.sorted()

    private fun normalize(host: String?): String? {
        val value = host?.trim()?.lowercase() ?: return null
        return value.ifBlank { null }
    }

    private fun persist() {
        val root = JSONObject()
        decisions.forEach { (host, map) ->
            val inner = JSONObject()
            map.forEach { (kind, granted) -> inner.put(kind, granted) }
            root.put(host, inner)
        }
        prefs.edit().putString("decisions", root.toString()).apply()
        version++
    }

    private fun load(): MutableMap<String, MutableMap<String, Boolean>> {
        val result = LinkedHashMap<String, MutableMap<String, Boolean>>()
        val raw = prefs.getString("decisions", null) ?: return result
        try {
            val root = JSONObject(raw)
            root.keys().forEach { host ->
                val inner = root.optJSONObject(host) ?: return@forEach
                val map = LinkedHashMap<String, Boolean>()
                inner.keys().forEach { kind ->
                    map[kind] = inner.optBoolean(kind)
                }
                if (map.isNotEmpty()) result[host] = map
            }
        } catch (_: Exception) {
            return result
        }
        return result
    }
}
