package io.github.sachintha82288.devbrowser.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/**
 * All debug-suite state for exactly one tab. Every panel binds to this object,
 * so panels can be opened/closed without touching the page or other tabs.
 */
class DebugSession(val tabId: String) {

    // --- Console (category 1) -------------------------------------------

    var console by mutableStateOf<List<ConsoleEntry>>(emptyList())
        private set

    /** Highest console sequence already copied out of the page. */
    var lastSeq: Long = 0
    var draining: Boolean = false

    /** Level toggles; by default everything is visible. */
    var visibleLevels by mutableStateOf(ConsoleLevel.entries.toSet())
    var showTimestamps by mutableStateOf(true)
    var consoleSearch by mutableStateOf("")

    /** Ids of group headers the user collapsed. */
    var collapsedGroups by mutableStateOf<Set<String>>(emptySet())

    /** Command input history for this tab (feature 4). */
    var commandHistory by mutableStateOf<List<String>>(emptyList())
    var commandIndex by mutableStateOf(-1)

    // --- Network inspector (category 2) ---------------------------------

    var network by mutableStateOf<List<NetworkEntry>>(emptyList())
        private set
    var lastNetSeq: Long = 0
    var networkDraining: Boolean = false

    var networkTypes by mutableStateOf(RequestType.entries.toSet())
    var networkQuery by mutableStateOf("")
    var networkRegex by mutableStateOf(false)
    var selectedRequestId by mutableStateOf<Long?>(null)

    var webSockets by mutableStateOf<List<WebSocketRecord>>(emptyList())

    fun appendNetwork(entries: List<NetworkEntry>) {
        if (entries.isEmpty()) return
        entries.forEach { entry -> if (entry.id > lastNetSeq) lastNetSeq = entry.id }
        network = (network + entries).takeLast(MAX_NETWORK)
    }

    fun clearNetwork() {
        network = emptyList()
    }

    // --- Aggregated JS errors (feature 44) ------------------------------

    var jsErrors by mutableStateOf<List<JsErrorRecord>>(emptyList())

    // --- JS execution (category 5) --------------------------------------

    var frames by mutableStateOf<List<FrameInfo>>(emptyList())
    var selectedFrameIndex by mutableStateOf(0)
    var lastOutput by mutableStateOf<JsOutput?>(null)
    var mutationLogging by mutableStateOf(false)

    // --- Performance (category 6) ---------------------------------------

    var perfOverlay by mutableStateOf(false)
    var vitalsOverlay by mutableStateOf(false)
    var longTaskLogging by mutableStateOf(false)
    var shiftFlash by mutableStateOf(false)

    // --- Responsive emulation (category 9) -------------------------------

    var responsiveTouch by mutableStateOf(false)
    var responsiveOverlay by mutableStateOf(false)
    var responsiveRuler by mutableStateOf(false)

    // --- Rendering (category 11) -----------------------------------------

    var renderingPaint by mutableStateOf(false)
    var renderingShift by mutableStateOf(false)
    var renderingLayers by mutableStateOf(false)
    var renderingScheme by mutableStateOf("system")
    var renderingMotion by mutableStateOf("no-preference")

    // --- Accessibility (category 12) -------------------------------------

    var a11yContrast by mutableStateOf(false)
    var a11yTabOrder by mutableStateOf(false)

    // --- DOM inspector (category 3) -------------------------------------

    var domRoot by mutableStateOf<DomNode?>(null)
    var domNodeCount by mutableStateOf(0)
    var domLoading by mutableStateOf(false)
    var selectedNode by mutableStateOf<DomNodeInfo?>(null)
    var pickerActive by mutableStateOf(false)
    var highlightActive by mutableStateOf(false)
    var domSearch by mutableStateOf("")

    /** Ids of nodes currently expanded in the tree. */
    var expandedNodes by mutableStateOf<Set<Int>>(emptySet())

    // --- CSS panel (category 4) -----------------------------------------

    var cssRules by mutableStateOf<List<CssRule>>(emptyList())
    var computedStyles by mutableStateOf<List<CssDeclaration>>(emptyList())
    var boxModel by mutableStateOf<BoxModel?>(null)
    var forcedStates by mutableStateOf<Set<String>>(emptySet())

    // --- Appends --------------------------------------------------------

    fun appendConsole(entries: List<ConsoleEntry>) {
        if (entries.isEmpty()) return
        entries.forEach { entry -> if (entry.id > lastSeq) lastSeq = entry.id }
        val merged = (console + entries).takeLast(MAX_CONSOLE)
        console = merged
        aggregateErrors(entries)
    }

    fun clearConsole() {
        console = emptyList()
    }

    private fun aggregateErrors(entries: List<ConsoleEntry>) {
        val failures = entries.filter { it.level == ConsoleLevel.ERROR }
        if (failures.isEmpty()) return
        val updated = jsErrors.toMutableList()
        failures.forEach { failure ->
            val key = failure.message.substringBefore("  (")
            val index = updated.indexOfFirst { it.message == key }
            if (index >= 0) {
                val old = updated[index]
                updated[index] = old.copy(count = old.count + 1, lastSeen = failure.timestamp)
            } else {
                updated.add(0, JsErrorRecord(key, failure.stack, 1, failure.timestamp))
            }
        }
        jsErrors = updated.take(MAX_ERRORS)
    }

    fun addCommandHistory(command: String) {
        if (command.isBlank()) return
        commandHistory = (commandHistory + command).takeLast(50)
        commandIndex = -1
    }

    /** Up/down navigation through the command history (feature 4). */
    fun navigateHistory(direction: Int): String? {
        if (commandHistory.isEmpty()) return null
        val next = when {
            commandIndex == -1 && direction < 0 -> commandHistory.lastIndex
            else -> (commandIndex + direction).coerceIn(-1, commandHistory.lastIndex)
        }
        commandIndex = next
        return if (next == -1) null else commandHistory[next]
    }

    companion object {
        private const val MAX_CONSOLE = 2000
        private const val MAX_ERRORS = 200
        private const val MAX_NETWORK = 600

        /** Parses the JSON array returned by [DebugJs.drainScript]. */
        fun parseConsoleArray(array: JSONArray?): List<ConsoleEntry> {
            if (array == null) return emptyList()
            val out = ArrayList<ConsoleEntry>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                out.add(
                    ConsoleEntry(
                        id = o.optLong("i"),
                        level = ConsoleLevel.from(o.optString("l")),
                        message = o.optString("m"),
                        timestamp = o.optLong("t"),
                        stack = o.optString("s"),
                        group = o.optString("g"),
                        depth = o.optInt("d"),
                    ),
                )
            }
            return out
        }
    }
}
