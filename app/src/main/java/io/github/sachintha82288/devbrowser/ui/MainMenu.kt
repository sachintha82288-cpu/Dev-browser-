package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.util.UrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenu(
    app: DevBrowserApplication,
    tab: BrowserTab?,
    onDismiss: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onFindInPage: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    val desktopLabel = stringResource(R.string.desktop_site)
    val bookmarksAndHistory = stringResource(R.string.bookmarks_and_history)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuRow(Icons.Rounded.Add, stringResource(R.string.new_tab)) {
                app.tabs.newTab(isPrivate = false)
                onDismiss()
            }
            MenuRow(Icons.Rounded.VisibilityOff, stringResource(R.string.new_private_tab)) {
                app.tabs.newTab(isPrivate = true)
                onDismiss()
            }
            MenuRow(Icons.Rounded.FindInPage, stringResource(R.string.find_in_page)) {
                onFindInPage()
            }
            MenuRow(
                Icons.Rounded.DesktopWindows,
                if (tab?.isDesktopMode == true) "$desktopLabel ✓" else desktopLabel,
            ) {
                app.tabs.toggleDesktopMode()
                onDismiss()
            }
            if (tab?.url != null) {
                MenuRow(Icons.Rounded.Code, stringResource(R.string.view_page_source)) {
                    val url = tab.url ?: ""
                    if (url.startsWith("http")) {
                        app.tabs.newTab(url = "view-source:$url")
                    } else {
                        app.tabs.loadInSelectedTab("view-source:$url")
                    }
                    onDismiss()
                }
            }
            MenuRow(Icons.Rounded.Tune, stringResource(R.string.about_config)) {
                app.tabs.loadInSelectedTab("about:config")
                onDismiss()
            }
            MenuRow(Icons.Rounded.Extension, stringResource(R.string.extensions)) {
                onNavigate(Screen.Extensions)
            }
            MenuRow(Icons.Rounded.Security, stringResource(R.string.proxy_and_tor)) {
                onNavigate(Screen.Proxy)
            }
            MenuRow(Icons.Rounded.History, bookmarksAndHistory) {
                onNavigate(Screen.Library)
            }
            if (tab?.url != null) {
                MenuRow(Icons.Rounded.Share, stringResource(R.string.share)) {
                    val url = tab.url ?: ""
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            "${tab.title.ifBlank { UrlUtils.titleFromUrl(url) }} — $url",
                        )
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null))
                    onDismiss()
                }
            }
            MenuRow(Icons.Rounded.Settings, stringResource(R.string.settings)) {
                onNavigate(Screen.Settings)
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
