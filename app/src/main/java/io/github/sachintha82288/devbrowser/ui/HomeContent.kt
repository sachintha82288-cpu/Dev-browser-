package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.ui.components.DevBrowserWordmark
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed

private data class QuickLink(val label: String, val url: String, val accent: Color)

private val QUICK_LINKS = listOf(
    QuickLink("MDN", "https://developer.mozilla.org", NeonRed),
    QuickLink("GitHub", "https://github.com", Color(0xFFE6E6F0)),
    QuickLink("Stack Overflow", "https://stackoverflow.com", Color(0xFFF48024)),
    QuickLink("Can I use", "https://caniuse.com", NeonPurple),
    QuickLink("npm", "https://www.npmjs.com", Color(0xFFCD3E6C)),
    QuickLink("DevDocs", "https://devdocs.io", Color(0xFF47A3F3)),
    QuickLink("Regex101", "https://regex101.com", NeonRed),
    QuickLink("Hacker News", "https://news.ycombinator.com", Color(0xFFFF6600)),
    QuickLink("GitHub Trending", "https://github.com/trending", Color(0xFFE6E6F0)),
    QuickLink("JSONLint", "https://jsonlint.com", NeonPurple),
    QuickLink("Tor Check", "https://check.torproject.org", Color(0xFF59C1F0)),
    QuickLink("about:config", "about:config", Color(0xFF3DDC97)),
)

/** The built-in start page: wordmark, search field and a developer speed dial. */
@Composable
fun HomeContent(
    isPrivate: Boolean,
    searchEngineLabel: String,
    onSearch: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        DevBrowserWordmark()

        if (isPrivate) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = NeonPurple,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.private_tab),
                    style = MonoTextStyle,
                    color = NeonPurple,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text(stringResource(R.string.search_hint), style = MonoTextStyle) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            textStyle = MonoTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                if (input.isNotBlank()) onSearch(input)
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "· $searchEngineLabel ·",
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(QUICK_LINKS) { link ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .height(76.dp)
                        .clickable { onSearch(link.url) },
                ) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(width = 22.dp, height = 3.dp)
                                .background(Brush.horizontalGradient(listOf(link.accent, Color.Transparent))),
                        )
                        Text(
                            text = link.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
