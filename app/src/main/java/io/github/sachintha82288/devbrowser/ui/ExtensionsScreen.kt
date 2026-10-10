package io.github.sachintha82288.devbrowser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import io.github.sachintha82288.devbrowser.extensions.ExtensionInfo
import io.github.sachintha82288.devbrowser.ui.components.NeonCard
import io.github.sachintha82288.devbrowser.ui.components.ScreenHeader
import io.github.sachintha82288.devbrowser.ui.components.SectionLabel
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle

/** Add-ons manager: install from URL / file and manage installed extensions. */
@Composable
fun ExtensionsScreen(app: DevBrowserApplication, onClose: () -> Unit) {
    val manager = app.extensions
    var url by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            manager.installFromFile(uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.extensions_title), onBack = onClose)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.extensions_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.extension_url_hint)) },
                        textStyle = MonoTextStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { manager.installFromUrl(url) },
                            enabled = url.isNotBlank() && !manager.busy,
                        ) {
                            Text(stringResource(R.string.install))
                        }
                        OutlinedButton(
                            onClick = {
                                filePicker.launch(
                                    arrayOf(
                                        "application/x-xpinstall-install",
                                        "application/zip",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                            enabled = !manager.busy,
                        ) {
                            Icon(
                                Icons.Rounded.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.install_from_file))
                        }
                        if (manager.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                    Text(
                        text = stringResource(R.string.amo_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionLabel("Installed")

            if (manager.extensions.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_extensions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            manager.extensions.forEach { ext ->
                ExtensionCard(
                    ext = ext,
                    onToggleEnabled = { manager.setEnabled(ext, it) },
                    onTogglePrivate = { manager.setPrivateBrowsing(ext, it) },
                    onUpdate = { manager.update(ext) },
                    onUninstall = { manager.uninstall(ext) },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ExtensionCard(
    ext: ExtensionInfo,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
) {
    NeonCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = ext.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
                Text(
                    text = "v${ext.version}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (ext.description.isNotBlank()) {
                Text(
                    text = ext.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = ext.enabled, onCheckedChange = onToggleEnabled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.allow_in_private),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = ext.allowedInPrivateBrowsing, onCheckedChange = onTogglePrivate)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onUpdate) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(R.string.update_extension))
                }
                OutlinedButton(onClick = onUninstall) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(R.string.uninstall))
                }
            }
        }
    }
}
