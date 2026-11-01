package io.github.sachintha82288.devbrowser

import android.app.Application
import io.github.sachintha82288.devbrowser.browser.GeckoEngine
import io.github.sachintha82288.devbrowser.browser.TabsController
import io.github.sachintha82288.devbrowser.data.AppSettings
import io.github.sachintha82288.devbrowser.data.Library
import io.github.sachintha82288.devbrowser.debug.DebugRuntime
import io.github.sachintha82288.devbrowser.debug.DebugStore
import io.github.sachintha82288.devbrowser.extensions.ExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Application entry point: owns the Gecko runtime and all browser state. */
class DevBrowserApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var settings: AppSettings
        private set
    lateinit var library: Library
        private set
    lateinit var engine: GeckoEngine
        private set
    lateinit var tabs: TabsController
        private set
    lateinit var extensions: ExtensionManager
        private set
    lateinit var debug: DebugRuntime
        private set

    override fun onCreate() {
        super.onCreate()

        settings = AppSettings(this)
        library = Library(this) { settings.encryptStorage }
        engine = GeckoEngine(this, settings)
        tabs = TabsController(this, engine, settings, library)
        extensions = ExtensionManager(this, tabs, appScope)

        extensions.start()
        tabs.startup()

        debug = DebugRuntime(
            settings = settings,
            store = DebugStore(this),
            tabsProvider = { tabs.tabs },
            selectedTabProvider = { tabs.selectedTab },
        )
        tabs.pageLifecycleListener = { tab, started ->
            if (started) debug.onPageStarted(tab) else debug.onPageStopped(tab)
        }
        tabs.tabClosedListener = { tabId -> debug.forget(tabId) }
        debug.startPolling(appScope)
    }

    /**
     * Stage B panic button: close every tab and wipe session data immediately.
     * Runs synchronously from the user's point of view — the wipe itself is
     * asynchronous inside the engine.
     */
    fun panic() {
        tabs.closeAllTabs()
        library.clearHistory()
        library.clearBookmarks()
        tabs.permissions.clearAll()
        engine.clearAllBrowsingData { }
        tabs.showSnackbar(getString(R.string.panic_done))
    }

    /** Stage B clear-data-on-exit: history + cookies/cache, tabs stay open. */
    fun clearSessionData() {
        library.clearHistory()
        engine.clearAllBrowsingData { }
    }
}
