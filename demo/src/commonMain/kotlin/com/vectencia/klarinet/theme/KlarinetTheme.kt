package com.vectencia.klarinet.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

/** Studio charcoal. Not pure black. */
val Ink = Color(0xFF101214)
val Slate = Color(0xFF171A1D)
val Raised = Color(0xFF1F2428)
val Hairline = Color(0x22FFFFFF)
val Ivory = Color(0xFFE6E8EB)
val Mute = Color(0xFF8D939A)
val Teal = Color(0xFF3D9B8F)
val TealDim = Color(0xFF2A6F67)
val Warn = Color(0xFFC9A227)
val Clip = Color(0xFFD96B6B)
val Paper = Color(0xFFF3F4F6)
val PaperInk = Color(0xFF16181B)

val KlarinetEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

fun <T> klarinetTween(durationMillis: Int = 420) = tween<T>(
    durationMillis = durationMillis,
    easing = KlarinetEase,
)

fun klarinetSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF04110F),
    primaryContainer = TealDim,
    onPrimaryContainer = Ivory,
    secondary = Mute,
    onSecondary = Ink,
    background = Ink,
    onBackground = Ivory,
    surface = Slate,
    onSurface = Ivory,
    surfaceVariant = Raised,
    onSurfaceVariant = Mute,
    outline = Hairline,
    error = Clip,
    onError = Ivory,
)

private val LightColors = lightColorScheme(
    primary = TealDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EDE9),
    onPrimaryContainer = PaperInk,
    secondary = Mute,
    onSecondary = Paper,
    background = Paper,
    onBackground = PaperInk,
    surface = Color(0xFFFFFFFF),
    onSurface = PaperInk,
    surfaceVariant = Color(0xFFE7EAEB),
    onSurfaceVariant = Color(0xFF5C636A),
    outline = Color(0x22000000),
    error = Clip,
    onError = Color.White,
)

private val KlarinetTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        letterSpacing = (-0.8).sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.4).sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Mute,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
    ),
)

private val KlarinetShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun KlarinetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KlarinetTypography,
        shapes = KlarinetShapes,
        content = content,
    )
}
