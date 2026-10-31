package io.github.sachintha82288.devbrowser.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Applies the language chosen in Settings to every Activity's resources.
 *
 * The project deliberately avoids `androidx.appcompat` (no third-party UI
 * libraries beyond Compose), so `AppCompatDelegate.setApplicationLocales` is not
 * available. Instead the Activity wraps its base context with a
 * configuration-specific context; [MainActivity] calls this from
 * `attachBaseContext` and recreates itself when the language changes.
 */
object LocaleHelper {

    private const val PREFS = "devbrowser_settings"
    private const val KEY = "language"
    private const val DEFAULT_TAG = "en"

    /** Reads the stored tag directly from prefs (safe before AppSettings exists). */
    fun storedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TAG

    /** Returns [base] with the chosen [tag] applied, falling back to [base]. */
    fun wrap(base: Context, tag: String = storedTag(base)): Context {
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        }
        return base.createConfigurationContext(config)
    }
}
