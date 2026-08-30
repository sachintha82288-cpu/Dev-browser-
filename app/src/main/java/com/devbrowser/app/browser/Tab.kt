package com.devbrowser.app.browser

import android.util.Log
import com.devbrowser.app.data.Prefs
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebRequestError
import java.util.UUID

/** A single browser tab, backed by one [GeckoSession]. */
class Tab(
    val isPrivate: Boolean = false,
    private val onChanged: (Tab) -> Unit
) {

    val id: String = UUID.randomUUID().toString()

    var url: String = "about:blank"
        private set
    var title: String = "New tab"
        private set
    var progress: Int = 0
        private set
    var canGoBack: Boolean = false
        private set
    var canGoForward: Boolean = false
        private set
    var isSecure: Boolean = false
        private set
    var isLoading: Boolean = false
        private set
    val session: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .useTrackingProtection(Prefs.trackingProtection)
            .userAgentMode(
                if (Prefs.desktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (Prefs.desktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .build()
    )

    init {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, url: String) {
                this@Tab.url = url
                isLoading = true
                progress = 5
                onChanged(this@Tab)
            }

            override fun onPageStop(s: GeckoSession, success: Boolean) {
                isLoading = false
                progress = 100
                onChanged(this@Tab)
            }

            override fun onProgressChange(s: GeckoSession, p: Int) {
                progress = p
                onChanged(this@Tab)
            }

            override fun onSecurityChange(s: GeckoSession, info: GeckoSession.ProgressDelegate.SecurityInformation) {
                isSecure = info.isSecure
                onChanged(this@Tab)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, t: String?) {
                title = t?.takeIf { it.isNotBlank() } ?: url
                onChanged(this@Tab)
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(s: GeckoSession, u: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>) {
                u?.let { url = it }
                onChanged(this@Tab)
            }

            override fun onCanGoBack(s: GeckoSession, value: Boolean) {
                canGoBack = value
                onChanged(this@Tab)
            }

            override fun onCanGoForward(s: GeckoSession, value: Boolean) {
                canGoForward = value
                onChanged(this@Tab)
            }

            override fun onLoadError(s: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                Log.w("Tab", "load error for $uri: ${error.code}")
                return null
            }
        }
    }

    fun open() {
        if (!session.isOpen) session.open(GeckoEngine.runtime)
        applyUserAgentOverride()
    }

    /** Applies the custom user agent from settings, if one is set. */
    fun applyUserAgentOverride() {
        val ua = Prefs.customUserAgent
        session.settings.userAgentOverride = ua.ifBlank { null }
    }

    fun load(input: String) {
        open()
        session.loadUri(UrlUtils.normalize(input, Prefs.searchEngine))
    }

    fun reload() = session.reload()
    fun stop() = session.stop()
    fun goBack() = session.goBack()
    fun goForward() = session.goForward()

    fun setDesktopMode(desktop: Boolean) {
        session.settings.userAgentMode =
            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode =
            if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        reload()
    }

    fun close() {
        if (session.isOpen) session.close()
    }
}
