package com.devbrowser.app.tools

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.devbrowser.app.databinding.ActivityListBinding
import com.devbrowser.app.extensions.DevToolsBridge
import com.devbrowser.app.ui.Row
import com.devbrowser.app.ui.SimpleRowAdapter
import org.json.JSONObject

/** Live request log captured by the built-in extension's webRequest listener. */
class NetworkLogActivity : AppCompatActivity() {

    private lateinit var b: ActivityListBinding
    private lateinit var adapter: SimpleRowAdapter
    private val listener: (JSONObject) -> Unit = { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = SimpleRowAdapter()
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.btnPrimary.text = "Refresh"
        b.btnSecondary.text = "Clear"
        b.btnPrimary.setOnClickListener { render() }
        b.btnSecondary.setOnClickListener { DevToolsBridge.clearNetworkLog(); render() }

        DevToolsBridge.networkListeners.add(listener)
        render()
    }

    private fun render() {
        val entries = synchronized(DevToolsBridge.networkLog) {
            DevToolsBridge.networkLog.toList()
        }.asReversed()
        b.header.text = "${entries.size} requests captured"
        adapter.submit(entries.map { e ->
            val status = e.optInt("status", -1)
            val label = if (status < 0) "ERR" else status.toString()
            Row(
                "$label  ${e.optString("method")}  ${e.optString("type")}",
                e.optString("url")
            )
        })
    }

    override fun onDestroy() {
        DevToolsBridge.networkListeners.remove(listener)
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
