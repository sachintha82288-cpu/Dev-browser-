package com.devbrowser.app.tools

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.devbrowser.app.browser.TabManager
import com.devbrowser.app.databinding.ActivitySourceEditorBinding
import com.devbrowser.app.extensions.DevToolsBridge
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Live HTML source viewer / editor. Reads the current DOM through the built-in
 * extension, lets you edit it and writes it straight back into the live page.
 */
class SourceEditorActivity : AppCompatActivity() {

    private lateinit var b: ActivitySourceEditorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySourceEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        b.pageUrl.text = TabManager.selected?.url ?: ""
        loadSource()

        b.btnReload.setOnClickListener { loadSource() }
        b.btnBeautify.setOnClickListener {
            b.sourceInput.setText(HtmlFormatter.format(b.sourceInput.text.toString()))
        }
        b.btnApply.setOnClickListener { applySource() }
    }

    private fun loadSource() = lifecycleScope.launch {
        try {
            val result = DevToolsBridge.requestResult("getSource") as? JSONObject
            b.pageUrl.text = result?.optString("url") ?: ""
            b.sourceInput.setText(result?.optString("source") ?: "")
        } catch (t: Throwable) {
            toast("Cannot read source: ${t.message}")
        }
    }

    private fun applySource() = lifecycleScope.launch {
        try {
            DevToolsBridge.request("setSource") { put("source", b.sourceInput.text.toString()) }
            toast("Applied to live page")
        } catch (t: Throwable) {
            toast("Failed: ${t.message}")
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
