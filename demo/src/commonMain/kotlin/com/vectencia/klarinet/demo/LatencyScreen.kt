package com.vectencia.klarinet.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.vectencia.klarinet.theme.DemoPanel
import com.vectencia.klarinet.theme.DemoPrimaryButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LatencyScreen(viewModel: LatencyViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Latency Info", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        DemoPanel(modifier = Modifier.fillMaxWidth()) {
                LatencyRow("Output Latency", state.outputLatency)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LatencyRow("Input Latency", state.inputLatency)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LatencyRow("Sample Rate", state.sampleRate)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LatencyRow("Buffer Size", state.bufferSize)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LatencyRow("Performance Mode", state.performanceMode)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LatencyRow("Xruns", "${state.xruns}")
        }

        Spacer(modifier = Modifier.height(16.dp))

        DemoPrimaryButton(
            label = if (state.isMeasuring) "Stop" else "Measure",
            onClick = { viewModel.onEvent(LatencyEvent.ToggleMeasure) },
            active = state.isMeasuring,
        )
    }
}

@Composable
private fun LatencyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp)
        Text(value, fontSize = 14.sp)
    }
}
