package com.devbrowser.app.browser

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime

/**
 * Thin bridge that lets us set arbitrary Gecko preferences (the same ones you
 * would edit in about:config on desktop Firefox).
 *
 * GeckoView does not expose a stable public API for this, so we talk to the
 * privileged `GeckoViewPrefs:Set` event bus message and fall back to the
 * internal `GeckoRuntimeSettings#setPrefs` method through reflection. Both
 * paths are guarded, so an API change can never crash the browser -- the pref
 * simply is not applied.
 */
object GeckoPrefs {

    private const val TAG = "GeckoPrefs"

    fun set(runtime: GeckoRuntime, prefs: Map<String, Any>) {
        if (prefs.isEmpty()) return
        if (trySettingsReflection(runtime, prefs)) return
        tryEventDispatcher(prefs)
    }

    fun set(runtime: GeckoRuntime, key: String, value: Any) = set(runtime, mapOf(key to value))

    private fun trySettingsReflection(runtime: GeckoRuntime, prefs: Map<String, Any>): Boolean = try {
        val settings = runtime.settings
        val bundleClass = Class.forName("org.mozilla.gecko.util.GeckoBundle")
        val bundle = bundleClass.getConstructor().newInstance()
        prefs.forEach { (k, v) ->
            when (v) {
                is Int -> bundleClass.getMethod("putInt", String::class.java, Int::class.javaPrimitiveType)
                    .invoke(bundle, k, v)
                is Boolean -> bundleClass.getMethod("putBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                    .invoke(bundle, k, v)
                else -> bundleClass.getMethod("putString", String::class.java, String::class.java)
                    .invoke(bundle, k, v.toString())
            }
        }
        val m = settings.javaClass.getDeclaredMethod("setPrefs", bundleClass)
        m.isAccessible = true
        m.invoke(settings, bundle)
        true
    } catch (t: Throwable) {
        Log.d(TAG, "setPrefs reflection unavailable: ${t.message}")
        false
    }

    private fun tryEventDispatcher(prefs: Map<String, Any>) {
        try {
            val dispatcherClass = Class.forName("org.mozilla.gecko.EventDispatcher")
            val instance = dispatcherClass.getMethod("getInstance").invoke(null)
            val bundleClass = Class.forName("org.mozilla.gecko.util.GeckoBundle")
            val bundle = bundleClass.getConstructor().newInstance()
            val json = JSONObject()
            prefs.forEach { (k, v) -> json.put(k, v) }
            bundleClass.getMethod("putString", String::class.java, String::class.java)
                .invoke(bundle, "prefs", json.toString())
            dispatcherClass
                .getMethod("dispatch", String::class.java, bundleClass)
                .invoke(instance, "GeckoView:SetPrefs", bundle)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to apply Gecko prefs: ${t.message}")
        }
    }
}
