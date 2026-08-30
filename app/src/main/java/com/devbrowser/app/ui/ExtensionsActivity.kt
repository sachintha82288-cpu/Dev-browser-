package com.devbrowser.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.devbrowser.app.databinding.ActivityListBinding
import com.devbrowser.app.extensions.ExtensionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.mozilla.geckoview.WebExtension

/** Install / enable / remove Firefox add-ons. */
class ExtensionsActivity : AppCompatActivity() {

    private lateinit var b: ActivityListBinding
    private lateinit var adapter: SimpleRowAdapter

    private val pickXpi = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        ExtensionManager.installFromFile(this, uri) { ext, err ->
            runOnUiThread {
                toast(if (err == null) "Installed ${ext?.metaData?.name}" else "Failed: ${err.message}")
                reload()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = SimpleRowAdapter(onClick = { row -> showActions(row.payload as WebExtension) })
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.header.text = "Firefox add-ons (.xpi). Tap an add-on to enable, disable or remove it."

        b.btnPrimary.text = "Install from URL"
        b.btnSecondary.text = "Install .xpi file"
        b.btnPrimary.setOnClickListener { promptUrl() }
        b.btnSecondary.setOnClickListener {
            pickXpi.launch(arrayOf("application/x-xpinstall", "application/zip", "*/*"))
        }

        reload()
    }

    private fun reload() = ExtensionManager.refresh { list ->
        runOnUiThread {
            adapter.submit(list.map { ext ->
                val md = ext.metaData
                Row(
                    (md.name ?: ext.id) + if (md.enabled) "" else "  (disabled)",
                    "v${md.version ?: "?"} · ${md.description ?: ext.id}",
                    ext
                )
            })
        }
    }

    private fun promptUrl() {
        val input = EditText(this).apply {
            hint = "https://addons.mozilla.org/.../latest.xpi"
            setText("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Install add-on from URL")
            .setView(input)
            .setPositiveButton("Install") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isEmpty()) return@setPositiveButton
                toast("Installing…")
                ExtensionManager.installFromUrl(url) { ext, err ->
                    runOnUiThread {
                        toast(if (err == null) "Installed ${ext?.metaData?.name}" else "Failed: ${err.message}")
                        reload()
                    }
                }
            }
            .setNeutralButton("Browse AMO") { _, _ ->
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://addons.mozilla.org/firefox/extensions/"))
                        .setPackage(packageName)
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showActions(ext: WebExtension) {
        if (ext.id == ExtensionManager.BUILTIN_ID) {
            toast("Built-in dev tools extension cannot be modified")
            return
        }
        val enabled = ext.metaData.enabled
        MaterialAlertDialogBuilder(this)
            .setTitle(ext.metaData.name ?: ext.id)
            .setItems(arrayOf(if (enabled) "Disable" else "Enable", "Uninstall")) { _, which ->
                if (which == 0) {
                    ExtensionManager.setEnabled(ext, !enabled) { e ->
                        runOnUiThread { e?.let { toast("Failed: ${it.message}") }; reload() }
                    }
                } else {
                    ExtensionManager.uninstall(ext) { e ->
                        runOnUiThread { e?.let { toast("Failed: ${it.message}") }; reload() }
                    }
                }
            }
            .show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
