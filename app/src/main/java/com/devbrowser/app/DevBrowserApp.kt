package com.devbrowser.app

import android.app.Application
import com.devbrowser.app.browser.GeckoEngine
import com.devbrowser.app.data.Prefs

class DevBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        GeckoEngine.init(this)
    }

    companion object {
        lateinit var instance: DevBrowserApp
            private set
    }
}
