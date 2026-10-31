package io.github.sachintha82288.devbrowser.ui.debug.js

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.JsSnippet
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.ui.debug.components.copyToClipboard
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen

private enum class JsSection { RUN, HISTORY, SNIPPETS, ERRORS }

/**
 * Category 5 — JavaScript execution (features 37-44).
 *
 * The panel keeps its own input so long snippets survive switching between
 * sections, while history and snippets come from [DebugStore].
 */
@Composable
fun JsPanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val context = LocalContext.current
    val session = app.debug.session(tab)
    val store = app.debug.store
    val copied = stringResource(R.string.dbg_copied)

    var section by remember { mutableStateOf(JsSection.RUN) }
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<JsSnippet?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val origin = remember(tab.url) { originOf(tab.url) }

    LaunchedEffect(tab.id) { app.debug.refreshFrames(tab) }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_js),
        subtitle = origin,
        onClose = onClose,
        actions = {
            IconButton(onClick = { showEditor = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.dbg_js_snippet_new), tint = NeonCyan)
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DebugChip(stringResource(R.string.dbg_js_run), section == JsSection.RUN) { section = JsSection.RUN }
                DebugChip(stringResource(R.string.dbg_js_history), section == JsSection.HISTORY) { section = JsSection.HISTORY }
                DebugChip(stringResource(R.string.dbg_js_snippets), section == JsSection.SNIPPETS) { section = JsSection.SNIPPETS }
                DebugChip(
                    "${stringResource(R.string.dbg_js_errors)} (${session.jsErrors.size})",
                    section == JsSection.ERRORS,
                ) { section = JsSection.ERRORS }
            }

            when (section) {
                JsSection.RUN -> JsRunSection(
                    app = app,
                    tab = tab,
                    input = input,
                    origin = origin,
                    onInputChange = { input = it },
                    onCopy = { text ->
                        copyToClipboard(context, "js", text)
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                )
                JsSection.HISTORY -> JsHistorySection(
                    app = app,
                    tab = tab,
                    onLoadIntoEditor = { code ->
                        input = code
                        section = JsSection.RUN
                    },
                )
                JsSection.SNIPPETS -> JsSnippetSection(
                    app = app,
                    tab = tab,
                    onEdit = { snippet ->
                        editing = snippet
                        showEditor = true
                    },
                    onRun = { snippet ->
                        input = snippet.code
                        section = JsSection.RUN
                        app.debug.evaluate(tab, snippet.code) { session.lastOutput = it }
                    },
                )
                JsSection.ERRORS -> JsErrorSection(session.jsErrors.map { it })
            }
        }
    }

    if (showEditor) {
        JsSnippetEditor(
            initial = editing,
            onDismiss = {
                showEditor = false
                editing = null
            },
            onSave = { name, code, pattern, onLoad ->
                val existing = editing
                if (existing == null) {
                    store.saveSnippet(name, code, pattern, onLoad)
                } else {
                    store.updateSnippet(existing.copy(name = name, code = code, urlPattern = pattern, runOnLoad = onLoad))
                }
                showEditor = false
                editing = null
            },
        )
    }
}

@Composable
private fun JsRunSection(
    app: DevBrowserApplication,
    tab: BrowserTab,
    input: String,
    origin: String?,
    onInputChange: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val session = app.debug.session(tab)
    val store = app.debug.store
    val jsBlocked = store.isJsBlocked(origin)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            session.frames.ifEmpty { listOf(io.github.sachintha82288.devbrowser.debug.FrameInfo(0, "top", tab.url ?: "")) }
                .forEach { frame ->
                    DebugChip(
                        label = frame.label,
                        selected = session.selectedFrameIndex == frame.index,
                        onClick = { session.selectedFrameIndex = frame.index },
                        accent = NeonPurple,
                    )
                }
        }

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            textStyle = DebugMono,
            placeholder = { Text(stringResource(R.string.dbg_js_input_hint), style = DebugMonoSmall) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (input.isNotBlank()) {
                    app.debug.evaluate(tab, input) { session.lastOutput = it }
                }
            }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.dbg_js_run), tint = NeonCyan)
            }
            Text(
                text = stringResource(R.string.dbg_js_run),
                style = DebugMonoSmall,
                color = NeonCyan,
                modifier = Modifier.clickable {
                    if (input.isNotBlank()) app.debug.evaluate(tab, input) { session.lastOutput = it }
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            if (session.lastOutput != null) {
                Text(
                    text = stringResource(R.string.dbg_js_copy_output),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .clickable { session.lastOutput?.let { onCopy(it.display) } }
                        .padding(8.dp),
                )
            }
        }

        session.lastOutput?.let { output ->
            DebugSection(stringResource(R.string.dbg_js_output))
            Text(
                text = output.display,
                style = DebugMono,
                color = if (output.ok) SuccessGreen else NeonRed,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopy(output.display) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        DebugSection(stringResource(R.string.dbg_js_options))
        SwitchRow(
            label = stringResource(R.string.dbg_js_block_site),
            checked = jsBlocked,
            enabled = origin != null,
            onCheckedChange = { blocked ->
                store.setJsBlocked(origin ?: "", blocked)
                // GeckoView exposes JavaScript as a runtime-wide preference; the
                // per-site switch is honoured by flipping it for the active page.
                app.engine.applyPrefs(mapOf("javascript.enabled" to !blocked))
            },
        )
        if (jsBlocked) {
            Text(
                text = stringResource(R.string.dbg_js_block_note),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        SwitchRow(
            label = stringResource(R.string.dbg_js_mutation),
            checked = session.mutationLogging,
            enabled = true,
            onCheckedChange = { enabled -> app.debug.setMutationLogging(tab, enabled) },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun JsHistorySection(
    app: DevBrowserApplication,
    tab: BrowserTab,
    onLoadIntoEditor: (String) -> Unit,
) {
    val store = app.debug.store
    val session = app.debug.session(tab)
    if (store.jsHistory.isEmpty()) {
        DebugEmpty(stringResource(R.string.dbg_js_no_history))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(store.jsHistory, key = { "${it.at}-${it.code.hashCode()}" }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onLoadIntoEditor(item.code)
                        app.debug.evaluate(tab, item.code) { session.lastOutput = it }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text = item.code, style = DebugMono, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                Text(
                    text = item.output.display.lineSequence().firstOrNull().orEmpty(),
                    style = DebugMonoSmall,
                    color = if (item.output.ok) MaterialTheme.colorScheme.onSurfaceVariant else NeonRed,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun JsSnippetSection(
    app: DevBrowserApplication,
    tab: BrowserTab,
    onEdit: (JsSnippet) -> Unit,
    onRun: (JsSnippet) -> Unit,
) {
    val store = app.debug.store
    if (store.snippets.isEmpty()) {
        DebugEmpty(stringResource(R.string.dbg_js_no_snippets))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(store.snippets, key = { it.id }) { snippet ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = snippet.name, style = DebugMono, color = NeonCyan)
                    if (snippet.urlPattern.isNotBlank()) {
                        Text(
                            text = if (snippet.runOnLoad) {
                                "⇢ ${snippet.urlPattern}"
                            } else {
                                snippet.urlPattern
                            },
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onRun(snippet) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.dbg_js_run), tint = NeonCyan)
                }
                IconButton(onClick = { onEdit(snippet) }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.dbg_js_snippet_edit),
                        tint = NeonPurple,
                    )
                }
                IconButton(onClick = { store.deleteSnippet(snippet.id) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.dbg_js_snippet_delete), tint = NeonRed)
                }
            }
        }
    }
}

@Composable
private fun JsErrorSection(errors: List<io.github.sachintha82288.devbrowser.debug.JsErrorRecord>) {
    if (errors.isEmpty()) {
        DebugEmpty(stringResource(R.string.dbg_js_no_errors))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(errors, key = { it.message }) { error ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DebugBadge(text = "×${error.count}", color = NeonRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = error.message,
                        style = DebugMono,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (error.stack.isNotBlank()) {
                    Text(
                        text = error.stack.lineSequence().take(4).joinToString("\n"),
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DebugMonoSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange, modifier = Modifier.size(44.dp, 28.dp))
    }
}

/** "https://example.com" for an http(s) URL, else null. */
internal fun originOf(url: String?): String? {
    if (url == null) return null
    return try {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        "$scheme://$host"
    } catch (_: Exception) {
        null
    }
}
