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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun ToneGeneratorScreen(viewModel: ToneGeneratorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Tone Generator", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Frequency: ${state.frequency.roundToInt()} Hz", fontSize = 18.sp)

                Slider(
                    value = state.frequency,
                    onValueChange = { viewModel.onEvent(ToneGeneratorEvent.FrequencyChanged(it)) },
                    valueRange = 220f..880f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.onEvent(ToneGeneratorEvent.TogglePlayback) },
        ) {
            Text(if (state.isPlaying) "Stop" else "Play")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("State: ${state.streamState}", fontSize = 14.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = { viewModel.onEvent(ToneGeneratorEvent.SleepDuration(5_000L)) }) {
                Text("5 s")
            }
            Button(onClick = { viewModel.onEvent(ToneGeneratorEvent.SleepDuration(10_000L)) }) {
                Text("10 s")
            }
            Button(onClick = { viewModel.onEvent(ToneGeneratorEvent.SleepDuration(30_000L)) }) {
                Text("30 s")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = { viewModel.onEvent(ToneGeneratorEvent.SleepFade(500f)) }) {
                Text("0.5 s")
            }
            Button(onClick = { viewModel.onEvent(ToneGeneratorEvent.SleepFade(1_000f)) }) {
                Text("1 s")
            }
            Button(onClick = { viewModel.onEvent(ToneGeneratorEvent.SleepFade(2_000f)) }) {
                Text("2 s")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(
                onClick = { viewModel.onEvent(ToneGeneratorEvent.ScheduleSleep) },
                enabled = state.isPlaying,
            ) {
                Text("Schedule")
            }
            Button(
                onClick = { viewModel.onEvent(ToneGeneratorEvent.PauseSleep) },
                enabled = state.isPlaying,
            ) {
                Text("Pause")
            }
            Button(
                onClick = { viewModel.onEvent(ToneGeneratorEvent.ResumeSleep) },
                enabled = state.isPlaying,
            ) {
                Text("Resume")
            }
            Button(
                onClick = { viewModel.onEvent(ToneGeneratorEvent.CancelSleep) },
                enabled = state.isPlaying,
            ) {
                Text("Cancel")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Timer: ${state.sleepState} remaining ${state.sleepRemainingMs} ms", fontSize = 14.sp)
    }
}
