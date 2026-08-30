package com.devbrowser.app.extensions

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Request/response channel to the built-in "DevBrowser Tools" WebExtension.
 * All dev tools (source editor, cookie editor, console, network log) talk
 * through here.
 */
object DevToolsBridge {

    private const val TAG = "DevToolsBridge"
    private const val PORT_NAME = "devtools"

    private var port: WebExtension.Port? = null
    private val counter = AtomicInteger(0)
    private val pending = ConcurrentHashMap<Int, Continuation<JSONObject>>()

    /** Listeners for streamed network events. */
    val networkListeners = mutableListOf<(JSONObject) -> Unit>()
    val networkLog = mutableListOf<JSONObject>()

    val isConnected: Boolean get() = port != null

    fun attach(extension: WebExtension) {
        extension.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onConnect(p: WebExtension.Port) {
                if (p.name != PORT_NAME) return
                port = p
                p.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, p: WebExtension.Port) {
                        handleMessage(message)
                    }

                    override fun onDisconnect(p: WebExtension.Port) {
                        if (port == p) port = null
                    }
                })
            }
        }, PORT_NAME)
    }

    private fun handleMessage(message: Any) {
        val json = message as? JSONObject ?: return
        if (json.optString("event") == "network") {
            val entry = json.optJSONObject("entry") ?: return
            synchronized(networkLog) {
                networkLog.add(entry)
                if (networkLog.size > 500) networkLog.removeAt(0)
            }
            networkListeners.toList().forEach { it(entry) }
            return
        }
        val id = json.optInt("id", -1)
        val cont = pending.remove(id) ?: return
        if (json.optBoolean("ok")) {
            cont.resume(json)
        } else {
            cont.resumeWithException(RuntimeException(json.optString("error", "unknown error")))
        }
    }

    /** Sends a request and suspends until the extension replies. */
    suspend fun request(action: String, build: JSONObject.() -> Unit = {}): JSONObject {
        val p = port ?: throw IllegalStateException("Dev tools extension is not connected yet")
        val id = counter.incrementAndGet()
        val payload = JSONObject().apply {
            put("id", id)
            put("action", action)
            build()
        }
        return suspendCoroutine { cont ->
            pending[id] = cont
            try {
                p.postMessage(payload)
            } catch (t: Throwable) {
                pending.remove(id)
                cont.resumeWithException(t)
            }
        }
    }

    suspend fun requestResult(action: String, build: JSONObject.() -> Unit = {}): Any? =
        request(action, build).opt("result")

    fun clearNetworkLog() = synchronized(networkLog) { networkLog.clear() }

    fun disconnect() {
        try {
            port?.disconnect()
        } catch (t: Throwable) {
            Log.d(TAG, "disconnect: ${t.message}")
        }
        port = null
    }
}
