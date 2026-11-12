package io.github.sachintha82288.devbrowser.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The add-on link shapes the "install from URL" box has to accept, and the AMO API
 * URL they resolve to. Pure string logic, so it runs on the JVM.
 */
class AmoLinksTest {

    @Test
    fun listingPageWithLocaleGivesTheSlug() {
        assertEquals(
            "ublock-origin",
            AmoLinks.slug("https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/"),
        )
    }

    @Test
    fun listingPageWithoutLocaleOrTrailingSlashGivesTheSlug() {
        assertEquals(
            "darkreader",
            AmoLinks.slug("https://addons.mozilla.org/firefox/addon/darkreader"),
        )
        assertEquals(
            "privacy-badger17",
            AmoLinks.slug("https://addons.mozilla.org/en-GB/firefox/addon/privacy-badger17/"),
        )
    }

    @Test
    fun httpAndQueryStringsAreAccepted() {
        assertEquals(
            "darkreader",
            AmoLinks.slug("http://addons.mozilla.org/en-US/firefox/addon/darkreader/?src=search"),
        )
    }

    /** Direct .xpi links and other sites are installed as-is, never sent to the API. */
    @Test
    fun nonListingUrlsGiveNoSlug() {
        assertNull(AmoLinks.slug("https://addons.mozilla.org/firefox/downloads/file/1/ublock.xpi"))
        assertNull(AmoLinks.slug("https://addons.mozilla.org/en-US/firefox/"))
        assertNull(AmoLinks.slug("https://example.com/firefox/addon/darkreader/"))
        assertNull(AmoLinks.slug(""))
    }

    @Test
    fun apiUrlPointsAtThePublicAddonEndpoint() {
        assertEquals(
            "https://addons.mozilla.org/api/v5/addons/addon/ublock-origin/?lang=en-US",
            AmoLinks.apiUrl("ublock-origin"),
        )
    }
}
