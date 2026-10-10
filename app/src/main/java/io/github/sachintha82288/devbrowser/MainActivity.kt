package io.github.sachintha82288.devbrowser

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.sachintha82288.devbrowser.ui.BrowserScreen
import io.github.sachintha82288.devbrowser.ui.theme.DevBrowserTheme
import io.github.sachintha82288.devbrowser.util.UrlUtils

class MainActivity : ComponentActivity() {

    private var pendingAndroidPermissionCallback: ((Boolean) -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val cb = pendingAndroidPermissionCallback
            pendingAndroidPermissionCallback = null
            cb?.invoke(grants.values.isNotEmpty() && grants.values.all { it })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DevBrowserApplication

        // Let Gecko delegates start activities and request runtime permissions.
        app.tabs.intentLauncher = { intent -> startActivitySafely(intent) }
        app.tabs.androidPermissionRequester = { permissions, callback ->
            pendingAndroidPermissionCallback = callback
            permissionLauncher.launch(permissions)
        }

        handleBrowserIntent(intent)

        setContent {
            DevBrowserTheme(amoledBlack = app.settings.amoledBlack) {
                BrowserScreen(app = app, activity = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleBrowserIntent(intent)
    }

    private fun handleBrowserIntent(intent: Intent?) {
        if (intent == null) return
        val app = application as DevBrowserApplication
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString ?: return
                app.tabs.newTab(url = url)
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val trimmed = text.trim()
                if (trimmed.isEmpty()) return
                if (UrlUtils.looksLikeUrl(trimmed)) {
                    app.tabs.newTab(url = UrlUtils.toUrl(trimmed))
                } else {
                    app.tabs.newTab(url = UrlUtils.searchUrl(trimmed, app.settings.searchEngine.queryTemplate))
                }
            }
        }
    }

    private fun startActivitySafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // No handler for the intent — ignore.
        }
    }

    /** Hardware/Bluetooth keyboard shortcuts for desktop-style use. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val app = application as DevBrowserApplication
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_T -> { app.tabs.newTab(); return true }
                KeyEvent.KEYCODE_W -> {
                    app.tabs.selectedId?.let { app.tabs.closeTab(it) }
                    return true
                }
                KeyEvent.KEYCODE_R -> {
                    app.tabs.selectedTab?.session?.reload()
                    return true
                }
                KeyEvent.KEYCODE_F -> { app.tabs.showFindBarRequest += 1; return true }
                KeyEvent.KEYCODE_L -> { app.tabs.focusUrlBarRequest += 1; return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
