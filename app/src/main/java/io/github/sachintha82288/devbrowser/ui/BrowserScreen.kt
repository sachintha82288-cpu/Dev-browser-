package io.github.sachintha82288.devbrowser.ui

import androidx.activity.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.ActivePrompt
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.ui.components.FindBar
import io.github.sachintha82288.devbrowser.ui.components.FindBarState
import io.github.sachintha82288.devbrowser.ui.components.TabChip
import io.github.sachintha82288.devbrowser.ui.components.rememberFindBarState
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.TextSecondary
import io.github.sachintha82288.devbrowser.util.UrlUtils
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Full-screen overlays navigated from the browser chrome. */
enum class Screen { Browser, Tabs, Extensions, Proxy, Settings, Library }

@Composable
fun BrowserScreen(app: DevBrowserApplication, activity: android.app.Activity) {
    val controller = app.tabs
    val tab = controller.selectedTab

    var screen by remember { mutableStateOf(Screen.Browser) }
    var menuOpen by remember { mutableStateOf(false) }
    var urlEdit by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    val findBar = rememberFindBarState()

    val urlFocusRequester = remember { FocusRequester() }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val prompt = controller.activePrompt
        if (prompt is ActivePrompt.File) {
            prompt.onPick(uris)
            controller.dismissPrompt()
        }
    }

    // Launch the system file picker whenever a file-upload prompt appears.
    LaunchedEffect(controller.activePrompt) {
        val prompt = controller.activePrompt
        if (prompt is ActivePrompt.File) {
            filePicker.launch(prompt.mimeTypes.toTypedArray())
        }
    }

    // Ctrl+L from a hardware keyboard focuses the URL bar.
    LaunchedEffect(controller.focusUrlBarRequest) {
        if (controller.focusUrlBarRequest > 0) {
            urlInput = tab?.url ?: ""
            urlEdit = true
        }
    }

    fun closeFind() {
        findBar.close(tab?.session)
    }

    // Back-button cascade — later declarations have higher priority.
    BackHandler(enabled = tab?.url == null) {
        activity.moveTaskToBack(true)
    }
    BackHandler(enabled = tab?.url != null && !tab.canGoBack) {
        tab?.goHome()
    }
    BackHandler(enabled = tab?.canGoBack == true) {
        tab?.session?.goBack()
    }
    BackHandler(enabled = urlEdit) { urlEdit = false }
    BackHandler(enabled = screen != Screen.Browser) { screen = Screen.Browser }
    BackHandler(enabled = findBar.active) { closeFind() }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                val fullScreen = tab?.isFullScreen == true

                AnimatedVisibility(visible = !fullScreen) {
                    Column {
                        UrlBar(
                        tab = tab,
                        isBookmarked = app.library.isBookmarked(tab?.url),
                        editMode = urlEdit,
                        urlInput = urlInput,
                        onInputChange = { urlInput = it },
                        focusRequester = urlFocusRequester,
                        onEnterEdit = {
                            urlInput = tab?.url ?: ""
                            urlEdit = true
                        },
                        onExitEdit = { urlEdit = false },
                        onSubmit = {
                            if (urlInput.isNotBlank()) {
                                controller.loadInSelectedTab(urlInput)
                            }
                            urlEdit = false
                        },
                        onReloadStop = {
                            val s = tab?.session
                            if (tab?.loading == true) s?.stop() else s?.reload()
                        },
                        onToggleBookmark = {
                            val url = tab?.url
                            if (url != null) {
                                if (app.library.isBookmarked(url)) {
                                    app.library.removeBookmark(url)
                                    controller.showSnackbar("Bookmark removed")
                                } else {
                                    app.library.addBookmark(
                                        url,
                                        tab.title.ifBlank { UrlUtils.titleFromUrl(url) },
                                    )
                                    controller.showSnackbar("Bookmarked")
                                }
                            }
                        },
                        onMenu = { menuOpen = true },
                    )
                    if (controller.tabs.size > 1) {
                        TabStripRow(
                            tabs = controller.tabs,
                            selectedId = controller.selectedId,
                            onSelect = { controller.selectTab(it) },
                            onClose = { controller.closeTab(it) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                GeckoSessionHost(tab = tab)

                if (tab == null || tab.url == null) {
                    HomeContent(
                        isPrivate = tab?.isPrivate == true,
                        searchEngineLabel = app.settings.searchEngine.label,
                        onSearch = { input -> controller.loadInSelectedTab(input) },
                    )
                }

                if (tab?.loading == true) {
                    LinearProgressIndicator(
                        progress = { (tab.progress / 100f).coerceIn(0.02f, 1f) },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                    )
                }

                if (findBar.active) {
                    FindBar(
                        state = findBar,
                        session = tab?.session,
                        onClose = { closeFind() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            AnimatedVisibility(visible = !fullScreen) {
                BottomBar(
                    canGoBack = tab?.canGoBack == true,
                    canGoForward = tab?.canGoForward == true,
                    tabCount = controller.tabs.size,
                    onBack = { tab?.session?.goBack() },
                    onForward = { tab?.session?.goForward() },
                    onHome = { tab?.goHome() },
                    onTabs = { screen = Screen.Tabs },
                    onMenu = { menuOpen = true },
                )
            }
        }
    }

    // Ctrl+F / menu "Find in page".
    LaunchedEffect(controller.showFindBarRequest) {
        if (controller.showFindBarRequest > 0) {
            findBar.activate()
        }
    }

    if (menuOpen) {
        MainMenu(
            app = app,
            tab = tab,
            onDismiss = { menuOpen = false },
            onNavigate = { target ->
                menuOpen = false
                screen = target
            },
            onFindInPage = {
                menuOpen = false
                findBar.activate()
            },
        )
    }

    when (screen) {
        Screen.Tabs -> TabsTrayScreen(app = app, onClose = { screen = Screen.Browser })
        Screen.Extensions -> ExtensionsScreen(app = app, onClose = { screen = Screen.Browser })
        Screen.Proxy -> ProxyScreen(app = app, onClose = { screen = Screen.Browser })
        Screen.Settings -> SettingsScreen(app = app, onClose = { screen = Screen.Browser })
        Screen.Library -> LibraryScreen(app = app, onClose = { screen = Screen.Browser })
        Screen.Browser -> Unit
    }

    // Dialog layer -----------------------------------------------------

    controller.activePermission?.let { permission ->
        PermissionDialog(
            host = permission.host,
            kind = permission.kind,
            onAnswer = { controller.answerPermission(it) },
        )
    }

    val prompt = controller.activePrompt
    if (prompt != null) {
        PromptDialog(
            prompt = prompt,
            onDismiss = { controller.dismissPrompt() },
        )
    }

    controller.activeExtensionPrompt?.let { extPrompt ->
        ExtensionPermissionDialog(
            prompt = extPrompt,
            onAnswer = { granted, privateMode ->
                controller.answerExtensionPrompt(granted, privateMode)
            },
        )
    }

    SnackbarHost(message = controller.snackbarMessage) { controller.consumeSnackbar() }
    } // root Box
}

/** The single, persistent GeckoView instance; sessions are swapped in-place. */
@Composable
private fun GeckoSessionHost(tab: BrowserTab?) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> GeckoView(context) },
        update = { view ->
            val session = tab?.session
            val attached = view.tag as? GeckoSession
            if (session != null && attached !== session) {
                try {
                    view.setSession(session)
                    view.tag = session
                } catch (_: IllegalStateException) {
                    // Session not open (yet) — attached on a later update pass.
                }
            }
        },
    )
}

@Composable
private fun UrlBar(
    tab: BrowserTab?,
    isBookmarked: Boolean,
    editMode: Boolean,
    urlInput: String,
    onInputChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onEnterEdit: () -> Unit,
    onExitEdit: () -> Unit,
    onSubmit: () -> Unit,
    onReloadStop: () -> Unit,
    onToggleBookmark: () -> Unit,
    onMenu: () -> Unit,
) {
    val host = UrlUtils.hostOf(tab?.url) ?: tab?.url

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        if (editMode) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExitEdit) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MonoTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    placeholder = {
                        Text(stringResource(R.string.search_hint), style = MonoTextStyle)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    trailingIcon = {
                        if (urlInput.isNotEmpty()) {
                            IconButton(onClick = { onInputChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    },
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val loading = tab?.loading == true
                IconButton(onClick = onReloadStop) {
                    if (loading) {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = stringResource(R.string.stop),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.reload),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEnterEdit),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (tab?.isSecure == true) Icons.Rounded.Lock else Icons.Rounded.Search,
                            contentDescription = null,
                            tint = if (tab?.isSecure == true) NeonCyan else TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = host ?: stringResource(R.string.search_hint),
                            style = MonoTextStyle,
                            color = if (host != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (tab?.url != null) {
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(R.string.add_bookmark),
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else TextSecondary,
                        )
                    }
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.menu))
                }
            }
        }
    }
}

@Composable
private fun TabStripRow(
    tabs: List<BrowserTab>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(tabs, key = { it.id }) { tab ->
            TabChip(
                title = tab.title.ifBlank { UrlUtils.titleFromUrl(tab.url) },
                isSelected = tab.id == selectedId,
                isPrivate = tab.isPrivate,
                onClick = { onSelect(tab.id) },
                onClose = { onClose(tab.id) },
            )
        }
    }
}

@Composable
private fun BottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.Rounded.ArrowForward, contentDescription = stringResource(R.string.forward))
            }
            IconButton(onClick = onHome) {
                Icon(Icons.Rounded.Home, contentDescription = stringResource(R.string.home))
            }
            IconButton(onClick = onTabs) {
                BadgedBox(
                    badge = {
                        if (tabCount > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(tabCount.toString())
                            }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.Tab, contentDescription = stringResource(R.string.tabs))
                }
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.menu))
            }
        }
    }
}

@Composable
private fun SnackbarHost(message: String?, onConsume: () -> Unit) {
    if (message != null) {
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2600)
            onConsume()
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 72.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
