package io.github.sachintha82288.devbrowser.browser

import io.github.sachintha82288.devbrowser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.WebRequestError

/**
 * The error page is generated inside a `data:` URI, so nothing in it can rely on
 * Android resource lookup or Compose. [ErrorPage.buildHtml] and the helpers around
 * it are pure functions, which is what these tests pin down.
 */
class ErrorPageTest {

    private fun page(
        heading: String = "CONNECTION_ERROR",
        host: String = "example.com",
        message: String = "The connection timed out.",
        hint: String = "Check the address.",
        retryUrl: String? = null,
        retryLabel: String = "Try again",
    ) = ErrorPage.buildHtml(heading, host, message, hint, retryUrl, retryLabel)

    @Test
    fun markupCarriesEveryStringItIsGiven() {
        val html = page(host = "dev.example.com", message = "DNS failure", hint = "Try the proxy")

        assertTrue(html.contains("<h1>CONNECTION_ERROR</h1>"))
        assertTrue(html.contains("""<div class="host">dev.example.com</div>"""))
        assertTrue(html.contains("<p>DNS failure</p>"))
        assertTrue(html.contains("<p>Try the proxy</p>"))
        assertTrue(html.startsWith("<!DOCTYPE html>"))
    }

    /** The URL and the error text are attacker-adjacent input, so they must not become markup. */
    @Test
    fun angleBracketsAndAmpersandsAreEscaped() {
        val html = page(
            host = "<script>alert(1)</script>",
            message = "host & port <b>",
            hint = "a > b",
        )

        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("host &amp; port &lt;b&gt;"))
        assertTrue(html.contains("a &gt; b"))
    }

    @Test
    fun escapeHandlesAmpersandFirstSoEntitiesAreNotDoubleEscaped() {
        assertEquals("&amp;lt;", ErrorPage.escape("&lt;"))
        assertEquals("&lt;a&gt;", ErrorPage.escape("<a>"))
        assertEquals("", ErrorPage.escape(""))
    }

    @Test
    fun attributeEscapingAlsoCoversQuotes() {
        assertEquals("&quot;x&quot;", ErrorPage.escapeAttr("\"x\""))
        assertEquals("&#39;", ErrorPage.escapeAttr("'"))
        // `escape` alone leaves quotes intact, which would break out of an attribute.
        assertEquals("\"x\"", ErrorPage.escape("\"x\""))
    }

    @Test
    fun retryLinkIsOnlyRenderedWhenThereIsAnAddress() {
        assertFalse(page(retryUrl = null).contains("class=\"retry\""))

        val html = page(retryUrl = "https://example.com/a?b=1&c=2", retryLabel = "නැවත උත්සාහ කරන්න")
        assertTrue(html.contains("href=\"https://example.com/a?b=1&amp;c=2\""))
        assertTrue(html.contains(">නැවත උත්සාහ කරන්න</a>"))
    }

    @Test
    fun retryLinkCannotBreakOutOfItsAttribute() {
        val html = page(retryUrl = """https://example.com/"><img src=x>""")
        assertFalse(html.contains("<img src=x>"))
        assertTrue(html.contains("&quot;&gt;&lt;img src=x&gt;"))
    }

    @Test
    fun hostIsShownWithoutSchemePortOrPath() {
        assertEquals("example.com", ErrorPage.hostOf("https://example.com:8443/a/b?c=1"))
        assertEquals("127.0.0.1", ErrorPage.hostOf("http://127.0.0.1:3000/"))
    }

    /** Input the address bar accepts but java.net.URI does not parse as a URL is shown as typed. */
    @Test
    fun unparsableInputFallsBackToTheRawText() {
        assertEquals("localhost:8080", ErrorPage.hostOf("localhost:8080"))
        assertEquals("not a url", ErrorPage.hostOf("not a url"))
        assertEquals("", ErrorPage.hostOf(null))
        assertEquals("", ErrorPage.hostOf(""))
    }

    @Test
    fun onlyWebAddressesGetARetryLink() {
        assertEquals("https://example.com", ErrorPage.retryUrl("https://example.com"))
        assertEquals("http://localhost:8080", ErrorPage.retryUrl("http://localhost:8080"))
        assertNull(ErrorPage.retryUrl("about:config"))
        assertNull(ErrorPage.retryUrl("file:///sdcard/x.html"))
        assertNull(ErrorPage.retryUrl(null))
    }

    @Test
    fun geckoErrorCodesMapToTheirOwnMessages() {
        assertEquals(R.string.error_page_ssl, ErrorPage.messageRes(WebRequestError.ERROR_SECURITY_SSL))
        assertEquals(R.string.error_page_ssl, ErrorPage.messageRes(WebRequestError.ERROR_SECURITY_BAD_CERT))
        assertEquals(R.string.error_page_timeout, ErrorPage.messageRes(WebRequestError.ERROR_NET_TIMEOUT))
        assertEquals(R.string.error_page_reset, ErrorPage.messageRes(WebRequestError.ERROR_NET_RESET))
        assertEquals(R.string.error_page_refused, ErrorPage.messageRes(WebRequestError.ERROR_CONNECTION_REFUSED))
        assertEquals(R.string.error_page_unknown_host, ErrorPage.messageRes(WebRequestError.ERROR_UNKNOWN_HOST))
        assertEquals(R.string.error_page_proxy, ErrorPage.messageRes(WebRequestError.ERROR_UNKNOWN_PROXY_HOST))
        assertEquals(R.string.error_page_proxy, ErrorPage.messageRes(WebRequestError.ERROR_PROXY_CONNECTION_REFUSED))
        assertEquals(R.string.error_page_redirect_loop, ErrorPage.messageRes(WebRequestError.ERROR_REDIRECT_LOOP))
        assertEquals(R.string.error_page_offline, ErrorPage.messageRes(WebRequestError.ERROR_OFFLINE))
        assertEquals(R.string.error_page_port_blocked, ErrorPage.messageRes(WebRequestError.ERROR_PORT_BLOCKED))
        assertEquals(R.string.error_page_malformed_uri, ErrorPage.messageRes(WebRequestError.ERROR_MALFORMED_URI))
        assertEquals(R.string.error_page_unknown_protocol, ErrorPage.messageRes(WebRequestError.ERROR_UNKNOWN_PROTOCOL))
        assertEquals(R.string.error_page_content_crashed, ErrorPage.messageRes(WebRequestError.ERROR_CONTENT_CRASHED))
        assertEquals(R.string.error_page_https_only, ErrorPage.messageRes(WebRequestError.ERROR_HTTPS_ONLY))
    }

    @Test
    fun unknownAndMissingCodesFallBackToTheGenericMessage() {
        // 0 is WebRequestError.ERROR_UNKNOWN: the catch-all the app itself reports.
        assertEquals(R.string.error_page_generic, ErrorPage.messageRes(0))
        assertEquals(R.string.error_page_generic, ErrorPage.messageRes(-12345))
        assertEquals(R.string.error_page_generic, ErrorPage.messageRes(null))
    }
}
