package io.github.sachintha82288.devbrowser.ui.debug.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.NetworkEntry
import io.github.sachintha82288.devbrowser.debug.RequestType
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSearchField
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber

/**
 * Category 2 — Network inspector (features 11-22).
 *
 * The list is filled by [io.github.sachintha82288.devbrowser.debug.NetworkScripts]
 * (fetch/XHR/WebSocket wrappers plus Resource Timing). Details open in a
 * sub-view so the list position is preserved when going back.
 */
@Composable
fun NetworkPanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val session = app.debug.session(tab)
    var selectedId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(tab.id) { app.debug.drainNetwork(tab) }

    val selected = session.network.firstOrNull { it.id == selectedId }
    if (selected != null) {
        NetworkDetail(
            app = app,
            tab = tab,
            entry = selected,
            onBack = { selectedId = null },
        )
        return
    }

    val filtered = remember(
        session.network,
        session.networkTypes,
        session.networkQuery,
        session.networkRegex,
    ) {
        session.network
            .filter { it.type in session.networkTypes && matchesNetworkQuery(it.url, session.networkQuery, session.networkRegex) }
            .asReversed()
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_network),
        subtitle = "${filtered.size}/${session.network.size} ${stringResource(R.string.dbg_net_requests)}",
        onClose = onClose,
        actions = {
            IconButton(onClick = {
                app.debug.clearNetwork(tab)
                selectedId = null
            }) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.dbg_net_clear), tint = NeonRed)
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RequestType.entries.forEach { type ->
                    DebugChip(
                        label = type.js,
                        selected = session.networkTypes.contains(type),
                        onClick = {
                            session.networkTypes = if (session.networkTypes.contains(type)) {
                                session.networkTypes - type
                            } else {
                                session.networkTypes + type
                            }
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    DebugSearchField(
                        value = session.networkQuery,
                        onValueChange = { session.networkQuery = it },
                        placeholder = stringResource(R.string.dbg_net_search),
                    )
                }
                DebugChip(
                    label = ".*",
                    selected = session.networkRegex,
                    accent = NeonPurple,
                    onClick = { session.networkRegex = !session.networkRegex },
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            if (filtered.isEmpty()) {
                DebugEmpty(stringResource(R.string.dbg_net_empty), modifier = Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { entry ->
                        NetworkRow(entry = entry, onClick = { selectedId = entry.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(entry: NetworkEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DebugBadge(text = entry.method, color = NeonCyan)
        Spacer(modifier = Modifier.width(5.dp))
        DebugBadge(
            text = if (entry.status > 0) entry.status.toString() else "ERR",
            color = statusColor(entry),
        )
        Spacer(modifier = Modifier.width(5.dp))
        DebugBadge(text = entry.type.js, color = NeonPurple)
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.host,
                style = DebugMono,
                color = if (entry.isFailed) NeonRed else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = entry.path,
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = formatBytes(entry.size), style = DebugMonoSmall, color = NeonCyan)
            Text(text = "${entry.durationMs}ms", style = DebugMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun statusColor(entry: NetworkEntry): Color = when {
    entry.blocked -> NeonRed
    entry.status == 0 -> NeonRed
    entry.status >= 500 -> NeonRed
    entry.status >= 400 -> WarningAmber
    entry.status >= 300 -> NeonPurple
    else -> SuccessGreen
}

internal fun formatBytes(size: Long): String = when {
    size <= 0 -> "0 B"
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
}

/** Feature 13: substring or regular-expression URL filtering. */
internal fun matchesNetworkQuery(url: String, query: String, regex: Boolean): Boolean {
    if (query.isBlank()) return true
    return if (regex) {
        try {
            Regex(query, RegexOption.IGNORE_CASE).containsMatchIn(url)
        } catch (_: Exception) {
            url.contains(query, ignoreCase = true)
        }
    } else {
        url.contains(query, ignoreCase = true)
    }
}
