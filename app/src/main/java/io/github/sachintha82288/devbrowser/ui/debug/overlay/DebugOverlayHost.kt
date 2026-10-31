package io.github.sachintha82288.devbrowser.ui.debug.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.PerfParser
import io.github.sachintha82288.devbrowser.debug.PerfScripts
import io.github.sachintha82288.devbrowser.debug.PerfSnapshot
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber
import kotlinx.coroutines.delay

/**
 * Floating performance pills drawn over the live page (features 45, 46, 49).
 *
 * Enabled from the performance panel; the sampler is stopped as soon as the
 * overlay disappears so browsing stays cheap.
 */
@Composable
fun DebugOverlayHost(app: DevBrowserApplication, tab: BrowserTab, modifier: Modifier = Modifier) {
    val session = app.debug.session(tab)
    if (!session.perfOverlay && !session.vitalsOverlay) return

    var snapshot by remember { mutableStateOf<PerfSnapshot?>(null) }

    LaunchedEffect(tab.id, session.perfOverlay, session.vitalsOverlay) {
        DebugBridge.evalRaw(tab.session, PerfScripts.setFpsSampler(true)) {}
        try {
            while (true) {
                delay(1000)
                snapshot = PerfParser.parse(DebugBridge.awaitJson(tab.session, PerfScripts.snapshot))
            }
        } finally {
            DebugBridge.evalRaw(tab.session, PerfScripts.setFpsSampler(false)) {}
        }
    }

    val current = snapshot
    Column(modifier = modifier.padding(8.dp)) {
        if (session.perfOverlay) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "FPS", style = DebugMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${current?.fps ?: 0}",
                    style = DebugMonoSmall,
                    color = when (val fps = current?.fps ?: 0) {
                        in 0..29 -> NeonRed
                        in 30..49 -> WarningAmber
                        else -> SuccessGreen
                    },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "HEAP", style = DebugMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = current?.heapBytes?.let { PerfParser.formatBytes(it) } ?: "n/a",
                    style = DebugMonoSmall,
                    color = NeonCyan,
                )
            }
        }
        if (session.vitalsOverlay) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.dbg_perf_vitals_title),
                    style = DebugMonoSmall,
                    color = NeonPurple,
                )
                val vitals = current?.vitals
                VitalRow("LCP", vitals?.lcp?.let { "${it.toInt()} ms" } ?: "—", PerfParser.rateLcp(vitals?.lcp ?: 0.0))
                VitalRow("INP", vitals?.inp?.let { "${it.toInt()} ms" } ?: "—", PerfParser.rateInp(vitals?.inp ?: 0.0))
                VitalRow(
                    "CLS",
                    String.format(java.util.Locale.US, "%.3f", vitals?.cls ?: 0.0),
                    PerfParser.rateCls(vitals?.cls ?: 0.0),
                )
            }
        }
    }
}

@Composable
private fun VitalRow(label: String, value: String, rating: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = DebugMonoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = value,
            style = DebugMonoSmall,
            color = when (rating) {
                "good" -> SuccessGreen
                "mid" -> WarningAmber
                "poor" -> NeonRed
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
