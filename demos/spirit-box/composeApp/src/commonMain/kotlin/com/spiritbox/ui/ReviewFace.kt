package com.spiritbox.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spiritbox.MAX_DETECT_THRESHOLD
import com.spiritbox.PsicofoniaSpan
import com.spiritbox.audio.SAMPLE_RATE
import com.spiritbox.playback.MAX_PLAYBACK_RATE
import com.spiritbox.playback.MIN_PLAYBACK_RATE
import com.spiritbox.playback.formatRate
import com.spiritbox.review.MarkedSpan
import com.spiritbox.session.ListedSession
import com.spiritbox.station.MAX_DWELL_MS
import com.spiritbox.station.MIN_DWELL_MS
import com.spiritbox.transcription.TranscriptionView
import kotlin.math.roundToInt

private fun spanClock(frame: Int): String {
    val tenths = frame / (SAMPLE_RATE / 10)
    return "${tenths / 10}.${tenths % 10}s"
}

@Composable
internal fun ReviewFace(
    title: String,
    recorded: String,
    duration: String,
    panel: PanelState,
    wave: FloatArray,
    marks: List<MarkedSpan>,
    playing: Boolean,
    paused: Boolean,
    rate: Float,
    reverse: Boolean,
    gain: Float,
    voiceBand: Boolean,
    threshold: Float,
    transcription: TranscriptionView,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRate: (Float) -> Unit,
    onReverse: (Boolean) -> Unit,
    onGain: (Float) -> Unit,
    onVoice: (Boolean) -> Unit,
    onThreshold: (Float) -> Unit,
    onAddMark: () -> Unit,
    onRemoveMark: (PsicofoniaSpan) -> Unit,
    onJump: (Int) -> Unit,
    onTheme: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "$recorded  $duration",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.height(12.dp))
        ControlButton(label = if (dark) "Dark" else "Light", onClick = onTheme, selected = dark)
        Spacer(Modifier.height(12.dp))
        if (transcription.notConnected) {
            Text(
                text = transcription.status,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            transcription.lines.forEach { line ->
                Text(
                    text = "${line.text}  ${line.confidence}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ControlButton(label = "Back", onClick = onBack)
        when (panel) {
            PanelState.Loading -> {
                Spacer(Modifier.height(20.dp))
                Text("Loading take", style = MaterialTheme.typography.titleMedium)
            }
            PanelState.Empty -> {
                Spacer(Modifier.height(20.dp))
                Text("This take is empty", style = MaterialTheme.typography.titleMedium)
            }
            PanelState.Error -> {
                Spacer(Modifier.height(20.dp))
                Text("Couldn't open this take", style = MaterialTheme.typography.titleMedium)
            }
            PanelState.Ready -> {
                Spacer(Modifier.height(16.dp))
                EnvelopeStrip(wave)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ControlButton(label = "Play", onClick = onPlay, selected = playing && !paused)
                    ControlButton(label = "Pause", onClick = onPause, selected = playing && paused)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Speed ${formatRate(rate)} · 0.25×–2×",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                )
                Slider(
                    value = rate,
                    onValueChange = onRate,
                    modifier = Modifier.fillMaxWidth(),
                    valueRange = MIN_PLAYBACK_RATE..MAX_PLAYBACK_RATE,
                )
                ControlButton(label = "Reverse", onClick = { onReverse(!reverse) }, selected = reverse)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Boost",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = gain,
                    onValueChange = onGain,
                    modifier = Modifier.fillMaxWidth(),
                    valueRange = 0f..4f,
                )
                ControlButton(label = "Voice band", onClick = { onVoice(!voiceBand) }, selected = voiceBand)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Threshold ${threshold.roundToInt()}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                )
                Slider(
                    value = threshold,
                    onValueChange = onThreshold,
                    modifier = Modifier.fillMaxWidth(),
                    valueRange = 1f..MAX_DETECT_THRESHOLD,
                )
                Spacer(Modifier.height(8.dp))
                ControlButton(label = "Add mark", onClick = onAddMark)
                marks.forEach { mark ->
                    val span = PsicofoniaSpan(mark.startFrame, mark.endFrame)
                    Spacer(Modifier.height(16.dp))
                    ControlButton(
                        label = if (mark.manual) "Manual" else "Auto",
                        onClick = { onJump(mark.startFrame) },
                        modifier = Modifier.fillMaxWidth(),
                        selected = mark.manual,
                    )
                    Text(
                        text = spanClock(mark.startFrame),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                    if (mark.manual) {
                        Spacer(Modifier.height(8.dp))
                        ControlButton(label = "Remove", onClick = { onRemoveMark(span) })
                    }
                }
            }
        }
    }
}
