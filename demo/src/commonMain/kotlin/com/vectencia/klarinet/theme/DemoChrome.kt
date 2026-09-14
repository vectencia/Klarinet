package com.vectencia.klarinet.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DemoBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(scheme.surfaceVariant.copy(alpha = 0.55f), scheme.background),
            ),
        ),
    ) {
        content()
    }
}

@Composable
fun DemoPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.outline.copy(alpha = 0.35f))
            .padding(2.dp),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(scheme.surface)
                .padding(18.dp),
            content = content,
        )
    }
}

@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    val clamped = level.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = klarinetSpring(),
        label = "meter",
    )
    val fill = when {
        clamped > 0.8f -> Clip
        clamped > 0.5f -> Warn
        else -> Teal
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animated.coerceAtLeast(0.01f))
                .clip(CircleShape)
                .background(fill),
        )
    }
}

@Composable
fun DemoPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = klarinetSpring(),
        label = "press",
    )
    val scheme = MaterialTheme.colorScheme
    val bg = if (active) Clip else scheme.primary
    val fg = if (active) Color.White else scheme.onPrimary
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SessionBanner(text: String?) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(klarinetTween(280)) + slideInVertically(klarinetTween(280)) { -it / 2 },
        exit = fadeOut(klarinetTween(220)) + slideOutVertically(klarinetTween(220)) { -it / 2 },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Warn.copy(alpha = 0.18f))
                .border(1.dp, Warn.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = text.orEmpty(),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
            )
        }
    }
}
