package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.ui.debug.components.copyToClipboard
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.util.QrCode

/** Stage A: QR code for the current URL (scan on another device). */
@Composable
fun QrDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val result = remember(url) { QrCode.encode(url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_title), color = NeonCyan) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (result == null) {
                    Text(
                        text = stringResource(R.string.qr_failed),
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                    ) {
                        Canvas(modifier = Modifier.size(220.dp)) {
                            val cell = size.minDimension / result.size
                            for (y in 0 until result.size) {
                                val row = result.modules[y]
                                for (x in 0 until result.size) {
                                    if (row[x]) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(x * cell, y * cell),
                                            size = Size(cell, cell),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = url,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                copyToClipboard(context, "url", url)
                onDismiss()
            }) {
                Text(stringResource(R.string.qr_copy), style = MonoTextStyle)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.zoom_done), style = MonoTextStyle)
            }
        },
    )
}
