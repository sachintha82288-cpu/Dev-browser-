package io.github.sachintha82288.devbrowser.ui.debug.network

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.NetworkEntry
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugKeyValue
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.ui.debug.components.copyToClipboard
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private enum class DetailTab { HEADERS, COOKIES, TIMING, PREVIEW, BODY }

/** Features 14-22: full detail view of one captured request. */
@Composable
fun NetworkDetail(
    app: DevBrowserApplication,
    tab: BrowserTab,
    entry: NetworkEntry,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(DetailTab.HEADERS) }
    var note by remember { mutableStateOf<String?>(null) }
    val copied = stringResource(R.string.dbg_copied)

    fun copy(label: String, text: String) {
        copyToClipboard(context, label, text)
        note = copied
        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }

    DebugPanelScaffold(
        title = "${entry.method} ${if (entry.status > 0) entry.status else "ERR"}",
        subtitle = entry.url,
        onClose = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DebugChip(stringResource(R.string.dbg_net_headers), selected == DetailTab.HEADERS) { selected = DetailTab.HEADERS }
                DebugChip(stringResource(R.string.dbg_net_cookies), selected == DetailTab.COOKIES) { selected = DetailTab.COOKIES }
                DebugChip(stringResource(R.string.dbg_net_timing), selected == DetailTab.TIMING) { selected = DetailTab.TIMING }
                DebugChip(stringResource(R.string.dbg_net_preview), selected == DetailTab.PREVIEW) { selected = DetailTab.PREVIEW }
                DebugChip(stringResource(R.string.dbg_net_body), selected == DetailTab.BODY) { selected = DetailTab.BODY }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DebugChip(stringResource(R.string.dbg_net_curl), selected = false, accent = NeonCyan) {
                    copy("curl", entry.asCurl())
                }
                DebugChip(stringResource(R.string.dbg_net_fetch), selected = false, accent = NeonCyan) {
                    copy("fetch", entry.asFetch())
                }
                if (entry.isJson) {
                    DebugChip(stringResource(R.string.dbg_net_json), selected = false, accent = SuccessGreen) {
                        entry.prettyJson()?.let { copy("json", it) }
                    }
                }
                DebugChip(stringResource(R.string.dbg_net_block), selected = false, accent = NeonRed) {
                    app.debug.blockRequest(tab, entry.url)
                    note = context.getString(R.string.dbg_net_blocked)
                }
                DebugChip(stringResource(R.string.dbg_net_block_origin), selected = false, accent = NeonRed) {
                    app.debug.blockRequest(tab, originOfUrl(entry.url))
                    note = context.getString(R.string.dbg_net_blocked)
                }
            }

            note?.let {
                Text(
                    text = it,
                    style = DebugMonoSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (selected) {
                    DetailTab.HEADERS -> HeadersTab(entry, onCopy = { text -> copy("headers", text) })
                    DetailTab.COOKIES -> CookiesTab(entry)
                    DetailTab.TIMING -> TimingTab(entry)
                    DetailTab.PREVIEW -> PreviewTab(entry)
                    DetailTab.BODY -> BodyTab(entry)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HeadersTab(entry: NetworkEntry, onCopy: (String) -> Unit) {
    DebugSection(stringResource(R.string.dbg_net_general))
    DebugKeyValue(stringResource(R.string.dbg_net_url), entry.url)
    DebugKeyValue(stringResource(R.string.dbg_net_method), entry.method)
    DebugKeyValue(stringResource(R.string.dbg_net_status), if (entry.status > 0) entry.status.toString() else "ERR")
    DebugKeyValue(stringResource(R.string.dbg_net_type), entry.type.js)
    DebugKeyValue(stringResource(R.string.dbg_net_size), formatBytes(entry.size))
    DebugKeyValue(stringResource(R.string.dbg_net_time), "${entry.durationMs} ms")
    if (entry.mimeType.isNotBlank()) DebugKeyValue("Content-Type", entry.mimeType)

    DebugSection(stringResource(R.string.dbg_net_request_headers))
    if (entry.requestHeaders.isEmpty()) {
        DebugEmpty(stringResource(R.string.dbg_net_no_headers))
    } else {
        entry.requestHeaders.forEach { header -> DebugKeyValue(header.name, header.value) }
        Text(
            text = stringResource(R.string.dbg_net_copy_all),
            style = DebugMonoSmall,
            color = NeonCyan,
            modifier = Modifier
                .clickable {
                    onCopy(entry.requestHeaders.joinToString("\n") { "${it.name}: ${it.value}" })
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }

    DebugSection(stringResource(R.string.dbg_net_response_headers))
    if (entry.responseHeaders.isEmpty()) {
        DebugEmpty(stringResource(R.string.dbg_net_no_headers))
    } else {
        entry.responseHeaders.forEach { header -> DebugKeyValue(header.name, header.value) }
        Text(
            text = stringResource(R.string.dbg_net_copy_all),
            style = DebugMonoSmall,
            color = NeonCyan,
            modifier = Modifier
                .clickable {
                    onCopy(entry.responseHeaders.joinToString("\n") { "${it.name}: ${it.value}" })
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (entry.blocked) {
        Text(
            text = stringResource(R.string.dbg_net_blocked_hint),
            style = DebugMonoSmall,
            color = NeonRed,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun CookiesTab(entry: NetworkEntry) {
    DebugSection(stringResource(R.string.dbg_net_sent_cookies))
    if (entry.requestCookies.isEmpty()) {
        DebugEmpty(stringResource(R.string.dbg_net_no_cookies))
    } else {
        entry.requestCookies.forEach { cookie ->
            val name = cookie.substringBefore('=')
            val value = cookie.substringAfter('=', "")
            DebugKeyValue(name, value)
        }
    }
    DebugSection(stringResource(R.string.dbg_net_set_cookies))
    if (entry.setCookies.isEmpty()) {
        DebugEmpty(stringResource(R.string.dbg_net_no_cookies))
    } else {
        entry.setCookies.forEach { cookie ->
            DebugKeyValue(cookie.substringBefore('='), cookie.substringAfter('=', ""))
            val expiry = Regex("""(?i)expires=([^;]+)""").find(cookie)?.groupValues?.get(1)
            if (expiry != null) DebugKeyValue("expires", expiry)
        }
        Text(
            text = stringResource(R.string.dbg_net_cookie_note),
            style = DebugMonoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun TimingTab(entry: NetworkEntry) {
    DebugSection(stringResource(R.string.dbg_net_timing))
    val timing = entry.timing
    if (timing.isEmpty) {
        DebugEmpty(stringResource(R.string.dbg_net_no_timing))
        return
    }
    val total = timing.total.coerceAtLeast(1)
    val segments = listOf(
        Triple(stringResource(R.string.dbg_net_dns), timing.dns, NeonPurple),
        Triple(stringResource(R.string.dbg_net_connect), timing.connect, NeonCyan),
        Triple(stringResource(R.string.dbg_net_tls), timing.tls, WarningAmber),
        Triple(stringResource(R.string.dbg_net_ttfb), timing.ttfb, SuccessGreen),
        Triple(stringResource(R.string.dbg_net_download), timing.download, NeonRed),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(4.dp)),
    ) {
        segments.forEach { (_, value, color) ->
            val weight = (value.toFloat() / total).coerceAtLeast(0.0001f)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxSize()
                    .padding(end = if (value > 0) 1.dp else 0.dp)
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
    }
    segments.forEach { (label, value, color) ->
        DebugKeyValue(label, "$value ms", valueColor = color)
    }
    DebugKeyValue(stringResource(R.string.dbg_net_total), "${timing.total} ms")
}

@Composable
private fun PreviewTab(entry: NetworkEntry) {
    val pretty = entry.prettyJson()
    when {
        pretty != null -> {
            DebugSection("JSON")
            Text(
                text = pretty.take(6000),
                style = DebugMono,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        entry.type == io.github.sachintha82288.devbrowser.debug.RequestType.IMG -> {
            DebugSection(stringResource(R.string.dbg_net_image))
            RemoteImage(entry.url)
        }
        entry.body.isNotBlank() -> {
            DebugSection(stringResource(R.string.dbg_net_preview))
            Text(
                text = entry.body.take(6000),
                style = DebugMono,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        else -> DebugEmpty(stringResource(R.string.dbg_net_no_preview))
    }
}

@Composable
private fun BodyTab(entry: NetworkEntry) {
    DebugSection(stringResource(R.string.dbg_net_body))
    if (entry.body.isBlank()) {
        DebugEmpty(stringResource(R.string.dbg_net_no_body))
        return
    }
    if (entry.bodyTruncated) {
        Text(
            text = stringResource(R.string.dbg_net_truncated),
            style = DebugMonoSmall,
            color = WarningAmber,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
    Text(
        text = entry.body,
        style = DebugMono,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 900.dp)
            .padding(horizontal = 12.dp),
    )
}

/** Feature 17: thumbnail of an image response, loaded directly from its URL. */
@Composable
private fun RemoteImage(url: String) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
        }
        bitmap = loaded
        failed = loaded == null
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .padding(12.dp),
        )
        Text(
            text = "${image.width} × ${image.height}",
            style = DebugMonoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    } else if (failed) {
        DebugEmpty(stringResource(R.string.dbg_net_image_failed))
    }
}

internal fun originOfUrl(url: String): String {
    val parts = url.split("/")
    return if (parts.size >= 3) "${parts[0]}//${parts[2]}" else url
}
