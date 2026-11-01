package io.github.sachintha82288.devbrowser.util

import java.util.Locale

/**
 * Stage B: IDN / homograph heuristics.
 *
 * Browsers must not hide a non-ASCII host behind a friendly-looking name, so
 * this flags punycode hosts and labels that mix writing systems — the classic
 * look-alike attack (e.g. a Cyrillic "а" inside "apple.com").
 */
object IdnCheck {

    data class Risk(val level: Level, val reason: String)

    enum class Level { PUNYCODE, MIXED_SCRIPT }

    private val LATIN = 'a'..'z'
    private val CYRILLIC = '\u0400'..'\u04FF'
    private val GREEK = '\u0370'..'\u03FF'
    private val ARMENIAN = '\u0530'..'\u058F'
    private val HEBREW = '\u0590'..'\u05FF'
    private val ARABIC = '\u0600'..'\u06FF'

    /** Returns null for plain ASCII hosts and IP literals. */
    fun check(host: String?): Risk? {
        if (host.isNullOrBlank()) return null
        val lower = host.lowercase(Locale.US)
        if (lower.matches(Regex("^[0-9.]+$"))) return null
        if (lower.startsWith("[")) return null
        if (lower.contains("xn--")) {
            return Risk(Level.PUNYCODE, "punycode")
        }
        if (lower.all { it.code < 128 }) return null

        // Collect the writing systems used in the labels (TLD excluded).
        val labels = lower.split('.')
        val name = labels.dropLast(1).joinToString("")
        val scripts = HashSet<String>()
        name.forEach { ch ->
            when (ch) {
                in LATIN -> scripts.add("latin")
                in CYRILLIC -> scripts.add("cyrillic")
                in GREEK -> scripts.add("greek")
                in ARMENIAN -> scripts.add("armenian")
                in HEBREW -> scripts.add("hebrew")
                in ARABIC -> scripts.add("arabic")
            }
        }
        if (scripts.size > 1) {
            return Risk(Level.MIXED_SCRIPT, scripts.sorted().joinToString("+"))
        }
        return null
    }
}
