package com.devbrowser.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.devbrowser.app.browser.GeckoEngine
import com.devbrowser.app.browser.TabManager
import com.devbrowser.app.data.Prefs
import com.devbrowser.app.databinding.ActivityProxyBinding
import com.devbrowser.app.proxy.ProxyController

class ProxyActivity : AppCompatActivity() {

    private lateinit var b: ActivityProxyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProxyBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        when (Prefs.proxyMode) {
            Prefs.MODE_TOR -> b.modeTor.isChecked = true
            Prefs.MODE_MANUAL -> b.modeManual.isChecked = true
            else -> b.modeNone.isChecked = true
        }
        if (Prefs.proxyType == "http") b.typeHttp.isChecked = true else b.typeSocks.isChecked = true
        b.proxyHost.setText(Prefs.proxyHost)
        b.proxyPort.setText(Prefs.proxyPort.toString())
        b.proxyDns.isChecked = Prefs.proxyDns
        updateStatus()

        b.btnStartOrbot.setOnClickListener {
            if (ProxyController.isOrbotInstalled(this)) {
                ProxyController.requestOrbotStart(this)
                toast("Asked Orbot to start Tor")
            } else {
                TabManager.newTab("https://play.google.com/store/apps/details?id=org.torproject.android")
                finish()
            }
        }

        b.btnApply.setOnClickListener {
            Prefs.proxyMode = when {
                b.modeTor.isChecked -> Prefs.MODE_TOR
                b.modeManual.isChecked -> Prefs.MODE_MANUAL
                else -> Prefs.MODE_NONE
            }
            Prefs.proxyType = if (b.typeHttp.isChecked) "http" else "socks"
            Prefs.proxyHost = b.proxyHost.text.toString().ifBlank { "127.0.0.1" }
            Prefs.proxyPort = b.proxyPort.text.toString().toIntOrNull() ?: 9050
            Prefs.proxyDns = b.proxyDns.isChecked

            if (Prefs.proxyMode == Prefs.MODE_TOR && ProxyController.isOrbotInstalled(this)) {
                ProxyController.requestOrbotStart(this)
            }
            GeckoEngine.applyRuntimePrefs()
            TabManager.selected?.reload()
            updateStatus()
            toast("Applied: " + ProxyController.describe())
        }

        b.btnTest.setOnClickListener {
            val url = if (Prefs.proxyMode == Prefs.MODE_TOR)
                "https://check.torproject.org" else "https://ifconfig.me"
            TabManager.newTab(url)
            finish()
        }
    }

    private fun updateStatus() {
        val orbot = if (ProxyController.isOrbotInstalled(this)) "installed" else "not installed"
        b.torStatus.text = "Current: ${ProxyController.describe()}\nOrbot: $orbot"
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
