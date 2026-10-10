package io.github.sachintha82288.devbrowser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed

/** Small tab chip used in the horizontal tab strip. */
@Composable
fun TabChip(
    title: String,
    isSelected: Boolean,
    isPrivate: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isPrivate) Icons.Rounded.VisibilityOff else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (isPrivate) NeonPurple else NeonCyan,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = title.ifBlank { "New tab" },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .width(110.dp),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Header with a back arrow and title for full-screen overlay screens. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}

/** Neon section label with a gradient accent bar. */
@Composable
fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .background(Brush.verticalGradient(listOf(NeonRed, NeonPurple))),
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = NeonCyan,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A settings row with leading icon, title, optional subtitle and trailing slot. */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** A neon-accented card used across overlay screens. */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** The neon "Dev//Browser" wordmark rendered with a gradient slash. */
@Composable
fun DevBrowserWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Dev",
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "//",
            color = NeonRed,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Browser",
            color = NeonCyan,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
