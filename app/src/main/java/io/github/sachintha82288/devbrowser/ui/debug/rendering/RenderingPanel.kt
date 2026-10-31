package io.github.sachintha82288.devbrowser.ui.debug.rendering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.github.sachintha82288.devbrowser.debug.DebugSession
import io.github.sachintha82288.devbrowser.debug.RenderingScripts
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber

/** Toggle row used by the rendering panel. */
@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Category 11 — rendering (features 91-96). */
@Composable
fun RenderingPanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val session: DebugSession = app.debug.session(tab)
    val scheme = session.renderingScheme
    val motion = session.renderingMotion
    var paintNote by remember { mutableStateOf<String?>(null) }
    var layerCount by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    fun setPaint(enabled: Boolean) {
        session.renderingPaint = enabled
        DebugBridge.evalJson(tab.session, RenderingScripts.paintFlash(enabled)) { }
    }

    fun setShifts(enabled: Boolean) {
        session.renderingShift = enabled
        DebugBridge.evalJson(tab.session, RenderingScripts.shiftOutline(enabled)) { }
    }

    fun setLayers(enabled: Boolean) {
        session.renderingLayers = enabled
        DebugBridge.evalJson(tab.session, RenderingScripts.layerBorders(enabled)) { payload ->
            layerCount = payload?.optInt("count")
        }
    }

    fun setMedia(nextScheme: String, nextMotion: String) {
        session.renderingScheme = nextScheme
        session.renderingMotion = nextMotion
        DebugBridge.evalJson(tab.session, RenderingScripts.media(nextScheme, nextMotion)) {
            note = "media: $nextScheme / $nextMotion"
        }
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_rendering),
        subtitle = tab.url,
        onClose = onClose,
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            DebugSection(stringResource(R.string.dbg_ren_overlays))
            ToggleRow(
                label = stringResource(R.string.dbg_ren_paint),
                description = stringResource(R.string.dbg_ren_paint_sub),
                checked = session.renderingPaint,
                onCheckedChange = { setPaint(it); paintNote = if (it) "on" else "off" },
            )
            ToggleRow(
                label = stringResource(R.string.dbg_ren_shift),
                description = stringResource(R.string.dbg_ren_shift_sub),
                checked = session.renderingShift,
                onCheckedChange = { setShifts(it) },
            )
            ToggleRow(
                label = stringResource(R.string.dbg_ren_layers),
                description = stringResource(R.string.dbg_ren_layers_sub),
                checked = session.renderingLayers,
                onCheckedChange = { setLayers(it) },
            )
            if (layerCount != null) {
                Text(
                    text = stringResource(R.string.dbg_ren_layers_count, layerCount ?: 0),
                    style = DebugMonoSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            DebugSection(stringResource(R.string.dbg_ren_media))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("system", "light", "dark").forEach { value ->
                    DebugChip(
                        label = value,
                        selected = scheme == value,
                        accent = NeonCyan,
                        onClick = { setMedia(value, motion) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.dbg_ren_motion),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("no-preference", "reduce").forEach { value ->
                    DebugChip(
                        label = value,
                        selected = motion == value,
                        accent = WarningAmber,
                        onClick = { setMedia(scheme, value) },
                    )
                }
                DebugChip(
                    label = stringResource(R.string.dbg_ren_media_reset),
                    selected = false,
                    onClick = {
                        session.renderingScheme = "system"
                        session.renderingMotion = "no-preference"
                        DebugBridge.evalJson(tab.session, RenderingScripts.resetMedia) {
                            note = "media reset"
                        }
                    },
                )
            }
            Text(
                text = stringResource(R.string.dbg_ren_media_note),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )

            DebugSection(stringResource(R.string.dbg_ren_throttle))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("off", "slow3g", "fast3g", "4g", "offline").forEach { profile ->
                    DebugChip(
                        label = profile,
                        selected = app.debug.store.networkThrottle == profile,
                        accent = NeonCyan,
                        onClick = {
                            app.debug.setThrottle(profile)
                            note = "throttle: $profile"
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.dbg_ren_throttle_note),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )

            paintNote?.let {
                Text(
                    text = stringResource(R.string.dbg_ren_paint_state, it),
                    style = DebugMonoSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            note?.let {
                Text(
                    text = it,
                    style = DebugMonoSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Text(
                text = stringResource(R.string.dbg_ren_limits),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
