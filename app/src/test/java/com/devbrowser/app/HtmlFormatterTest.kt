package com.devbrowser.app

import com.devbrowser.app.tools.HtmlFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlFormatterTest {

    @Test
    fun `indents nested structure`() {
        assertEquals(
            "<div>\n  <p>\n    hi\n  </p>\n</div>",
            HtmlFormatter.format("<div><p>hi</p></div>")
        )
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals("", HtmlFormatter.format("   "))
    }

    /** Re-indenting script bodies used to corrupt JS. It must be verbatim. */
    @Test
    fun `preserves javascript verbatim`() {
        val html = "<script>\nvar s = `a\nb`;\nif (a < 2 && a > 0) { f(\"<div>x</div>\"); }\n</script>"
        val out = HtmlFormatter.format(html)
        assertTrue(out.contains("var s = `a\nb`;"))
        assertTrue(out.contains("if (a < 2 && a > 0) { f(\"<div>x</div>\"); }"))
        // The <div> inside the JS string must not become real markup.
        assertFalse(out.contains("\n  <div>"))
    }

    @Test
    fun `preserves pre whitespace`() {
        assertTrue(HtmlFormatter.format("<pre>keep\n   spaces</pre>").contains("keep\n   spaces"))
    }

    @Test
    fun `does not split attributes containing angle brackets`() {
        assertTrue(HtmlFormatter.format("""<a title="a > b">l</a>""").contains("""<a title="a > b">"""))
    }

    @Test
    fun `void elements do not increase depth`() {
        val out = HtmlFormatter.format("<div><br><img src=\"a.png\"></div>")
        assertTrue(out.contains("  <br>"))
        assertTrue(out.contains("  <img src=\"a.png\">"))
    }

    @Test
    fun `keeps comments and doctype`() {
        assertTrue(HtmlFormatter.format("<div><!-- c --></div>").contains("<!-- c -->"))
        assertTrue(HtmlFormatter.format("<!DOCTYPE html><html></html>").startsWith("<!DOCTYPE html>"))
    }
}
