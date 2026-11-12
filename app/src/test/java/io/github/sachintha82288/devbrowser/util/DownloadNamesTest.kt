package io.github.sachintha82288.devbrowser.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DownloadManager refuses names with path separators or reserved characters, so the
 * header/URI → filename rules are pure string logic and tested here.
 */
class DownloadNamesTest {

    @Test
    fun quotedFilenameIsRead() {
        assertEquals("report.zip", DownloadNames.fromDisposition("attachment; filename=\"report.zip\""))
    }

    @Test
    fun unquotedFilenameIsRead() {
        assertEquals("report.zip", DownloadNames.fromDisposition("attachment; filename=report.zip"))
        assertEquals("a b.txt", DownloadNames.fromDisposition("inline; filename=a b.txt; size=12"))
    }

    @Test
    fun rfc5987FilenameIsRead() {
        assertEquals("report.zip", DownloadNames.fromDisposition("attachment; filename*=UTF-8''report.zip"))
    }

    @Test
    fun headerWithoutFilenameGivesNull() {
        assertNull(DownloadNames.fromDisposition(null))
        assertNull(DownloadNames.fromDisposition("attachment"))
        assertNull(DownloadNames.fromDisposition("attachment; filename="))
        assertNull(DownloadNames.fromDisposition("attachment; filename=\""))
    }

    @Test
    fun pathAndReservedCharactersAreReplaced() {
        assertEquals("a_b_c_d_e_f_g_h_.zip", DownloadNames.sanitize("""a/b\c:d*e?f"g<h>.zip"""))
        assertEquals("_etc_passwd", DownloadNames.sanitize("/etc/passwd"))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("file.txt", DownloadNames.sanitize("  file.txt  "))
    }

    @Test
    fun emptyNamesFallBackInsteadOfProducingAnEmptyFile() {
        assertEquals(DownloadNames.FALLBACK, DownloadNames.sanitize(""))
        assertEquals(DownloadNames.FALLBACK, DownloadNames.sanitize("   "))
    }

    /** Long names keep their tail: the extension is the part the user needs. */
    @Test
    fun longNamesKeepTheTailUpToTheLimit() {
        val long = "x".repeat(300) + ".apk"
        val sanitized = DownloadNames.sanitize(long)

        assertEquals(DownloadNames.MAX_LENGTH, sanitized.length)
        assertEquals(".apk", sanitized.takeLast(4))
    }

    @Test
    fun namesAtTheLimitAreUntouched() {
        val exact = "y".repeat(DownloadNames.MAX_LENGTH)
        assertEquals(exact, DownloadNames.sanitize(exact))
    }
}
