package io.github.sachintha82288.devbrowser.ui.debug.responsive

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.DebugSession
import io.github.sachintha82288.devbrowser.debug.ResponsiveScripts
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
import kotlinx.coroutines.delay
import org.json.JSONObject

private data class DevicePreset(val label: String, val width: Int, val height: Int)

private val presets = listOf(
    DevicePreset("iPhone 15", 393, 852),
    DevicePreset("Pixel 8", 412, 915),
    DevicePreset("iPad mini", 744, 1133),
    DevicePreset("Galaxy Tab", 800, 1280),
)

/** Category 9 — responsive design (features 75-82). */
@Composable
fun ResponsivePanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val session = app.debug.session(tab)
    var width by remember { mutableStateOf("393") }
    var height by remember { mutableStateOf("852") }
    var dpr by remember { mutableStateOf(3f) }
    var mediaQueries by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var mediaSource by remember { mutableStateOf<List<String>>(emptyList()) }
    var rulerDistance by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    fun apply(widthValue: Int, heightValue: Int, dprValue: Float, touch: Boolean) {
        width = widthValue.toString()
        height = heightValue.toString()
        dpr = dprValue
        DebugBridge.evalJson(
            tab.session,
            ResponsiveScripts.apply(widthValue, heightValue, dprValue, touch),
        ) { note = "viewport ${widthValue}x${heightValue} @${dprValue}x" }
    }

    LaunchedEffect(tab.id, refreshTick) {
        if (refreshTick > 0) {
            val payload = DebugBridge.awaitJson(tab.session, ResponsiveScripts.mediaQueries)
            val array = payload?.optJSONArray("queries")
            val parsed = ArrayList<Pair<String, Boolean>>()
            val sources = ArrayList<String>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parsed.add(obj.optString("q") to obj.optBoolean("m"))
                    sources.add(obj.optString("src"))
                }
            }
            mediaQueries = parsed
            mediaSource = sources
        }
    }

    LaunchedEffect(tab.id, session.responsiveRuler) {
        if (!session.responsiveRuler) return@LaunchedEffect
        while (session.responsiveRuler) {
            delay(400)
            val payload = DebugBridge.awaitJson(tab.session, ResponsiveScripts.rulerResult)
            if (payload != null) rulerDistance = payload.optInt("dist")
        }
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_responsive),
        subtitle = tab.url,
        onClose = onClose,
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DebugSection(stringResource(R.string.dbg_res_presets))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presets.forEach { preset ->
                    DebugChip(
                        label = "${preset.label} ${preset.width}x${preset.height}",
                        selected = width == preset.width.toString() && height == preset.height.toString(),
                        onClick = { apply(preset.width, preset.height, dpr, session.responsiveTouch) },
                    )
                }
                DebugChip(
                    label = stringResource(R.string.dbg_res_reset),
                    selected = false,
                    accent = NeonRed,
                    onClick = {
                        DebugBridge.evalJson(tab.session, ResponsiveScripts.reset) {}
                        session.responsiveRuler = false
                        note = null
                    },
                )
            }

            DebugSection(stringResource(R.string.dbg_res_custom))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.dbg_res_width), style = DebugMonoSmall) },
                    singleLine = true,
                    textStyle = DebugMono,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.dbg_res_height), style = DebugMonoSmall) },
                    singleLine = true,
                    textStyle = DebugMono,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = {
                    val w = width.toIntOrNull() ?: return@TextButton
                    val h = height.toIntOrNull() ?: return@TextButton
                    apply(w, h, dpr, session.responsiveTouch)
                }) { Text(stringResource(R.string.dbg_res_apply), style = DebugMonoSmall, color = NeonCyan) }
                TextButton(onClick = {
                    val w = width.toIntOrNull() ?: return@TextButton
                    val h = height.toIntOrNull() ?: return@TextButton
                    apply(h, w, dpr, session.responsiveTouch)
                }) { Text(stringResource(R.string.dbg_res_orientation), style = DebugMonoSmall, color = NeonPurple) }
            }

            DebugSection(stringResource(R.string.dbg_res_dpr))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(1f, 2f, 3f).forEach { value ->
                    DebugChip(
                        label = "${value.toInt()}x",
                        selected = dpr == value,
                        accent = NeonPurple,
                        onClick = {
                            val w = width.toIntOrNull() ?: 393
                            val h = height.toIntOrNull() ?: 852
                            apply(w, h, value, session.responsiveTouch)
                        },
                    )
                }
            }

            SwitchLine(
                label = stringResource(R.string.dbg_res_touch),
                checked = session.responsiveTouch,
                onChange = { enabled ->
                    session.responsiveTouch = enabled
                    val w = width.toIntOrNull() ?: 393
                    val h = height.toIntOrNull() ?: 852
                    apply(w, h, dpr, enabled)
                },
            )
            SwitchLine(
                label = stringResource(R.string.dbg_res_overlay),
                checked = session.responsiveOverlay,
                onChange = { enabled ->
                    session.responsiveOverlay = enabled
                    DebugBridge.evalJson(tab.session, ResponsiveScripts.overlay(enabled)) {}
                },
            )
            SwitchLine(
                label = stringResource(R.string.dbg_res_ruler),
                checked = session.responsiveRuler,
                onChange = { enabled ->
                    session.responsiveRuler = enabled
                    DebugBridge.evalJson(tab.session, ResponsiveScripts.ruler(enabled)) {}
                    if (!enabled) rulerDistance = 0
                },
            )
            if (session.responsiveRuler) {
                Text(
                    text = "${stringResource(R.string.dbg_res_distance)}: $rulerDistance px",
                    style = DebugMonoSmall,
                    color = NeonRed,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                Text(
                    text = stringResource(R.string.dbg_res_ruler_hint),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = stringResource(R.string.dbg_res_note),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )

            DebugSection(stringResource(R.string.dbg_res_media))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dbg_res_media_hint),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { refreshTick++ }) {
                    Text(stringResource(R.string.dbg_res_load_media), style = DebugMonoSmall, color = NeonCyan)
                }
            }
            if (mediaQueries.isEmpty()) {
                DebugEmpty(stringResource(R.string.dbg_res_media_empty))
            } else {
                Column(modifier = Modifier.heightIn(max = 360.dp)) {
                    mediaQueries.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DebugBadge(
                                text = if (entry.second) "match" else "no",
                                color = if (entry.second) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = entry.first,
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = mediaSource.getOrNull(index) ?: "",
                                style = DebugMonoSmall,
                                color = NeonPurple,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DebugMonoSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
