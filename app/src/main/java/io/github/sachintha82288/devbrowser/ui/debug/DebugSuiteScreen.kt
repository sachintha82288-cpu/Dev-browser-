package io.github.sachintha82288.devbrowser.ui.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.console.ConsolePanel
import io.github.sachintha82288.devbrowser.ui.debug.css.CssPanel
import io.github.sachintha82288.devbrowser.ui.debug.dom.DomPanel
import io.github.sachintha82288.devbrowser.ui.debug.js.JsPanel
import io.github.sachintha82288.devbrowser.ui.debug.network.NetworkPanel
import io.github.sachintha82288.devbrowser.ui.debug.performance.PerformancePanel
import io.github.sachintha82288.devbrowser.ui.debug.responsive.ResponsivePanel
import io.github.sachintha82288.devbrowser.ui.debug.security.SecurityPanel
import io.github.sachintha82288.devbrowser.ui.debug.storage.StoragePanel
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed

/** Navigation inside the debug suite. Every panel closes back to [Home]. */
sealed interface DebugRoute {
    data object Home : DebugRoute
    data object Console : DebugRoute
    data object Javascript : DebugRoute
    data object Dom : DebugRoute
    data object Css : DebugRoute
    data object Network : DebugRoute
    data object Storage : DebugRoute
    data object Performance : DebugRoute
    data object Security : DebugRoute
    data object Responsive : DebugRoute
}

private data class DebugEntry(
    val route: DebugRoute,
    val title: Int,
    val subtitle: Int,
    val icon: ImageVector,
)

private val debugEntries = listOf(
    DebugEntry(DebugRoute.Console, R.string.dbg_cat_console, R.string.dbg_cat_console_sub, Icons.Rounded.Search),
    DebugEntry(DebugRoute.Javascript, R.string.dbg_cat_js, R.string.dbg_cat_js_sub, Icons.Rounded.Code),
    DebugEntry(DebugRoute.Dom, R.string.dbg_cat_dom, R.string.dbg_cat_dom_sub, Icons.Rounded.Tune),
    DebugEntry(DebugRoute.Css, R.string.dbg_cat_css, R.string.dbg_cat_css_sub, Icons.Rounded.Palette),
    DebugEntry(DebugRoute.Network, R.string.dbg_cat_network, R.string.dbg_cat_network_sub, Icons.Rounded.Tune),
    DebugEntry(DebugRoute.Storage, R.string.dbg_cat_storage, R.string.dbg_cat_storage_sub, Icons.Rounded.Storage),
    DebugEntry(DebugRoute.Performance, R.string.dbg_cat_perf, R.string.dbg_cat_perf_sub, Icons.Rounded.Star),
    DebugEntry(DebugRoute.Security, R.string.dbg_cat_security, R.string.dbg_cat_security_sub, Icons.Rounded.Security),
    DebugEntry(DebugRoute.Responsive, R.string.dbg_cat_responsive, R.string.dbg_cat_responsive_sub, Icons.Rounded.DesktopWindows),
)

/**
 * Full-screen overlay hosting the on-device debugging suite. The GeckoView
 * keeps rendering behind this overlay, so inspecting never pauses the page.
 */
@Composable
fun DebugSuiteScreen(app: DevBrowserApplication, onClose: () -> Unit) {
    val tab = app.tabs.selectedTab
    var route by remember { mutableStateOf<DebugRoute>(DebugRoute.Home) }

    BackHandler(enabled = true) {
        if (route == DebugRoute.Home) onClose() else route = DebugRoute.Home
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (tab == null) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = stringResource(R.string.dbg_no_page),
                    style = DebugMonoSmall,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@Surface
        }
        when (route) {
            DebugRoute.Home -> DebugHome(
                app = app,
                tab = tab,
                onClose = onClose,
                onOpen = { route = it },
            )
            DebugRoute.Console -> ConsolePanel(app = app, tab = tab, onClose = { route = DebugRoute.Home })
            DebugRoute.Javascript -> JsPanel(app = app, tab = tab, onClose = { route = DebugRoute.Home })
            DebugRoute.Dom -> DomPanel(app = app, tab = tab, onClose = { route = DebugRoute.Home })
            DebugRoute.Css -> CssPanel(app = app, tab = tab, onClose = { route = DebugRoute.Home })
            DebugRoute.Network -> NetworkPanel(app = app, tab = tab, onClose = { route = DebugRoute.Home })
            DebugRoute.Storage -> StoragePanel(app = app, tab = tab, onClose = { route = DebugRoute.Home })
            DebugRoute.Performance -> PerformancePanel(app = app, tab = tab, onClose = { route = DebugRoute.Home })
            DebugRoute.Security -> SecurityPanel(app = app, tab = tab, onClose = { route = DebugRoute.Home })
            DebugRoute.Responsive -> ResponsivePanel(app = app, tab = tab, onClose = { route = DebugRoute.Home })
        }
    }
}

@Composable
private fun DebugHome(
    app: DevBrowserApplication,
    tab: BrowserTab,
    onClose: () -> Unit,
    onOpen: (DebugRoute) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.dbg_suite_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan,
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.dbg_suite_badge),
                            style = DebugMonoSmall,
                            color = NeonRed,
                        )
                    }
                    Text(
                        text = tab.url ?: stringResource(R.string.dbg_no_page),
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dbg_close), tint = NeonRed)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.dbg_suite_hint),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            debugEntries.forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onOpen(entry.route) },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(entry.title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(entry.subtitle),
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
