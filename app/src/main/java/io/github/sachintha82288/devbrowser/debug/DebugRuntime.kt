package io.github.sachintha82288.devbrowser.debug

import android.os.Handler
import android.os.Looper
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Application-scoped coordinator for the on-device debugging suite.
 *
 * Responsibilities:
 *  - keep one [DebugSession] per tab,
 *  - install the console/error hooks on every page load,
 *  - poll the page for new console lines (GeckoView has no console callback in
 *    this version, so polling `evaluateJS` is the supported workaround),
 *  - run URL-matched auto-injection snippets.
 */
class DebugRuntime(
    val settings: AppSettings,
    val store: DebugStore,
    private val tabsProvider: () -> List<BrowserTab>,
    private val selectedTabProvider: () -> BrowserTab?,
) {

    private val sessions = LinkedHashMap<String, DebugSession>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollingStarted = false

    /** True while the page has been injected for this tab in this navigation. */
    private val injected = HashSet<String>()

    fun session(tab: BrowserTab): DebugSession =
        sessions.getOrPut(tab.id) { DebugSession(tab.id) }

    fun forget(tabId: String) {
        sessions.remove(tabId)
        injected.remove(tabId)
    }

    // ------------------------------------------------------------------
    // Page lifecycle hooks (called from BrowserDelegates via TabsController)
    // ------------------------------------------------------------------

    fun onPageStarted(tab: BrowserTab) {
        if (!settings.showDebugPanels) return
        injected.remove(tab.id)
        installBootstrap(tab)
    }

    fun onPageStopped(tab: BrowserTab) {
        if (!settings.showDebugPanels) return
        // Re-install after load: guarantees hooks exist even if the page
        // replaced the document early in the navigation.
        installBootstrap(tab) {
            runAutoSnippets(tab)
            drainConsole(tab)
        }
    }

    private fun installBootstrap(tab: BrowserTab, then: (() -> Unit)? = null) {
        DebugBridge.evalRaw(tab.session, DebugJs.bootstrap) { result ->
            if (result != null) {
                injected.add(tab.id)
                installNetworkRecorder(tab)
                then?.invoke()
            }
        }
    }

    /**
     * Installs the network recorder (category 2). Called for every page load so
     * requests made during parsing are captured as early as GeckoView allows.
     */
    fun installNetworkRecorder(tab: BrowserTab) {
        DebugBridge.evalRaw(
            tab.session,
            NetworkScripts.recorder(store.blockedRequestTargets, store.networkThrottle),
        ) { }
    }

    fun drainNetwork(tab: BrowserTab) {
        val session = session(tab)
        if (session.networkDraining) return
        session.networkDraining = true
        DebugBridge.evalJsonArray(tab.session, NetworkScripts.drainScript(session.lastNetSeq)) { array ->
            session.networkDraining = false
            session.appendNetwork(NetworkParser.parseEntries(array))
        }
        DebugBridge.evalJsonArray(tab.session, NetworkScripts.drainWsScript) { array ->
            val sockets = NetworkParser.parseWebSockets(array)
            if (sockets.isNotEmpty()) session.webSockets = sockets
        }
    }

    fun clearNetwork(tab: BrowserTab) {
        session(tab).clearNetwork()
    }

    /** Feature 22: block a URL (or its origin) for this site. */
    fun blockRequest(tab: BrowserTab, target: String) {
        store.blockRequestTarget(target)
        refreshBlocklist(tab)
    }

    fun unblockRequest(tab: BrowserTab, target: String) {
        store.unblockRequestTarget(target)
        refreshBlocklist(tab)
    }

    private fun refreshBlocklist(tab: BrowserTab) {
        val list = JSONArray().apply { store.blockedRequestTargets.forEach { put(it) } }
        DebugBridge.evalRaw(
            tab.session,
            "(function(){try{if(window.__db&&window.__db.setBlocked){window.__db.setBlocked($list);}return 'ok';}catch(e){return 'err';}})()",
        ) { }
    }

    /** Feature 96: apply a throttling profile to every open tab. */
    fun setThrottle(profile: String) {
        store.setNetworkThrottle(profile)
        tabsProvider().forEach { tab ->
            if (tab.url != null) {
                DebugBridge.evalRaw(tab.session, NetworkScripts.setThrottleScript(profile)) { }
            }
        }
    }

    private fun runAutoSnippets(tab: BrowserTab) {
        val snippets = store.snippetsForUrl(tab.url)
        snippets.forEach { snippet ->
            DebugBridge.evalUserCode(tab.session, snippet.code) { output ->
                store.recordEvaluation(snippet.code, output)
            }
        }
    }

    // ------------------------------------------------------------------
    // Console polling
    // ------------------------------------------------------------------

    fun startPolling(scope: CoroutineScope) {
        if (pollingStarted) return
        pollingStarted = true
        scope.launch {
            while (isActive) {
                if (settings.showDebugPanels) {
                    tabsProvider().forEach { tab ->
                        if (tab.url != null) {
                            drainConsole(tab)
                            drainNetwork(tab)
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun drainConsole(tab: BrowserTab) {
        val session = session(tab)
        if (session.draining) return
        session.draining = true
        DebugBridge.evalJsonArray(tab.session, DebugJs.drainScript(session.lastSeq)) { array ->
            session.draining = false
            if (array != null) {
                session.appendConsole(DebugSession.parseConsoleArray(array))
            }
        }
    }

    fun clearConsole(tab: BrowserTab) {
        val session = session(tab)
        DebugBridge.evalRaw(tab.session, DebugJs.clearScript) { session.clearConsole() }
    }

    /** Evaluates console/JS-panel code in the selected frame (feature 37/39). */
    fun evaluate(tab: BrowserTab, code: String, done: (JsOutput) -> Unit) {
        val frameIndex = session(tab).selectedFrameIndex
        if (frameIndex == 0) {
            DebugBridge.evalUserCode(tab.session, code) { output ->
                store.recordEvaluation(code, output)
                done(output)
            }
        } else {
            val quoted = JSONObject.quote(code)
            val script =
                "(function(){try{var f=window.frames[${frameIndex - 1}];if(!f){throw new Error('frame not found');}" +
                    "var r=f.eval($quoted);var v;try{v=(typeof r==='string')?r:JSON.stringify(r);}catch(e){v=String(r);}" +
                    "if(v===undefined){v='undefined';}" +
                    "return JSON.stringify({ok:true,v:String(v).slice(0,40000),e:''});}" +
                    "catch(e){return JSON.stringify({ok:false,v:''," +
                    "e:'Frame execution blocked: '+String((e&&(e.message||e.name))||e)});}})()"
            DebugBridge.evalJson(tab.session, script) { obj ->
                val output = if (obj == null) {
                    JsOutput(false, "", "Frame not reachable")
                } else {
                    JsOutput(obj.optBoolean("ok"), obj.optString("v"), obj.optString("e"))
                }
                store.recordEvaluation(code, output)
                done(output)
            }
        }
    }

    /** Lists the top window plus every iframe (feature 39's context picker). */
    fun refreshFrames(tab: BrowserTab, done: (List<FrameInfo>) -> Unit = {}) {
        val script = DebugJs.json(
            """
var out = [];
out.push({ i: 0, n: 'top', u: location.href });
try {
  for (var i = 0; i < window.frames.length; i++) {
    var f = window.frames[i];
    var n = '';
    var u = '';
    try { n = f.name || ''; } catch (e) {}
    try { u = f.location.href || ''; } catch (e) { u = 'cross-origin'; }
    out.push({ i: i + 1, n: n, u: u });
  }
} catch (e) {}
return JSON.stringify(out);
""",
        )
        DebugBridge.evalJsonArray(tab.session, script) { array ->
            val frames = array?.let { parseFrames(it) } ?: listOf(FrameInfo(0, "top", tab.url ?: ""))
            session(tab).frames = frames
            done(frames)
        }
    }

    private fun parseFrames(array: JSONArray): List<FrameInfo> {
        val out = ArrayList<FrameInfo>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out.add(FrameInfo(o.optInt("i"), o.optString("n"), o.optString("u")))
        }
        return out
    }

    /** Feature 43: install / remove a MutationObserver that logs to the console. */
    fun setMutationLogging(tab: BrowserTab, enabled: Boolean) {
        val script = DebugJs.json(
            """
if (!D.mo) { D.mo = null; }
if (D.mo && D.mo.disconnect) { D.mo.disconnect(); D.mo = null; }
if (!${if (enabled) "true" else "false"}) { return JSON.stringify({ on: false }); }
var target = document.documentElement || document;
D.mo = new MutationObserver(function(muts){
  var parts = [];
  for (var i = 0; i < muts.length && i < 20; i++) {
    var m = muts[i];
    if (m.type === 'attributes') {
      parts.push('attr ' + (m.target.nodeName || '') + ' [' + m.attributeName + ']');
    } else if (m.type === 'childList') {
      parts.push('childList +' + m.addedNodes.length + ' -' + m.removedNodes.length + ' on ' + (m.target.nodeName || ''));
    } else if (m.type === 'characterData') {
      parts.push('text changed');
    }
  }
  if (parts.length) { try { console.debug('[mutation] ' + parts.join('; ')); } catch (e) {} }
});
D.mo.observe(target, { subtree: true, childList: true, attributes: true, characterData: true });
return JSON.stringify({ on: true });
""",
        )
        DebugBridge.evalJson(tab.session, script) { obj ->
            session(tab).mutationLogging = obj?.optBoolean("on") ?: false
        }
    }

    // ------------------------------------------------------------------
    // Helpers used by several panels
    // ------------------------------------------------------------------

    fun currentTab(): BrowserTab? = selectedTabProvider()

    fun postDelayed(delayMs: Long, block: () -> Unit) {
        mainHandler.postDelayed(block, delayMs)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 600L
    }
}
