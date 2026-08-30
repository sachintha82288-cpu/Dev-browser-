package com.devbrowser.app.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.devbrowser.app.R
import com.devbrowser.app.browser.TabManager
import com.devbrowser.app.browser.UrlUtils
import com.devbrowser.app.databinding.ActivityListBinding
import com.devbrowser.app.extensions.DevToolsBridge
import com.devbrowser.app.ui.Row
import com.devbrowser.app.ui.SimpleRowAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cookie editor for the current site (view / edit / add / delete), plus a
 * localStorage + sessionStorage inspector when started in storage mode.
 */
class CookieEditorActivity : AppCompatActivity() {

    private lateinit var b: ActivityListBinding
    private lateinit var adapter: SimpleRowAdapter
    private var storageMode = false

    private val pageUrl: String get() = TabManager.selected?.url ?: "about:blank"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        storageMode = intent.getBooleanExtra(EXTRA_STORAGE_MODE, false)
        title = if (storageMode) "Storage inspector" else getString(R.string.cookie_editor)

        adapter = SimpleRowAdapter(onClick = { row ->
            if (storageMode) editStorage(row) else editCookie(row.payload as JSONObject)
        })
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        b.btnPrimary.text = if (storageMode) "Add item" else "Add cookie"
        b.btnSecondary.text = "Clear all"
        b.btnPrimary.setOnClickListener {
            if (storageMode) editStorage(null) else editCookie(null)
        }
        b.btnSecondary.setOnClickListener { clearAll() }

        refresh()
    }

    /* --------------------------- loading --------------------------- */

    private fun refresh() = lifecycleScope.launch {
        try {
            if (storageMode) loadStorage() else loadCookies()
        } catch (t: Throwable) {
            toast("Failed: ${t.message}")
        }
    }

    private suspend fun loadCookies() {
        val arr = DevToolsBridge.requestResult("listCookies") { put("url", pageUrl) } as? JSONArray
            ?: JSONArray()
        val rows = (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            val flags = buildList {
                if (c.optBoolean("secure")) add("Secure")
                if (c.optBoolean("httpOnly")) add("HttpOnly")
                if (c.optBoolean("session")) add("Session")
                add("SameSite=" + c.optString("sameSite", "none"))
            }.joinToString(" · ")
            Row(c.optString("name"), "${c.optString("value")}\n${c.optString("domain")}${c.optString("path")}\n$flags", c)
        }
        b.header.text = "${rows.size} cookies for ${UrlUtils.host(pageUrl)}"
        adapter.submit(rows)
    }

    private suspend fun loadStorage() {
        val obj = DevToolsBridge.requestResult("getStorage") as? JSONObject ?: JSONObject()
        val rows = mutableListOf<Row>()
        listOf("local", "session").forEach { area ->
            val arr = obj.optJSONArray(area) ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                rows.add(
                    Row(
                        "[$area] ${item.optString("key")}",
                        item.optString("value"),
                        JSONObject().put("area", area)
                            .put("key", item.optString("key"))
                            .put("value", item.optString("value"))
                    )
                )
            }
        }
        b.header.text = "${rows.size} storage entries for ${UrlUtils.host(pageUrl)}"
        adapter.submit(rows)
    }

    /* --------------------------- editing --------------------------- */

    private fun editCookie(existing: JSONObject?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_cookie, null)
        val name = view.findViewById<EditText>(R.id.cName)
        val value = view.findViewById<EditText>(R.id.cValue)
        val domain = view.findViewById<EditText>(R.id.cDomain)
        val path = view.findViewById<EditText>(R.id.cPath)
        val secure = view.findViewById<CheckBox>(R.id.cSecure)
        val httpOnly = view.findViewById<CheckBox>(R.id.cHttpOnly)

        domain.setText(UrlUtils.host(pageUrl))
        existing?.let {
            name.setText(it.optString("name"))
            value.setText(it.optString("value"))
            domain.setText(it.optString("domain"))
            path.setText(it.optString("path", "/"))
            secure.isChecked = it.optBoolean("secure")
            httpOnly.isChecked = it.optBoolean("httpOnly")
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "New cookie" else "Edit cookie")
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                lifecycleScope.launch {
                    try {
                        DevToolsBridge.request("setCookie") {
                            put(
                                "cookie",
                                JSONObject()
                                    .put("url", pageUrl)
                                    .put("name", name.text.toString())
                                    .put("value", value.text.toString())
                                    .put("domain", domain.text.toString())
                                    .put("path", path.text.toString().ifBlank { "/" })
                                    .put("secure", secure.isChecked)
                                    .put("httpOnly", httpOnly.isChecked)
                            )
                        }
                        refresh()
                    } catch (t: Throwable) {
                        toast("Failed: ${t.message}")
                    }
                }
            }
            .setNegativeButton(R.string.close, null)

        if (existing != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    try {
                        DevToolsBridge.request("removeCookie") {
                            put("url", pageUrl)
                            put("name", existing.optString("name"))
                        }
                        refresh()
                    } catch (t: Throwable) {
                        toast("Failed: ${t.message}")
                    }
                }
            }
        }
        builder.show()
    }

    private fun editStorage(row: Row?) {
        val payload = row?.payload as? JSONObject
        val container = LayoutInflater.from(this).inflate(R.layout.dialog_cookie, null)
        val key = container.findViewById<EditText>(R.id.cName).apply { hint = "key" }
        val value = container.findViewById<EditText>(R.id.cValue).apply { hint = "value" }
        val area = container.findViewById<EditText>(R.id.cDomain).apply {
            hint = "area (local | session)"
            setText(payload?.optString("area") ?: "local")
        }
        container.findViewById<EditText>(R.id.cPath).visibility = android.view.View.GONE
        container.findViewById<CheckBox>(R.id.cSecure).visibility = android.view.View.GONE
        container.findViewById<CheckBox>(R.id.cHttpOnly).visibility = android.view.View.GONE

        payload?.let {
            key.setText(it.optString("key"))
            value.setText(it.optString("value"))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (payload == null) "New storage item" else "Edit storage item")
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                lifecycleScope.launch {
                    try {
                        DevToolsBridge.request("setStorageItem") {
                            put("area", area.text.toString().ifBlank { "local" })
                            put("key", key.text.toString())
                            put("value", value.text.toString())
                        }
                        refresh()
                    } catch (t: Throwable) {
                        toast("Failed: ${t.message}")
                    }
                }
            }
            .setNeutralButton(R.string.delete) { _, _ ->
                payload ?: return@setNeutralButton
                lifecycleScope.launch {
                    try {
                        DevToolsBridge.request("removeStorageItem") {
                            put("area", payload.optString("area"))
                            put("key", payload.optString("key"))
                        }
                        refresh()
                    } catch (t: Throwable) {
                        toast("Failed: ${t.message}")
                    }
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun clearAll() = lifecycleScope.launch {
        try {
            if (storageMode) {
                toast("Use per-item delete for storage")
            } else {
                DevToolsBridge.request("clearCookies") { put("domain", UrlUtils.host(pageUrl)) }
                refresh()
            }
        } catch (t: Throwable) {
            toast("Failed: ${t.message}")
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        const val EXTRA_STORAGE_MODE = "storage_mode"
    }
}
