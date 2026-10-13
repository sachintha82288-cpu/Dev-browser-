package io.github.sachintha82288.devbrowser.browser

import io.github.sachintha82288.devbrowser.data.AppSettings
import io.github.sachintha82288.devbrowser.proxy.ProxyPrefs

/**
 * Every Gecko preference DevBrowser owns, composed into the map that is written
 * to the GeckoView startup config (`geckoview-config.yaml`) and re-applied live
 * through [GeckoEngine.applyPrefs] when a setting changes.
 *
 * Keeping this in one place means a cold start and a live toggle always agree on
 * what the engine should be doing.
 */
object StartupPrefs {

    /** Signature checks relaxed so self-built / unsigned .xpi files install. */
    const val PREF_XPINSTALL_SIGNATURES_REQUIRED = "xpinstall.signatures.required"
    const val PREF_XPINSTALL_SIGNATURES_DEV_ROOT = "xpinstall.signatures.dev-root"
    const val PREF_LANGPACK_SIGNATURES_REQUIRED = "extensions.langpacks.signatures.required"
    const val PREF_EXPERIMENTS_ENABLED = "extensions.experiments.enabled"
    const val PREF_EXTENSIONS_UPDATE_ENABLED = "extensions.update.enabled"

    /** Full preference set applied at engine startup. */
    fun all(settings: AppSettings): Map<String, Any> = buildMap {
        // network.proxy.* and network.trr.* (manual proxy, Tor preset, DoH)
        putAll(ProxyPrefs.startupPrefs(settings))
        // add-on installation behaviour
        putAll(extensionPrefs(settings))
    }

    /**
     * Preferences that control add-on installation.
     *
     * Note: release-channel Firefox builds may lock `xpinstall.signatures.required`,
     * in which case GeckoView still rejects an unsigned .xpi with
     * `ERROR_SIGNEDSTATE_REQUIRED` — the error is surfaced to the user as-is.
     */
    fun extensionPrefs(settings: AppSettings): Map<String, Any> = buildMap {
        if (settings.allowUnsignedExtensions) {
            put(PREF_XPINSTALL_SIGNATURES_REQUIRED, false)
            put(PREF_XPINSTALL_SIGNATURES_DEV_ROOT, true)
            put(PREF_LANGPACK_SIGNATURES_REQUIRED, false)
            put(PREF_EXPERIMENTS_ENABLED, true)
        }
        put(PREF_EXTENSIONS_UPDATE_ENABLED, true)
    }

    /** Preference names to clear from the user branch when a toggle is switched off. */
    fun extensionPrefsToClear(settings: AppSettings): List<String> =
        if (settings.allowUnsignedExtensions) {
            emptyList()
        } else {
            listOf(
                PREF_XPINSTALL_SIGNATURES_REQUIRED,
                PREF_XPINSTALL_SIGNATURES_DEV_ROOT,
                PREF_LANGPACK_SIGNATURES_REQUIRED,
                PREF_EXPERIMENTS_ENABLED,
            )
        }
}
