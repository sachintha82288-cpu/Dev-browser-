package io.github.sachintha82288.devbrowser.ui.debug.storage

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.CacheGroup
import io.github.sachintha82288.devbrowser.debug.CookieItem
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.IdbDatabase
import io.github.sachintha82288.devbrowser.debug.ServiceWorkerInfo
import io.github.sachintha82288.devbrowser.debug.StorageItem
import io.github.sachintha82288.devbrowser.debug.StorageParser
import io.github.sachintha82288.devbrowser.debug.StorageScripts
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugChip
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.util.FileExport
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private enum class StorageSection { COOKIES, LOCAL, SESSION, IDB, CACHE, WORKERS }

/**
 * Category 7 — storage & cookies (features 55-64).
 *
 * Everything is read through the page so it always reflects the live origin;
 * httpOnly cookies are invisible to page scripts (engine limitation, noted in
 * the cookies section).
 */
@Composable
fun StoragePanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    val context = LocalContext.current
    var section by remember { mutableStateOf(StorageSection.COOKIES) }
    var cookies by remember { mutableStateOf<List<CookieItem>>(emptyList()) }
    var localItems by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var sessionItems by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var databases by remember { mutableStateOf<List<IdbDatabase>>(emptyList()) }
    var caches by remember { mutableStateOf<List<CacheGroup>>(emptyList()) }
    var workers by remember { mutableStateOf<List<ServiceWorkerInfo>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var openDatabase by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tab.id, refreshTick) {
        cookies = StorageParser.parseCookies(DebugBridge.awaitJson(tab.session, StorageScripts.cookies))
        localItems = StorageParser.parseItems(
            DebugBridge.awaitJson(tab.session, StorageScripts.localDump)?.optJSONArray("items"),
        )
        sessionItems = StorageParser.parseItems(
            DebugBridge.awaitJson(tab.session, StorageScripts.sessionDump)?.optJSONArray("items"),
        )
        databases = StorageParser.parseIdb(loadAsync(tab, StorageScripts.indexedDbStart))
        caches = StorageParser.parseCaches(loadAsync(tab, StorageScripts.cacheStart))
        workers = StorageParser.parseWorkers(loadAsync(tab, StorageScripts.workerStart))
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_storage),
        subtitle = tab.url,
        onClose = onClose,
        actions = {
            IconButton(onClick = {
                if (section == StorageSection.COOKIES) {
                    editTarget = EditTarget.Cookie(null)
                } else if (section == StorageSection.LOCAL || section == StorageSection.SESSION) {
                    editTarget = EditTarget.Storage(section, null)
                }
            }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.dbg_storage_add), tint = NeonCyan)
            }
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.dbg_storage_refresh), tint = NeonCyan)
            }
            IconButton(onClick = {
                val snapshot = buildSnapshot(cookies, localItems, sessionItems, databases, caches, workers, tab.url)
                val name = "devbrowser-storage-${System.currentTimeMillis()}.json"
                val where = FileExport.saveToDownloads(context, name, "application/json", snapshot)
                note = if (where != null) {
                    context.getString(R.string.dbg_storage_exported, where)
                } else {
                    context.getString(R.string.dbg_failed)
                }
            }) {
                Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.dbg_storage_export), tint = NeonCyan)
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
                DebugChip(stringResource(R.string.dbg_storage_cookies), section == StorageSection.COOKIES) { section = StorageSection.COOKIES }
                DebugChip(stringResource(R.string.dbg_storage_local), section == StorageSection.LOCAL) { section = StorageSection.LOCAL }
                DebugChip(stringResource(R.string.dbg_storage_session), section == StorageSection.SESSION) { section = StorageSection.SESSION }
                DebugChip("IndexedDB", section == StorageSection.IDB) { section = StorageSection.IDB }
                DebugChip("CacheStorage", section == StorageSection.CACHE) { section = StorageSection.CACHE }
                DebugChip(stringResource(R.string.dbg_storage_workers), section == StorageSection.WORKERS) { section = StorageSection.WORKERS }
            }

            note?.let {
                Text(
                    text = it,
                    style = DebugMonoSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            when (section) {
                StorageSection.COOKIES -> CookiesList(
                    cookies = cookies,
                    onEdit = { cookie -> editTarget = EditTarget.Cookie(cookie) },
                    onDelete = { cookie ->
                        DebugBridge.evalRaw(tab.session, StorageScripts.deleteCookie(cookie.name)) {
                            refreshTick++
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                StorageSection.LOCAL -> StorageList(
                    items = localItems,
                    onEdit = { item -> editTarget = EditTarget.Storage(StorageSection.LOCAL, item) },
                    onDelete = { item ->
                        DebugBridge.evalRaw(tab.session, StorageScripts.deleteLocal(item.key)) { refreshTick++ }
                    },
                    modifier = Modifier.weight(1f),
                )
                StorageSection.SESSION -> StorageList(
                    items = sessionItems,
                    onEdit = { item -> editTarget = EditTarget.Storage(StorageSection.SESSION, item) },
                    onDelete = { item ->
                        DebugBridge.evalRaw(tab.session, StorageScripts.deleteSession(item.key)) { refreshTick++ }
                    },
                    modifier = Modifier.weight(1f),
                )
                StorageSection.IDB -> Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    if (databases.isEmpty()) {
                        DebugEmpty(stringResource(R.string.dbg_storage_no_idb))
                    }
                    databases.forEach { database ->
                        Text(
                            text = "${if (openDatabase == database.name) "▾" else "▸"} ${database.name}",
                            style = DebugMono,
                            color = NeonCyan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openDatabase = if (openDatabase == database.name) null else database.name
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        if (openDatabase == database.name) {
                            database.stores.forEach { store ->
                                DebugSection(store.name)
                                if (store.records.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.dbg_storage_empty_store),
                                        style = DebugMonoSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                                store.records.forEach { record ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                                        Text(record.key, style = DebugMonoSmall, color = NeonPurple, modifier = Modifier.width(110.dp))
                                        Text(record.value, style = DebugMonoSmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
                StorageSection.CACHE -> Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (caches.isEmpty()) {
                        DebugEmpty(stringResource(R.string.dbg_storage_no_caches))
                    }
                    caches.forEach { cache ->
                        DebugSection(cache.name)
                        cache.urls.take(200).forEach { url ->
                            Text(
                                text = url,
                                style = DebugMonoSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                StorageSection.WORKERS -> Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (workers.isEmpty()) {
                        DebugEmpty(stringResource(R.string.dbg_storage_no_workers))
                    }
                    workers.forEach { worker ->
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DebugBadge(text = worker.state, color = SuccessGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(worker.scope, style = DebugMono, color = NeonCyan, maxLines = 2)
                            }
                            Text(worker.scriptUrl, style = DebugMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            TextButton(
                onClick = { confirmClear = true },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.dbg_storage_clear), color = NeonRed, style = DebugMonoSmall)
            }
        }
    }

    editTarget?.let { target ->
        StorageEditDialog(
            target = target,
            onDismiss = { editTarget = null },
            onConfirm = { key, value ->
                when (target) {
                    is EditTarget.Cookie -> {
                        DebugBridge.evalRaw(tab.session, StorageScripts.setCookie(key, value, 30)) { refreshTick++ }
                    }
                    is EditTarget.Storage -> {
                        val script = if (target.section == StorageSection.LOCAL) {
                            StorageScripts.setLocal(key, value)
                        } else {
                            StorageScripts.setSession(key, value)
                        }
                        DebugBridge.evalRaw(tab.session, script) { refreshTick++ }
                    }
                }
                editTarget = null
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.dbg_storage_clear)) },
            text = { Text(stringResource(R.string.dbg_storage_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    DebugBridge.evalRaw(tab.session, StorageScripts.clearSiteData) {
                        note = context.getString(R.string.dbg_storage_cleared)
                        refreshTick++
                    }
                }) { Text(stringResource(R.string.dbg_save)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.dbg_cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun CookiesList(
    cookies: List<CookieItem>,
    onEdit: (CookieItem) -> Unit,
    onDelete: (CookieItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cookies.isEmpty()) {
        DebugEmpty(stringResource(R.string.dbg_storage_no_cookies), modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier) {
        items(cookies, key = { it.name }) { cookie ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEdit(cookie) },
                ) {
                    Text(cookie.name, style = DebugMono, color = NeonCyan)
                    Text(
                        text = cookie.value.take(120),
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Text(
                        text = "${cookie.domain}${cookie.path} · secure=${cookie.secure} · httpOnly=${cookie.httpOnly} · ${cookie.sameSite}",
                        style = DebugMonoSmall,
                        color = NeonPurple,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { onDelete(cookie) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.dbg_storage_delete), tint = NeonRed)
                }
            }
        }
    }
}

@Composable
private fun StorageList(
    items: List<StorageItem>,
    onEdit: (StorageItem) -> Unit,
    onDelete: (StorageItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        DebugEmpty(stringResource(R.string.dbg_storage_empty), modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier) {
        items(items, key = { it.key }) { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEdit(item) },
                ) {
                    Text(item.key, style = DebugMono, color = NeonCyan)
                    Text(
                        text = item.value,
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.dbg_storage_delete), tint = NeonRed)
                }
            }
        }
    }
}

/** Fires an async storage script and polls until it publishes a result. */
private suspend fun loadAsync(tab: BrowserTab, startScript: String): JSONObject? {
    DebugBridge.evalRaw(tab.session, startScript) {}
    repeat(16) {
        delay(350)
        val result = DebugBridge.awaitJson(tab.session, StorageScripts.asyncResult)
        if (result != null) return result
    }
    return null
}

private sealed interface EditTarget {
    data class Cookie(val cookie: CookieItem?) : EditTarget
    data class Storage(val section: StorageSection, val item: StorageItem?) : EditTarget
}

@Composable
private fun StorageEditDialog(
    target: EditTarget,
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit,
) {
    val initialKey = when (target) {
        is EditTarget.Cookie -> target.cookie?.name ?: ""
        is EditTarget.Storage -> target.item?.key ?: ""
    }
    val initialValue = when (target) {
        is EditTarget.Cookie -> target.cookie?.value ?: ""
        is EditTarget.Storage -> target.item?.value ?: ""
    }
    var key by remember { mutableStateOf(initialKey) }
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dbg_storage_edit_title), style = DebugMono) },
        text = {
            Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.dbg_storage_key)) },
                    singleLine = true,
                    textStyle = DebugMono,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.dbg_storage_value)) },
                    textStyle = DebugMono,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.dbg_storage_edit_note),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = key.isNotBlank(), onClick = { onConfirm(key, value) }) {
                Text(stringResource(R.string.dbg_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dbg_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

private fun buildSnapshot(
    cookies: List<CookieItem>,
    local: List<StorageItem>,
    session: List<StorageItem>,
    idb: List<IdbDatabase>,
    caches: List<CacheGroup>,
    workers: List<ServiceWorkerInfo>,
    url: String?,
): String {
    val root = JSONObject()
    root.put("url", url ?: "")
    root.put("exportedAt", System.currentTimeMillis())
    root.put(
        "cookies",
        JSONArray().apply {
            cookies.forEach { cookie ->
                put(
                    JSONObject()
                        .put("name", cookie.name)
                        .put("value", cookie.value)
                        .put("domain", cookie.domain)
                        .put("path", cookie.path)
                        .put("secure", cookie.secure),
                )
            }
        },
    )
    root.put("localStorage", itemsJson(local))
    root.put("sessionStorage", itemsJson(session))
    root.put(
        "indexedDB",
        JSONArray().apply {
            idb.forEach { database ->
                put(
                    JSONObject()
                        .put("name", database.name)
                        .put(
                            "stores",
                            JSONArray().apply {
                                database.stores.forEach { store ->
                                    put(JSONObject().put("name", store.name).put("records", itemsJson(store.records)))
                                }
                            },
                        ),
                )
            }
        },
    )
    root.put(
        "caches",
        JSONArray().apply {
            caches.forEach { cache ->
                put(JSONObject().put("name", cache.name).put("urls", JSONArray(cache.urls)))
            }
        },
    )
    root.put(
        "serviceWorkers",
        JSONArray().apply {
            workers.forEach { worker ->
                put(JSONObject().put("scope", worker.scope).put("script", worker.scriptUrl).put("state", worker.state))
            }
        },
    )
    return root.toString(2)
}

private fun itemsJson(items: List<StorageItem>): JSONArray = JSONArray().apply {
    items.forEach { item -> put(JSONObject().put("key", item.key).put("value", item.value)) }
}
