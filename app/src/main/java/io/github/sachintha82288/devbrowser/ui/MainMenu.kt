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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
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
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuRow(Icons.Rounded.Add, "New tab") {
                app.tabs.newTab(isPrivate = false)
                onDismiss()
            }
            MenuRow(Icons.Rounded.VisibilityOff, "New private tab") {
                app.tabs.newTab(isPrivate = true)
                onDismiss()
            }
            MenuRow(Icons.Rounded.Search, "Find in page") {
                onFindInPage()
            }
            MenuRow(Icons.Rounded.DesktopWindows, if (tab?.isDesktopMode == true) "Desktop site ✓" else "Desktop site") {
                app.tabs.toggleDesktopMode()
                onDismiss()
            }
            if (tab?.url != null) {
                MenuRow(Icons.Rounded.Code, "View page source") {
                    val url = tab.url ?: ""
                    if (url.startsWith("http")) {
                        app.tabs.newTab(url = "view-source:$url")
                    } else {
                        app.tabs.loadInSelectedTab("view-source:$url")
                    }
                    onDismiss()
                }
            }
            MenuRow(Icons.Rounded.Tune, "about:config") {
                app.tabs.loadInSelectedTab("about:config")
                onDismiss()
            }
            MenuRow(Icons.Rounded.Extension, "Add-ons") {
                onNavigate(Screen.Extensions)
            }
            MenuRow(Icons.Rounded.Security, "Proxy & Tor") {
                onNavigate(Screen.Proxy)
            }
            MenuRow(Icons.Rounded.Bookmark, "Bookmarks & history") {
                onNavigate(Screen.Library)
            }
            if (tab?.url != null) {
                MenuRow(Icons.Rounded.Share, "Share") {
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
            MenuRow(Icons.Rounded.Settings, "Settings") {
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
