package com.devbrowser.app.tools

/** Small dependency-free pretty printer for HTML / XML markup. */
object HtmlFormatter {

    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr", "!doctype"
    )

    fun format(input: String, indentUnit: String = "  "): String {
        val src = input.trim()
        if (src.isEmpty()) return ""
        val out = StringBuilder()
        var depth = 0
        var i = 0
        var inPre = false

        while (i < src.length) {
            if (src[i] == '<') {
                val end = src.indexOf('>', i)
                if (end < 0) { out.append(src.substring(i)); break }
                val tag = src.substring(i, end + 1)
                val name = tagName(tag)
                val closing = tag.startsWith("</")
                val selfClosing = tag.endsWith("/>") || name in VOID
                val comment = tag.startsWith("<!--")

                if (name == "pre" || name == "textarea" || name == "script" || name == "style") {
                    inPre = !closing
                }

                if (closing) depth = (depth - 1).coerceAtLeast(0)
                if (out.isNotEmpty()) out.append('\n')
                out.append(indentUnit.repeat(depth)).append(tag.trim())
                if (!closing && !selfClosing && !comment) depth++

                i = end + 1
            } else {
                val next = src.indexOf('<', i)
                val raw = if (next < 0) src.substring(i) else src.substring(i, next)
                val text = if (inPre) raw else raw.trim()
                if (text.isNotEmpty()) {
                    if (out.isNotEmpty()) out.append('\n')
                    out.append(indentUnit.repeat(depth)).append(text)
                }
                if (next < 0) break
                i = next
            }
        }
        return out.toString()
    }

    private fun tagName(tag: String): String =
        tag.trim('<', '>', '/').trim()
            .substringBefore(' ')
            .substringBefore('>')
            .lowercase()
}
