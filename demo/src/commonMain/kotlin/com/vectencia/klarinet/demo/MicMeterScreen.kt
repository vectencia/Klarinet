package com.vectencia.klarinet.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vectencia.klarinet.AudioMath
import com.vectencia.klarinet.theme.DemoPanel
import com.vectencia.klarinet.theme.DemoPrimaryButton
import com.vectencia.klarinet.theme.LevelMeter

@Composable
fun MicMeterScreen(viewModel: MicMeterViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Mic Meter", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Input analysis on the worker thread", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))

        DemoPanel(modifier = Modifier.fillMaxWidth()) {
            Text("Level", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            LevelMeter(state.level)
            Spacer(Modifier.height(12.dp))
            Text(
                "Peak ${(state.level * 100).toInt()}%   RMS ${state.rmsDb}   Peak ${state.peakDb}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Bands L/M/H  ${formatDb(AudioMath.linearToDb(state.lowBand))} / ${formatDb(AudioMath.linearToDb(state.midBand))} / ${formatDb(AudioMath.linearToDb(state.highBand))}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text("Latency ${state.inputLatency}   Xruns ${state.xruns}", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        DemoPrimaryButton(
            label = if (state.isRecording) "Stop" else "Record",
            onClick = { viewModel.onEvent(MicMeterEvent.ToggleRecording) },
            active = state.isRecording,
        )
    }
}
