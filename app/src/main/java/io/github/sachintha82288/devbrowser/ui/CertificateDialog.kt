package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugKeyValue
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber

/**
 * Stage B: certificate viewer reachable from the lock badge / security centre.
 * Shows the leaf certificate only — GeckoView does not expose the chain.
 */
@Composable
fun CertificateDialog(tab: BrowserTab, onDismiss: () -> Unit) {
    val certificate = tab.certificate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cert_title), color = NeonCyan) },
        text = {
            if (certificate == null) {
                Text(
                    text = stringResource(R.string.cert_missing),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = if (certificate.validNow) {
                            stringResource(R.string.cert_valid)
                        } else {
                            stringResource(R.string.cert_invalid)
                        },
                        style = MonoTextStyle,
                        color = if (certificate.validNow) SuccessGreen else MaterialTheme.colorScheme.error,
                    )
                    if (certificate.selfSigned) {
                        Text(
                            text = stringResource(R.string.cert_self_signed),
                            style = MonoTextStyle,
                            color = WarningAmber,
                        )
                    }
                    DebugKeyValue(stringResource(R.string.cert_subject), certificate.subject)
                    DebugKeyValue(stringResource(R.string.cert_issuer), certificate.issuer)
                    DebugKeyValue(stringResource(R.string.cert_valid_from), certificate.notBefore)
                    DebugKeyValue(stringResource(R.string.cert_valid_to), certificate.notAfter)
                    DebugKeyValue(stringResource(R.string.cert_serial), certificate.serial)
                    DebugKeyValue(stringResource(R.string.cert_signature), certificate.signatureAlgorithm)
                    DebugKeyValue(
                        stringResource(R.string.cert_key),
                        "${certificate.keyAlgorithm} ${certificate.keySize} bit",
                    )
                    if (certificate.subjectAltNames.isNotEmpty()) {
                        DebugKeyValue(
                            stringResource(R.string.cert_san),
                            certificate.subjectAltNames.take(6).joinToString(", "),
                        )
                    }
                    Text(
                        text = stringResource(R.string.cert_note),
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
