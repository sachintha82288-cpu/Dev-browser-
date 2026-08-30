package com.devbrowser.app

import com.devbrowser.app.browser.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {

    private val search = "https://duckduckgo.com/?q=%s"
    private fun norm(s: String) = UrlUtils.normalize(s, search)

    @Test
    fun `bare domain gets https`() = assertEquals("https://example.com", norm("example.com"))

    @Test
    fun `existing scheme is kept`() =
        assertEquals("http://example.com/x", norm("http://example.com/x"))

    @Test
    fun `phrase becomes a search`() =
        assertEquals("https://duckduckgo.com/?q=hello+world", norm("hello world"))

    @Test
    fun `single word becomes a search`() =
        assertEquals("https://duckduckgo.com/?q=kotlin", norm("kotlin"))

    @Test
    fun `onion address is a url`() = assertEquals("https://abc.onion", norm("abc.onion"))

    @Test
    fun `localhost uses http`() = assertEquals("http://localhost:8080", norm("localhost:8080"))

    @Test
    fun `ipv4 with port uses http`() = assertEquals("http://127.0.0.1:3000", norm("127.0.0.1:3000"))

    @Test
    fun `internal schemes pass through`() {
        assertEquals("about:config", norm("about:config"))
        assertEquals("view-source:https://a.com", norm("view-source:https://a.com"))
    }

    @Test
    fun `blank input is about blank`() = assertEquals("about:blank", norm("   "))
}
