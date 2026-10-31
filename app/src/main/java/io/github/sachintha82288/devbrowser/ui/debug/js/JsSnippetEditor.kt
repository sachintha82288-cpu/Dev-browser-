package io.github.sachintha82288.devbrowser.ui.debug.js

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.debug.JsSnippet
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall

/** Create / edit dialog for the snippet library (features 40 and 41). */
@Composable
fun JsSnippetEditor(
    initial: JsSnippet?,
    onDismiss: () -> Unit,
    onSave: (name: String, code: String, pattern: String, runOnLoad: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var pattern by remember { mutableStateOf(initial?.urlPattern ?: "") }
    var runOnLoad by remember { mutableStateOf(initial?.runOnLoad ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initial == null) R.string.dbg_js_snippet_new else R.string.dbg_js_snippet_edit,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dbg_js_snippet_name)) },
                    singleLine = true,
                    textStyle = DebugMono,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.dbg_js_snippet_code)) },
                    textStyle = DebugMono,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.dbg_js_snippet_pattern)) },
                    singleLine = true,
                    textStyle = DebugMono,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Checkbox(checked = runOnLoad, onCheckedChange = { runOnLoad = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.dbg_js_snippet_onload), style = DebugMonoSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.isNotBlank(),
                onClick = { onSave(name.ifBlank { "Snippet" }, code, pattern, runOnLoad) },
            ) {
                Text(stringResource(R.string.dbg_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dbg_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
