package io.github.sachintha82288.devbrowser.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Stage A: per-site zoom levels, keyed by host.
 *
 * GeckoView has no public page-zoom API, so a level is applied by injecting a
 * CSS `zoom` value into the document (the same approach the debug suite uses
 * for viewport emulation). The preference itself is stored per host and is
 * therefore restored on every navigation.
 */
class ZoomStore(context: Context) {

    private val prefs = context.getSharedPreferences("devbrowser_zoom", Context.MODE_PRIVATE)

    /** Bumped whenever a value changes so Compose can recompose readers. */
    var version by mutableStateOf(0)
        private set

    fun percentFor(host: String?): Int {
        if (host.isNullOrBlank()) return 100
        return prefs.getInt(host.lowercase(), 100)
    }

    fun set(host: String?, percent: Int) {
        if (host.isNullOrBlank()) return
        val clamped = percent.coerceIn(MIN, MAX)
        prefs.edit().putInt(host.lowercase(), clamped).apply()
        version++
    }

    fun clear(host: String?) {
        if (host.isNullOrBlank()) return
        prefs.edit().remove(host.lowercase()).apply()
        version++
    }

    fun all(): Map<String, Int> =
        prefs.all.mapNotNull { (key, value) ->
            val level = value as? Int ?: return@mapNotNull null
            key to level
        }.toMap()

    companion object {
        const val MIN = 50
        const val MAX = 200
        const val STEP = 10
        const val DEFAULT = 100
    }
}
