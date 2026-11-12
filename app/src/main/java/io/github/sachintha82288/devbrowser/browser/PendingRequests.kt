package io.github.sachintha82288.devbrowser.browser

import org.mozilla.geckoview.GeckoSession

/**
 * Pending interactive requests (site prompts, permissions, extension install
 * prompts) surfaced to the Compose UI as state.
 */

/** A JS / website prompt waiting for the user. */
sealed class ActivePrompt {
    abstract val tabSession: GeckoSession

    /** JS alert() — only dismissable. */
    class Alert(
        override val tabSession: GeckoSession,
        val title: String?,
        val message: String?,
        val onDismiss: () -> Unit,
    ) : ActivePrompt()

    /** JS confirm() */
    class Confirm(
        override val tabSession: GeckoSession,
        val title: String?,
        val message: String?,
        val onAnswer: (Boolean) -> Unit,
    ) : ActivePrompt()

    /** JS prompt() */
    class Input(
        override val tabSession: GeckoSession,
        val title: String?,
        val message: String?,
        val defaultValue: String?,
        val onSubmit: (String?) -> Unit,
    ) : ActivePrompt()

    /** HTTP basic / proxy authentication. */
    class Auth(
        override val tabSession: GeckoSession,
        val title: String?,
        val message: String?,
        val onlyPassword: Boolean,
        val previousFailed: Boolean,
        val onSubmit: (username: String, password: String) -> Unit,
        val onCancel: () -> Unit,
    ) : ActivePrompt()

    /** <select> menus / choice lists. */
    class Choice(
        override val tabSession: GeckoSession,
        val title: String?,
        val message: String?,
        val allowMultiple: Boolean,
        val choices: List<GeckoSession.PromptDelegate.ChoicePrompt.Choice>,
        val onSelect: (List<String>) -> Unit,
    ) : ActivePrompt()

    /** <input type="color"> */
    class Color(
        override val tabSession: GeckoSession,
        val defaultValue: String?,
        val predefined: List<String>,
        val onSubmit: (String) -> Unit,
    ) : ActivePrompt()

    /** <input type="date"/"time"/...> — edited as a raw value for now. */
    class DateTime(
        override val tabSession: GeckoSession,
        val type: Int,
        val defaultValue: String?,
        val onSubmit: (String) -> Unit,
    ) : ActivePrompt()

    /** beforeunload / repost confirmations etc. */
    class LeaveSite(
        override val tabSession: GeckoSession,
        val title: String?,
        val message: String?,
        val onAnswer: (Boolean) -> Unit,
    ) : ActivePrompt()

    /** File upload picker. */
    class File(
        override val tabSession: GeckoSession,
        val mimeTypes: List<String>,
        val allowMultiple: Boolean,
        val onPick: (List<android.net.Uri>) -> Unit,
    ) : ActivePrompt()

    /** Something the page asked for that DevBrowser does not support natively. */
    class Unsupported(
        override val tabSession: GeckoSession,
        val message: String,
    ) : ActivePrompt()
}

/** A website permission request (geolocation, camera, notifications, ...). */
class ActivePermission(
    val host: String,
    val kind: Kind,
    val onAnswer: (Boolean) -> Unit,
) {
    enum class Kind { LOCATION, NOTIFICATION, PERSISTENT_STORAGE, MEDIA, OTHER }
}

/** An extension install/update permission prompt. */
class ActiveExtensionPrompt(
    val extensionName: String,
    val permissions: List<String>,
    val isUpdate: Boolean,
    val onAnswer: (granted: Boolean, privateModeGranted: Boolean) -> Unit,
)
