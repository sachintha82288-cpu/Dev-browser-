package io.github.sachintha82288.devbrowser.debug

import org.json.JSONArray
import org.json.JSONObject

/** Turns the JSON produced by [DomScripts] into UI models. */
object DomParser {

    fun parseTree(payload: JSONObject?): Pair<DomNode?, Int> {
        if (payload == null) return null to 0
        val root = payload.optJSONObject("root")?.let { parseNode(it) }
        return root to payload.optInt("count", 0)
    }

    private fun parseNode(obj: JSONObject): DomNode {
        val children = ArrayList<DomNode>()
        val kids = obj.optJSONArray("k")
        if (kids != null) {
            for (i in 0 until kids.length()) {
                kids.optJSONObject(i)?.let { children.add(parseNode(it)) }
            }
        }
        return DomNode(
            id = obj.optInt("i"),
            tag = obj.optString("t"),
            path = obj.optString("p"),
            htmlId = obj.optString("id"),
            classes = obj.optString("c"),
            text = obj.optString("x"),
            childCount = obj.optInt("n"),
            children = children,
        )
    }

    fun parseNodeInfo(payload: JSONObject?): DomNodeInfo? {
        if (payload == null || payload.has("error")) return null
        val attributes = ArrayList<DomAttribute>()
        val array = payload.optJSONArray("a")
        if (array != null) {
            for (i in 0 until array.length()) {
                val pair = array.optJSONArray(i) ?: continue
                attributes.add(DomAttribute(pair.optString(0), pair.optString(1)))
            }
        }
        val rect = payload.optJSONObject("r")
        return DomNodeInfo(
            id = 0,
            path = payload.optString("p"),
            tag = payload.optString("t"),
            selector = payload.optString("s"),
            html = payload.optString("h"),
            attributes = attributes,
            text = payload.optString("x"),
            rect = ElementRect(
                x = rect?.optInt("px") ?: 0,
                y = rect?.optInt("py") ?: 0,
                width = rect?.optInt("w") ?: 0,
                height = rect?.optInt("h") ?: 0,
            ),
        )
    }

    /** `[prop, value]` pairs → [CssDeclaration] list. */
    fun parseDeclarations(array: JSONArray?): List<CssDeclaration> {
        if (array == null) return emptyList()
        val out = ArrayList<CssDeclaration>(array.length())
        for (i in 0 until array.length()) {
            val pair = array.optJSONArray(i) ?: continue
            out.add(CssDeclaration(pair.optString(0), pair.optString(1)))
        }
        return out
    }

    fun parseRules(payload: JSONObject?): List<CssRule> {
        val rules = payload?.optJSONArray("rules") ?: return emptyList()
        val out = ArrayList<CssRule>(rules.length())
        for (i in 0 until rules.length()) {
            val obj = rules.optJSONObject(i) ?: continue
            out.add(
                CssRule(
                    selector = obj.optString("sel"),
                    source = shortenSource(obj.optString("src")),
                    origin = obj.optString("origin"),
                    declarations = parseDeclarations(obj.optJSONArray("d")),
                ),
            )
        }
        return out
    }

    /** Computed style payload → declarations + optional box model. */
    fun parseComputed(payload: JSONObject?): Pair<List<CssDeclaration>, BoxModel?> {
        if (payload == null) return emptyList<CssDeclaration>() to null
        val props = parseDeclarations(payload.optJSONArray("props"))
        val box = payload.optJSONObject("box")?.let { obj ->
            BoxModel(
                margin = parseEdges(obj.optJSONArray("margin")),
                border = parseEdges(obj.optJSONArray("border")),
                padding = parseEdges(obj.optJSONArray("padding")),
                contentWidth = pixels(obj.optString("width")),
                contentHeight = pixels(obj.optString("height")),
            )
        }
        return props to box
    }

    private fun parseEdges(array: JSONArray?): Edges {
        fun at(index: Int): Int = pixels(array?.optString(index) ?: "")
        return Edges(top = at(0), right = at(1), bottom = at(2), left = at(3))
    }

    fun pixels(raw: String): Int =
        Regex("""-?\d+(\.\d+)?""").find(raw)?.value?.toDoubleOrNull()?.toInt() ?: 0

    private fun shortenSource(source: String): String {
        if (source.isBlank() || source == "inline style") return source
        val name = source.substringAfterLast('/')
        return if (name.length > 42) name.takeLast(42) else name
    }
}
