package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.data.ZoomStore
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.util.UrlUtils
import kotlin.math.roundToInt

/** Stage A: per-site zoom picker (50–200 %). */
@Composable
fun ZoomDialog(app: DevBrowserApplication, tab: BrowserTab, onDismiss: () -> Unit) {
    val host = UrlUtils.hostOf(tab.url)
    var percent by remember(host) {
        mutableStateOf(app.tabs.zoom.percentFor(host).toFloat())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zoom_title), color = NeonCyan) },
        text = {
            Column {
                Text(
                    text = host?.let { stringResource(R.string.zoom_site_note, it) }
                        ?: stringResource(R.string.zoom_no_site),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${percent.roundToInt()}%",
                    style = MonoTextStyle,
                    color = NeonCyan,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Slider(
                    value = percent,
                    onValueChange = { percent = it },
                    onValueChangeFinished = {
                        val value = (percent / ZoomStore.STEP).roundToInt() * ZoomStore.STEP
                        percent = value.toFloat()
                        app.tabs.setZoomForSelected(value)
                    },
                    valueRange = ZoomStore.MIN.toFloat()..ZoomStore.MAX.toFloat(),
                    steps = (ZoomStore.MAX - ZoomStore.MIN) / ZoomStore.STEP - 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        app.tabs.resetZoomForSelected()
                        percent = ZoomStore.DEFAULT.toFloat()
                    }) {
                        Text(stringResource(R.string.zoom_reset), style = MonoTextStyle)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.zoom_done), style = MonoTextStyle)
            }
        },
    )
}
