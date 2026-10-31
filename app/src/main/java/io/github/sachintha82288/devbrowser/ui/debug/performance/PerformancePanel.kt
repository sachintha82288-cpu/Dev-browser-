package io.github.sachintha82288.devbrowser.ui.debug.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.PerfParser
import io.github.sachintha82288.devbrowser.debug.PerfScripts
import io.github.sachintha82288.devbrowser.debug.PerfSnapshot
import io.github.sachintha82288.devbrowser.debug.ResourceMetric
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugKeyValue
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber
import io.github.sachintha82288.devbrowser.util.FileExport
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * Category 6 — performance (features 45-54).
 *
 * Metrics come from one snapshot script; recording simply samples that script
 * on a timer and stores the results for export (feature 54).
 */
@Composable
fun PerformancePanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val context = LocalContext.current
    val session = app.debug.session(tab)
    var snapshot by remember { mutableStateOf<PerfSnapshot?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var samples by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var showExportResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tab.id, refreshTick) {
        DebugBridge.evalRaw(tab.session, PerfScripts.installObservers) {}
        DebugBridge.evalRaw(tab.session, PerfScripts.setFpsSampler(true)) {}
        snapshot = PerfParser.parse(DebugBridge.awaitJson(tab.session, PerfScripts.snapshot))
    }

    // Live refresh while the panel is open + recording.
    LaunchedEffect(tab.id, recording) {
        while (true) {
            delay(1000)
            val payload = DebugBridge.awaitJson(tab.session, PerfScripts.snapshot)
            snapshot = PerfParser.parse(payload) ?: snapshot
            if (recording && payload != null) {
                samples = (samples + payload).takeLast(600)
            }
        }
    }

    val current = snapshot

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_perf),
        subtitle = tab.url,
        onClose = onClose,
        actions = {
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.dbg_perf_refresh), tint = NeonCyan)
            }
            IconButton(onClick = {
                val array = JSONArray()
                samples.forEach { array.put(it) }
                val payload = JSONObject()
                    .put("url", tab.url ?: "")
                    .put("exportedAt", System.currentTimeMillis())
                    .put("samples", array)
                val location = FileExport.saveToDownloads(
                    context,
                    "devbrowser-performance-${System.currentTimeMillis()}.json",
                    "application/json",
                    payload.toString(2),
                )
                showExportResult = location
            }) {
                Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.dbg_perf_export), tint = NeonCyan)
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (current == null) {
                DebugEmpty(stringResource(R.string.dbg_perf_unavailable))
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(stringResource(R.string.dbg_perf_fps), "${current.fps}", NeonCyan)
                MetricCard(
                    stringResource(R.string.dbg_perf_heap),
                    current.heapBytes?.let { PerfParser.formatBytes(it) } ?: "n/a",
                    NeonPurple,
                )
                MetricCard(
                    "LCP",
                    if (current.vitals.lcp > 0) "${current.vitals.lcp.toInt()} ms" else "—",
                    ratingColor(PerfParser.rateLcp(current.vitals.lcp)),
                )
                MetricCard(
                    "INP",
                    if (current.vitals.inp > 0) "${current.vitals.inp.toInt()} ms" else "—",
                    ratingColor(PerfParser.rateInp(current.vitals.inp)),
                )
                MetricCard(
                    "CLS",
                    String.format(java.util.Locale.US, "%.3f", current.vitals.cls),
                    ratingColor(PerfParser.rateCls(current.vitals.cls)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DebugChip(
                    label = stringResource(R.string.dbg_perf_pin_fps),
                    selected = session.perfOverlay,
                    accent = NeonCyan,
                    onClick = { session.perfOverlay = !session.perfOverlay },
                )
                DebugChip(
                    label = stringResource(R.string.dbg_perf_pin_vitals),
                    selected = session.vitalsOverlay,
                    accent = NeonPurple,
                    onClick = { session.vitalsOverlay = !session.vitalsOverlay },
                )
                DebugChip(
                    label = stringResource(R.string.dbg_perf_record),
                    selected = recording,
                    accent = NeonRed,
                    onClick = {
                        recording = !recording
                        if (!recording) samples = emptyList()
                    },
                )
            }

            showExportResult?.let { location ->
                Text(
                    text = if (location != null) {
                        stringResource(R.string.dbg_perf_exported, location)
                    } else {
                        stringResource(R.string.dbg_failed)
                    },
                    style = DebugMonoSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            DebugSection(stringResource(R.string.dbg_perf_paints))
            val paint = current.paints
            if (paint.isEmpty()) {
                DebugEmpty(stringResource(R.string.dbg_perf_no_paint))
            } else {
                DebugKeyValue("first-paint", "${paint["first-paint"] ?: 0} ms")
                DebugKeyValue("first-contentful-paint", "${paint["first-contentful-paint"] ?: 0} ms")
            }

            DebugSection(stringResource(R.string.dbg_perf_navigation))
            DebugKeyValue("DNS", "${current.nav.dns} ms")
            DebugKeyValue("TCP", "${current.nav.tcp} ms")
            DebugKeyValue("TLS", "${current.nav.tls} ms")
            DebugKeyValue("TTFB", "${current.nav.ttfb} ms")
            DebugKeyValue("DOM interactive", "${current.nav.domInteractive} ms")
            DebugKeyValue("DOMContentLoaded", "${current.nav.domContentLoaded} ms")
            DebugKeyValue("Load", "${current.nav.load} ms")

            DebugSection(stringResource(R.string.dbg_perf_bundle))
            DebugKeyValue("JavaScript", PerfParser.formatBytes(current.jsTotal), valueColor = NeonCyan)
            DebugKeyValue("CSS", PerfParser.formatBytes(current.cssTotal), valueColor = NeonPurple)
            current.bundleAssets().take(25).forEach { asset ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                    Text(
                        text = asset.url.substringAfterLast('/').take(40),
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${PerfParser.formatBytes(asset.transferSize)} · ${asset.durationMs}ms",
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DebugSection(stringResource(R.string.dbg_perf_resources))
            val slowest = current.resources.sortedByDescending { it.durationMs }.take(20)
            if (slowest.isEmpty()) {
                DebugEmpty(stringResource(R.string.dbg_perf_no_resources))
            }
            ResourceChart(slowest)

            DebugSection(stringResource(R.string.dbg_perf_long_tasks))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dbg_perf_long_task_log),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = session.longTaskLogging,
                    onCheckedChange = { enabled ->
                        session.longTaskLogging = enabled
                        DebugBridge.evalRaw(tab.session, PerfScripts.setLongTaskLogging(enabled)) {}
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dbg_perf_shift_flash),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = session.shiftFlash,
                    onCheckedChange = { enabled ->
                        session.shiftFlash = enabled
                        DebugBridge.evalRaw(tab.session, PerfScripts.setShiftFlash(enabled)) {}
                    },
                )
            }
            if (current.longTasks.isEmpty()) {
                DebugEmpty(stringResource(R.string.dbg_perf_no_long_tasks))
            } else {
                current.longTasks.takeLast(25).reversed().forEach { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DebugBadge(text = "${task.durationMs} ms", color = NeonRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "@ ${task.at} ms",
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (recording && samples.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.dbg_perf_record)) },
            text = { Text("${samples.size} ${stringResource(R.string.dbg_perf_samples)}") },
            confirmButton = {
                TextButton(onClick = {
                    recording = false
                    val array = JSONArray()
                    samples.forEach { array.put(it) }
                    val payload = JSONObject()
                        .put("url", tab.url ?: "")
                        .put("exportedAt", System.currentTimeMillis())
                        .put("samples", array)
                    showExportResult = FileExport.saveToDownloads(
                        context,
                        "devbrowser-performance-${System.currentTimeMillis()}.json",
                        "application/json",
                        payload.toString(2),
                    )
                }) { Text(stringResource(R.string.dbg_perf_export)) }
            },
            dismissButton = {
                TextButton(onClick = { recording = false }) { Text(stringResource(R.string.dbg_cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: Color) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column {
            Text(text = label, style = DebugMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = DebugMono, color = accent)
        }
    }
}

@Composable
private fun ratingColor(rating: String): Color = when (rating) {
    "good" -> SuccessGreen
    "mid" -> WarningAmber
    "poor" -> NeonRed
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Feature 48: horizontal bar chart of the slowest resources. */
@Composable
private fun ResourceChart(resources: List<ResourceMetric>) {
    if (resources.isEmpty()) return
    val max = resources.maxOf { it.durationMs }.coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .padding(horizontal = 12.dp),
    ) {
        resources.forEach { resource ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = resource.url.substringAfterLast('/').take(46),
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${resource.durationMs} ms · ${PerfParser.formatBytes(resource.transferSize)}",
                        style = DebugMonoSmall,
                        color = NeonCyan,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((resource.durationMs.toFloat() / max).coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .background(
                                if (resource.durationMs > 300) NeonRed else NeonCyan,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }
    }
}
