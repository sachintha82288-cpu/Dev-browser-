package io.github.sachintha82288.devbrowser.debug

import org.json.JSONObject

/** Storage models for category 7. */
data class CookieItem(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val sameSite: String,
    val expires: String = "",
)

data class StorageItem(val key: String, val value: String)

data class IdbStore(val name: String, val records: List<StorageItem>)

data class IdbDatabase(val name: String, val stores: List<IdbStore>)

data class CacheGroup(val name: String, val urls: List<String>)

data class ServiceWorkerInfo(val scope: String, val scriptUrl: String, val state: String)

/**
 * Known limitation: GeckoView hides httpOnly cookies and the full cookie jar from
 * page scripts, so the cookie table is limited to `document.cookie` (name, value,
 * domain, path, secure, sameSite defaults). Everything else is exact.
 */
object StorageParser {

    fun parseCookies(payload: JSONObject?): List<CookieItem> {
        val array = payload?.optJSONArray("cookies") ?: return emptyList()
        val out = ArrayList<CookieItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                CookieItem(
                    name = obj.optString("n"),
                    value = obj.optString("v"),
                    domain = obj.optString("d"),
                    path = obj.optString("p"),
                    secure = obj.optBoolean("s"),
                    httpOnly = obj.optBoolean("h"),
                    sameSite = obj.optString("ss"),
                    expires = obj.optString("e"),
                ),
            )
        }
        return out
    }

    fun parseItems(array: org.json.JSONArray?): List<StorageItem> {
        if (array == null) return emptyList()
        val out = ArrayList<StorageItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(StorageItem(obj.optString("k"), obj.optString("v")))
        }
        return out
    }

    fun parseIdb(payload: JSONObject?): List<IdbDatabase> {
        val array = payload?.optJSONArray("databases") ?: return emptyList()
        val out = ArrayList<IdbDatabase>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val stores = ArrayList<IdbStore>()
            val storeNames = obj.optJSONArray("stores")
            val records = obj.optJSONObject("records")
            if (storeNames != null) {
                for (s in 0 until storeNames.length()) {
                    val name = storeNames.optString(s)
                    stores.add(IdbStore(name, parseItems(records?.optJSONArray(name))))
                }
            }
            out.add(IdbDatabase(obj.optString("name"), stores))
        }
        return out
    }

    fun parseCaches(payload: JSONObject?): List<CacheGroup> {
        val array = payload?.optJSONArray("caches") ?: return emptyList()
        val out = ArrayList<CacheGroup>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val urls = ArrayList<String>()
            val list = obj.optJSONArray("urls")
            if (list != null) {
                for (u in 0 until list.length()) urls.add(list.optString(u))
            }
            out.add(CacheGroup(obj.optString("name"), urls))
        }
        return out
    }

    fun parseWorkers(payload: JSONObject?): List<ServiceWorkerInfo> {
        val array = payload?.optJSONArray("workers") ?: return emptyList()
        val out = ArrayList<ServiceWorkerInfo>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                ServiceWorkerInfo(
                    scope = obj.optString("scope"),
                    scriptUrl = obj.optString("script"),
                    state = obj.optString("state"),
                ),
            )
        }
        return out
    }
}
