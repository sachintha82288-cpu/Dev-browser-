package com.devbrowser.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.devbrowser.app.browser.GeckoEngine
import com.devbrowser.app.browser.TabManager
import com.devbrowser.app.data.Prefs
import com.devbrowser.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        b.homePage.setText(Prefs.homePage)
        b.searchEngine.setText(Prefs.searchEngine)
        b.userAgent.setText(Prefs.customUserAgent)
        b.cbJs.isChecked = Prefs.javascriptEnabled
        b.cbRemote.isChecked = Prefs.remoteDebugging
        b.cbDesktop.isChecked = Prefs.desktopMode
        b.cbTp.isChecked = Prefs.trackingProtection

        b.btnSave.setOnClickListener {
            Prefs.homePage = b.homePage.text.toString().ifBlank { "https://duckduckgo.com" }
            Prefs.searchEngine = b.searchEngine.text.toString().ifBlank { "https://duckduckgo.com/?q=%s" }
            Prefs.customUserAgent = b.userAgent.text.toString()
            Prefs.javascriptEnabled = b.cbJs.isChecked
            Prefs.remoteDebugging = b.cbRemote.isChecked
            Prefs.desktopMode = b.cbDesktop.isChecked
            Prefs.trackingProtection = b.cbTp.isChecked
            GeckoEngine.applyRuntimePrefs()
            TabManager.selected?.setDesktopMode(Prefs.desktopMode)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        b.btnAboutConfig.setOnClickListener {
            TabManager.newTab("about:config")
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
