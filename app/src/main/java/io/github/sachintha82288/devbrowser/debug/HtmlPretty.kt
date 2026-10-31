package io.github.sachintha82288.devbrowser.debug

/**
 * Very small HTML pretty-printer used by the sources panel (feature 83).
 *
 * It intentionally does not try to be a full parser: it indents tags, keeps raw
 * text elements (`script`, `style`, `pre`, `textarea`) intact, and caps the
 * output so a huge page cannot freeze the UI.
 */
object HtmlPretty {

    private val rawTextTags = setOf("script", "style", "pre", "textarea")
    private val voidTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
        "meta", "param", "source", "track", "wbr",
    )

    fun format(html: String, maxLines: Int = 4000): List<String> {
        val lines = ArrayList<String>()
        var indent = 0
        var pos = 0
        val source = if (html.length > 1_500_000) html.substring(0, 1_500_000) else html

        fun add(indentLevel: Int, text: String) {
            if (lines.size >= maxLines) return
            if (text.isBlank()) return
            lines.add("    ".repeat(indentLevel.coerceAtLeast(0)) + text)
        }

        fun addText(text: String) {
            val compact = text.replace(Regex("\\s+"), " ").trim()
            if (compact.isNotEmpty()) add(indent, compact)
        }

        while (pos < source.length && lines.size < maxLines) {
            val lt = source.indexOf('<', pos)
            if (lt < 0) {
                addText(source.substring(pos))
                break
            }
            if (lt > pos) addText(source.substring(pos, lt))

            if (source.startsWith("<!--", lt)) {
                val end = source.indexOf("-->", lt).let { if (it < 0) source.length else it + 3 }
                add(indent, source.substring(lt, minOf(end, source.length)))
                pos = end
                continue
            }
            if (source.startsWith("<!", lt) || source.startsWith("<?", lt)) {
                val end = source.indexOf('>', lt).let { if (it < 0) source.length else it + 1 }
                add(indent, source.substring(lt, end))
                pos = end
                continue
            }

            val gt = source.indexOf('>', lt)
            if (gt < 0) {
                addText(source.substring(lt))
                break
            }
            val tag = source.substring(lt, gt + 1)
            val name = tagName(tag)
            val closing = tag.startsWith("</")

            when {
                closing -> {
                    indent = (indent - 1).coerceAtLeast(0)
                    add(indent, tag)
                    pos = gt + 1
                }
                tag.endsWith("/>") || name in voidTags -> {
                    add(indent, tag)
                    pos = gt + 1
                }
                name in rawTextTags -> {
                    val closeStart = source.indexOf("</$name", gt, ignoreCase = true)
                    if (closeStart < 0) {
                        add(indent, tag)
                        pos = gt + 1
                        indent++
                    } else {
                        val closeEnd = source.indexOf('>', closeStart).let {
                            if (it < 0) source.length else it + 1
                        }
                        add(indent, tag)
                        source.substring(gt + 1, closeStart)
                            .trim()
                            .lines()
                            .filter { it.isNotBlank() }
                            .take(200)
                            .forEach { line -> add(indent + 1, line) }
                        add(indent, source.substring(closeStart, closeEnd))
                        pos = closeEnd
                    }
                }
                else -> {
                    add(indent, tag)
                    indent++
                    pos = gt + 1
                }
            }
        }
        if (lines.isEmpty()) lines.add("(empty document)")
        return lines
    }

    private fun tagName(tag: String): String {
        val stripped = tag.removePrefix("</").removePrefix("<").trim()
        val end = stripped.indexOfFirst { it == ' ' || it == '>' || it == '/' || it == '\n' }
        val name = if (end < 0) stripped else stripped.substring(0, end)
        return name.lowercase()
    }

    /** Lines matching [query] (case-insensitive), as indices. */
    fun matches(lines: List<String>, query: String): List<Int> {
        if (query.isBlank()) return emptyList()
        return lines.indices.filter { lines[it].contains(query, ignoreCase = true) }
    }
}
