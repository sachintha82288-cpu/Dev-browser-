package io.github.sachintha82288.devbrowser.debug

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persistent debug-suite data: saved snippets, the JS execution history, the
 * per-site request blocklist and the per-site "block JavaScript" switches.
 *
 * Stored as JSON in SharedPreferences so no extra dependency is needed.
 */
class DebugStore(context: Context) {

    private val prefs = context.getSharedPreferences("devbrowser_debug", Context.MODE_PRIVATE)

    // --- Snippet library (feature 40 / 41) ------------------------------

    var snippets by mutableStateOf(loadSnippets())
        private set

    fun saveSnippet(name: String, code: String, urlPattern: String, runOnLoad: Boolean): JsSnippet {
        val snippet = JsSnippet(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Snippet" },
            code = code,
            urlPattern = urlPattern.trim(),
            runOnLoad = runOnLoad,
        )
        snippets = snippets + snippet
        persistSnippets()
        return snippet
    }

    fun updateSnippet(snippet: JsSnippet) {
        snippets = snippets.map { if (it.id == snippet.id) snippet else it }
        persistSnippets()
    }

    fun deleteSnippet(id: String) {
        snippets = snippets.filterNot { it.id == id }
        persistSnippets()
    }

    /** Snippets whose URL glob matches [url] and that are marked auto-run. */
    fun snippetsForUrl(url: String?): List<JsSnippet> {
        if (url == null) return emptyList()
        return snippets.filter { it.runOnLoad && it.urlPattern.isNotBlank() && globMatches(it.urlPattern, url) }
    }

    // --- JS execution history (feature 38) ------------------------------

    var jsHistory by mutableStateOf(loadJsHistory())
        private set

    fun recordEvaluation(code: String, output: JsOutput) {
        if (code.isBlank()) return
        val trimmed = code.trim()
        val entry = JsEvaluation(trimmed, output, System.currentTimeMillis())
        jsHistory = (listOf(entry) + jsHistory.filterNot { it.code == trimmed }).take(MAX_JS_HISTORY)
        persistJsHistory()
    }

    fun clearJsHistory() {
        jsHistory = emptyList()
        persistJsHistory()
    }

    // --- Request blocklist (feature 22) ---------------------------------

    var blockedRequestTargets by mutableStateOf(loadStringSet("blocked_requests"))
        private set

    fun blockRequestTarget(target: String) {
        if (target.isBlank()) return
        blockedRequestTargets = blockedRequestTargets + target
        persistStringSet("blocked_requests", blockedRequestTargets)
    }

    fun unblockRequestTarget(target: String) {
        blockedRequestTargets = blockedRequestTargets - target
        persistStringSet("blocked_requests", blockedRequestTargets)
    }

    fun isRequestBlocked(url: String): Boolean {
        val targets = blockedRequestTargets
        if (targets.isEmpty()) return false
        return targets.any { target ->
            url == target || url.startsWith("$target/") || url.startsWith(target)
        }
    }

    // --- Per-site JavaScript blocking (feature 42) ----------------------

    var jsBlockedOrigins by mutableStateOf(loadStringSet("js_blocked_origins"))
        private set

    fun setJsBlocked(origin: String, blocked: Boolean) {
        if (origin.isBlank()) return
        jsBlockedOrigins = if (blocked) jsBlockedOrigins + origin else jsBlockedOrigins - origin
        persistStringSet("js_blocked_origins", jsBlockedOrigins)
    }

    fun isJsBlocked(origin: String?): Boolean {
        if (origin == null) return false
        return jsBlockedOrigins.contains(origin)
    }

    // --- Request throttling (feature 96) --------------------------------

    /** One of "off", "slow3g", "fast3g", "4g", "offline". */
    var networkThrottle by mutableStateOf(prefs.getString("throttle", "off") ?: "off")
        private set

    fun setNetworkThrottle(profile: String) {
        networkThrottle = profile
        prefs.edit().putString("throttle", profile).apply()
    }

    // --- Persistence helpers -------------------------------------------

    private fun loadSnippets(): List<JsSnippet> = try {
        val raw = prefs.getString("snippets", null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            JsSnippet(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = o.optString("name"),
                code = o.optString("code"),
                urlPattern = o.optString("pattern"),
                runOnLoad = o.optBoolean("onload", false),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun persistSnippets() {
        val array = JSONArray()
        snippets.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("code", it.code)
                    .put("pattern", it.urlPattern)
                    .put("onload", it.runOnLoad),
            )
        }
        prefs.edit().putString("snippets", array.toString()).apply()
    }

    private fun loadJsHistory(): List<JsEvaluation> = try {
        val raw = prefs.getString("js_history", null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            JsEvaluation(
                code = o.optString("code"),
                output = JsOutput(
                    ok = o.optBoolean("ok", true),
                    value = o.optString("value"),
                    error = o.optString("error"),
                ),
                at = o.optLong("at"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun persistJsHistory() {
        val array = JSONArray()
        jsHistory.forEach {
            array.put(
                JSONObject()
                    .put("code", it.code)
                    .put("ok", it.output.ok)
                    .put("value", it.output.value)
                    .put("error", it.output.error)
                    .put("at", it.at),
            )
        }
        prefs.edit().putString("js_history", array.toString()).apply()
    }

    private fun loadStringSet(key: String): Set<String> {
        val raw = prefs.getString(key, null) ?: return emptySet()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun persistStringSet(key: String, values: Set<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    companion object {
        private const val MAX_JS_HISTORY = 100

        /**
         * Simple glob matcher used for auto-injected snippets: `*` matches any
         * run of characters, everything else is literal (`?` matches one).
         */
        fun globMatches(pattern: String, url: String): Boolean {
            if (pattern.isBlank()) return false
            val regex = buildString {
                append('^')
                pattern.forEach { c ->
                    when (c) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        else -> append(Regex.escape(c.toString()))
                    }
                }
                append('$')
            }
            return Regex(regex, RegexOption.IGNORE_CASE).matches(url)
        }
    }
}
