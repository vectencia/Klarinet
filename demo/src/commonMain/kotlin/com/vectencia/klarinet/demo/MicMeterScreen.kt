package com.vectencia.klarinet.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MicMeterScreen(viewModel: MicMeterViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Mic Meter", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text("Level", fontSize = 16.sp)

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.DarkGray),
                ) {
                    val clampedLevel = state.level.coerceIn(0f, 1f)
                    val barColor = when {
                        clampedLevel > 0.8f -> Color.Red
                        clampedLevel > 0.5f -> Color.Yellow
                        else -> Color.Green
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = clampedLevel)
                            .height(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Peak: ${(state.level * 100).toInt()}%", fontSize = 14.sp)

                Spacer(modifier = Modifier.height(8.dp))

                Text("Input Latency: ${state.inputLatency}", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.onEvent(MicMeterEvent.ToggleRecording) },
        ) {
            Text(if (state.isRecording) "Stop" else "Record")
        }
    }
}
