package com.devbrowser.app.tools

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.devbrowser.app.databinding.ActivityConsoleBinding
import com.devbrowser.app.extensions.DevToolsBridge
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A JavaScript REPL that evaluates in the context of the current page. */
class ConsoleActivity : AppCompatActivity() {

    private lateinit var b: ActivityConsoleBinding
    private val history = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityConsoleBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        append("// DevBrowser console — evaluates in the active page")
        b.btnRun.setOnClickListener { run() }
        b.input.setOnEditorActionListener { _, _, _ -> run(); true }
    }

    private fun run() {
        val code = b.input.text.toString()
        if (code.isBlank()) return
        append("> $code")
        b.input.setText("")
        lifecycleScope.launch {
            val out = try {
                val res = DevToolsBridge.requestResult("eval") { put("code", code) }
                (res as? JSONObject)?.optString("result") ?: res?.toString() ?: "undefined"
            } catch (t: Throwable) {
                "Error: ${t.message}"
            }
            append(out)
        }
    }

    private fun append(line: String) {
        history.append(line).append('\n')
        b.output.text = history.toString()
        b.outputScroll.post { b.outputScroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
