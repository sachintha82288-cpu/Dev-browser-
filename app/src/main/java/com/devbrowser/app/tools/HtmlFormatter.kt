package com.devbrowser.app.tools

/**
 * Small dependency-free pretty printer for HTML markup.
 *
 * It is deliberately conservative: the contents of "raw text" elements
 * (`script`, `style`, `pre`, `textarea`) are copied through byte-for-byte.
 * Re-indenting those would corrupt JavaScript template literals, `a < b`
 * comparisons and whitespace-significant text, so they are never touched.
 */
object HtmlFormatter {

    /** Elements that never have a closing tag. */
    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    /** Elements whose content must be preserved verbatim. */
    private val RAW_TEXT = setOf("script", "style", "pre", "textarea")

    fun format(input: String, indentUnit: String = "  "): String {
        val src = input.trim()
        if (src.isEmpty()) return ""

        val out = StringBuilder()
        var depth = 0
        var i = 0

        fun newline() {
            if (out.isNotEmpty()) out.append('\n')
        }

        while (i < src.length) {
            val lt = src.indexOf('<', i)

            // Trailing text after the last tag.
            if (lt < 0) {
                val text = src.substring(i).trim()
                if (text.isNotEmpty()) {
                    newline(); out.append(indentUnit.repeat(depth)).append(text)
                }
                break
            }

            // Text sitting between two tags.
            if (lt > i) {
                val text = src.substring(i, lt).trim()
                if (text.isNotEmpty()) {
                    newline(); out.append(indentUnit.repeat(depth)).append(text)
                }
            }

            // Comments and doctype/CDATA are emitted as-is.
            if (src.startsWith("<!--", lt)) {
                val end = src.indexOf("-->", lt).let { if (it < 0) src.length else it + 3 }
                newline(); out.append(indentUnit.repeat(depth)).append(src, lt, end)
                i = end
                continue
            }
            if (src.startsWith("<!", lt)) {
                val end = src.indexOf('>', lt).let { if (it < 0) src.length else it + 1 }
                newline(); out.append(indentUnit.repeat(depth)).append(src, lt, end)
                i = end
                continue
            }

            val gt = findTagEnd(src, lt)
            if (gt < 0) { // Unterminated '<' -- treat the rest as text.
                val text = src.substring(lt).trim()
                if (text.isNotEmpty()) {
                    newline(); out.append(indentUnit.repeat(depth)).append(text)
                }
                break
            }

            val tag = src.substring(lt, gt + 1)
            val name = tagName(tag)
            val closing = tag.startsWith("</")
            val selfClosing = tag.endsWith("/>") || name in VOID

            if (closing) depth = (depth - 1).coerceAtLeast(0)
            newline(); out.append(indentUnit.repeat(depth)).append(tag.trim())
            i = gt + 1

            // Raw-text element: copy its body through untouched.
            if (!closing && !selfClosing && name in RAW_TEXT) {
                val closeTag = "</$name"
                val closeIdx = indexOfIgnoreCase(src, closeTag, i)
                if (closeIdx < 0) {
                    out.append(src.substring(i))
                    break
                }
                val body = src.substring(i, closeIdx)
                if (body.isNotBlank()) out.append(body.trimEnd('\n', '\r'))
                val closeEnd = src.indexOf('>', closeIdx).let { if (it < 0) src.length else it }
                out.append('\n').append(indentUnit.repeat(depth))
                    .append(src, closeIdx, closeEnd + 1)
                i = closeEnd + 1
                continue
            }

            if (!closing && !selfClosing) depth++
        }
        return out.toString()
    }

    /**
     * Finds the '>' that ends a tag, skipping any that appear inside quoted
     * attribute values (e.g. `<a title="a > b">`).
     */
    private fun findTagEnd(src: String, start: Int): Int {
        var quote = ' '
        var i = start + 1
        while (i < src.length) {
            val c = src[i]
            when {
                quote != ' ' -> if (c == quote) quote = ' '
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return i
            }
            i++
        }
        return -1
    }

    private fun indexOfIgnoreCase(src: String, needle: String, from: Int): Int =
        src.indexOf(needle, from, ignoreCase = true)

    private fun tagName(tag: String): String =
        tag.removePrefix("</").removePrefix("<").trimStart()
            .takeWhile { !it.isWhitespace() && it != '>' && it != '/' }
            .lowercase()
}
