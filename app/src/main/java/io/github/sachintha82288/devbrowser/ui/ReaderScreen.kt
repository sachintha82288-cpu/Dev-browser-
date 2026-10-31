package io.github.sachintha82288.devbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.ReaderArticle
import io.github.sachintha82288.devbrowser.ui.components.ScreenHeader
import io.github.sachintha82288.devbrowser.ui.debug.components.copyToClipboard
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan

/**
 * Stage A: distraction-free reader view rendered natively from the extracted
 * article. No scripts, images or layout from the page survive.
 */
@Composable
fun ReaderScreen(article: ReaderArticle?, onClose: () -> Unit) {
    val context = LocalContext.current
    val minutesLabel = stringResource(R.string.reader_minutes)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.reader_title),
            onBack = onClose,
            trailing = {
                IconButton(
                    onClick = {
                        article?.let {
                            copyToClipboard(context, "article", it.paragraphs.joinToString("\n\n"))
                        }
                    },
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.reader_copy))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = stringResource(R.string.reader_back))
                }
            },
        )

        if (article == null) {
            Text(
                text = stringResource(R.string.reader_empty),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = article.title,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        if (article.siteName.isNotBlank()) append(article.siteName).append("  ·  ")
                        if (article.byline.isNotBlank()) append(article.byline).append("  ·  ")
                        append(article.minutes)
                        append(" ")
                        append(minutesLabel)
                    },
                    style = MonoTextStyle,
                    color = NeonCyan,
                )
            }
            article.paragraphs.forEachIndexed { index, paragraph ->
                val isHeading = paragraph.length < 90 && !paragraph.endsWith(".")
                Text(
                    text = paragraph,
                    fontSize = if (isHeading) 19.sp else 17.sp,
                    lineHeight = if (isHeading) 25.sp else 27.sp,
                    fontWeight = if (isHeading) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (index == 0) {
                    Text(
                        text = article.url,
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                text = stringResource(R.string.reader_note),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }
}
