package io.github.sachintha82288.devbrowser.ui.debug.sources

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.BrokenLink
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.ExternalAsset
import io.github.sachintha82288.devbrowser.debug.FontUsage
import io.github.sachintha82288.devbrowser.debug.HtmlPretty
import io.github.sachintha82288.devbrowser.debug.ImageInfo
import io.github.sachintha82288.devbrowser.debug.InlineScript
import io.github.sachintha82288.devbrowser.debug.SourceParser
import io.github.sachintha82288.devbrowser.debug.SourceScripts
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSearchField
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SourceTab { SOURCE, ASSETS, INLINE, LINKS, IMAGES, FONTS }

/**
 * Category 10 — sources & resources (features 83-90).
 *
 * The HTML shown is the live DOM serialization, pretty-printed with a bounded
 * line count; searching scrolls the list instead of re-rendering it.
 */
@Composable
fun SourcesPanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    var section by remember { mutableStateOf(SourceTab.SOURCE) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var assets by remember { mutableStateOf<List<ExternalAsset>>(emptyList()) }
    var inlineScripts by remember { mutableStateOf<List<InlineScript>>(emptyList()) }
    var openScript by remember { mutableStateOf<String?>(null) }
    var links by remember { mutableStateOf<List<BrokenLink>>(emptyList()) }
    var checkingLinks by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<ImageInfo>>(emptyList()) }
    var fonts by remember { mutableStateOf<List<FontUsage>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }
    var refreshTick by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val matches = remember(lines, query) { HtmlPretty.matches(lines, query) }

    LaunchedEffect(tab.id, refreshTick) {
        val source = DebugBridge.awaitJson(tab.session, SourceScripts.pageSource)
        lines = HtmlPretty.format(source?.optString("html").orEmpty())
        assets = SourceParser.parseAssets(DebugBridge.awaitJson(tab.session, SourceScripts.assets))
        inlineScripts = SourceParser.parseInlineScripts(
            DebugBridge.awaitJson(tab.session, SourceScripts.inlineScripts),
        )
        images = SourceParser.parseImages(DebugBridge.awaitJson(tab.session, SourceScripts.images))
        fonts = SourceParser.parseFonts(DebugBridge.awaitJson(tab.session, SourceScripts.fonts))
    }

    LaunchedEffect(matchIndex, matches) {
        if (matches.isNotEmpty()) {
            listState.animateScrollToItem(matches[matchIndex.coerceIn(0, matches.lastIndex)])
        }
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_sources),
        subtitle = tab.url,
        onClose = onClose,
        actions = {
            TextButton(onClick = { refreshTick++ }) {
                Text(stringResource(R.string.dbg_src_refresh), style = DebugMonoSmall, color = NeonCyan)
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
                DebugChip(stringResource(R.string.dbg_src_source), section == SourceTab.SOURCE) { section = SourceTab.SOURCE }
                DebugChip(stringResource(R.string.dbg_src_assets), section == SourceTab.ASSETS) { section = SourceTab.ASSETS }
                DebugChip(stringResource(R.string.dbg_src_inline), section == SourceTab.INLINE) { section = SourceTab.INLINE }
                DebugChip(stringResource(R.string.dbg_src_links), section == SourceTab.LINKS) { section = SourceTab.LINKS }
                DebugChip(stringResource(R.string.dbg_src_images), section == SourceTab.IMAGES) { section = SourceTab.IMAGES }
                DebugChip(stringResource(R.string.dbg_src_fonts), section == SourceTab.FONTS) { section = SourceTab.FONTS }
            }

            when (section) {
                SourceTab.SOURCE -> {
                    DebugSearchField(
                        value = query,
                        onValueChange = {
                            query = it
                            matchIndex = 0
                        },
                        placeholder = stringResource(R.string.dbg_src_search),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (query.isBlank()) {
                                "${lines.size} ${stringResource(R.string.dbg_src_lines)}"
                            } else {
                                "${matches.size} ${stringResource(R.string.dbg_src_matches)}"
                            },
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            if (matches.isNotEmpty()) matchIndex = (matchIndex - 1 + matches.size) % matches.size
                        }) { Text("▲", style = DebugMonoSmall, color = NeonCyan) }
                        TextButton(onClick = {
                            if (matches.isNotEmpty()) matchIndex = (matchIndex + 1) % matches.size
                        }) { Text("▼", style = DebugMonoSmall, color = NeonCyan) }
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(lines.size) { index ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${index + 1}",
                                    style = DebugMonoSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(42.dp).padding(start = 4.dp),
                                )
                                Text(
                                    text = highlightHtml(lines[index]),
                                    style = DebugMonoSmall,
                                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                                )
                            }
                        }
                    }
                }
                SourceTab.ASSETS -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(assets) { asset ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DebugBadge(text = asset.kind, color = if (asset.kind == "js") NeonCyan else NeonPurple)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = asset.url.substringAfterLast('/').take(40),
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${asset.transferSize / 1024} KB · ${asset.durationMs} ms",
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                SourceTab.INLINE -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(inlineScripts) { script ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DebugBadge(text = "#${script.index}", color = NeonPurple)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${script.bytes} B",
                                    style = DebugMonoSmall,
                                    color = NeonCyan,
                                    modifier = Modifier.width(64.dp),
                                )
                                Text(
                                    text = script.preview,
                                    style = DebugMonoSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val payload = DebugBridge.awaitJson(
                                        tab.session,
                                        SourceScripts.inlineScript(script.index),
                                    )
                                    openScript = payload?.optString("code").orEmpty()
                                }
                            }) { Text(stringResource(R.string.dbg_src_open), style = DebugMonoSmall, color = NeonCyan) }
                        }
                    }
                }
                SourceTab.LINKS -> Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.dbg_src_links_hint),
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(enabled = !checkingLinks, onClick = {
                            checkingLinks = true
                            scope.launch {
                                DebugBridge.evalRaw(tab.session, SourceScripts.brokenLinksStart) {}
                                repeat(20) {
                                    delay(500)
                                    val payload = DebugBridge.awaitJson(tab.session, SourceScripts.asyncLinks)
                                    if (payload != null) {
                                        links = SourceParser.parseBrokenLinks(payload)
                                        checkingLinks = false
                                        return@launch
                                    }
                                }
                                checkingLinks = false
                            }
                        }) {
                            Text(
                                text = if (checkingLinks) {
                                    stringResource(R.string.dbg_src_checking)
                                } else {
                                    stringResource(R.string.dbg_src_check)
                                },
                                style = DebugMonoSmall,
                                color = NeonCyan,
                            )
                        }
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(links) { link ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DebugBadge(
                                    text = link.status,
                                    color = when {
                                        link.status == "failed" -> NeonRed
                                        link.status.startsWith("4") || link.status.startsWith("5") -> WarningAmber
                                        else -> SuccessGreen
                                    },
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = link.url,
                                    style = DebugMonoSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
                SourceTab.IMAGES -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(images) { image ->
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DebugBadge(text = image.format, color = NeonPurple)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = image.url.substringAfterLast('/').take(44),
                                    style = DebugMonoSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (image.oversized) {
                                    DebugBadge(text = "2x", color = WarningAmber)
                                }
                            }
                            Text(
                                text = "natural ${image.naturalWidth}x${image.naturalHeight} · displayed ${image.displayedWidth}x${image.displayedHeight}",
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                SourceTab.FONTS -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(fonts) { font ->
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(
                                text = font.family,
                                style = DebugMonoSmall,
                                color = NeonCyan,
                                maxLines = 2,
                            )
                            Text(
                                text = "${font.source} · ${font.usedOn} element(s)",
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    openScript?.let { code ->
        AlertDialog(
            onDismissRequest = { openScript = null },
            title = { Text(stringResource(R.string.dbg_src_inline), style = DebugMono) },
            text = {
                Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(text = code.take(20000), style = DebugMono, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = {
                TextButton(onClick = { openScript = null }) { Text(stringResource(R.string.dbg_close)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** Lightweight syntax colouring for a single HTML line. */
private fun highlightHtml(line: String): AnnotatedString = buildAnnotatedString {
    val token = Regex("""(<!--.*?-->|</?[\w:-]+|/?>|"[^"]*"|'[^']*'|[\w:-]+=)""")
    var last = 0
    token.findAll(line).forEach { match ->
        if (match.range.first > last) {
            append(line.substring(last, match.range.first))
        }
        val text = match.value
        val color: Color = when {
            text.startsWith("<!--") -> Color(0xFF63637A)
            text.startsWith("<") || text == ">" || text == "/>" -> NeonCyan
            text.startsWith("\"") || text.startsWith("'") -> WarningAmber
            else -> NeonPurple
        }
        withStyle(SpanStyle(color = color)) { append(text) }
        last = match.range.last + 1
    }
    if (last < line.length) append(line.substring(last))
}
