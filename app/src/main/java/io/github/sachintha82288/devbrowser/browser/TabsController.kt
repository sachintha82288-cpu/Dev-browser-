package io.github.sachintha82288.devbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sachintha82288.devbrowser.data.AppSettings
import io.github.sachintha82288.devbrowser.data.Library
import io.github.sachintha82288.devbrowser.downloads.Downloader
import io.github.sachintha82288.devbrowser.util.UrlUtils
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebResponse
import java.util.UUID

/**
 * Central tab manager (application-scoped): owns the list of [BrowserTab]s,
 * the selected tab, and all pending UI requests (prompts, permissions).
 */
class TabsController(
    val appContext: Context,
    val engine: GeckoEngine,
    val settings: AppSettings,
    val library: Library,
) {
    var tabs by mutableStateOf(listOf<BrowserTab>())
        private set

    var selectedId by mutableStateOf<String?>(null)

    val selectedTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == selectedId } ?: tabs.firstOrNull()

    /** Pending website prompt / permission dialogs rendered by the UI. */
    var activePrompt: ActivePrompt? by mutableStateOf(null)
        private set

    var activePermission: ActivePermission? by mutableStateOf(null)
        private set

    var activeExtensionPrompt: ActiveExtensionPrompt? by mutableStateOf(null)
        private set

    /** Simple message surface for snack-style events. */
    var snackbarMessage: String? by mutableStateOf(null)

    /** Incremented when the UI should open (Ctrl+F) / close the find-in-page bar. */
    var showFindBarRequest: Int by mutableStateOf(0)

    /** Incremented when the UI should focus the URL bar (Ctrl+L). */
    var focusUrlBarRequest: Int by mutableStateOf(0)

    /** Set by MainActivity so delegates can start activities / request permissions. */
    var intentLauncher: ((Intent) -> Unit)? = null
    var androidPermissionRequester: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tabsPrefs = appContext.getSharedPreferences("devbrowser_tabs", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // Tab lifecycle
    // ------------------------------------------------------------------

    fun createTab(isPrivate: Boolean = false, openIt: Boolean = true): BrowserTab {
        val sessionSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .suspendMediaWhenInactive(true)
            .build()
        val session = GeckoSession(sessionSettings)
        val tab = BrowserTab(
            id = UUID.randomUUID().toString(),
            session = session,
            isPrivate = isPrivate,
        )
        BrowserDelegates.attachTo(tab, this)
        if (openIt) {
            session.open(engine.runtime)
        }
        tabs = tabs + tab
        return tab
    }

    fun newTab(isPrivate: Boolean = false, url: String? = null): BrowserTab {
        val tab = createTab(isPrivate = isPrivate)
        selectedId = tab.id
        url?.let { tab.load(it) }
        persistTabs()
        return tab
    }

    fun selectTab(id: String) {
        if (tabs.none { it.id == id }) return
        selectedId = id
        val tab = selectedTab
        if (tab != null) {
            engine.webExtensionController.setTabActive(tab.session, true)
        }
        persistTabs()
    }

    fun closeTab(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        try {
            tab.session.close()
        } catch (e: Throwable) {
            Log.w(TAG, "closeTab: session already closed", e)
        }
        val wasSelected = tab.id == selectedId
        val index = tabs.indexOf(tab)
        tabs = tabs - tab
        if (wasSelected) {
            val next = tabs.getOrNull((index - 1).coerceAtLeast(0)) ?: tabs.firstOrNull()
            selectedId = next?.id
            if (next != null) {
                engine.webExtensionController.setTabActive(next.session, true)
            }
        }
        if (activePrompt?.tabSession === tab.session) activePrompt = null
        if (tabs.isEmpty()) {
            // A browser without tabs is a sad browser; restore the home state.
            newTab()
        }
        persistTabs()
    }

    fun closeAllTabs(privateOnly: Boolean = false) {
        val toClose = if (privateOnly) tabs.filter { it.isPrivate } else tabs.toList()
        toClose.forEach { tab ->
            try {
                tab.session.close()
            } catch (_: Throwable) {
            }
        }
        tabs = if (privateOnly) tabs.filterNot { it.isPrivate } else emptyList()
        if (selectedTab == null) newTab()
        persistTabs()
    }

    /** Number of tracker requests blocked across all tabs. */
    fun totalBlockedCount(): Int = tabs.sumOf { it.blockedCount }

    // ------------------------------------------------------------------
    // Navigation helpers
    // ------------------------------------------------------------------

    fun loadInSelectedTab(input: String) {
        val tab = selectedTab ?: newTab()
        if (UrlUtils.looksLikeUrl(input)) {
            tab.load(UrlUtils.toUrl(input))
        } else {
            val query = UrlUtils.searchUrl(input, settings.searchEngine.queryTemplate)
            tab.load(query)
        }
    }

    fun toggleDesktopMode() {
        val tab = selectedTab ?: return
        val newMode = !tab.isDesktopMode
        tab.isDesktopMode = newMode
        tab.session.settings.setViewportMode(
            if (newMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else GeckoSessionSettings.VIEWPORT_MODE_MOBILE,
        )
        tab.session.settings.setUserAgentMode(
            if (newMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else GeckoSessionSettings.USER_AGENT_MODE_MOBILE,
        )
        tab.session.reload()
    }

    // ------------------------------------------------------------------
    // Request surfaces (prompts, permissions, downloads)
    // ------------------------------------------------------------------

    fun showPrompt(prompt: ActivePrompt) {
        mainHandler.post {
            if (activePrompt != null) {
                // Only one dialog at a time; drop duplicate requests (pages rarely
                // trigger two prompts at once).
                Log.w(TAG, "Dropping extra prompt while another one is active")
                return@post
            }
            activePrompt = prompt
        }
    }

    fun dismissPrompt() {
        activePrompt = null
    }

    fun requestPermission(permission: ActivePermission) {
        mainHandler.post {
            if (activePermission != null) {
                permission.onAnswer(false)
                return@post
            }
            activePermission = permission
        }
    }

    fun answerPermission(granted: Boolean) {
        activePermission?.let { it.onAnswer(granted) }
        activePermission = null
    }

    fun requestAndroidPermissions(permissions: Array<String>, onResult: (Boolean) -> Unit) {
        val requester = androidPermissionRequester
        if (requester == null) {
            onResult(false)
            return
        }
        requester(permissions) { granted -> mainHandler.post { onResult(granted) } }
    }

    fun showExtensionPrompt(prompt: ActiveExtensionPrompt) {
        mainHandler.post { activeExtensionPrompt = prompt }
    }

    fun answerExtensionPrompt(granted: Boolean, privateGranted: Boolean) {
        activeExtensionPrompt?.let { it.onAnswer(granted, privateGranted) }
        activeExtensionPrompt = null
    }

    fun showSnackbar(message: String) {
        mainHandler.post { snackbarMessage = message }
    }

    fun consumeSnackbar() {
        snackbarMessage = null
    }

    fun launchIntent(intent: Intent) {
        val toLaunch = if (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent
        } else {
            intent
        }
        intentLauncher?.invoke(toLaunch) ?: try {
            appContext.startActivity(toLaunch)
        } catch (e: Throwable) {
            Log.w(TAG, "Could not launch intent", e)
        }
    }

    fun download(response: WebResponse) {
        val ok = Downloader.enqueue(appContext, response)
        showSnackbar(
            if (ok) "Download started: ${response.uri.substringAfterLast('/')}"
            else "Download failed",
        )
    }

    // ------------------------------------------------------------------
    // Session persistence (URLs only — lightweight restore)
    // ------------------------------------------------------------------

    fun persistTabs() {
        if (!settings.restoreTabs) return
        try {
            val array = JSONArray()
            tabs.forEach { tab ->
                tab.url?.let { url ->
                    if (URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url)) {
                        array.put(
                            JSONObject()
                                .put("url", url)
                                .put("private", tab.isPrivate),
                        )
                    }
                }
            }
            tabsPrefs.edit().putString("open_tabs", array.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "persistTabs failed", e)
        }
    }

    fun restoreTabs() {
        if (!settings.restoreTabs) return
        val raw = tabsPrefs.getString("open_tabs", null) ?: return
        try {
            val array = JSONArray(raw)
            if (array.length() == 0) return
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val url = obj.optString("url")
                val isPrivate = obj.optBoolean("private", false)
                if (url.isNotBlank()) {
                    createTab(isPrivate = isPrivate).load(url)
                }
            }
            selectedId = tabs.firstOrNull()?.id
        } catch (e: Exception) {
            Log.w(TAG, "restoreTabs failed", e)
        }
    }

    fun startup() {
        restoreTabs()
        if (tabs.isEmpty()) {
            newTab()
        } else {
            selectedTab?.let { engine.webExtensionController.setTabActive(it.session, true) }
        }
    }

    companion object {
        private const val TAG = "DevBrowser/Tabs"
    }
}
