package io.github.sachintha82288.devbrowser.ui.debug.dom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.debug.DomNode
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber

data class DomRow(
    val node: DomNode,
    val depth: Int,
    val expanded: Boolean,
    val hasChildren: Boolean,
)

/** Depth-first flattening of the tree, honouring the expansion set. */
fun flattenDom(root: DomNode?, expanded: Set<Int>, query: String): List<DomRow> {
    if (root == null) return emptyList()
    val search = query.trim()
    if (search.isNotEmpty()) return searchDom(root, search)

    val rows = ArrayList<DomRow>()
    fun walk(node: DomNode, depth: Int) {
        val hasChildren = node.children.isNotEmpty()
        val isExpanded = expanded.contains(node.id)
        rows.add(DomRow(node, depth, isExpanded, hasChildren))
        if (hasChildren && isExpanded) {
            node.children.forEach { walk(it, depth + 1) }
        }
    }
    walk(root, 0)
    return rows
}

private fun searchDom(root: DomNode, query: String): List<DomRow> {
    val rows = ArrayList<DomRow>()
    fun walk(node: DomNode, depth: Int) {
        val hit = node.tag.contains(query, ignoreCase = true) ||
            node.htmlId.contains(query, ignoreCase = true) ||
            node.classes.contains(query, ignoreCase = true)
        if (hit) rows.add(DomRow(node, depth / 2, false, node.children.isNotEmpty()))
        node.children.forEach { walk(it, depth + 1) }
    }
    walk(root, 0)
    return rows
}

/** Ids of every node that is an ancestor of [path] (for auto-expanding). */
fun ancestorsOf(root: DomNode?, path: String): Set<Int> {
    if (root == null || path.isEmpty()) return emptySet()
    val out = HashSet<Int>()
    fun walk(node: DomNode) {
        if (path == node.path || path.startsWith(node.path + "/")) out.add(node.id)
        node.children.forEach { walk(it) }
    }
    walk(root)
    return out
}

/** All ids below [node], used by "expand all". */
fun descendantsOf(node: DomNode): Set<Int> =
    node.children.flatMap { child -> setOf(child.id) + descendantsOf(child) }.toSet()

@Composable
fun DomTreeRow(
    row: DomRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) NeonRed.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(start = (6 + row.depth * 14).dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                !row.hasChildren -> "·"
                row.expanded -> "▾"
                else -> "▸"
            },
            style = DebugMonoSmall,
            color = NeonPurple,
            modifier = Modifier
                .width(16.dp)
                .clickable(enabled = row.hasChildren, onClick = onToggle),
        )
        Text(text = nodeLabel(row.node), style = DebugMonoSmall, maxLines = 2)
        Spacer(modifier = Modifier.width(4.dp))
    }
}

/** `<tag#id.class> text` rendered with DevBrowser colours. */
private fun nodeLabel(node: DomNode) = buildAnnotatedString {
    append("<")
    withStyle(SpanStyle(color = NeonCyan)) { append(node.tag) }
    if (node.htmlId.isNotBlank()) {
        withStyle(SpanStyle(color = NeonRed)) { append("#${node.htmlId}") }
    }
    if (node.classes.isNotBlank()) {
        val first = node.classes.trim().split(Regex("\\s+")).take(2).joinToString(".")
        withStyle(SpanStyle(color = NeonPurple)) { append(".$first") }
    }
    append(">")
    if (node.childCount > 0 && node.children.size < node.childCount) {
        withStyle(SpanStyle(color = WarningAmber)) { append(" +${node.childCount - node.children.size}") }
    }
    if (node.text.isNotBlank()) {
        withStyle(SpanStyle(color = Color(0xFF9E9EB3))) { append(" ${node.text.trim().take(48)}") }
    }
}
