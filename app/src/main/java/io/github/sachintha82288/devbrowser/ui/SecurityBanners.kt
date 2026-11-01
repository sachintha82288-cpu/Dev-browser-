package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber

/** Stage B: credentials typed into a plain-http page are sent in clear text. */
@Composable
fun InsecureLoginBanner(fieldCount: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = NeonRed.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp)) {
                Text(
                    text = stringResource(R.string.insecure_login_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonRed,
                )
                Text(
                    text = stringResource(R.string.insecure_login_body, fieldCount),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss), style = MonoTextStyle)
            }
        }
    }
}

/** Stage B: punycode / mixed-script host warning. */
@Composable
fun IdnBanner(host: String, reason: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = WarningAmber.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp)) {
                Text(
                    text = stringResource(R.string.idn_warning_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = WarningAmber,
                )
                Text(
                    text = stringResource(R.string.idn_warning_body, host, reason),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss), style = MonoTextStyle)
            }
        }
    }
}
