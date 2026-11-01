package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.ui.components.ScreenHeader
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber
import io.github.sachintha82288.devbrowser.util.IdnCheck
import io.github.sachintha82288.devbrowser.util.UrlUtils

/**
 * Stage B: security centre.
 *
 * One screen collects the protection status, the current site's certificate and
 * IDN verdict, the per-site permission dashboard, the at-rest/exit/privacy
 * switches and the panic button.
 */
@Composable
fun SecurityCenterScreen(app: DevBrowserApplication, onClose: () -> Unit) {
    val settings = app.settings
    val tab = app.tabs.selectedTab
    val host = UrlUtils.hostOf(tab?.url)
    val idn = IdnCheck.check(host)
    var showCertificate by remember { mutableStateOf(false) }
    val permissionsVersion = app.tabs.permissions.version
    val permissionEntries = remember(permissionsVersion) { app.tabs.permissions.entries() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.security_center), onBack = onClose)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            DebugSection(stringResource(R.string.sec_protection))
            StatusRow(
                label = stringResource(R.string.https_only),
                value = if (settings.httpsOnly) {
                    stringResource(R.string.sec_on)
                } else {
                    stringResource(R.string.sec_off)
                },
                good = settings.httpsOnly,
            )
            StatusRow(
                label = stringResource(R.string.tracking_protection),
                value = settings.trackingProtection.name,
                good = settings.trackingProtection.name != "NONE",
            )
            StatusRow(
                label = stringResource(R.string.sec_doh),
                value = settings.dohMode.name,
                good = settings.dohMode.name != "OFF",
            )
            StatusRow(
                label = stringResource(R.string.sec_blocked_total),
                value = app.tabs.totalBlockedCount().toString(),
                good = app.tabs.totalBlockedCount() > 0,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DebugSection(stringResource(R.string.sec_site))
            if (host == null) {
                Text(
                    text = stringResource(R.string.sec_no_site),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    text = host,
                    style = MonoTextStyle,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                if (idn != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DebugBadge(stringResource(R.string.sec_idn_badge), WarningAmber)
                        Text(
                            text = stringResource(R.string.sec_idn_risk, idn.reason),
                            style = DebugMonoSmall,
                            color = WarningAmber,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.sec_idn_ok),
                        style = DebugMonoSmall,
                        color = SuccessGreen,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                if (tab != null) {
                    TextButton(onClick = { showCertificate = true }) {
                        Text(stringResource(R.string.sec_view_certificate), style = MonoTextStyle)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DebugSection(stringResource(R.string.sec_permissions))
            if (permissionEntries.isEmpty()) {
                Text(
                    text = stringResource(R.string.sec_permissions_empty),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                permissionEntries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.host,
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.kind,
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DebugBadge(
                            text = stringResource(
                                if (entry.granted) R.string.sec_granted else R.string.sec_denied,
                            ),
                            color = if (entry.granted) SuccessGreen else NeonRed,
                        )
                        TextButton(
                            onClick = { app.tabs.permissions.revoke(entry.host, entry.kind) },
                        ) {
                            Text(stringResource(R.string.sec_revoke), style = DebugMonoSmall)
                        }
                    }
                }
                TextButton(onClick = { app.tabs.permissions.clearAll() }) {
                    Text(stringResource(R.string.sec_revoke_all), style = MonoTextStyle)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DebugSection(stringResource(R.string.sec_privacy))
            ToggleRow(
                title = stringResource(R.string.sec_clear_exit),
                subtitle = stringResource(R.string.sec_clear_exit_sub),
                checked = settings.clearOnExit,
                onCheckedChange = { settings.clearOnExit = it },
            )
            ToggleRow(
                title = stringResource(R.string.sec_encrypted),
                subtitle = stringResource(R.string.sec_encrypted_sub),
                checked = settings.encryptStorage,
                onCheckedChange = { settings.encryptStorage = it },
            )
            ToggleRow(
                title = stringResource(R.string.sec_app_lock),
                subtitle = stringResource(R.string.sec_app_lock_sub),
                checked = settings.appLock,
                onCheckedChange = { settings.appLock = it },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DebugSection(stringResource(R.string.sec_panic))
            Text(
                text = stringResource(R.string.sec_panic_sub),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Button(
                onClick = {
                    app.panic()
                    onClose()
                },
                modifier = Modifier.padding(12.dp),
            ) {
                Text(stringResource(R.string.sec_panic_button))
            }

            Text(
                text = stringResource(R.string.sec_note, "apk"),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    if (showCertificate && tab != null) {
        CertificateDialog(tab = tab, onDismiss = { showCertificate = false })
    }
}

@Composable
private fun StatusRow(label: String, value: String, good: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DebugMonoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        DebugBadge(text = value, color = if (good) SuccessGreen else WarningAmber)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
