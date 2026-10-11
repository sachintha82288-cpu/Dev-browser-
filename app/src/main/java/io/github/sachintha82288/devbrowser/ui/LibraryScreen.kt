package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.data.Library
import io.github.sachintha82288.devbrowser.ui.components.ScreenHeader
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle

/** Bookmarks and browsing history. */
@Composable
fun LibraryScreen(app: DevBrowserApplication, onClose: () -> Unit) {
    val library = app.library
    var mode by remember { mutableStateOf(LibraryMode.BOOKMARKS) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.library_title),
            onBack = onClose,
            trailing = {
                if (mode == LibraryMode.HISTORY && library.history.isNotEmpty()) {
                    IconButton(onClick = { library.clearHistory() }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_history))
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = mode == LibraryMode.BOOKMARKS,
                onClick = { mode = LibraryMode.BOOKMARKS },
                label = { Text(stringResource(R.string.bookmarks)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
            FilterChip(
                selected = mode == LibraryMode.HISTORY,
                onClick = { mode = LibraryMode.HISTORY },
                label = { Text(stringResource(R.string.history)) },
                leadingIcon = {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
        }

        if (mode == LibraryMode.BOOKMARKS) {
            if (library.bookmarks.isEmpty()) {
                EmptyHint(stringResource(R.string.no_bookmarks))
            } else {
                LazyColumn {
                    items(library.bookmarks, key = { it.url }) { bookmark ->
                        LibraryRow(
                            title = bookmark.title,
                            subtitle = bookmark.url,
                            onClick = {
                                app.tabs.newTab(url = bookmark.url)
                                onClose()
                            },
                            onDelete = { library.removeBookmark(bookmark.url) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        } else {
            if (library.history.isEmpty()) {
                EmptyHint(stringResource(R.string.no_history))
            } else {
                LazyColumn {
                    items(library.history, key = { "${it.visitedAt}-${it.url}" }) { entry ->
                        LibraryRow(
                            title = entry.title,
                            subtitle = "${Library.formatTime(entry.visitedAt)} · ${entry.url}",
                            onClick = {
                                app.tabs.newTab(url = entry.url)
                                onClose()
                            },
                            onDelete = { library.removeHistory(entry.url) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

private enum class LibraryMode { BOOKMARKS, HISTORY }

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(18.dp))
        }
    }
}
