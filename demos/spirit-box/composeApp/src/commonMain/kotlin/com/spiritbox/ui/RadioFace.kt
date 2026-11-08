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

@Composable
internal fun RadioFace(
    frequency: String,
    callsign: String,
    running: Boolean,
    dwellMs: Int,
    forward: Boolean,
    holding: Boolean,
    am: Boolean,
    recording: Boolean,
    sessions: List<ListedSession>,
    library: PanelState,
    playing: String?,
    mic: Float,
    sessionName: String,
    recordGain: Float,
    live: FloatArray,
    elapsedLabel: String,
    dark: Boolean,
    onTheme: () -> Unit,
    onName: (String) -> Unit,
    onRecordGain: (Float) -> Unit,
    onStartStop: () -> Unit,
    onRecord: () -> Unit,
    onPlay: (String) -> Unit,
    onDwell: (Int) -> Unit,
    onForward: () -> Unit,
    onReverse: () -> Unit,
    onHold: () -> Unit,
    onAm: () -> Unit,
    onFm: () -> Unit,
    onMic: (Float) -> Unit,
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
            text = frequency,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = callsign,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        ControlButton(label = if (dark) "Dark" else "Light", onClick = onTheme, selected = dark)
        Spacer(Modifier.height(28.dp))
        ControlButton(
            label = if (running) "Stop" else "Start",
            onClick = onStartStop,
            selected = true,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Dwell $dwellMs ms",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        )
        Slider(
            value = dwellMs.toFloat(),
            onValueChange = { onDwell(it.roundToInt().coerceIn(MIN_DWELL_MS, MAX_DWELL_MS)) },
            modifier = Modifier.fillMaxWidth(),
            valueRange = MIN_DWELL_MS.toFloat()..MAX_DWELL_MS.toFloat(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlButton(label = "Forward", onClick = onForward, selected = forward)
            ControlButton(label = "Reverse", onClick = onReverse, selected = !forward)
        }
        Spacer(Modifier.height(12.dp))
        ControlButton(label = "Hold", onClick = onHold, selected = holding)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlButton(label = "AM", onClick = onAm, selected = am)
            ControlButton(label = "FM", onClick = onFm, selected = !am)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Radio to mic",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = mic,
            onValueChange = onMic,
            modifier = Modifier.fillMaxWidth(),
            valueRange = 0f..1f,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "dry noise",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = sessionName,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Session name") },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Record gain",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = recordGain,
            onValueChange = onRecordGain,
            modifier = Modifier.fillMaxWidth(),
            valueRange = 0f..2f,
        )
        ControlButton(
            label = if (recording) "Stop record" else "Record",
            onClick = onRecord,
            selected = recording,
        )
        if (recording) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "REC",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = elapsedLabel,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            )
            EnvelopeStrip(live)
        }
        Spacer(Modifier.height(20.dp))
        when (library) {
            PanelState.Loading -> Text("Loading sessions", style = MaterialTheme.typography.titleMedium)
            PanelState.Empty -> Text("No sessions yet", style = MaterialTheme.typography.titleMedium)
            PanelState.Error -> Text("Couldn't open sessions", style = MaterialTheme.typography.titleMedium)
            PanelState.Ready -> sessions.forEach { session ->
                Spacer(Modifier.height(8.dp))
                ControlButton(
                    label = session.title,
                    onClick = { onPlay(session.path) },
                    modifier = Modifier.fillMaxWidth(),
                    selected = session.path == playing,
                )
                Text(
                    text = "${session.recorded}  ${session.duration}",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}
