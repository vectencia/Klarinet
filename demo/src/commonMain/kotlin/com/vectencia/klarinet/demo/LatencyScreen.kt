package com.vectencia.klarinet.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamConfig

@Composable
fun LatencyScreen() {
    var isMeasuring by remember { mutableStateOf(false) }
    var engine by remember { mutableStateOf<AudioEngine?>(null) }
    var stream by remember { mutableStateOf<AudioStream?>(null) }

    var outputLatency by remember { mutableStateOf("--") }
    var inputLatency by remember { mutableStateOf("--") }
    var sampleRate by remember { mutableStateOf("--") }
    var bufferSize by remember { mutableStateOf("--") }
    var performanceMode by remember { mutableStateOf("--") }

    DisposableEffect(Unit) {
        onDispose {
            try {
                stream?.stop()
                stream?.close()
            } catch (_: Exception) {}
            try {
                engine?.release()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Latency Info", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                LatencyRow("Output Latency", outputLatency)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LatencyRow("Input Latency", inputLatency)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LatencyRow("Sample Rate", sampleRate)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LatencyRow("Buffer Size", bufferSize)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LatencyRow("Performance Mode", performanceMode)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isMeasuring) {
                    // Stop
                    try {
                        stream?.stop()
                        stream?.close()
                    } catch (_: Exception) {}
                    try {
                        engine?.release()
                    } catch (_: Exception) {}
                    stream = null
                    engine = null
                    isMeasuring = false
                } else {
                    // Measure
                    try {
                        val newEngine = AudioEngine.create()
                        engine = newEngine

                        val config = AudioStreamConfig()

                        val newStream = newEngine.openStream(config)
                        stream = newStream
                        newStream.start()
                        isMeasuring = true

                        val latency = newStream.latencyInfo
                        val streamConfig = newStream.config

                        outputLatency = "${latency.outputLatencyMs.toInt()} ms"
                        inputLatency = "${latency.inputLatencyMs.toInt()} ms"
                        sampleRate = "${streamConfig.sampleRate} Hz"
                        bufferSize = if (streamConfig.bufferCapacityInFrames > 0) {
                            "${streamConfig.bufferCapacityInFrames} frames"
                        } else {
                            "Platform default"
                        }
                        performanceMode = streamConfig.performanceMode.name
                    } catch (e: Exception) {
                        isMeasuring = false
                        outputLatency = "Error"
                        inputLatency = e.message ?: "Unknown error"
                    }
                }
            },
        ) {
            Text(if (isMeasuring) "Stop" else "Measure")
        }
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
