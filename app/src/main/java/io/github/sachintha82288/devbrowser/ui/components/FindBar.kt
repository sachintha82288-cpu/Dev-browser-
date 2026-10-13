package io.github.sachintha82288.devbrowser.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.R
import org.mozilla.geckoview.GeckoSession

/**
 * Find-in-page state backed by [GeckoSession.getFinder]. Exposed as a plain
 * class so [io.github.sachintha82288.devbrowser.MainActivity] can open it with
 * Ctrl+F.
 */
class FindBarState {
    var active: Boolean by mutableStateOf(false)
    var query: String by mutableStateOf("")
    var matchCase: Boolean by mutableStateOf(false)
    var current: Int by mutableStateOf(-1)
    var total: Int by mutableStateOf(0)

    fun activate() {
        active = true
        query = ""
        current = -1
        total = 0
    }

    fun close(session: GeckoSession?) {
        try {
            session?.finder?.clear()
        } catch (_: Exception) {
        }
        active = false
    }

    fun find(session: GeckoSession?, forward: Boolean) {
        if (query.isEmpty()) return
        val flags = (if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS) or
            (if (matchCase) GeckoSession.FINDER_FIND_MATCH_CASE else 0)
        try {
            session?.finder?.find(query, flags)?.accept({ result ->
                current = result?.current ?: -1
                total = result?.total ?: 0
            }) {
                current = -1
                total = 0
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
fun rememberFindBarState(): FindBarState = remember { FindBarState() }

/** Bottom overlay bar with match count, next/prev and match-case toggle. */
@Composable
fun FindBar(
    state: FindBarState,
    session: GeckoSession?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(8.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { state.query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.find_in_page)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.find(session, true) }),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (state.total > 0) {
                                "${state.current}/${state.total}"
                            } else {
                                stringResource(R.string.find_no_matches)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { state.matchCase = !state.matchCase }) {
                            Text(
                                text = "Aa",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (state.matchCase) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { state.find(session, false) }) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.find_previous),
                            )
                        }
                        IconButton(onClick = { state.find(session, true) }) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.find_next),
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                },
            )
        }
    }
}
