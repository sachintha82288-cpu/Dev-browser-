package io.github.sachintha82288.devbrowser.ui.debug.dom

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.sachintha82288.devbrowser.debug.CssDeclaration
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.DebugSession
import io.github.sachintha82288.devbrowser.debug.DomAttribute
import io.github.sachintha82288.devbrowser.debug.DomNode
import io.github.sachintha82288.devbrowser.debug.DomNodeInfo
import io.github.sachintha82288.devbrowser.debug.DomParser
import io.github.sachintha82288.devbrowser.debug.DomScripts
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSearchField
import io.github.sachintha82288.devbrowser.ui.debug.components.copyToClipboard
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Category 3 — DOM inspector (features 23-30).
 *
 * The tree is serialized once per refresh (bounded to [MAX_NODES]) and kept in
 * [DebugSession]; the selected node's detail is fetched on demand so edits are
 * always applied to the live element.
 */
@Composable
fun DomPanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val context = LocalContext.current
    val session = app.debug.session(tab)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.dbg_copied)

    var refreshTick by remember { mutableStateOf(0) }
    var computed by remember { mutableStateOf<List<CssDeclaration>>(emptyList()) }
    var attrEdit by remember { mutableStateOf<DomAttribute?>(null) }
    var textEdit by remember { mutableStateOf<String?>(null) }

    val rows = remember(session.domRoot, session.expandedNodes, session.domSearch) {
        flattenDom(session.domRoot, session.expandedNodes, session.domSearch)
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun loadComputed(path: String) {
        DebugBridge.evalJson(tab.session, DomScripts.computedStyles(path)) { payload ->
            computed = DomParser.parseComputed(payload).first
        }
    }

    LaunchedEffect(tab.id, refreshTick) {
        session.domLoading = true
        DebugBridge.evalJson(tab.session, DomScripts.tree(MAX_NODES)) { payload ->
            val (root, count) = DomParser.parseTree(payload)
            session.domRoot = root
            session.domNodeCount = count
            session.domLoading = false
            if (root != null && session.expandedNodes.isEmpty()) {
                session.expandedNodes = (setOf(root.id) + root.children.map { it.id }).toSet()
            }
        }
    }

    // Feature 24: while the picker is active, poll for the tapped element.
    LaunchedEffect(session.pickerActive) {
        if (!session.pickerActive) {
            DebugBridge.evalRaw(tab.session, DomScripts.picker(false)) {}
            return@LaunchedEffect
        }
        DebugBridge.evalRaw(tab.session, DomScripts.picker(true)) {}
        while (session.pickerActive) {
            delay(350)
            DebugBridge.evalRaw(tab.session, DomScripts.picked) { value ->
                val text = value as? String ?: return@evalRaw
                if (text.isBlank() || text == "null") return@evalRaw
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@evalRaw
                val path = obj.optString("p")
                if (path.isNotEmpty()) {
                    session.pickerActive = false
                    session.expandedNodes = session.expandedNodes + ancestorsOf(session.domRoot, path)
                    describeNode(tab, session, path, highlight = true) { info ->
                        session.selectedNode = info
                        computed = emptyList()
                        val index = rows.indexOfFirst { it.node.path == path }
                        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                    }
                }
            }
        }
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_dom),
        subtitle = "${session.domNodeCount} ${stringResource(R.string.dbg_dom_nodes)}",
        onClose = onClose,
        actions = {
            IconButton(onClick = {
                session.pickerActive = !session.pickerActive
            }) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.dbg_dom_picker),
                    tint = if (session.pickerActive) NeonRed else NeonCyan,
                )
            }
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.dbg_dom_refresh), tint = NeonCyan)
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (session.pickerActive) {
                Text(
                    text = stringResource(R.string.dbg_dom_pick_hint),
                    style = DebugMonoSmall,
                    color = NeonRed,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            DebugSearchField(
                value = session.domSearch,
                onValueChange = { session.domSearch = it },
                placeholder = stringResource(R.string.dbg_dom_search),
                modifier = Modifier,
            )

            if (rows.isEmpty()) {
                DebugEmpty(
                    text = if (session.domLoading) {
                        stringResource(R.string.dbg_dom_loading)
                    } else {
                        stringResource(R.string.dbg_dom_empty)
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(rows, key = { it.node.path }) { row ->
                        DomTreeRow(
                            row = row,
                            selected = session.selectedNode?.path == row.node.path,
                            onSelect = {
                                describeNode(tab, session, row.node.path, highlight = false) { info ->
                                    session.selectedNode = info
                                    computed = emptyList()
                                }
                            },
                            onToggle = {
                                session.expandedNodes = if (session.expandedNodes.contains(row.node.id)) {
                                    session.expandedNodes - row.node.id
                                } else {
                                    session.expandedNodes + row.node.id
                                }
                            },
                        )
                    }
                }
            }

            session.selectedNode?.let { info ->
                DomDetailCard(
                    info = info,
                    computed = computed,
                    onCopy = { label, text ->
                        copyToClipboard(context, label, text)
                        toast(copied)
                    },
                    onHighlight = {
                        session.highlightActive = !session.highlightActive
                        if (session.highlightActive) {
                            DebugBridge.evalJson(tab.session, DomScripts.highlight(info.path)) {}
                        } else {
                            DebugBridge.evalJson(tab.session, DomScripts.clearHighlight) {}
                        }
                    },
                    highlightActive = session.highlightActive,
                    onEditAttribute = { attrEdit = it },
                    onEditText = { textEdit = info.text },
                    onLoadComputed = { loadComputed(info.path) },
                )
            }
        }
    }

    attrEdit?.let { attribute ->
        EditValueDialog(
            title = attribute.name,
            initial = attribute.value,
            singleLine = true,
            onDismiss = { attrEdit = null },
            onConfirm = { value ->
                val path = session.selectedNode?.path
                if (path != null) {
                    DebugBridge.evalJson(tab.session, DomScripts.setAttribute(path, attribute.name, value)) {
                        describeNode(tab, session, path) { session.selectedNode = it }
                    }
                }
                attrEdit = null
            },
        )
    }

    textEdit?.let { content ->
        EditValueDialog(
            title = stringResource(R.string.dbg_dom_text_content),
            initial = content,
            singleLine = false,
            onDismiss = { textEdit = null },
            onConfirm = { value ->
                val path = session.selectedNode?.path
                if (path != null) {
                    DebugBridge.evalJson(tab.session, DomScripts.setText(path, value)) {
                        describeNode(tab, session, path) { session.selectedNode = it }
                    }
                }
                textEdit = null
            },
        )
    }
}

@Composable
private fun DomDetailCard(
    info: DomNodeInfo,
    computed: List<CssDeclaration>,
    onCopy: (String, String) -> Unit,
    onHighlight: () -> Unit,
    highlightActive: Boolean,
    onEditAttribute: (DomAttribute) -> Unit,
    onEditText: () -> Unit,
    onLoadComputed: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 268.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DebugBadge(text = "<${info.tag}>", color = NeonCyan)
                DebugBadge(text = "${info.rect.width}×${info.rect.height}", color = NeonPurple)
                DebugChip(
                    label = stringResource(R.string.dbg_dom_highlight),
                    selected = highlightActive,
                    onClick = onHighlight,
                    accent = NeonRed,
                )
                DebugChip(
                    label = stringResource(R.string.dbg_dom_copy_selector),
                    selected = false,
                    onClick = { onCopy("selector", info.selector) },
                )
                DebugChip(
                    label = stringResource(R.string.dbg_dom_copy_html),
                    selected = false,
                    onClick = { onCopy("html", info.html) },
                )
                DebugChip(
                    label = stringResource(R.string.dbg_dom_computed),
                    selected = computed.isNotEmpty(),
                    onClick = onLoadComputed,
                    accent = NeonPurple,
                )
            }

            Spacer(modifier = Modifier.padding(2.dp))

            Text(
                text = info.selector,
                style = DebugMonoSmall,
                color = NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopy("selector", info.selector) }
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dbg_dom_attributes),
                    style = DebugMonoSmall,
                    color = NeonPurple,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEditText) {
                    Text(stringResource(R.string.dbg_dom_edit_text), style = DebugMonoSmall, color = NeonCyan)
                }
            }

            info.attributes.take(40).forEach { attribute ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditAttribute(attribute) }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = attribute.name,
                        style = DebugMonoSmall,
                        color = NeonPurple,
                        modifier = Modifier.width(110.dp),
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = attribute.value,
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                    )
                }
            }

            if (computed.isNotEmpty()) {
                Text(
                    text = "${stringResource(R.string.dbg_dom_computed)} (${computed.size})",
                    style = DebugMono,
                    color = NeonRed,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                )
                computed.take(240).forEach { declaration ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp)) {
                        Text(
                            text = declaration.name,
                            style = DebugMonoSmall,
                            color = NeonPurple,
                            modifier = Modifier.width(150.dp),
                        )
                        Text(
                            text = declaration.value,
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Shared edit dialog for attribute values and text content. */
@Composable
private fun EditValueDialog(
    title: String,
    initial: String,
    singleLine: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = DebugMono) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = singleLine,
                    textStyle = DebugMono,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(value) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.dbg_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dbg_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** Fetches the selected element's full description and optional highlight. */
internal fun describeNode(
    tab: BrowserTab,
    session: DebugSession,
    path: String,
    highlight: Boolean = false,
    onDone: (DomNodeInfo?) -> Unit,
) {
    DebugBridge.evalJson(tab.session, DomScripts.describe(path)) { payload ->
        val info = DomParser.parseNodeInfo(payload)
        if (highlight && info != null) {
            DebugBridge.evalJson(tab.session, DomScripts.highlight(path)) {}
            session.highlightActive = true
        }
        onDone(info)
    }
}

private const val MAX_NODES = 2500

/** Finds a node id for a path (used when expanding to a picked node). */
internal fun nodeIdForPath(root: DomNode?, path: String): Int? {
    if (root == null) return null
    if (root.path == path) return root.id
    root.children.forEach { child ->
        nodeIdForPath(child, path)?.let { return it }
    }
    return null
}
