package io.github.sachintha82288.devbrowser.extensions

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.ActiveExtensionPrompt
import io.github.sachintha82288.devbrowser.browser.TabsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Immutable snapshot of an installed extension for the UI. */
data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
    val allowedInPrivateBrowsing: Boolean,
    val optionsPageUrl: String?,
    val homepageUrl: String?,
)

/**
 * WebExtension ("add-on") support built directly on GeckoView:
 *  - install from a direct .xpi URL,
 *  - install from an addons.mozilla.org page link (resolved through the public AMO API),
 *  - install from a local .xpi file,
 *  - enable / disable / uninstall / update / allow-in-private-tabs.
 */
class ExtensionManager(
    private val appContext: Context,
    private val controller: TabsController,
    private val engineScope: CoroutineScope,
) {
    var extensions by mutableStateOf(listOf<ExtensionInfo>())
        private set

    var busy by mutableStateOf(false)
        private set

    /** Raw GeckoView extension objects from the last refresh. */
    private var rawExtensions: List<WebExtension> = emptyList()

    private val extController: WebExtensionController = controller.engine.webExtensionController

    fun start() {
        extController.promptDelegate = PromptHandler()
        // The controller only exposes a setter here, so there is no synthetic
        // Kotlin property to assign to.
        extController.setDebuggerDelegate(DebuggerHandler())
        refresh()
    }

    fun refresh() {
        extController.list().accept({ result ->
            // GeckoView hands the list over nullable.
            val list: List<WebExtension> = result.orEmpty()
            rawExtensions = list
            extensions = list.map { it.toInfo() }.sortedBy { it.name.lowercase() }
        }) { e ->
            Log.w(TAG, "list() failed", e)
        }
    }

    /** Install from a URL that is either a direct .xpi link or an AMO add-on page. */
    fun installFromUrl(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isEmpty()) return
        busy = true
        engineScope.launch {
            val resolved = try {
                resolveAmoPage(url)
            } catch (e: Exception) {
                Log.w(TAG, "AMO resolve failed", e)
                null
            }
            if (resolved == null && !url.startsWith("http")) {
                busy = false
                controller.showSnackbar(appContext.getString(R.string.could_not_resolve_addon))
                return@launch
            }
            install(resolved ?: url)
        }
    }

    /** Install from a SAF content URI pointing at a local .xpi file. */
    fun installFromFile(uri: Uri) {
        busy = true
        engineScope.launch {
            val staged = withContext(Dispatchers.IO) { stageFile(uri) }
            if (staged == null) {
                busy = false
                controller.showSnackbar(appContext.getString(R.string.could_not_read_file))
                return@launch
            }
            install("file://$staged", fromFile = true)
        }
    }

    private fun install(url: String, fromFile: Boolean = false) {
        val method = if (fromFile) WebExtensionController.INSTALLATION_METHOD_FROM_FILE else null
        val result: GeckoResult<WebExtension> =
            if (method != null) extController.install(url, method) else extController.install(url)
        result.accept({ ext ->
            busy = false
            val name = ext?.metaData?.name ?: ext?.id ?: url.substringAfterLast('/')
            controller.showSnackbar(
                appContext.getString(R.string.extension_installed, name),
            )
            refresh()
        }) { e ->
            busy = false
            val reason =
                if (e is WebExtension.InstallException) {
                    describeError(e)
                } else {
                    e?.message ?: "unknown error"
                }
            controller.showSnackbar(appContext.getString(R.string.install_failed_reason, reason))
        }
    }

    fun uninstall(info: ExtensionInfo) {
        findExtension(info.id)?.let { ext ->
            extController.uninstall(ext).accept({
                controller.showSnackbar(
                    appContext.getString(R.string.extension_uninstalled, info.name),
                )
                refresh()
            }) { e ->
                controller.showSnackbar(
                    appContext.getString(R.string.uninstall_failed_reason, e?.message ?: ""),
                )
            }
        }
    }

    fun setEnabled(info: ExtensionInfo, enabled: Boolean) {
        findExtension(info.id)?.let { ext ->
            val result = if (enabled) {
                extController.enable(ext, WebExtensionController.EnableSource.USER)
            } else {
                extController.disable(ext, WebExtensionController.EnableSource.USER)
            }
            result.accept({ refresh() }) { e ->
                controller.showSnackbar(
                    appContext.getString(R.string.addon_change_failed, e?.message ?: ""),
                )
            }
        }
    }

    fun setPrivateBrowsing(info: ExtensionInfo, allowed: Boolean) {
        findExtension(info.id)?.let { ext ->
            extController.setAllowedInPrivateBrowsing(ext, allowed).accept({
                refresh()
            }) { e ->
                controller.showSnackbar(
                    appContext.getString(R.string.addon_change_failed, e?.message ?: ""),
                )
            }
        }
    }

    fun update(info: ExtensionInfo) {
        findExtension(info.id)?.let { ext ->
            busy = true
            extController.update(ext).accept({ updated ->
                busy = false
                if (updated == null || updated.metaData.version == info.version) {
                    controller.showSnackbar(
                        appContext.getString(R.string.extension_update_no_update),
                    )
                } else {
                    controller.showSnackbar(
                        appContext.getString(
                            R.string.updated_to_version,
                            updated.metaData.version,
                        ),
                    )
                }
                refresh()
            }) { e ->
                busy = false
                controller.showSnackbar(
                    appContext.getString(R.string.update_check_failed, e?.message ?: ""),
                )
            }
        }
    }

    private fun findExtension(id: String): WebExtension? =
        rawExtensions.firstOrNull { it.id == id }

    // ------------------------------------------------------------------
    // AMO link resolution
    // ------------------------------------------------------------------

    private val amoPageRegex =
        Regex("""^https?://addons\.mozilla\.org/(?:[a-zA-Z-]+/)?firefox/addon/([^/?#]+)/?""")

    /**
     * When the user pastes an AMO *page* URL, ask the public AMO API for the
     * actual .xpi URL (preferring the Android build of the extension).
     */
    private suspend fun resolveAmoPage(url: String): String? = withContext(Dispatchers.IO) {
        val match = amoPageRegex.find(url) ?: return@withContext null
        val slug = match.groupValues[1]
        val apiUrl = "https://addons.mozilla.org/api/v5/addons/addon/$slug/?lang=en-US"

        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")

        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val version = root.optJSONObject("current_version") ?: return@withContext null
            val files = version.optJSONArray("files") ?: return@withContext null
            var fallback: String? = null
            for (i in 0 until files.length()) {
                val file = files.optJSONObject(i) ?: continue
                val fileUrl = file.optString("url", "")
                if (fileUrl.isBlank()) continue
                val absolute = if (fileUrl.startsWith("http")) fileUrl else "https://addons.mozilla.org$fileUrl"
                if (file.optString("platform") == "android") return@withContext absolute
                if (fallback == null) fallback = absolute
            }
            fallback
        } finally {
            connection.disconnect()
        }
    }

    /** Copy a content URI into the app cache so GeckoView can read it as file://. */
    private fun stageFile(uri: Uri): String? = try {
        val dir = File(appContext.cacheDir, "extensions").apply { mkdirs() }
        val file = File(dir, "staged-${System.currentTimeMillis()}.xpi")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file.absolutePath
    } catch (e: IOException) {
        Log.w(TAG, "stageFile failed", e)
        null
    }

    private fun describeError(e: WebExtension.InstallException): String = when (e.code) {
        WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE -> "network failure"
        WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE -> "corrupt file"
        WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED -> "extension is not signed by Mozilla"
        WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_ID -> "manifest ID mismatch"
        WebExtension.InstallException.ErrorCodes.ERROR_INVALID_DOMAIN -> "not allowed from this domain"
        WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED -> "extension is blocklisted"
        WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE,
        WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE,
        WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE,
        WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_VERSION,
        -> "not compatible with this browser"
        else -> "error ${e.code}"
    }

    private fun WebExtension.toInfo(): ExtensionInfo {
        val meta = metaData
        return ExtensionInfo(
            id = id,
            name = meta.name ?: id,
            version = meta.version,
            description = meta.description ?: "",
            enabled = meta.enabled,
            allowedInPrivateBrowsing = meta.allowedInPrivateBrowsing,
            optionsPageUrl = meta.optionsPageUrl,
            homepageUrl = meta.homepageUrl,
        )
    }

    /** Shows the install-permission dialog through [TabsController] UI state. */
    private inner class PromptHandler : WebExtensionController.PromptDelegate {

        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollectionPermissions: Array<out String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse>? {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            controller.showExtensionPrompt(
                ActiveExtensionPrompt(
                    extensionName = extension.metaData.name ?: extension.id,
                    permissions = permissions.toList() + origins.map { "access: $it" },
                    isUpdate = false,
                    onAnswer = { granted, privateGranted ->
                        result.complete(
                            WebExtension.PermissionPromptResponse(
                                granted,
                                privateGranted,
                                granted,
                            ),
                        )
                    },
                ),
            )
            return result
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<out String>,
            newOrigins: Array<out String>,
            newDataCollectionPermissions: Array<out String>,
        ): GeckoResult<AllowOrDeny>? {
            val result = GeckoResult<AllowOrDeny>()
            controller.showExtensionPrompt(
                ActiveExtensionPrompt(
                    extensionName = extension.metaData.name ?: extension.id,
                    permissions = newPermissions.toList() + newOrigins.map { "access: $it" },
                    isUpdate = true,
                    onAnswer = { granted, _ ->
                        result.complete(
                            if (granted) AllowOrDeny.ALLOW else AllowOrDeny.DENY,
                        )
                    },
                ),
            )
            return result
        }
    }

    private inner class DebuggerHandler : WebExtensionController.DebuggerDelegate {
        // Fired when extensions are installed/updated via desktop web-ext debugging.
        override fun onExtensionListUpdated() {
            refresh()
        }
    }

    companion object {
        private const val TAG = "DevBrowser/Extensions"
        private const val USER_AGENT =
            "DevBrowser/0.1 (Android; GeckoView) Mozilla/5.0 (Android)"
    }
}
