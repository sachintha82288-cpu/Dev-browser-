package io.github.sachintha82288.devbrowser.extensions

/**
 * Pure helpers for turning an addons.mozilla.org link into an AMO API request.
 *
 * Kept out of [ExtensionManager] (which needs a Context and the engine) so the URL
 * shapes can be unit-tested on the JVM.
 */
object AmoLinks {

    const val API_BASE = "https://addons.mozilla.org/api/v5/addons/addon/"

    /** `https://addons.mozilla.org[/<locale>]/firefox/addon/<slug>/` */
    private val PAGE_URL =
        Regex("""^https?://addons\.mozilla\.org/(?:[a-zA-Z-]+/)?firefox/addon/([^/?#]+)/?""")

    /** Add-on slug from an AMO listing page, or null when the link is not one. */
    fun slug(pageUrl: String): String? = PAGE_URL.find(pageUrl)?.groupValues?.get(1)

    /** Public AMO API URL that describes the add-on and its downloadable files. */
    fun apiUrl(slug: String): String = "$API_BASE$slug/?lang=en-US"
}
