package io.github.sachintha82288.devbrowser.ui.debug.css

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.sachintha82288.devbrowser.debug.BoxModel
import io.github.sachintha82288.devbrowser.debug.CssDeclaration
import io.github.sachintha82288.devbrowser.debug.CssRule
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.DomParser
import io.github.sachintha82288.devbrowser.debug.DomScripts
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.ui.debug.components.copyToClipboard
import io.github.sachintha82288.devbrowser.ui.debug.dom.describeNode
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Category 4 — CSS panel (features 31-36).
 *
 * Rules come from readable stylesheets only; cross-origin stylesheets cannot be
 * inspected through the CSSOM, which is a hard engine limitation. Live edits are
 * applied as `!important` inline declarations so they always win.
 */
@Composable
fun CssPanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val context = LocalContext.current
    val session = app.debug.session(tab)
    val copied = stringResource(R.string.dbg_copied)

    var refreshTick by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<CssDeclaration?>(null) }
    var picked by remember { mutableStateOf<Boolean>(false) }

    val info = session.selectedNode

    LaunchedEffect(tab.id, info?.path, refreshTick) {
        val path = info?.path ?: return@LaunchedEffect
        DebugBridge.evalJson(tab.session, DomScripts.matchingRules(path)) { payload ->
            session.cssRules = DomParser.parseRules(payload)
        }
        DebugBridge.evalJson(tab.session, DomScripts.computedStyles(path)) { payload ->
            val (props, box) = DomParser.parseComputed(payload)
            session.computedStyles = props
            session.boxModel = box
        }
    }

    // Element picker for users who open the CSS panel first.
    LaunchedEffect(picked) {
        if (!picked) {
            DebugBridge.evalRaw(tab.session, DomScripts.picker(false)) {}
            return@LaunchedEffect
        }
        DebugBridge.evalRaw(tab.session, DomScripts.picker(true)) {}
        while (picked) {
            delay(350)
            DebugBridge.evalRaw(tab.session, DomScripts.picked) { value ->
                val text = value as? String ?: return@evalRaw
                if (text.isBlank() || text == "null") return@evalRaw
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@evalRaw
                val path = obj.optString("p")
                if (path.isNotEmpty()) {
                    picked = false
                    describeNode(tab, session, path, highlight = true) { node ->
                        session.selectedNode = node
                        refreshTick++
                    }
                }
            }
        }
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_css),
        subtitle = info?.selector,
        onClose = onClose,
        actions = {
            IconButton(onClick = { picked = !picked }) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.dbg_css_pick),
                    tint = if (picked) NeonRed else NeonCyan,
                )
            }
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.dbg_css_refresh), tint = NeonCyan)
            }
        },
    ) {
        if (info == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                DebugEmpty(stringResource(R.string.dbg_css_no_selection))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    DebugChip(
                        label = stringResource(R.string.dbg_css_pick),
                        selected = picked,
                        onClick = { picked = !picked },
                        accent = NeonRed,
                    )
                }
            }
            return@DebugPanelScaffold
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DebugBadge(text = "<${info.tag}>", color = NeonCyan)
                DebugChip(
                    label = stringResource(R.string.dbg_dom_copy_selector),
                    selected = false,
                    onClick = {
                        copyToClipboard(context, "selector", info.selector)
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                )
                listOf("hover", "active", "focus", "visited").forEach { state ->
                    DebugChip(
                        label = ":$state",
                        selected = session.forcedStates.contains(state),
                        accent = NeonPurple,
                        onClick = {
                            val enable = !session.forcedStates.contains(state)
                            session.forcedStates = if (enable) {
                                session.forcedStates + state
                            } else {
                                session.forcedStates - state
                            }
                            DebugBridge.evalJson(
                                tab.session,
                                DomScripts.forceState(info.path, state, enable),
                            ) { refreshTick++ }
                        },
                    )
                }
            }

            session.boxModel?.let { box ->
                DebugSection(stringResource(R.string.dbg_css_box_model))
                BoxModelView(box)
            }

            DebugSection("${stringResource(R.string.dbg_css_rules)} (${session.cssRules.size})")

            if (session.cssRules.isEmpty()) {
                Text(
                    text = stringResource(R.string.dbg_css_no_rules),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(session.cssRules) { rule ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rule.selector,
                                style = DebugMono,
                                color = NeonCyan,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = rule.source,
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        rule.declarations.forEach { declaration ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editing = declaration }
                                    .padding(start = 10.dp, top = 1.dp, bottom = 1.dp),
                            ) {
                                Text(
                                    text = declaration.name,
                                    style = DebugMonoSmall,
                                    color = NeonPurple,
                                    modifier = Modifier.width(148.dp),
                                )
                                Text(
                                    text = declaration.value,
                                    style = DebugMonoSmall,
                                    color = SuccessGreen,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { declaration ->
        CssEditDialog(
            declaration = declaration,
            onDismiss = { editing = null },
            onConfirm = { value ->
                val path = session.selectedNode?.path
                if (path != null && value != declaration.value) {
                    DebugBridge.evalJson(
                        tab.session,
                        DomScripts.applyProperty(path, declaration.name, value),
                    ) { refreshTick++ }
                }
                editing = null
            },
        )
    }
}

/** Feature 33: colour-coded margin / border / padding / content diagram. */
@Composable
private fun BoxModelView(box: BoxModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(WarningAmber.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                .padding(4.dp),
        ) {
            Column {
                EdgeCaption("margin", box.margin, WarningAmber)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NeonPurple.copy(alpha = 0.22f), RoundedCornerShape(5.dp))
                        .padding(4.dp),
                ) {
                    Column {
                        EdgeCaption("border", box.border, NeonPurple)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NeonCyan.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                .padding(4.dp),
                        ) {
                            Column {
                                EdgeCaption("padding", box.padding, NeonCyan)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp))
                                        .padding(8.dp),
                                ) {
                                    Text(
                                        text = "${box.contentWidth} × ${box.contentHeight}",
                                        style = DebugMono,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.padding(2.dp))
        Text(
            text = stringResource(
                R.string.dbg_css_edges,
                box.margin.top, box.margin.right, box.margin.bottom, box.margin.left,
                box.padding.top, box.padding.right, box.padding.bottom, box.padding.left,
            ),
            style = DebugMonoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EdgeCaption(label: String, edges: io.github.sachintha82288.devbrowser.debug.Edges, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = "$label  ${edges.top} ${edges.right} ${edges.bottom} ${edges.left}",
        style = DebugMonoSmall,
        color = color,
    )
}

@Composable
private fun CssEditDialog(
    declaration: CssDeclaration,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(declaration.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(declaration.name, style = DebugMono) },
        text = {
            Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = DebugMono,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
