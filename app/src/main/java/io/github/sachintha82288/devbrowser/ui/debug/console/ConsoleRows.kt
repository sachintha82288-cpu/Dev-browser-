package io.github.sachintha82288.devbrowser.ui.debug.console

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.debug.ConsoleEntry
import io.github.sachintha82288.devbrowser.debug.ConsoleLevel
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.severityColor
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One visual row of the console: either a group header or a log entry. */
sealed interface ConsoleRow {
    data class Header(val key: String, val group: String, val count: Int) : ConsoleRow
    data class Entry(val entry: ConsoleEntry) : ConsoleRow
}

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

fun formatConsoleTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

/**
 * Turns the raw console list into rows, honouring the level filter (1), the
 * search box (5) and collapsed console groups (9).
 */
fun buildConsoleRows(
    entries: List<ConsoleEntry>,
    levels: Set<ConsoleLevel>,
    search: String,
    collapsedGroups: Set<String>,
): List<ConsoleRow> {
    val query = search.trim()
    val filtered = entries.filter { entry ->
        entry.level in levels &&
            (query.isEmpty() || entry.message.contains(query, ignoreCase = true) ||
                entry.group.contains(query, ignoreCase = true))
    }
    val rows = ArrayList<ConsoleRow>(filtered.size)
    var currentGroup: String? = null
    var headerKey: String? = null
    var headerIndex = 0
    val groupCounts = filtered.groupingBy { it.group }.eachCount()
    filtered.forEach { entry ->
        if (entry.group.isEmpty()) {
            currentGroup = null
            headerKey = null
        } else if (entry.group != currentGroup) {
            currentGroup = entry.group
            headerIndex++
            headerKey = "g-$headerIndex-${entry.group}"
            rows.add(ConsoleRow.Header(headerKey, entry.group, groupCounts[entry.group] ?: 1))
        }
        val collapsed = headerKey != null && collapsedGroups.contains(headerKey)
        if (!collapsed) rows.add(ConsoleRow.Entry(entry))
    }
    return rows
}

/** Highlights every case-insensitive occurrence of [query]. */
fun highlight(text: String, query: String, color: androidx.compose.ui.graphics.Color): AnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val index = text.indexOf(needle, start, ignoreCase = true)
            if (index < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, index))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(text.substring(index, index + needle.length))
            }
            start = index + needle.length
        }
    }
}

/** Extracts `file:line:column` frames from a V8 stack string. */
fun stackFrames(stack: String): List<String> = stack.lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("Error") }
    .map { line ->
        val match = Regex("""([^\s()]+:\d+:\d+)""").find(line)
        if (match != null) match.groupValues[1] else line
    }
    .distinct()
    .take(40)
    .toList()

@Composable
fun ConsoleHeaderRow(
    row: ConsoleRow.Header,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (collapsed) "▸" else "▾",
            style = DebugMonoSmall,
            color = NeonPurple,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = row.group,
            style = DebugMonoSmall.copy(fontWeight = FontWeight.Bold),
            color = NeonPurple,
            modifier = Modifier.weight(1f),
        )
        DebugBadge(text = row.count.toString(), color = NeonPurple)
    }
}

@Composable
fun ConsoleEntryRow(
    entry: ConsoleEntry,
    showTimestamp: Boolean,
    search: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopyFrame: (String) -> Unit,
) {
    val accent = severityColor(entry.level.jsName)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 10.dp + (entry.depth * 10).dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (showTimestamp) {
                Text(
                    text = formatConsoleTime(entry.timestamp),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            DebugBadge(text = entry.level.jsName.uppercase(Locale.US), color = accent)
            Spacer(modifier = Modifier.width(6.dp))
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlight(entry.message, search, accent),
                    style = DebugMono,
                    color = if (entry.level == ConsoleLevel.ERROR) accent else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (expanded && entry.stack.isNotBlank()) {
            Column(modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
                Text(
                    text = entry.stack,
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                stackFrames(entry.stack).forEach { frame ->
                    Text(
                        text = "↳ $frame",
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCopyFrame(frame) }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
