package io.github.sachintha82288.devbrowser.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Address-bar heuristics.
 *
 * [UrlUtils.looksLikeUrl] and [UrlUtils.toUrl] are pure string functions, so they run on the JVM
 * without Robolectric. The helpers that touch `android.net.Uri` (`searchUrl`, `hostOf`,
 * `titleFromUrl`) are deliberately not covered here: they would need an instrumented test.
 */
class UrlUtilsTest {

    @Test
    fun schemesAreLoadedAsTyped() {
        for (input in listOf("about:config", "https://example.com", "view-source:https://x.dev")) {
            assertEquals(input, UrlUtils.toUrl(input))
        }
    }

    @Test
    fun dottedHostsAreUrls() {
        assertTrue(UrlUtils.looksLikeUrl("example.com"))
        assertTrue(UrlUtils.looksLikeUrl("foo.bar.baz"))
        assertTrue(UrlUtils.looksLikeUrl("example.com:8443/path"))
        assertEquals("https://example.com", UrlUtils.toUrl("example.com"))
    }

    @Test
    fun wordsAndPhrasesAreSearches() {
        assertFalse(UrlUtils.looksLikeUrl("geckoview"))
        assertFalse(UrlUtils.looksLikeUrl("how to install xpi"))
        assertFalse(UrlUtils.looksLikeUrl("compose 1.12"))
        assertFalse(UrlUtils.looksLikeUrl(""))
        assertEquals("geckoview", UrlUtils.toUrl("geckoview"))
    }

    /** A dev server almost never has a certificate, so these must not be forced to https. */
    @Test
    fun devHostsLoadOverPlainHttp() {
        assertEquals("http://localhost:8080", UrlUtils.toUrl("localhost:8080"))
        assertEquals("http://LOCALHOST:8080", UrlUtils.toUrl("LOCALHOST:8080"))
        assertEquals("http://127.0.0.1:3000", UrlUtils.toUrl("127.0.0.1:3000"))
        assertEquals("http://10.0.0.7/", UrlUtils.toUrl("10.0.0.7/"))
        assertEquals("http://192.168.1.4:8000", UrlUtils.toUrl("192.168.1.4:8000"))
        assertEquals("http://172.16.0.9:5000", UrlUtils.toUrl("172.16.0.9:5000"))
        assertEquals("http://169.254.1.2", UrlUtils.toUrl("169.254.1.2"))
        assertEquals("http://0.0.0.0:5173", UrlUtils.toUrl("0.0.0.0:5173"))
        assertEquals("http://[::1]:3000", UrlUtils.toUrl("[::1]:3000"))
    }

    @Test
    fun publicHostsStayOnHttps() {
        assertEquals("https://1.1.1.1", UrlUtils.toUrl("1.1.1.1"))
        assertEquals("https://255.255.255.255", UrlUtils.toUrl("255.255.255.255"))
        assertEquals("https://example.com:8443/path", UrlUtils.toUrl("example.com:8443/path"))
    }

    @Test
    fun ipv4NeedsFourOctetsUnder256() {
        assertTrue(UrlUtils.looksLikeUrl("255.255.255.255"))
        assertFalse(UrlUtils.looksLikeUrl("999.1.1.1"))
    }
}
