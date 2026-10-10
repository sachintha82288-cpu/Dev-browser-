package io.github.sachintha82288.devbrowser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private fun devColorScheme(amoled: Boolean) = darkColorScheme(
    primary = NeonRed,
    onPrimary = Color.White,
    primaryContainer = NeonRedDark,
    onPrimaryContainer = Color.White,
    secondary = NeonCyan,
    onSecondary = Color(0xFF00252B),
    secondaryContainer = NeonCyanDark,
    onSecondaryContainer = Color.White,
    tertiary = NeonPurple,
    onTertiary = Color.White,
    tertiaryContainer = NeonPurpleDark,
    onTertiaryContainer = Color.White,
    background = if (amoled) AppBackgroundAmoled else AppBackground,
    onBackground = TextPrimary,
    surface = if (amoled) AppBackgroundAmoled else SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = if (amoled) Color(0xFF0A0A0F) else SurfaceElevated,
    surfaceContainerHigh = if (amoled) Color(0xFF101018) else SurfaceElevatedHigh,
    surfaceContainerHighest = if (amoled) Color(0xFF16161F) else SurfaceElevatedHigh,
    surfaceContainerLow = if (amoled) Color(0xFF07070C) else SurfaceDark,
    surfaceContainerLowest = if (amoled) Color.Black else AppBackground,
    surfaceTint = NeonRed,
    inverseSurface = Color(0xFFE6E6F0),
    inverseOnSurface = Color(0xFF14141F),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = NeonRedDark,
    onErrorContainer = Color.White,
    outline = SurfaceStroke,
    outlineVariant = Color(0xFF232335),
    scrim = Color(0xB3000000),
)

/** Whether the pure-black AMOLED variant is active — used for custom canvases/gradients. */
val LocalAmoledBlack = staticCompositionLocalOf { false }

@Composable
fun DevBrowserTheme(
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    // DevBrowser is always dark — like a proper dev tool.
    isSystemInDarkTheme() // (kept for future light theme support)
    androidx.compose.runtime.CompositionLocalProvider(LocalAmoledBlack provides amoledBlack) {
        MaterialTheme(
            colorScheme = devColorScheme(amoledBlack),
            typography = DevTypography,
            content = content,
        )
    }
}
