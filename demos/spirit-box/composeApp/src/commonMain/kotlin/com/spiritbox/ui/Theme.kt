package com.spiritbox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun SpiritTheme(dark: Boolean, content: @Composable () -> Unit) {
    val accent = colorOf(accentArgb().toLong())
    val mark = colorOf(markArgb().toLong())
    val surface = colorOf((if (dark) darkSurfaceArgb() else lightSurfaceArgb()).toLong())
    val ink = colorOf((if (dark) darkInkArgb() else lightInkArgb()).toLong())
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = colorOf(0xFF1C100C),
            secondary = mark,
            onSecondary = colorOf(0xFF041615),
            surface = surface,
            onSurface = ink,
            background = surface,
            onBackground = ink,
            surfaceVariant = colorOf(0xFF2A2622),
            onSurfaceVariant = colorOf(0xFFD4CBBF),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = colorOf(0xFFFFF8F3),
            secondary = mark,
            onSecondary = colorOf(0xFFF4FFFE),
            surface = surface,
            onSurface = ink,
            background = surface,
            onBackground = ink,
            surfaceVariant = colorOf(0xFFE7DFD2),
            onSurfaceVariant = colorOf(0xFF5C564C),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun colorOf(argb: Long): Color {
    val bits = argb.toInt()
    return Color(
        red = ((bits ushr 16) and 0xFF) / 255f,
        green = ((bits ushr 8) and 0xFF) / 255f,
        blue = (bits and 0xFF) / 255f,
    )
}
