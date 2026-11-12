package io.github.sachintha82288.devbrowser.util

/**
 * Filename rules for downloads, kept free of Android APIs so they can be unit-tested.
 *
 * [Downloads][io.github.sachintha82288.devbrowser.downloads.Downloader] uses these
 * before handing a request to Android's DownloadManager, which rejects names
 * containing path separators or reserved characters.
 */
object DownloadNames {

    const val FALLBACK = "download"

    /** Longest name kept as-is; longer ones keep their tail (the extension matters). */
    const val MAX_LENGTH = 120

    /** `attachment; filename="foo.zip"` / `filename=foo.zip` / RFC 5987 `filename*=UTF-8''foo.zip`. */
    private val DISPOSITION_FILENAME =
        Regex("""filename\*?=(?:UTF-8''|")?([^";]+)"?""", RegexOption.IGNORE_CASE)

    private val ILLEGAL_CHARS = Regex("""[\\/:*?"<>|]""")

    /** Filename advertised by a `Content-Disposition` header, or null when it has none. */
    fun fromDisposition(header: String?): String? =
        header?.let { DISPOSITION_FILENAME.find(it)?.groupValues?.get(1)?.trim() }
            ?.takeIf { it.isNotEmpty() }

    /** Make a name safe to hand to DownloadManager: strip path/reserved characters, cap the length. */
    fun sanitize(name: String): String {
        val cleaned = name.replace(ILLEGAL_CHARS, "_").trim()
        return when {
            cleaned.isEmpty() -> FALLBACK
            cleaned.length > MAX_LENGTH -> cleaned.takeLast(MAX_LENGTH)
            else -> cleaned
        }
    }
}
