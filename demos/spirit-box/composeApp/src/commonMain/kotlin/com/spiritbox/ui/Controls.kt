package com.spiritbox.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val PressEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Composable
internal fun EnvelopeStrip(levels: FloatArray, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    if (levels.isEmpty()) {
        Spacer(modifier.fillMaxWidth().height(48.dp))
        return
    }
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val gap = 2.dp.toPx()
        val count = levels.size
        val bar = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        levels.forEachIndexed { index, level ->
            val height = (level.coerceIn(0f, 1f) * size.height).coerceAtLeast(1f)
            drawRect(
                color = barColor,
                topLeft = Offset(index * (bar + gap), size.height - height),
                size = Size(bar, height),
            )
        }
    }
}

@Composable
internal fun ControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 140, easing = PressEase),
        label = "press",
    )
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 112.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) scheme.primary else scheme.surfaceVariant,
            contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        ),
    ) {
        Text(label)
    }
}
