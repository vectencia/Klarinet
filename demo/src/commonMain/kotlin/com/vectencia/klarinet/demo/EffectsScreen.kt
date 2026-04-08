package com.vectencia.klarinet.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun EffectsScreen(viewModel: EffectsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Effects Demo", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.onEvent(EffectsEvent.TogglePlayback) },
        ) {
            Text(if (state.isPlaying) "Stop" else "Play")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("State: ${state.streamState}", fontSize = 14.sp)

        if (state.isPlaying) {
            Text("Output Level: ${(state.outputLevel * 100).toInt()}%", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -- Gain Effect --
        EffectCard(
            name = "Gain",
            enabled = state.gainEnabled,
            onEnabledChange = { viewModel.onEvent(EffectsEvent.UpdateGainEnabled(it)) },
        ) {
            ParameterSlider(
                label = "Volume",
                value = state.gainDb,
                valueRange = -24f..12f,
                valueFormat = { "${it.roundToInt()} dB" },
                onValueChange = { viewModel.onEvent(EffectsEvent.UpdateGainDb(it)) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -- Delay Effect --
        EffectCard(
            name = "Delay",
            enabled = state.delayEnabled,
            onEnabledChange = { viewModel.onEvent(EffectsEvent.UpdateDelayEnabled(it)) },
        ) {
            ParameterSlider(
                label = "Time",
                value = state.delayTimeMs,
                valueRange = 10f..1000f,
                valueFormat = { "${it.roundToInt()} ms" },
                onValueChange = { viewModel.onEvent(EffectsEvent.UpdateDelayTimeMs(it)) },
            )
            ParameterSlider(
                label = "Feedback",
                value = state.delayFeedback,
                valueRange = 0f..0.95f,
                valueFormat = { ((it * 100).toInt() / 100f).toString() },
                onValueChange = { viewModel.onEvent(EffectsEvent.UpdateDelayFeedback(it)) },
            )
            ParameterSlider(
                label = "Mix",
                value = state.delayMix,
                valueRange = 0f..1f,
                valueFormat = { ((it * 100).toInt() / 100f).toString() },
                onValueChange = { viewModel.onEvent(EffectsEvent.UpdateDelayMix(it)) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -- Reverb Effect --
        EffectCard(
            name = "Reverb",
            enabled = state.reverbEnabled,
            onEnabledChange = { viewModel.onEvent(EffectsEvent.UpdateReverbEnabled(it)) },
        ) {
            ParameterSlider(
                label = "Room Size",
                value = state.reverbRoomSize,
                valueRange = 0f..1f,
                valueFormat = { ((it * 100).toInt() / 100f).toString() },
                onValueChange = { viewModel.onEvent(EffectsEvent.UpdateReverbRoomSize(it)) },
            )
            ParameterSlider(
                label = "Damping",
                value = state.reverbDamping,
                valueRange = 0f..1f,
                valueFormat = { ((it * 100).toInt() / 100f).toString() },
                onValueChange = { viewModel.onEvent(EffectsEvent.UpdateReverbDamping(it)) },
            )
            ParameterSlider(
                label = "Mix",
                value = state.reverbMix,
                valueRange = 0f..1f,
                valueFormat = { ((it * 100).toInt() / 100f).toString() },
                onValueChange = { viewModel.onEvent(EffectsEvent.UpdateReverbMix(it)) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun EffectCard(
    name: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormat: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(0.25f))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(0.55f),
        )
        Text(
            text = valueFormat(value),
            fontSize = 14.sp,
            modifier = Modifier.weight(0.2f),
        )
    }
}
