package io.github.sachintha82288.devbrowser.ui.debug.console

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.ConsoleLevel
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.DebugJs
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSearchField
import io.github.sachintha82288.devbrowser.ui.debug.components.copyToClipboard
import io.github.sachintha82288.devbrowser.util.FileExport
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import org.json.JSONArray

/**
 * Category 1 — Console & Logging (features 1-10).
 *
 * Everything is driven by [io.github.sachintha82288.devbrowser.debug.DebugSession]
 * so the panel can be opened and closed without losing entries, and clearing only
 * affects the tab that is currently visible.
 */
@Composable
fun ConsolePanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val context = LocalContext.current
    val session = app.debug.session(tab)
    val listState = rememberLazyListState()
    val copiedLabel = stringResource(R.string.dbg_copied)

    var input by remember { mutableStateOf("") }
    var globals by remember { mutableStateOf<List<String>>(emptyList()) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val rows = remember(
        session.console,
        session.visibleLevels,
        session.consoleSearch,
        session.collapsedGroups,
    ) {
        buildConsoleRows(
            entries = session.console,
            levels = session.visibleLevels,
            search = session.consoleSearch,
            collapsedGroups = session.collapsedGroups,
        )
    }

    LaunchedEffect(tab.id) {
        app.debug.drainConsole(tab)
        fetchGlobals(tab) { globals = it }
    }

    // Feature 1: follow new output while the user stays near the bottom.
    LaunchedEffect(rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisible >= rows.lastIndex - 5) listState.animateScrollToItem(rows.lastIndex)
    }

    fun runCommand() {
        val code = input.trim()
        if (code.isEmpty()) return
        session.addCommandHistory(code)
        app.debug.evaluate(tab, code) { output -> session.lastOutput = output }
        input = ""
    }

    fun export() {
        val text = session.console.joinToString("\n") { entry ->
            val time = formatConsoleTime(entry.timestamp)
            val group = if (entry.group.isNotEmpty()) "[${entry.group}] " else ""
            "$time [${entry.level.jsName.uppercase()}] $group${entry.message}" +
                if (entry.stack.isNotBlank()) "\n${entry.stack}" else ""
        }
        val name = "devbrowser-console-${System.currentTimeMillis()}.log"
        val location = FileExport.saveToDownloads(context, name, "text/plain", text)
        status = if (location != null) {
            context.getString(R.string.dbg_console_exported, location)
        } else {
            context.getString(R.string.dbg_console_export_failed)
        }
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_console),
        subtitle = "${session.console.size} ${stringResource(R.string.dbg_console_messages)}",
        onClose = onClose,
        actions = {
            IconButton(onClick = {
                session.clearConsole()
                app.debug.clearConsole(tab)
            }) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.dbg_console_clear), tint = NeonRed)
            }
            IconButton(onClick = { export() }) {
                Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.dbg_console_export), tint = NeonCyan)
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Feature 1: level toggles.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConsoleLevel.entries.forEach { level ->
                    DebugChip(
                        label = level.jsName,
                        selected = session.visibleLevels.contains(level),
                        onClick = {
                            session.visibleLevels = if (session.visibleLevels.contains(level)) {
                                session.visibleLevels - level
                            } else {
                                session.visibleLevels + level
                            }
                        },
                    )
                }
                DebugChip(
                    label = "HH:MM:SS",
                    selected = session.showTimestamps,
                    onClick = { session.showTimestamps = !session.showTimestamps },
                )
            }

            // Feature 5: search box.
            DebugSearchField(
                value = session.consoleSearch,
                onValueChange = { session.consoleSearch = it },
                placeholder = stringResource(R.string.dbg_console_search),
            )

            status?.let {
                Text(
                    text = it,
                    style = DebugMonoSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            if (rows.isEmpty()) {
                DebugEmpty(stringResource(R.string.dbg_console_empty), modifier = Modifier.weight(1f))
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(rows, key = { row ->
                        when (row) {
                            is ConsoleRow.Header -> row.key
                            is ConsoleRow.Entry -> "e-${row.entry.id}"
                        }
                    }) { row ->
                        when (row) {
                            is ConsoleRow.Header -> ConsoleHeaderRow(
                                row = row,
                                collapsed = session.collapsedGroups.contains(row.key),
                                onClick = {
                                    session.collapsedGroups = if (session.collapsedGroups.contains(row.key)) {
                                        session.collapsedGroups - row.key
                                    } else {
                                        session.collapsedGroups + row.key
                                    }
                                },
                            )
                            is ConsoleRow.Entry -> ConsoleEntryRow(
                                entry = row.entry,
                                showTimestamp = session.showTimestamps,
                                search = session.consoleSearch,
                                expanded = expandedId == row.entry.id,
                                onToggle = {
                                    expandedId = if (expandedId == row.entry.id) null else row.entry.id
                                },
                                onCopyFrame = { frame ->
                                    copyToClipboard(context, "frame", frame)
                                    Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }

            // Feature 2/3/4: command input, autocomplete and history.
            val suggestions = remember(input, session.commandHistory, globals) {
                if (input.isBlank()) {
                    emptyList()
                } else {
                    (session.commandHistory.asReversed() + globals)
                        .filter { it.contains(input, ignoreCase = true) && it != input }
                        .distinct()
                        .take(6)
                }
            }
            if (suggestions.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 132.dp).horizontalScroll(rememberScrollState())) {
                    suggestions.forEach { suggestion ->
                        Text(
                            text = suggestion,
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { input = suggestion }
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            session.lastOutput?.let { output ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    DebugBadge(text = stringResource(R.string.dbg_console_result), color = NeonCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = output.display.lineSequence().take(4).joinToString(" ⏎ "),
                        style = DebugMonoSmall,
                        color = if (output.ok) MaterialTheme.colorScheme.onSurface else NeonRed,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { session.navigateHistory(-1)?.let { input = it } },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.dbg_history_prev),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { session.navigateHistory(1)?.let { input = it } },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.dbg_history_next),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = DebugMonoSmall,
                    placeholder = { Text(stringResource(R.string.dbg_console_command_hint), style = DebugMonoSmall) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { runCommand() }),
                )
                IconButton(onClick = { runCommand() }) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.dbg_console_run),
                        tint = NeonCyan,
                    )
                }
            }
        }
    }
}

/** Feature 3 helper: names available on the page's `window` object. */
private fun fetchGlobals(tab: BrowserTab, onResult: (List<String>) -> Unit) {
    val script = DebugJs.json(
        """
var out = [];
function isName(n){
  if (!n || n.length > 40) { return false; }
  for (var j = 0; j < n.length; j++) {
    var c = n.charAt(j);
    var ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c === '_' || (j > 0 && c >= '0' && c <= '9');
    if (!ok) { return false; }
  }
  return true;
}
try {
  var names = Object.getOwnPropertyNames(window);
  for (var i = 0; i < names.length && out.length < 300; i++) {
    if (isName(names[i])) { out.push('window.' + names[i]); }
  }
} catch (e) {}
return JSON.stringify(out);
""",
    )
    DebugBridge.evalJsonArray(tab.session, script) { array: JSONArray? ->
        val values = array?.let { json ->
            (0 until json.length()).mapNotNull { json.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()
        onResult(values)
    }
}
