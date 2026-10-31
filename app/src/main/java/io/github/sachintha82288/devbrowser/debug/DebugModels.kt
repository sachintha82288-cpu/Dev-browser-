package io.github.sachintha82288.devbrowser.debug

/** Console severity levels mirrored from the page's `console.*` methods. */
enum class ConsoleLevel(val jsName: String) {
    LOG("log"),
    INFO("info"),
    WARN("warn"),
    ERROR("error"),
    DEBUG("debug"),
    ;

    companion object {
        fun from(raw: String?): ConsoleLevel =
            entries.firstOrNull { it.jsName == raw } ?: LOG
    }
}

/** One captured console line (or a grouped header row). */
data class ConsoleEntry(
    val id: Long,
    val level: ConsoleLevel,
    val message: String,
    val timestamp: Long,
    val stack: String = "",
    val group: String = "",
    val depth: Int = 0,
)

/** Result of evaluating a snippet typed by the user. */
data class JsOutput(
    val ok: Boolean,
    val value: String,
    val error: String,
) {
    val display: String
        get() = if (ok) value.ifEmpty { "undefined" } else error.ifEmpty { "Error" }
}

/** A past JS evaluation kept in history. */
data class JsEvaluation(
    val code: String,
    val output: JsOutput,
    val at: Long,
)

/** Aggregated uncaught error for the current tab. */
data class JsErrorRecord(
    val message: String,
    val stack: String,
    val count: Int,
    val lastSeen: Long,
)

/** A saved, named snippet, optionally auto-injected on matching URLs. */
data class JsSnippet(
    val id: String,
    val name: String,
    val code: String,
    val urlPattern: String = "",
    val runOnLoad: Boolean = false,
)

/** A selectable JavaScript execution context (top window or an iframe). */
data class FrameInfo(
    val index: Int,
    val name: String,
    val url: String,
) {
    val label: String
        get() = when {
            index == 0 -> "top"
            name.isNotBlank() -> "$name (#$index)"
            else -> "frame #$index"
        }
}
