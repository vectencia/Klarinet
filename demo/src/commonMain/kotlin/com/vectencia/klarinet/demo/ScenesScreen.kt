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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.vectencia.klarinet.theme.DemoPrimaryButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun ScenesScreen(viewModel: ScenesViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Scenes", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Fade: ${state.fadeMs.roundToInt()} ms", fontSize = 18.sp)
        Slider(
            value = state.fadeMs,
            onValueChange = { viewModel.onEvent(ScenesEvent.FadeMs(it)) },
            valueRange = 0f..5000f,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = { viewModel.onEvent(ScenesEvent.Select(ScenePresets.low)) }) {
                Text("Low")
            }
            Button(onClick = { viewModel.onEvent(ScenesEvent.Select(ScenePresets.high)) }) {
                Text("High")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = { viewModel.onEvent(ScenesEvent.Select(ScenePresets.stack)) }) {
                Text("Stack")
            }
            Button(onClick = { viewModel.onEvent(ScenesEvent.Select(ScenePresets.lowHall)) }) {
                Text("Low hall")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DemoPrimaryButton(
            label = "Stop",
            onClick = { viewModel.onEvent(ScenesEvent.Stop) },
            active = state.currentId != null,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Current: ${state.currentId ?: ""}", fontSize = 14.sp)
        Text("Layers: ${state.layerIds.joinToString()}", fontSize = 14.sp)
        Text(state.status, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.json,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
