package io.github.sachintha82288.devbrowser.browser

import android.content.Context
import android.util.Base64
import androidx.annotation.StringRes
import io.github.sachintha82288.devbrowser.R
import org.mozilla.geckoview.WebRequestError
import java.net.URI

/**
 * Self-contained dark/neon HTML error pages, returned from
 * [org.mozilla.geckoview.GeckoSession.NavigationDelegate.onLoadError] as data: URIs.
 *
 * The page is rendered inside a `data:` URI, so it cannot use the app's Compose
 * theme or its resource lookup: every string is resolved from resources here, in
 * the app's current locale, and interpolated into the HTML. [buildHtml] itself is
 * a pure function so it can be unit-tested on the JVM.
 */
object ErrorPage {

    fun create(context: Context, uri: String?, error: WebRequestError?): String {
        val messageRes = messageRes(error?.code)
        val message =
            if (messageRes == R.string.error_page_generic) {
                context.getString(
                    messageRes,
                    error?.code?.toString() ?: context.getString(R.string.error_page_unknown_code),
                )
            } else {
                context.getString(messageRes)
            }

        val html = buildHtml(
            heading = context.getString(R.string.error_page_heading),
            host = hostOf(uri),
            message = message,
            hint = context.getString(R.string.page_load_error_hint),
            retryUrl = retryUrl(uri),
            retryLabel = context.getString(R.string.try_again),
        )

        val encoded = Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
        return "data:text/html;base64,$encoded"
    }

    /** Host to display, falling back to the raw address when there is no host part. */
    internal fun hostOf(uri: String?): String {
        if (uri.isNullOrBlank()) return ""
        return try {
            val host = URI(uri).host
            if (host.isNullOrBlank()) uri else host
        } catch (_: Exception) {
            uri
        }
    }

    /** Only plain web addresses are offered as a retry link. */
    internal fun retryUrl(uri: String?): String? =
        uri?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    /**
     * String resource describing a [WebRequestError] code.
     *
     * The `ERROR_*` constants are compile-time ints, so this stays a pure lookup
     * and can be asserted directly in a unit test.
     */
    @StringRes
    internal fun messageRes(code: Int?): Int = when (code) {
        WebRequestError.ERROR_SECURITY_SSL,
        WebRequestError.ERROR_SECURITY_BAD_CERT -> R.string.error_page_ssl

        WebRequestError.ERROR_NET_TIMEOUT -> R.string.error_page_timeout
        WebRequestError.ERROR_NET_RESET -> R.string.error_page_reset
        WebRequestError.ERROR_CONNECTION_REFUSED -> R.string.error_page_refused
        WebRequestError.ERROR_UNKNOWN_HOST -> R.string.error_page_unknown_host

        WebRequestError.ERROR_UNKNOWN_PROXY_HOST,
        WebRequestError.ERROR_PROXY_CONNECTION_REFUSED -> R.string.error_page_proxy

        WebRequestError.ERROR_REDIRECT_LOOP -> R.string.error_page_redirect_loop
        WebRequestError.ERROR_OFFLINE -> R.string.error_page_offline
        WebRequestError.ERROR_PORT_BLOCKED -> R.string.error_page_port_blocked
        WebRequestError.ERROR_MALFORMED_URI -> R.string.error_page_malformed_uri
        WebRequestError.ERROR_UNKNOWN_PROTOCOL -> R.string.error_page_unknown_protocol
        WebRequestError.ERROR_CONTENT_CRASHED -> R.string.error_page_content_crashed
        WebRequestError.ERROR_HTTPS_ONLY -> R.string.error_page_https_only

        else -> R.string.error_page_generic
    }

    /** The page itself: no Android APIs, so a JVM test can assert on the markup. */
    internal fun buildHtml(
        heading: String,
        host: String,
        message: String,
        hint: String,
        retryUrl: String?,
        retryLabel: String,
    ): String {
        val retry = if (retryUrl.isNullOrBlank()) {
            ""
        } else {
            """<a class="retry" href="${escapeAttr(retryUrl)}">${escape(retryLabel)}</a>"""
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              :root { color-scheme: dark; }
              body {
                margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
                background: #0a0a12; color: #ededf7;
                font-family: ui-monospace, 'Cascadia Mono', Menlo, Consolas, monospace;
              }
              .card {
                max-width: 520px; margin: 32px; padding: 40px 36px;
                background: #12121c; border: 1px solid #2a2a3d; border-radius: 16px;
                box-shadow: 0 0 40px rgba(255,46,99,0.08);
              }
              h1 { font-size: 18px; margin: 0 0 8px; color: #ff2e63; letter-spacing: 1px; }
              .glyph { font-size: 40px; margin-bottom: 18px; }
              .host { color: #00e5ff; word-break: break-all; font-size: 13px; margin: 4px 0 16px; }
              p { color: #9e9eb3; font-size: 13px; line-height: 1.6; margin: 0 0 10px; }
              .retry {
                display: inline-block; margin-top: 14px; padding: 10px 18px;
                border: 1px solid #ff2e63; border-radius: 10px;
                color: #ff2e63; font-size: 13px; text-decoration: none;
              }
              code { color: #9d4edd; }
            </style>
            </head>
            <body>
              <div class="card">
                <div class="glyph">&lt;/&gt;</div>
                <h1>${escape(heading)}</h1>
                <div class="host">${escape(host)}</div>
                <p>${escape(message)}</p>
                <p>${escape(hint)}</p>
                $retry
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    internal fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Attribute-safe escaping: the same as [escape], plus the quote characters. */
    internal fun escapeAttr(s: String): String =
        escape(s).replace("\"", "&quot;").replace("'", "&#39;")
}
