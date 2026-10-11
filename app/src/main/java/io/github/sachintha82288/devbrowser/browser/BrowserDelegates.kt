package io.github.sachintha82288.devbrowser.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.sachintha82288.devbrowser.util.UrlUtils
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

/**
 * Wires every interesting [GeckoSession] callback into a [BrowserTab] +
 * [TabsController] so the Compose UI can render state and answer prompts.
 */
class BrowserDelegates(
    private val tab: BrowserTab,
    private val controller: TabsController,
) : GeckoSession.NavigationDelegate,
    GeckoSession.ProgressDelegate,
    GeckoSession.ContentDelegate,
    GeckoSession.PermissionDelegate,
    GeckoSession.PromptDelegate,
    ContentBlocking.Delegate {

    private fun attach() {
        tab.session.navigationDelegate = this
        tab.session.progressDelegate = this
        tab.session.contentDelegate = this
        tab.session.permissionDelegate = this
        tab.session.promptDelegate = this
        tab.session.contentBlockingDelegate = this
    }

    // ---------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------

    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean,
    ) {
        tab.url = url
        if (url != null && !tab.isPrivate &&
            (url.startsWith("http://") || url.startsWith("https://"))
        ) {
            controller.library.addHistory(url, tab.title)
        }
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        tab.canGoBack = canGoBack
    }

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        tab.canGoForward = canGoForward
    }

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? {
        val uri = request.uri
        if (UrlUtils.isExternalScheme(uri)) {
            try {
                controller.launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            } catch (_: ActivityNotFoundException) {
                // No handler — let Gecko show its error.
            }
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        return null
    }

    override fun onNewSession(
        session: GeckoSession,
        uri: String,
    ): GeckoResult<GeckoSession>? {
        // A page (usually window.open) asked for a new window — make it a new tab
        // with the same privacy mode, already opened and delegate-wired.
        val newTab = controller.createTab(isPrivate = tab.isPrivate, openIt = true)
        return GeckoResult.fromValue(newTab.session)
    }

    override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: WebRequestError,
    ): GeckoResult<String>? {
        return GeckoResult.fromValue(ErrorPage.create(uri, error))
    }

    // ---------------------------------------------------------------------
    // Progress
    // ---------------------------------------------------------------------

    override fun onPageStart(session: GeckoSession, url: String) {
        tab.loading = true
        tab.url = url
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        tab.loading = false
    }

    override fun onProgressChange(session: GeckoSession, progress: Int) {
        tab.progress = progress
    }

    override fun onSecurityChange(
        session: GeckoSession,
        securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
    ) {
        tab.isSecure = securityInfo.isSecure
    }

    // ---------------------------------------------------------------------
    // Content
    // ---------------------------------------------------------------------

    override fun onTitleChange(session: GeckoSession, title: String?) {
        tab.title = title.orEmpty()
        val url = tab.url ?: return
        if (!tab.isPrivate && (url.startsWith("http://") || url.startsWith("https://"))) {
            controller.library.updateLatestTitle(url, tab.title)
        }
    }

    override fun onCloseRequest(session: GeckoSession) {
        controller.closeTab(tab.id)
    }

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        tab.isFullScreen = fullScreen
    }

    override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
        controller.download(response)
    }

    override fun onCrash(session: GeckoSession) {
        Log.w("DevBrowser", "Session crashed, recovering")
        recoverSession(session)
    }

    override fun onKill(session: GeckoSession) {
        Log.w("DevBrowser", "Session killed by the OS, recovering")
        recoverSession(session)
    }

    private fun recoverSession(session: GeckoSession) {
        try {
            val lastUrl = tab.url
            session.close()
            session.open(controller.engine.runtime)
            if (lastUrl != null) {
                session.loadUri(lastUrl)
            } else {
                tab.goHome()
            }
        } catch (e: Throwable) {
            Log.e("DevBrowser", "Could not recover session", e)
        }
    }

    // ---------------------------------------------------------------------
    // Content blocking
    // ---------------------------------------------------------------------

    override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
        tab.blockedCount += 1
    }

    // ---------------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------------

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int>? {
        val kind = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> ActivePermission.Kind.LOCATION
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> ActivePermission.Kind.NOTIFICATION
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> ActivePermission.Kind.PERSISTENT_STORAGE
            else -> ActivePermission.Kind.OTHER
        }
        if (kind == ActivePermission.Kind.OTHER) {
            // Autoplay / XR / tracking etc. — sensible dev-browser defaults.
            return GeckoResult.fromValue(
                if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                    perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE
                ) {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                } else {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                },
            )
        }
        val result = GeckoResult<Int>()
        controller.requestPermission(
            ActivePermission(
                host = UrlUtils.hostOf(perm.uri) ?: perm.uri,
                kind = kind,
                onAnswer = { granted ->
                    result.complete(
                        if (granted) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                        else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
                    )
                },
            ),
        )
        return result
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) {
        controller.requestPermission(
            ActivePermission(
                host = UrlUtils.hostOf(uri) ?: uri,
                kind = ActivePermission.Kind.MEDIA,
                onAnswer = { granted ->
                    if (granted) {
                        callback.grant(
                            video?.firstOrNull()?.id,
                            audio?.firstOrNull()?.id,
                        )
                    } else {
                        callback.reject()
                    }
                },
            ),
        )
    }

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) {
        controller.requestAndroidPermissions(permissions ?: emptyArray()) { granted ->
            if (granted) callback.grant() else callback.reject()
        }
    }

    // ---------------------------------------------------------------------
    // Prompts
    // ---------------------------------------------------------------------

    private fun show(prompt: ActivePrompt) = controller.showPrompt(prompt)

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        show(
            ActivePrompt.Alert(
                tabSession = session,
                title = prompt.title,
                message = prompt.message,
                onDismiss = { result.complete(prompt.dismiss()) },
            ),
        )
        return result
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        show(
            ActivePrompt.Confirm(
                tabSession = session,
                title = prompt.title,
                message = prompt.message,
                onAnswer = { ok ->
                    result.complete(
                        prompt.confirm(
                            if (ok) GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE
                            else GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE,
                        ),
                    )
                },
            ),
        )
        return result
    }

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.TextPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        show(
            ActivePrompt.Input(
                tabSession = session,
                title = prompt.title,
                message = prompt.message,
                defaultValue = prompt.defaultValue,
                onSubmit = { text ->
                    if (text == null) result.complete(prompt.dismiss())
                    else result.complete(prompt.confirm(text))
                },
            ),
        )
        return result
    }

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val flags = prompt.authOptions.flags
        show(
            ActivePrompt.Auth(
                tabSession = session,
                title = prompt.title,
                message = prompt.message,
                onlyPassword = (flags and GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0,
                previousFailed = (flags and GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.PREVIOUS_FAILED) != 0,
                onSubmit = { username, password ->
                    result.complete(
                        if (onlyPasswordFlag(prompt)) prompt.confirm(password)
                        else prompt.confirm(username, password),
                    )
                },
                onCancel = { result.complete(prompt.dismiss()) },
            ),
        )
        return result
    }

    private fun onlyPasswordFlag(prompt: GeckoSession.PromptDelegate.AuthPrompt): Boolean =
        (prompt.authOptions.flags and
            GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        show(
            ActivePrompt.Choice(
                tabSession = session,
                title = prompt.title,
                message = prompt.message,
                allowMultiple = prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE,
                choices = prompt.choices.toList(),
                onSelect = { ids ->
                    if (ids.isEmpty()) result.complete(prompt.dismiss())
                    else result.complete(prompt.confirm(ids.toTypedArray()))
                },
            ),
        )
        return result
    }

    override fun onColorPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ColorPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        show(
            ActivePrompt.Color(
                tabSession = session,
                defaultValue = prompt.defaultValue,
                predefined = prompt.predefinedValues?.toList() ?: emptyList(),
                onSubmit = { color -> result.complete(prompt.confirm(color)) },
            ),
        )
        return result
    }

    override fun onDateTimePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        show(
            ActivePrompt.DateTime(
                tabSession = session,
                type = prompt.type,
                defaultValue = prompt.defaultValue,
                onSubmit = { value -> result.complete(prompt.confirm(value)) },
            ),
        )
        return result
    }

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        show(
            ActivePrompt.File(
                tabSession = session,
                mimeTypes = prompt.mimeTypes?.toList() ?: emptyList(),
                allowMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE,
                onPick = { uris ->
                    if (uris.isEmpty()) {
                        result.complete(prompt.dismiss())
                    } else {
                        result.complete(prompt.confirm(controller.appContext, uris.toTypedArray()))
                    }
                },
            ),
        )
        return result
    }

    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        return GeckoResult.fromValue(
            prompt.confirm(if (controller.settings.blockPopups) AllowOrDeny.DENY else AllowOrDeny.ALLOW),
        )
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        show(
            ActivePrompt.LeaveSite(
                tabSession = session,
                title = prompt.title,
                message = "This page is asking to confirm that you want to leave — data you entered may not be saved.",
                onAnswer = { leave ->
                    result.complete(prompt.confirm(if (leave) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
                },
            ),
        )
        return result
    }

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        show(
            ActivePrompt.LeaveSite(
                tabSession = session,
                title = prompt.title,
                message = "To display this page, DevBrowser must resend data that will repeat any action (such as a search or order confirmation) that was performed earlier.",
                onAnswer = { resend ->
                    result.complete(prompt.confirm(if (resend) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
                },
            ),
        )
        return result
    }

    // Prompts we deliberately decline (no login/autofill/credential backends yet).

    override fun onLoginSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onAddressSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSaveOption>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onCreditCardSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSaveOption>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onLoginSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onAddressSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSelectOption>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onCreditCardSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSelectOption>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onSharePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.SharePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        // Surface the Web Share request through Android's share sheet.
        val title = prompt.title
        val text = prompt.text
        val uri = prompt.uri
        if (text != null || uri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, listOfNotNull(text, uri).joinToString("\n"))
                if (title != null) putExtra(Intent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                controller.launchIntent(Intent.createChooser(intent, null))
                return GeckoResult.fromValue(
                    prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.SUCCESS),
                )
            } catch (_: ActivityNotFoundException) {
                // fall through
            }
        }
        return GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onRedirectPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RedirectPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

    override fun onFolderUploadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FolderUploadPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

    override fun onWebAuthnRelatedOriginPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.WebAuthnRelatedOriginPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

    companion object {
        fun attachTo(tab: BrowserTab, controller: TabsController): BrowserDelegates {
            val delegates = BrowserDelegates(tab, controller)
            delegates.attach()
            return delegates
        }
    }
}
