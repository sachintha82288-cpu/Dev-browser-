package io.github.sachintha82288.devbrowser.browser

import android.util.Base64
import org.mozilla.geckoview.WebRequestError

/**
 * Self-contained dark/neon HTML error pages, returned from
 * [org.mozilla.geckoview.GeckoSession.NavigationDelegate.onLoadError] as data: URIs.
 */
object ErrorPage {

    fun create(uri: String?, error: WebRequestError?): String {
        val title = describe(error)
        val host = uri?.let {
            try {
                android.net.Uri.parse(it).host
            } catch (_: Exception) {
                null
            }
        } ?: uri ?: ""

        val html = """
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
              code { color: #9d4edd; }
            </style>
            </head>
            <body>
              <div class="card">
                <div class="glyph">&lt;/&gt;</div>
                <h1>CONNECTION_ERROR</h1>
                <div class="host">${escape(host)}</div>
                <p>${escape(title)}</p>
                <p>Check the address, your proxy settings or your connection, then try again.</p>
              </div>
            </body>
            </html>
        """.trimIndent()

        val encoded = Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
        return "data:text/html;base64,$encoded"
    }

    private fun describe(error: WebRequestError?): String = when (error?.code) {
        WebRequestError.ERROR_SECURITY_SSL,
        WebRequestError.ERROR_SECURITY_BAD_CERT -> "The connection is not secure (TLS/certificate problem)."
        WebRequestError.ERROR_NET_TIMEOUT -> "The connection timed out."
        WebRequestError.ERROR_NET_RESET -> "The connection was reset while the page was loading."
        WebRequestError.ERROR_CONNECTION_REFUSED -> "The server refused the connection."
        WebRequestError.ERROR_UNKNOWN_HOST -> "The server could not be found (DNS)."
        WebRequestError.ERROR_UNKNOWN_PROXY_HOST,
        WebRequestError.ERROR_PROXY_CONNECTION_REFUSED -> "The proxy server refused the connection or could not be found."
        WebRequestError.ERROR_REDIRECT_LOOP -> "The page is redirecting in a loop."
        WebRequestError.ERROR_OFFLINE -> "You appear to be offline."
        WebRequestError.ERROR_PORT_BLOCKED -> "The port is blocked for security reasons."
        WebRequestError.ERROR_MALFORMED_URI -> "The address is not valid."
        WebRequestError.ERROR_UNKNOWN_PROTOCOL -> "The protocol of this address is not supported."
        WebRequestError.ERROR_CONTENT_CRASHED -> "The content process crashed while loading this page."
        WebRequestError.ERROR_HTTPS_ONLY -> "HTTPS-only mode is on and no secure version of this site is available."
        else -> "The page could not be loaded (error ${error?.code ?: "unknown"})."
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
