package com.devbrowser.app.extensions

import android.content.Context
import android.net.Uri
import android.util.Log
import com.devbrowser.app.browser.GeckoEngine
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File

/**
 * Installs and manages Firefox WebExtensions (.xpi add-ons).
 *
 * - Built-in tools extension is loaded from assets on start-up.
 * - User add-ons can be installed from an addons.mozilla.org URL or from a
 *   local .xpi file picked with the system file picker.
 */
object ExtensionManager {

    private const val TAG = "ExtensionManager"
    const val BUILTIN_ID = "devtools@devbrowser.app"
    private const val BUILTIN_LOCATION = "resource://android/assets/addons/devtools/"

    private val controller: WebExtensionController
        get() = GeckoEngine.runtime.webExtensionController

    val installed = mutableListOf<WebExtension>()
    var onChanged: (() -> Unit)? = null

    fun installBuiltin(onReady: (WebExtension) -> Unit = {}) {
        controller.ensureBuiltIn(BUILTIN_LOCATION, BUILTIN_ID).accept({ ext ->
            if (ext != null) {
                DevToolsBridge.attach(ext)
                onReady(ext)
                Log.i(TAG, "Built-in dev tools extension ready")
            }
        }, { e -> Log.e(TAG, "Failed to load built-in extension", e) })
    }

    fun refresh(onDone: (List<WebExtension>) -> Unit) {
        controller.list().accept({ list ->
            installed.clear()
            list?.let { installed.addAll(it) }
            onChanged?.invoke()
            onDone(installed)
        }, { e ->
            Log.e(TAG, "list() failed", e)
            onDone(emptyList())
        })
    }

    /** Installs an add-on straight from a URL (e.g. an AMO .xpi link). */
    fun installFromUrl(url: String, cb: (WebExtension?, Throwable?) -> Unit) {
        controller.install(url).accept(
            { ext -> ext?.let { enableIfNeeded(it) }; cb(ext, null); refresh {} },
            { e -> cb(null, e) }
        )
    }

    /** Copies a picked .xpi into app storage and installs it from file://. */
    fun installFromFile(context: Context, uri: Uri, cb: (WebExtension?, Throwable?) -> Unit) {
        try {
            val dir = File(context.filesDir, "xpi").apply { mkdirs() }
            val name = (uri.lastPathSegment ?: "addon").substringAfterLast('/')
                .let { if (it.endsWith(".xpi")) it else "$it.xpi" }
            val dest = File(dir, name)
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw IllegalStateException("Cannot read the selected file")
                dest.outputStream().use { input.copyTo(it) }
            }
            installFromUrl(Uri.fromFile(dest).toString(), cb)
        } catch (t: Throwable) {
            cb(null, t)
        }
    }

    fun uninstall(ext: WebExtension, cb: (Throwable?) -> Unit) {
        controller.uninstall(ext).accept({ cb(null); refresh {} }, { e -> cb(e) })
    }

    fun setEnabled(ext: WebExtension, enabled: Boolean, cb: (Throwable?) -> Unit) {
        val source = WebExtensionController.EnableSource.USER
        val result: GeckoResult<WebExtension> =
            if (enabled) controller.enable(ext, source) else controller.disable(ext, source)
        result.accept({ cb(null); refresh {} }, { e -> cb(e) })
    }

    private fun enableIfNeeded(ext: WebExtension) {
        if (ext.metaData.enabled) return
        controller.enable(ext, WebExtensionController.EnableSource.USER)
    }

    /**
     * Handles the install prompt / update prompt so add-ons can actually be
     * installed without a desktop-style doorhanger.
     */
    fun installPromptDelegate(autoApprove: () -> Boolean) =
        object : WebExtensionController.PromptDelegate {
            override fun onInstallPrompt(extension: WebExtension): GeckoResult<AllowOrDeny> =
                GeckoResult.fromValue(
                    if (autoApprove()) AllowOrDeny.ALLOW else AllowOrDeny.DENY
                )

            override fun onUpdatePrompt(
                current: WebExtension,
                updated: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>
            ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }
}
