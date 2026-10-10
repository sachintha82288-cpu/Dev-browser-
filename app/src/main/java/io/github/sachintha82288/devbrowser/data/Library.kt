package io.github.sachintha82288.devbrowser.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

data class Bookmark(val url: String, val title: String, val addedAt: Long)

data class HistoryEntry(val url: String, val title: String, val visitedAt: Long)

/**
 * Bookmarks + browsing history, stored as JSON in SharedPreferences. Plain and
 * dependency-free (org.json ships with Android).
 */
class Library(private val context: Context) {

    private val prefs = context.getSharedPreferences("devbrowser_library", Context.MODE_PRIVATE)

    var bookmarks by mutableStateOf(loadBookmarks())
        private set

    var history by mutableStateOf(loadHistory())
        private set

    fun isBookmarked(url: String?): Boolean =
        url != null && bookmarks.any { it.url == url }

    fun addBookmark(url: String, title: String) {
        if (url.isBlank() || isBookmarked(url)) return
        bookmarks = bookmarks + Bookmark(url, title.ifBlank { url }, System.currentTimeMillis())
        persistBookmarks()
    }

    fun removeBookmark(url: String) {
        bookmarks = bookmarks.filterNot { it.url == url }
        persistBookmarks()
    }

    fun clearBookmarks() {
        bookmarks = emptyList()
        persistBookmarks()
    }

    fun addHistory(url: String, title: String) {
        if (url.isBlank()) return
        val top = history.firstOrNull()
        if (top != null && top.url == url) {
            // Refresh the existing consecutive entry instead of duplicating it.
            history = listOf(HistoryEntry(url, title.ifBlank { top.title }, System.currentTimeMillis())) + history.drop(1)
            persistHistory()
            return
        }
        history = listOf(HistoryEntry(url, title.ifBlank { url }, System.currentTimeMillis())) +
            history.filterNot { it.url == url }
        if (history.size > MAX_HISTORY) history = history.take(MAX_HISTORY)
        persistHistory()
    }

    fun removeHistory(url: String) {
        history = history.filterNot { it.url == url }
        persistHistory()
    }

    /** Update the title of the most recent history entry for [url]. */
    fun updateLatestTitle(url: String, title: String) {
        if (title.isBlank()) return
        val idx = history.indexOfFirst { it.url == url }
        if (idx in 0..9 && history[idx].title != title) {
            history = history.toMutableList().also { list ->
                list[idx] = list[idx].copy(title = title)
            }
            persistHistory()
        }
    }

    fun clearHistory() {
        history = emptyList()
        persistHistory()
    }

    private fun loadBookmarks(): List<Bookmark> =
        loadList("bookmarks") { obj ->
            Bookmark(
                url = obj.optString("url"),
                title = obj.optString("title"),
                addedAt = obj.optLong("addedAt"),
            )
        }

    private fun loadHistory(): List<HistoryEntry> =
        loadList("history") { obj ->
            HistoryEntry(
                url = obj.optString("url"),
                title = obj.optString("title"),
                visitedAt = obj.optLong("visitedAt"),
            )
        }

    private fun <T> loadList(key: String, mapper: (JSONObject) -> T): List<T> = try {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { mapper(array.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun persistBookmarks() {
        prefs.edit().putString("bookmarks", toJson(bookmarks) {
            JSONObject().put("url", it.url).put("title", it.title).put("addedAt", it.addedAt)
        }).apply()
    }

    private fun persistHistory() {
        prefs.edit().putString("history", toJson(history) {
            JSONObject().put("url", it.url).put("title", it.title).put("visitedAt", it.visitedAt)
        }).apply()
    }

    private fun <T> toJson(list: List<T>, block: (T) -> JSONObject): String {
        val array = JSONArray()
        list.forEach { array.put(block(it)) }
        return array.toString()
    }

    companion object {
        private const val MAX_HISTORY = 500

        fun formatTime(timestamp: Long): String =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
    }
}
