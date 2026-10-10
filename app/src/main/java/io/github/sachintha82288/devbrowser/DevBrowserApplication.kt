package io.github.sachintha82288.devbrowser

import android.app.Application
import io.github.sachintha82288.devbrowser.browser.GeckoEngine
import io.github.sachintha82288.devbrowser.browser.TabsController
import io.github.sachintha82288.devbrowser.data.AppSettings
import io.github.sachintha82288.devbrowser.data.Library
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

    override fun onCreate() {
        super.onCreate()

        settings = AppSettings(this)
        library = Library(this)
        engine = GeckoEngine(this, settings)
        tabs = TabsController(this, engine, settings, library)
        extensions = ExtensionManager(this, tabs, appScope)

        extensions.start()
        tabs.startup()
    }
}
