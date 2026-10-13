package io.github.sachintha82288.devbrowser.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.data.ProxyMode
import io.github.sachintha82288.devbrowser.proxy.ProxyPrefs
import io.github.sachintha82288.devbrowser.ui.components.NeonCard
import io.github.sachintha82288.devbrowser.ui.components.ScreenHeader
import io.github.sachintha82288.devbrowser.ui.components.SectionLabel
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber

/** Proxy & Tor configuration; applied live through Gecko preferences. */
@Composable
fun ProxyScreen(app: DevBrowserApplication, onClose: () -> Unit) {
    val settings = app.settings
    val engine = app.engine
    val context = LocalContext.current
    val appliedText = stringResource(R.string.applied)

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.proxy_title), onBack = onClose)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(stringResource(R.string.proxy_mode))

            ProxyMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = settings.proxyMode == mode,
                        onClick = { settings.proxyMode = mode },
                    )
                    Text(
                        text = when (mode) {
                            ProxyMode.DIRECT -> stringResource(R.string.proxy_direct)
                            ProxyMode.SYSTEM -> stringResource(R.string.proxy_system)
                            ProxyMode.MANUAL -> stringResource(R.string.proxy_manual)
                            ProxyMode.TOR -> stringResource(R.string.proxy_tor)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (settings.proxyMode == ProxyMode.MANUAL) {
                HorizontalDivider()
                OutlinedTextField(
                    value = settings.proxyHost,
                    onValueChange = { settings.proxyHost = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.proxy_host)) },
                    textStyle = MonoTextStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = settings.proxyPort,
                    onValueChange = { v ->
                        settings.proxyPort = v.filter { it.isDigit() }.take(5)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.proxy_port)) },
                    textStyle = MonoTextStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (settings.proxyUseSocks) "SOCKS" else "HTTP",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.proxyUseSocks,
                        onCheckedChange = { settings.proxyUseSocks = it },
                    )
                }
                if (settings.proxyUseSocks) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.proxy_socks_version),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        listOf(4, 5).forEach { version ->
                            RadioButton(
                                selected = settings.proxySocksVersion == version,
                                onClick = { settings.proxySocksVersion = version },
                            )
                            Text(
                                stringResource(R.string.version_label, version.toString()),
                                style = MonoTextStyle,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.proxy_remote_dns),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = settings.proxyRemoteDns,
                            onCheckedChange = { settings.proxyRemoteDns = it },
                        )
                    }
                }
            }

            if (settings.proxyMode == ProxyMode.TOR) {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.tor_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.tor_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { openOrbot(context) }) {
                                Text(stringResource(R.string.open_orbot))
                            }
                            OutlinedButton(onClick = {
                                app.tabs.loadInSelectedTab("https://check.torproject.org")
                                onClose()
                            }) {
                                Icon(Icons.Rounded.Language, contentDescription = null)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(stringResource(R.string.tor_check))
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    // Persisted settings are already saved; apply them live to Gecko
                    // and regenerate the startup YAML for the next cold start.
                    engine.applyPrefs(ProxyPrefs.proxyPrefs(settings))
                    engine.writeStartupConfigNow(settings)
                    app.tabs.showSnackbar(appliedText)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.apply))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** Orbot's package name — also declared in `<queries>` for package visibility. */
private const val ORBOT_PACKAGE = "org.torproject.android"

/**
 * Launch Orbot when it is installed; otherwise open its Play Store listing
 * (falling back to the web listing when no store app is present).
 */
private fun openOrbot(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(ORBOT_PACKAGE)
    if (launch != null) {
        try {
            context.startActivity(launch)
            return
        } catch (_: Exception) {
            // Installed but not launchable — fall through to the store.
        }
    }
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$ORBOT_PACKAGE"))
    try {
        context.startActivity(market)
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$ORBOT_PACKAGE"),
                ),
            )
        } catch (_: Exception) {
            // Neither a store nor a browser is available.
        }
    }
}
