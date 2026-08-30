package com.devbrowser.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.devbrowser.app.R
import com.devbrowser.app.browser.GeckoEngine
import com.devbrowser.app.browser.Tab
import com.devbrowser.app.browser.TabManager
import com.devbrowser.app.data.Prefs
import com.devbrowser.app.databinding.ActivityBrowserBinding
import com.devbrowser.app.extensions.ExtensionManager
import com.devbrowser.app.proxy.ProxyController
import com.devbrowser.app.tools.ConsoleActivity
import com.devbrowser.app.tools.CookieEditorActivity
import com.devbrowser.app.tools.NetworkLogActivity
import com.devbrowser.app.tools.SourceEditorActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class BrowserActivity : AppCompatActivity() {

    private lateinit var b: ActivityBrowserBinding
    private lateinit var tabsAdapter: TabStripAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(b.root)

        GeckoEngine.runtime.webExtensionController.promptDelegate =
            ExtensionManager.installPromptDelegate { true }
        ExtensionManager.installBuiltin()

        setupTabStrip()
        setupUrlBar()
        setupButtons()

        TabManager.listener = { runOnUiThread { render() } }
        if (TabManager.tabs.isEmpty()) {
            TabManager.newTab(intent?.dataString ?: Prefs.homePage)
        }
        attachSelected()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tab = TabManager.selected
                if (tab != null && tab.canGoBack) tab.goBack() else moveTaskToBack(true)
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { TabManager.newTab(it); attachSelected() }
    }

    /* ------------------------------------------------------------------ */

    private fun setupTabStrip() {
        tabsAdapter = TabStripAdapter(
            onSelect = { index -> TabManager.select(index); attachSelected() },
            onClose = { index -> TabManager.close(index); attachSelected() }
        )
        b.tabStrip.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.RecyclerView.HORIZONTAL, false)
        b.tabStrip.adapter = tabsAdapter
    }

    private fun setupUrlBar() {
        b.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                TabManager.selected?.load(b.urlInput.text.toString())
                b.urlInput.clearFocus()
                true
            } else false
        }
        b.btnReload.setOnClickListener {
            val tab = TabManager.selected ?: return@setOnClickListener
            if (tab.isLoading) tab.stop() else tab.reload()
        }
        b.proxyBadge.setOnClickListener { startActivity(Intent(this, ProxyActivity::class.java)) }
    }

    private fun setupButtons() {
        b.btnBack.setOnClickListener { TabManager.selected?.goBack() }
        b.btnForward.setOnClickListener { TabManager.selected?.goForward() }
        b.btnNewTab.setOnClickListener { TabManager.newTab(); attachSelected() }
        b.btnTabs.setOnClickListener { showTabsSheet() }
        b.btnDevTools.setOnClickListener { showDevToolsSheet() }
        b.btnMenu.setOnClickListener { showMenuSheet() }
    }

    private fun attachSelected() {
        val tab: Tab = TabManager.selected ?: return
        tab.open()
        b.geckoView.setSession(tab.session)
        render()
    }

    private fun render() {
        val tab = TabManager.selected
        tabsAdapter.submit(TabManager.tabs, TabManager.selectedIndex)

        if (tab == null) return
        if (!b.urlInput.hasFocus()) b.urlInput.setText(tab.url)

        b.progressBar.progress = tab.progress
        b.progressBar.visibility = if (tab.isLoading) View.VISIBLE else View.INVISIBLE
        b.securityIcon.setColorFilter(
            getColor(if (tab.isSecure) R.color.gx_green else R.color.gx_warn)
        )
        b.btnBack.isEnabled = tab.canGoBack
        b.btnForward.isEnabled = tab.canGoForward
        b.btnBack.alpha = if (tab.canGoBack) 1f else 0.4f
        b.btnForward.alpha = if (tab.canGoForward) 1f else 0.4f

        val proxied = Prefs.proxyMode != Prefs.MODE_NONE
        b.proxyBadge.setColorFilter(
            getColor(
                when {
                    ProxyController.isTor() -> R.color.gx_purple
                    proxied -> R.color.gx_accent_alt
                    else -> R.color.gx_text_dim
                }
            )
        )
    }

    /* ---------------------------- sheets ---------------------------- */

    private fun showTabsSheet() {
        val titles = TabManager.tabs.mapIndexed { i, t ->
            (if (i == TabManager.selectedIndex) "● " else "○ ") +
                (if (t.isPrivate) "[private] " else "") + t.title
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tabs)
            .setItems(titles) { _, which -> TabManager.select(which); attachSelected() }
            .setPositiveButton(R.string.new_tab) { _, _ -> TabManager.newTab(); attachSelected() }
            .setNeutralButton(R.string.new_private_tab) { _, _ ->
                TabManager.newTab(isPrivate = true); attachSelected()
            }
            .show()
    }

    private fun showDevToolsSheet() {
        val items = arrayOf(
            getString(R.string.source_editor),
            getString(R.string.cookie_editor),
            getString(R.string.js_console),
            getString(R.string.network_log),
            "View source (view-source:)",
            "Storage inspector"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dev_tools)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, SourceEditorActivity::class.java))
                    1 -> startActivity(Intent(this, CookieEditorActivity::class.java))
                    2 -> startActivity(Intent(this, ConsoleActivity::class.java))
                    3 -> startActivity(Intent(this, NetworkLogActivity::class.java))
                    4 -> TabManager.selected?.let {
                        TabManager.newTab("view-source:${it.url}"); attachSelected()
                    }
                    5 -> startActivity(
                        Intent(this, CookieEditorActivity::class.java)
                            .putExtra(CookieEditorActivity.EXTRA_STORAGE_MODE, true)
                    )
                }
            }
            .show()
    }

    private fun showMenuSheet() {
        val tab = TabManager.selected
        val items = arrayOf(
            getString(R.string.add_bookmark),
            getString(R.string.bookmarks),
            getString(R.string.desktop_site) + if (Prefs.desktopMode) " ✓" else "",
            getString(R.string.share),
            getString(R.string.extensions),
            getString(R.string.proxy_and_tor),
            getString(R.string.settings)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> tab?.let { Prefs.addBookmark(it.url); toast("Bookmarked") }
                    1 -> showBookmarks()
                    2 -> {
                        Prefs.desktopMode = !Prefs.desktopMode
                        tab?.setDesktopMode(Prefs.desktopMode)
                    }
                    3 -> tab?.let {
                        startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, it.url),
                                getString(R.string.share)
                            )
                        )
                    }
                    4 -> startActivity(Intent(this, ExtensionsActivity::class.java))
                    5 -> startActivity(Intent(this, ProxyActivity::class.java))
                    6 -> startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            .show()
    }

    private fun showBookmarks() {
        val marks = Prefs.bookmarks()
        if (marks.isEmpty()) {
            toast("No bookmarks yet")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bookmarks)
            .setItems(marks.toTypedArray()) { _, which ->
                TabManager.selected?.load(marks[which])
            }
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        GeckoEngine.applyRuntimePrefs()
        render()
    }
}
