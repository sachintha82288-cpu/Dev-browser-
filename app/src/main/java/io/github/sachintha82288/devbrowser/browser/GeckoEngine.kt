package io.github.sachintha82288.devbrowser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.mozilla.geckoview.BuildConfig
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtensionController
import io.github.sachintha82288.devbrowser.data.AppSettings
import io.github.sachintha82288.devbrowser.data.TrackingProtectionLevel
import java.io.File

/**
 * Owns the Gecko (Firefox) runtime: engine lifecycle, startup preference file,
 * live preference updates and the storage / web-extension controllers.
 */
class GeckoEngine(
    context: Context,
    val settings: AppSettings,
) {
    val runtime: GeckoRuntime
    val webExtensionController: WebExtensionController
    val storageController: StorageController

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Human-readable engine version, e.g. "Firefox 157.0 (GeckoView 157.0.20260924084938)". */
    val engineVersion: String =
        "Firefox ${BuildConfig.MOZ_APP_VERSION} · GeckoView ${BuildConfig.MOZ_APP_BUILDID}"

    init {
        val configFile = writeStartupConfig(appContext, settings)

        val contentBlocking = ContentBlocking.Settings.Builder()
            .enhancedTrackingProtectionLevel(etpLevel(settings.trackingProtection))
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            .safeBrowsing(
                ContentBlocking.SafeBrowsing.MALWARE
                    or ContentBlocking.SafeBrowsing.PHISHING
                    or ContentBlocking.SafeBrowsing.UNWANTED
                    or ContentBlocking.SafeBrowsing.HARMFUL,
            )
            .build()

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .configFilePath(configFile.absolutePath)
            .contentBlocking(contentBlocking)
            .aboutConfigEnabled(settings.aboutConfig)
            .consoleOutput(settings.consoleOutput)
            .remoteDebuggingEnabled(settings.remoteDebugging)
            .javaScriptEnabled(settings.javascriptEnabled)
            .webFontsEnabled(settings.webFontsEnabled)
            .extensionsProcessEnabled(true)
            .extensionsWebAPIEnabled(true)
            .globalPrivacyControlEnabled(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .allowInsecureConnections(
                if (settings.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY
                else GeckoRuntimeSettings.ALLOW_ALL,
            )
            .build()

        runtime = GeckoRuntime.create(appContext, runtimeSettings)
        webExtensionController = runtime.webExtensionController
        storageController = runtime.storageController
    }

    /**
     * Apply a set of Gecko preferences live. Safe to call from any thread;
     * GeckoPreferenceController requires the runtime (handler) thread.
     */
    fun applyPrefs(prefs: Map<String, Any>) {
        onEngineThread {
            prefs.forEach { (name, value) ->
                try {
                    val result: org.mozilla.geckoview.GeckoResult<Void> = when (value) {
                        is Boolean -> GeckoPreferenceController.setGeckoPref(
                            name, value, GeckoPreferenceController.PREF_BRANCH_USER,
                        )
                        is Int -> GeckoPreferenceController.setGeckoPref(
                            name, value, GeckoPreferenceController.PREF_BRANCH_USER,
                        )
                        else -> GeckoPreferenceController.setGeckoPref(
                            name, value.toString(), GeckoPreferenceController.PREF_BRANCH_USER,
                        )
                    }
                    result.accept({ Log.d(TAG, "Pref $name applied") }) { e ->
                        Log.w(TAG, "Could not apply pref $name: $e")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not apply pref $name", e)
                }
            }
        }
    }

    /**
     * Remove preferences from the *user* branch so they fall back to their Gecko
     * default. Used when a pref-backed toggle is switched off (user-branch prefs
     * otherwise persist in the profile across restarts).
     */
    fun clearUserPrefs(names: List<String>) {
        if (names.isEmpty()) return
        onEngineThread {
            names.forEach { name ->
                try {
                    GeckoPreferenceController.clearGeckoUserPref(name).accept({
                        Log.d(TAG, "Pref $name cleared")
                    }) { e ->
                        Log.w(TAG, "Could not clear pref $name: $e")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not clear pref $name", e)
                }
            }
        }
    }

    /** GeckoPreferenceController is @HandlerThread — run [block] on the runtime thread. */
    private fun onEngineThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    /** Apply toggles that have typed runtime setters (JS, fonts, HTTPS-only, ETP, ...). */
    fun applyRuntimeToggles() {
        val s = runtime.settings
        s.setJavaScriptEnabled(settings.javascriptEnabled)
        s.setWebFontsEnabled(settings.webFontsEnabled)
        s.setRemoteDebuggingEnabled(settings.remoteDebugging)
        s.setAboutConfigEnabled(settings.aboutConfig)
        s.setConsoleOutputEnabled(settings.consoleOutput)
        s.setAllowInsecureConnections(
            if (settings.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY
            else GeckoRuntimeSettings.ALLOW_ALL,
        )
        s.contentBlocking.setEnhancedTrackingProtectionLevel(etpLevel(settings.trackingProtection))
    }

    fun clearAllBrowsingData(onDone: (Boolean) -> Unit) {
        storageController.clearData(StorageController.ClearFlags.ALL).accept({
            onDone(true)
        }) { e ->
            Log.w(TAG, "clearData failed", e)
            onDone(false)
        }
    }

    /**
     * Rewrite `geckoview-config.yaml` from the current settings. The file is only
     * read by Gecko at startup, so this keeps the *next* cold start in sync with a
     * change that was just applied live.
     */
    fun writeStartupConfigNow(currentSettings: AppSettings = settings) {
        try {
            writeStartupConfig(appContext, currentSettings)
        } catch (e: Throwable) {
            Log.w(TAG, "Could not rewrite the startup config", e)
        }
    }

    companion object {
        private const val TAG = "DevBrowser/Engine"

        private fun etpLevel(level: TrackingProtectionLevel): Int = when (level) {
            TrackingProtectionLevel.NONE -> ContentBlocking.EtpLevel.NONE
            TrackingProtectionLevel.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
            TrackingProtectionLevel.STRICT -> ContentBlocking.EtpLevel.STRICT
        }

        /**
         * GeckoView reads a YAML startup config (`prefs:` map). We regenerate it on
         * every launch so proxy, DNS and add-on settings always match the app settings.
         */
        fun writeStartupConfig(context: Context, settings: AppSettings): File {
            val prefs = StartupPrefs.all(settings)
            val sb = StringBuilder()
            sb.append("# Generated by DevBrowser — applied at engine startup.\n")
            sb.append("prefs:\n")
            prefs.forEach { (key, value) ->
                when (value) {
                    is Boolean -> sb.append("  $key: $value\n")
                    is Int -> sb.append("  $key: $value\n")
                    else -> sb.append("  $key: \"$value\"\n")
                }
            }
            val file = File(context.filesDir, "geckoview-config.yaml")
            file.parentFile?.mkdirs()
            file.writeText(sb.toString())
            return file
        }
    }
}
