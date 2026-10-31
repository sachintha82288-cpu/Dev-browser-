package io.github.sachintha82288.devbrowser.debug

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession

/**
 * Thin, thread-safe wrapper around [GeckoSession.evaluateJS].
 *
 * GeckoView's public API gives us exactly one general-purpose way to inspect a
 * live page: `evaluateJS`. Everything in the debug suite (DOM, CSS, storage,
 * performance, security, ...) is therefore built from JavaScript injected into
 * the page context; scripts must return a value so the result can be read back.
 *
 * Every injected script is an immediately-invoked expression that returns a JSON
 * string, which keeps Kotlin-side parsing identical for every panel.
 */
object DebugBridge {

    private const val TAG = "DevBrowser/Debug"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Delivers [block] on the main thread — Compose state may only change there. */
    fun postMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** Evaluates [script] and returns the raw result (string/boolean/number/null). */
    fun evalRaw(session: GeckoSession, script: String, onResult: (Any?) -> Unit) {
        val result = try {
            session.evaluateJS(script)
        } catch (e: Throwable) {
            Log.w(TAG, "evaluateJS failed to start", e)
            postMain { onResult(null) }
            return
        }
        result.accept(
            { value -> postMain { onResult(value) } },
            { error ->
                Log.d(TAG, "evaluateJS rejected: ${error?.message}")
                postMain { onResult(null) }
            },
        )
    }

    /** Evaluates [script] expecting a JSON document, and parses it. */
    fun evalJson(session: GeckoSession, script: String, onResult: (JSONObject?) -> Unit) {
        evalRaw(session, script) { value ->
            val text = jsonText(value)
            if (text == null) {
                onResult(null)
                return@evalRaw
            }
            val parsed = try {
                JSONObject(text)
            } catch (e: Exception) {
                Log.d(TAG, "Unparseable JSON: ${text.take(120)}")
                null
            }
            onResult(parsed)
        }
    }

    /** Evaluates [script] expecting a JSON array. */
    fun evalJsonArray(session: GeckoSession, script: String, onResult: (JSONArray?) -> Unit) {
        evalRaw(session, script) { value ->
            val text = jsonText(value)
            if (text == null) {
                onResult(null)
                return@evalRaw
            }
            val parsed = try {
                JSONArray(text)
            } catch (e: Exception) {
                null
            }
            onResult(parsed)
        }
    }

    /** Runs a snippet in the page and reports `{ok, value, error}`. */
    fun evalUserCode(session: GeckoSession, code: String, onResult: (JsOutput) -> Unit) {
        val script = DebugJs.evalScript(code)
        evalJson(session, script) { obj ->
            onResult(
                if (obj == null) {
                    JsOutput(false, "", "Evaluation failed (page not reachable)")
                } else {
                    JsOutput(
                        ok = obj.optBoolean("ok", false),
                        value = obj.optString("v", ""),
                        error = obj.optString("e", ""),
                    )
                },
            )
        }
    }

    /**
     * GeckoView returns the value as an `Any`; `evaluateJS` resolves strings and
     * JSON-serialisable values. Anything else is stringified defensively.
     */
    private fun jsonText(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is JSONObject -> value.toString()
        else -> value.toString()
    }
}
