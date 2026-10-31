package io.github.sachintha82288.devbrowser.debug

import org.json.JSONObject

data class InlineScript(val index: Int, val bytes: Int, val preview: String)

data class ExternalAsset(
    val url: String,
    val kind: String,
    val transferSize: Long,
    val decodedSize: Long,
    val durationMs: Long,
)

data class BrokenLink(val url: String, val kind: String, val status: String)

data class ImageInfo(
    val url: String,
    val naturalWidth: Int,
    val naturalHeight: Int,
    val displayedWidth: Int,
    val displayedHeight: Int,
    val format: String,
    val oversized: Boolean,
)

data class FontUsage(val family: String, val source: String, val usedOn: Int)

object SourceParser {

    fun parseInlineScripts(payload: JSONObject?): List<InlineScript> {
        val array = payload?.optJSONArray("scripts") ?: return emptyList()
        val out = ArrayList<InlineScript>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                InlineScript(
                    index = obj.optInt("i"),
                    bytes = obj.optInt("b"),
                    preview = obj.optString("p"),
                ),
            )
        }
        return out
    }

    fun parseAssets(payload: JSONObject?): List<ExternalAsset> {
        val array = payload?.optJSONArray("assets") ?: return emptyList()
        val out = ArrayList<ExternalAsset>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                ExternalAsset(
                    url = obj.optString("u"),
                    kind = obj.optString("k"),
                    transferSize = obj.optLong("tr"),
                    decodedSize = obj.optLong("de"),
                    durationMs = obj.optLong("d"),
                ),
            )
        }
        return out
    }

    fun parseBrokenLinks(payload: JSONObject?): List<BrokenLink> {
        val array = payload?.optJSONArray("links") ?: return emptyList()
        val out = ArrayList<BrokenLink>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                BrokenLink(
                    url = obj.optString("u"),
                    kind = obj.optString("k"),
                    status = obj.optString("s"),
                ),
            )
        }
        return out
    }

    fun parseImages(payload: JSONObject?): List<ImageInfo> {
        val array = payload?.optJSONArray("images") ?: return emptyList()
        val out = ArrayList<ImageInfo>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                ImageInfo(
                    url = obj.optString("u"),
                    naturalWidth = obj.optInt("nw"),
                    naturalHeight = obj.optInt("nh"),
                    displayedWidth = obj.optInt("dw"),
                    displayedHeight = obj.optInt("dh"),
                    format = obj.optString("f"),
                    oversized = obj.optBoolean("over"),
                ),
            )
        }
        return out
    }

    fun parseFonts(payload: JSONObject?): List<FontUsage> {
        val array = payload?.optJSONArray("fonts") ?: return emptyList()
        val out = ArrayList<FontUsage>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                FontUsage(
                    family = obj.optString("family"),
                    source = obj.optString("src"),
                    usedOn = obj.optInt("used"),
                ),
            )
        }
        return out
    }
}
