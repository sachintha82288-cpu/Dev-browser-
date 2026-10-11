package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.ActiveExtensionPrompt
import io.github.sachintha82288.devbrowser.browser.ActivePermission
import io.github.sachintha82288.devbrowser.browser.ActivePrompt
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle

/**
 * Renders every [ActivePrompt] type as a Material dialog. The prompt objects
 * complete the underlying GeckoResult themselves when answered.
 */
@Composable
fun PromptDialog(
    prompt: ActivePrompt,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        is ActivePrompt.Alert -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(prompt.title ?: "") },
            text = { Text(prompt.message ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    prompt.onDismiss()
                    onDismiss()
                }) { Text(stringResource(R.string.ok)) }
            },
        )

        is ActivePrompt.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(prompt.title ?: "") },
            text = { Text(prompt.message ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    prompt.onAnswer(true)
                    onDismiss()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    prompt.onAnswer(false)
                    onDismiss()
                }) { Text(stringResource(R.string.cancel)) }
            },
        )

        is ActivePrompt.LeaveSite -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(prompt.title ?: "") },
            text = { Text(prompt.message ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    prompt.onAnswer(true)
                    onDismiss()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    prompt.onAnswer(false)
                    onDismiss()
                }) { Text(stringResource(R.string.cancel)) }
            },
        )

        is ActivePrompt.Input -> {
            var value by remember(prompt) { mutableStateOf(prompt.defaultValue ?: "") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(prompt.title ?: "") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!prompt.message.isNullOrBlank()) Text(prompt.message)
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prompt.onSubmit(value)
                        onDismiss()
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        prompt.onSubmit(null)
                        onDismiss()
                    }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        is ActivePrompt.Auth -> {
            var username by remember(prompt) { mutableStateOf("") }
            var password by remember(prompt) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(prompt.title ?: "Authentication required") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!prompt.message.isNullOrBlank()) Text(prompt.message)
                        if (prompt.previousFailed) {
                            Text(
                                "Login failed — try again.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!prompt.onlyPassword) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                singleLine = true,
                                label = { Text(stringResource(R.string.username)) },
                            )
                        }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.password)) },
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prompt.onSubmit(username, password)
                        onDismiss()
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        prompt.onCancel()
                        onDismiss()
                    }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        is ActivePrompt.Choice -> {
            var selected by remember(prompt) { mutableStateOf(setOf<String>()) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(prompt.title ?: "Choose") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        if (!prompt.message.isNullOrBlank()) Text(prompt.message)
                        prompt.choices.filter { !it.separator }.forEach { choice ->
                            if (prompt.allowMultiple) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = choice.id in selected,
                                        onCheckedChange = { checked ->
                                            selected =
                                                if (checked) selected + choice.id else selected - choice.id
                                        },
                                    )
                                    Text(
                                        text = choice.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            } else {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        prompt.onSelect(listOf(choice.id))
                                        onDismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = choice.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (prompt.allowMultiple) {
                        TextButton(onClick = {
                            prompt.onSelect(selected.toList())
                            onDismiss()
                        }) { Text(stringResource(R.string.ok)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        prompt.onSelect(emptyList())
                        onDismiss()
                    }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        is ActivePrompt.Color -> {
            var value by remember(prompt) { mutableStateOf(prompt.defaultValue ?: "#FF2E63") }
            val swatches = (prompt.predefined + listOf("#FF2E63", "#00E5FF", "#9D4EDD", "#3DDC97")).distinct()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Pick a color") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            singleLine = true,
                            textStyle = MonoTextStyle,
                        )
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            swatches.take(6).forEach { hex ->
                                androidx.compose.material3.Surface(
                                    onClick = { value = hex },
                                    color = parseColorOr(hex, MaterialTheme.colorScheme.surfaceVariant),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.padding(2.dp),
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prompt.onSubmit(value)
                        onDismiss()
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        is ActivePrompt.DateTime -> {
            var value by remember(prompt) { mutableStateOf(prompt.defaultValue ?: "") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Enter date/time value") },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        textStyle = MonoTextStyle,
                        placeholder = { Text("e.g. 2026-01-31 or 13:45") },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        prompt.onSubmit(value)
                        onDismiss()
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        prompt.onSubmit(prompt.defaultValue ?: "")
                        onDismiss()
                    }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        is ActivePrompt.File -> Unit // handled by the system file picker launcher

        is ActivePrompt.Unsupported -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Not supported") },
            text = { Text(prompt.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
fun PermissionDialog(
    host: String,
    kind: ActivePermission.Kind,
    onAnswer: (Boolean) -> Unit,
) {
    val message = when (kind) {
        ActivePermission.Kind.LOCATION -> stringResource(R.string.location_permission)
        ActivePermission.Kind.NOTIFICATION -> stringResource(R.string.notification_permission)
        ActivePermission.Kind.PERSISTENT_STORAGE -> stringResource(R.string.persistent_storage)
        ActivePermission.Kind.MEDIA -> stringResource(R.string.media_permission)
        ActivePermission.Kind.OTHER -> "The site requests an extra permission"
    }
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(host, style = MonoTextStyle) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.allow)) }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.deny)) }
        },
    )
}

@Composable
fun ExtensionPermissionDialog(
    prompt: ActiveExtensionPrompt,
    onAnswer: (granted: Boolean, privateModeGranted: Boolean) -> Unit,
) {
    var allowPrivate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onAnswer(false, false) },
        title = {
            Text(
                if (prompt.isUpdate) "Update ${prompt.extensionName}?" else "Add ${prompt.extensionName}?",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prompt.permissions.isNotEmpty()) {
                    Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.labelLarge)
                    prompt.permissions.take(10).forEach { permission ->
                        Text(
                            text = "• $permission",
                            style = MonoTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text("This add-on requires no special permissions.")
                }
                Row {
                    Checkbox(checked = allowPrivate, onCheckedChange = { allowPrivate = it })
                    Text(
                        stringResource(R.string.allow_in_private),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true, allowPrivate) }) { Text(stringResource(R.string.allow)) }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false, false) }) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun parseColorOr(hex: String, fallback: androidx.compose.ui.graphics.Color) = try {
    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}
