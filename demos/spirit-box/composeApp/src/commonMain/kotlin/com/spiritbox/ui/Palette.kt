package com.spiritbox.ui

internal fun lightSurfaceArgb(): Int = 0xFFF4EFE6.toInt()

internal fun darkSurfaceArgb(): Int = 0xFF141311.toInt()

internal fun lightInkArgb(): Int = 0xFF1C1916.toInt()

internal fun darkInkArgb(): Int = 0xFFF3EEE6.toInt()

internal fun accentArgb(): Int = 0xFFC46B3A.toInt()

internal fun markArgb(): Int = 0xFF3E8E89.toInt()

internal fun channelLuminance(argb: Int): Float {
    val red = (argb ushr 16) and 0xFF
    val green = (argb ushr 8) and 0xFF
    val blue = argb and 0xFF
    return (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
}

internal fun surfaceLuminance(dark: Boolean): Float =
    channelLuminance(if (dark) darkSurfaceArgb() else lightSurfaceArgb())
