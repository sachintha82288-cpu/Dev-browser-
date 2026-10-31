package io.github.sachintha82288.devbrowser.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession

/**
 * A single browser tab: one [GeckoSession] plus the observable UI state that
 * the Compose layer renders.
 */
class BrowserTab(
    val id: String,
    val session: GeckoSession,
    val isPrivate: Boolean,
) {
    /** Last known page title; empty until the page reports one. */
    var title: String by mutableStateOf("")

    /** Current page URL; null means "show the DevBrowser home screen". */
    var url: String? by mutableStateOf(null)

    var loading: Boolean by mutableStateOf(false)
    var progress: Int by mutableStateOf(0)
    var canGoBack: Boolean by mutableStateOf(false)
    var canGoForward: Boolean by mutableStateOf(false)
    var isSecure: Boolean by mutableStateOf(false)

    /** Tracker/cookie requests blocked on this page (from ContentBlocking delegate). */
    var blockedCount: Int by mutableStateOf(0)

    /** Whether this tab renders in desktop mode. */
    var isDesktopMode: Boolean by mutableStateOf(false)

    /** Fullscreen video mode requested by the page. */
    var isFullScreen: Boolean by mutableStateOf(false)

    /** Leaf TLS certificate reported by the engine (debug suite, feature 65). */
    var certificate by mutableStateOf<CertificateInfo?>(null)

    /** Stage A: pinned tabs stay first in the tray and survive tab-close sweeps. */
    var isPinned: Boolean by mutableStateOf(false)

    /** Stage A: inactive tabs are deactivated to release engine resources. */
    var isSleeping: Boolean by mutableStateOf(false)

    /** Stage A: per-site zoom in percent, 100 = unchanged. */
    var zoomPercent: Int by mutableStateOf(100)

    fun goHome() {
        url = null
        title = ""
    }

    fun load(url: String) {
        session.load(GeckoSession.Loader().uri(url))
    }
}
