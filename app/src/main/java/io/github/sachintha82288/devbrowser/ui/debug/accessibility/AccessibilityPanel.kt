package io.github.sachintha82288.devbrowser.ui.debug.accessibility

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.AccessibilityScripts
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.DebugSession
import io.github.sachintha82288.devbrowser.debug.DomScripts
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class AriaNode(
    val role: String,
    val name: String,
    val value: String,
    val depth: Int,
    val path: String,
)

private data class ContrastResult(
    val ratio: Double,
    val aa: Boolean,
    val aaa: Boolean,
    val aaLarge: Boolean,
    val fg: String,
    val bg: String,
    val fgLabel: String,
    val bgLabel: String,
)

/** Category 12 — accessibility tree, contrast, tab order and WebSockets. */
@Composable
fun AccessibilityPanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val session: DebugSession = app.debug.session(tab)
    val context = LocalContext.current
    var nodes by remember { mutableStateOf<List<AriaNode>>(emptyList()) }
    var treeNote by remember { mutableStateOf<String?>(null) }
    var contrast by remember { mutableStateOf<ContrastResult?>(null) }
    var tabOrderNote by remember { mutableStateOf<String?>(null) }
    var expandedSocket by remember { mutableStateOf<Int?>(null) }
    val sockets = session.webSockets

    fun loadTree() {
        treeNote = "…"
        DebugBridge.evalJson(tab.session, AccessibilityScripts.ariaTree) { payload ->
            val array = payload?.optJSONArray("nodes")
            val parsed = ArrayList<AriaNode>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parsed.add(
                        AriaNode(
                            role = obj.optString("role"),
                            name = obj.optString("name"),
                            value = obj.optString("value"),
                            depth = obj.optInt("depth"),
                            path = obj.optString("path"),
                        ),
                    )
                }
            }
            nodes = parsed
            treeNote = "${parsed.size} nodes"
        }
    }

    LaunchedEffect(tab.id) { loadTree() }

    LaunchedEffect(tab.id, session.a11yContrast) {
        if (!session.a11yContrast) return@LaunchedEffect
        while (session.a11yContrast) {
            delay(500)
            val payload = DebugBridge.awaitJson(tab.session, AccessibilityScripts.contrastResult)
            if (payload != null) {
                val ratio = payload.optDouble("ratio", 0.0)
                if (ratio > 0.0) {
                    contrast = ContrastResult(
                        ratio = ratio,
                        aa = payload.optBoolean("aa"),
                        aaa = payload.optBoolean("aaa"),
                        aaLarge = payload.optBoolean("aaLarge"),
                        fg = payload.optString("fg"),
                        bg = payload.optString("bg"),
                        fgLabel = payload.optString("fgLabel"),
                        bgLabel = payload.optString("bgLabel"),
                    )
                }
            }
        }
    }

    fun setContrast(enabled: Boolean) {
        session.a11yContrast = enabled
        if (!enabled) {
            contrast = null
        }
        DebugBridge.evalJson(tab.session, AccessibilityScripts.contrastPicking(enabled)) { }
    }

    fun setTabOrder(enabled: Boolean) {
        session.a11yTabOrder = enabled
        DebugBridge.evalJson(tab.session, AccessibilityScripts.tabOrderOverlay(enabled)) { payload ->
            tabOrderNote = if (enabled) {
                context.getString(R.string.dbg_a11y_taborder_note, payload?.optInt("count") ?: 0, payload?.optInt("skipped") ?: 0)
            } else {
                null
            }
        }
    }

    val contrastColor = when {
        contrast == null -> NeonCyan
        contrast!!.aaa -> SuccessGreen
        contrast!!.aa -> NeonCyan
        contrast!!.aaLarge -> WarningAmber
        else -> NeonRed
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_a11y),
        subtitle = tab.url,
        onClose = onClose,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                DebugSection(stringResource(R.string.dbg_a11y_contrast))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DebugChip(
                        label = stringResource(R.string.dbg_a11y_pick),
                        selected = session.a11yContrast,
                        onClick = { setContrast(!session.a11yContrast) },
                    )
                    DebugChip(
                        label = stringResource(R.string.dbg_a11y_taborder),
                        selected = session.a11yTabOrder,
                        accent = NeonPurple,
                        onClick = { setTabOrder(!session.a11yTabOrder) },
                    )
                    DebugChip(
                        label = stringResource(R.string.dbg_a11y_clear),
                        selected = false,
                        onClick = { setContrast(false) },
                    )
                }
                Text(
                    text = stringResource(R.string.dbg_a11y_pick_note),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            contrast?.let { result ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                text = String.format(Locale.US, "%.2f:1", result.ratio),
                                style = MaterialTheme.typography.headlineSmall,
                                color = contrastColor,
                            )
                            Text(
                                text = "  ",
                                style = DebugMono,
                            )
                            if (result.aaa) DebugBadge("AAA", SuccessGreen)
                            if (!result.aaa && result.aa) DebugBadge("AA", NeonCyan)
                            if (!result.aa && result.aaLarge) DebugBadge("AA large", WarningAmber)
                            if (!result.aaLarge) DebugBadge("FAIL", NeonRed)
                        }
                        Text(
                            text = "${result.fgLabel.ifBlank { result.fg }}  on  " +
                                "${result.bgLabel.ifBlank { result.bg }}",
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${result.fg} on ${result.bg}",
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            tabOrderNote?.let { message ->
                item {
                    Text(
                        text = message,
                        style = DebugMonoSmall,
                        color = NeonPurple,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            item {
                DebugSection(
                    stringResource(R.string.dbg_a11y_tree) + (treeNote?.let { "  ·  $it" } ?: ""),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DebugChip(
                        label = stringResource(R.string.dbg_a11y_refresh),
                        selected = false,
                        onClick = { loadTree() },
                    )
                    DebugChip(
                        label = stringResource(R.string.dbg_a11y_clear_highlight),
                        selected = false,
                        onClick = {
                            DebugBridge.evalRaw(tab.session, DomScripts.clearHighlight) { }
                        },
                    )
                }
            }

            if (nodes.isEmpty()) {
                item { DebugEmpty(stringResource(R.string.dbg_a11y_tree_empty)) }
            }

            items(nodes) { node ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (node.path.isNotEmpty()) {
                                DebugBridge.evalJson(tab.session, DomScripts.highlight(node.path)) { }
                            }
                        }
                        .padding(start = (8 + node.depth * 10).dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    DebugBadge(node.role, NeonCyan)
                    Text(
                        text = "  " + (node.name.ifBlank { "—" }) +
                            (if (node.value.isNotBlank()) "  = ${node.value}" else ""),
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }

            item { DebugSection(stringResource(R.string.dbg_a11y_ws)) }

            if (sockets.isEmpty()) {
                item { DebugEmpty(stringResource(R.string.dbg_a11y_ws_empty)) }
            }

            items(sockets.size) { index ->
                val socket = sockets[index]
                val open = expandedSocket == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedSocket = if (open) null else index },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DebugBadge(
                            text = stringResource(R.string.dbg_a11y_ws_open),
                            color = SuccessGreen,
                        )
                        Text(
                            text = socket.url,
                            style = DebugMonoSmall,
                            color = NeonCyan,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${socket.messages.size}",
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (open) {
                        Text(
                            text = stringResource(
                                R.string.dbg_a11y_ws_opened,
                                timeOf(socket.openedAt),
                            ),
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        socket.messages.forEach { message ->
                            val outgoing = message.direction == "send"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 2.dp),
                            ) {
                                DebugBadge(
                                    text = if (outgoing) "▲" else "▼",
                                    color = if (outgoing) NeonPurple else NeonCyan,
                                )
                                Text(
                                    text = " ${timeOf(message.timestamp)}  ${message.payload}",
                                    style = DebugMonoSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.dbg_a11y_limits),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

private fun timeOf(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
