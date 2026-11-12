package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import io.github.sachintha82288.devbrowser.browser.StartupPrefs
import io.github.sachintha82288.devbrowser.data.DohMode
import io.github.sachintha82288.devbrowser.data.SearchEngine
import io.github.sachintha82288.devbrowser.data.TrackingProtectionLevel
import io.github.sachintha82288.devbrowser.proxy.ProxyPrefs
import io.github.sachintha82288.devbrowser.ui.components.ScreenHeader
import io.github.sachintha82288.devbrowser.ui.components.SectionLabel
import io.github.sachintha82288.devbrowser.ui.components.SettingRow
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle

/** Browser settings; most toggles apply instantly to the Gecko runtime. */
@Composable
fun SettingsScreen(app: DevBrowserApplication, onClose: () -> Unit) {
    val settings = app.settings
    val engine = app.engine

    var showSearchPicker by remember { mutableStateOf(false) }
    var showTpPicker by remember { mutableStateOf(false) }
    var showDohPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val clearedText = stringResource(R.string.data_cleared)
    val failedText = stringResource(R.string.clear_data_failed)

    fun applyNow() {
        engine.applyRuntimeToggles()
        engine.applyPrefs(ProxyPrefs.dohPrefs(settings))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onClose)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(stringResource(R.string.general))

            SettingRow(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.search_engine),
                subtitle = settings.searchEngine.label,
                onClick = { showSearchPicker = true },
            )
            SettingRow(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.restore_tabs),
                trailing = {
                    Switch(
                        checked = settings.restoreTabs,
                        onCheckedChange = { settings.restoreTabs = it },
                    )
                },
            )

            HorizontalDivider()
            SectionLabel(stringResource(R.string.privacy_security))

            SettingRow(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.tracking_protection),
                subtitle = when (settings.trackingProtection) {
                    TrackingProtectionLevel.NONE -> stringResource(R.string.tp_none)
                    TrackingProtectionLevel.STANDARD -> stringResource(R.string.tp_standard)
                    TrackingProtectionLevel.STRICT -> stringResource(R.string.tp_strict)
                },
                onClick = { showTpPicker = true },
            )
            SettingRow(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.https_only),
                trailing = {
                    Switch(
                        checked = settings.httpsOnly,
                        onCheckedChange = {
                            settings.httpsOnly = it
                            applyNow()
                        },
                    )
                },
            )
            SettingRow(
                icon = Icons.Rounded.Block,
                title = stringResource(R.string.block_popups),
                trailing = {
                    Switch(
                        checked = settings.blockPopups,
                        onCheckedChange = { settings.blockPopups = it },
                    )
                },
            )
            SettingRow(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.javascript),
                trailing = {
                    Switch(
                        checked = settings.javascriptEnabled,
                        onCheckedChange = {
                            settings.javascriptEnabled = it
                            applyNow()
                        },
                    )
                },
            )
            SettingRow(
                icon = Icons.Rounded.Palette,
                title = stringResource(R.string.web_fonts),
                trailing = {
                    Switch(
                        checked = settings.webFontsEnabled,
                        onCheckedChange = {
                            settings.webFontsEnabled = it
                            applyNow()
                        },
                    )
                },
            )
            SettingRow(
                icon = Icons.Rounded.VpnKey,
                title = stringResource(R.string.doh_title),
                subtitle = when (settings.dohMode) {
                    DohMode.OFF -> stringResource(R.string.doh_off)
                    DohMode.DEFAULT -> stringResource(R.string.doh_default)
                    DohMode.CUSTOM -> stringResource(R.string.doh_custom)
                },
                onClick = { showDohPicker = true },
            )
            if (settings.dohMode == DohMode.CUSTOM) {
                OutlinedTextField(
                    value = settings.dohUrl,
                    onValueChange = {
                        settings.dohUrl = it
                        engine.applyPrefs(ProxyPrefs.dohPrefs(settings))
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.doh_url_label)) },
                    textStyle = MonoTextStyle,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            SettingRow(
                icon = Icons.Rounded.Delete,
                title = stringResource(R.string.clear_browsing_data),
                onClick = { showClearConfirm = true },
            )

            HorizontalDivider()
            SectionLabel(stringResource(R.string.developer))

            SettingRow(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.remote_debugging),
                subtitle = stringResource(R.string.remote_debugging_summary),
                trailing = {
                    Switch(
                        checked = settings.remoteDebugging,
                        onCheckedChange = {
                            settings.remoteDebugging = it
                            applyNow()
                        },
                    )
                },
            )
            SettingRow(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.console_to_logcat),
                trailing = {
                    Switch(
                        checked = settings.consoleOutput,
                        onCheckedChange = {
                            settings.consoleOutput = it
                            applyNow()
                        },
                    )
                },
            )
            SettingRow(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.enable_about_config),
                trailing = {
                    Switch(
                        checked = settings.aboutConfig,
                        onCheckedChange = {
                            settings.aboutConfig = it
                            applyNow()
                        },
                    )
                },
            )
            SettingRow(
                icon = Icons.Rounded.Extension,
                title = stringResource(R.string.allow_unsigned_extensions),
                subtitle = stringResource(R.string.allow_unsigned_extensions_summary),
                trailing = {
                    Switch(
                        checked = settings.allowUnsignedExtensions,
                        onCheckedChange = {
                            settings.allowUnsignedExtensions = it
                            // User-branch prefs persist in the Gecko profile, so
                            // switching off has to clear them rather than flip a flag.
                            if (it) {
                                engine.applyPrefs(StartupPrefs.extensionPrefs(settings))
                            } else {
                                engine.clearUserPrefs(StartupPrefs.extensionPrefsToClear(settings))
                            }
                            engine.writeStartupConfigNow(settings)
                        },
                    )
                },
            )

            HorizontalDivider()
            SectionLabel(stringResource(R.string.appearance))

            SettingRow(
                icon = Icons.Rounded.Palette,
                title = stringResource(R.string.amoled_black),
                trailing = {
                    Switch(
                        checked = settings.amoledBlack,
                        onCheckedChange = { settings.amoledBlack = it },
                    )
                },
            )

            HorizontalDivider()
            SectionLabel(stringResource(R.string.about))

            SettingRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.engine_version),
                subtitle = engine.engineVersion,
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showSearchPicker) {
        AlertDialog(
            onDismissRequest = { showSearchPicker = false },
            title = { Text(stringResource(R.string.search_engine)) },
            text = {
                Column {
                    SearchEngine.entries.forEach { engineOption ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = settings.searchEngine == engineOption,
                                onClick = { settings.searchEngine = engineOption },
                            )
                            Text(engineOption.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchPicker = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    if (showTpPicker) {
        AlertDialog(
            onDismissRequest = { showTpPicker = false },
            title = { Text(stringResource(R.string.tracking_protection)) },
            text = {
                Column {
                    listOf(
                        TrackingProtectionLevel.NONE to stringResource(R.string.tp_none),
                        TrackingProtectionLevel.STANDARD to stringResource(R.string.tp_standard),
                        TrackingProtectionLevel.STRICT to stringResource(R.string.tp_strict),
                    ).forEach { (level, label) ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = settings.trackingProtection == level,
                                onClick = {
                                    settings.trackingProtection = level
                                    applyNow()
                                },
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTpPicker = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    if (showDohPicker) {
        AlertDialog(
            onDismissRequest = { showDohPicker = false },
            title = { Text(stringResource(R.string.doh_title)) },
            text = {
                Column {
                    listOf(
                        DohMode.OFF to stringResource(R.string.doh_off),
                        DohMode.DEFAULT to stringResource(R.string.doh_default),
                        DohMode.CUSTOM to stringResource(R.string.doh_custom),
                    ).forEach { (mode, label) ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = settings.dohMode == mode,
                                onClick = {
                                    settings.dohMode = mode
                                    applyNow()
                                },
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDohPicker = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.clear_browsing_data)) },
            text = { Text(stringResource(R.string.clear_data_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    engine.clearAllBrowsingData { ok ->
                        app.tabs.showSnackbar(if (ok) clearedText else failedText)
                    }
                    app.library.clearHistory()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
