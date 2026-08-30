package com.devbrowser.app.browser

import android.content.Context
import com.devbrowser.app.data.Prefs
import com.devbrowser.app.proxy.ProxyController
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/** Owns the single process-wide [GeckoRuntime]. */
object GeckoEngine {

    @Volatile
    private var runtimeRef: GeckoRuntime? = null

    val runtime: GeckoRuntime
        get() = runtimeRef ?: error("GeckoEngine.init() was not called")

    fun init(context: Context) {
        if (runtimeRef != null) return

        val settings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(Prefs.javascriptEnabled)
            .remoteDebuggingEnabled(Prefs.remoteDebugging)
            .aboutConfigEnabled(true)
            .consoleOutput(true)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .enhancedTrackingProtectionLevel(
                        if (Prefs.trackingProtection) ContentBlocking.EtpLevel.STRICT
                        else ContentBlocking.EtpLevel.NONE
                    )
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
                    .build()
            )
            .build()

        runtimeRef = GeckoRuntime.create(context.applicationContext, settings)
        applyRuntimePrefs()
    }

    /** Re-applies everything that can change at runtime from the settings UI. */
    fun applyRuntimePrefs() {
        val rt = runtimeRef ?: return
        rt.settings.javaScriptEnabled = Prefs.javascriptEnabled
        rt.settings.remoteDebuggingEnabled = Prefs.remoteDebugging
        rt.settings.contentBlocking.setEnhancedTrackingProtectionLevel(
            if (Prefs.trackingProtection) ContentBlocking.EtpLevel.STRICT
            else ContentBlocking.EtpLevel.NONE
        )
        GeckoPrefs.set(
            rt,
            mapOf(
                // Allow installing unsigned / third party XPIs from any source.
                "extensions.install.requireBuiltInCerts" to false,
                "xpinstall.signatures.required" to false,
                "extensions.webextensions.restrictedDomains" to "",
                "devtools.debugger.remote-enabled" to Prefs.remoteDebugging,
                "browser.download.start_downloads_in_tmp_dir" to false
            )
        )
        ProxyController.apply(rt)
    }
}
