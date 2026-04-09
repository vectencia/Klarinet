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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.StreamDirection
import com.vectencia.klarinet.coroutines.levelFlow

/** Holds native resources outside Compose State so cleanup is reliable. */
private class MicResources {
    var engine: AudioEngine? = null
    var stream: AudioStream? = null

    fun release() {
        try {
            stream?.stop()
            stream?.close()
        } catch (_: Exception) {}
        try {
            engine?.release()
        } catch (_: Exception) {}
        stream = null
        engine = null
    }
}

@Composable
fun MicMeterScreen() {
    var isRecording by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(0f) }
    var inputLatency by remember { mutableStateOf("--") }
    val resources = remember { MicResources() }

    DisposableEffect(Unit) {
        onDispose {
            resources.release()
        }
    }

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

                // VU meter bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.DarkGray),
                ) {
                    val clampedLevel = level.coerceIn(0f, 1f)
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

                Text("Peak: ${(level * 100).toInt()}% (raw: ${"%.4f".format(level)})", fontSize = 14.sp)

                Spacer(modifier = Modifier.height(8.dp))

                Text("Input Latency: $inputLatency", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isRecording) {
                    resources.release()
                    isRecording = false
                    level = 0f
                } else {
                    try {
                        val newEngine = AudioEngine.create()
                        resources.engine = newEngine

                        val callback = object : AudioStreamCallback {
                            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                                return numFrames
                            }
                        }

                        val config = AudioStreamConfig(
                            direction = StreamDirection.INPUT,
                            channelCount = 1,
                        )

                        val newStream = newEngine.openStream(config, callback)
                        resources.stream = newStream
                        newStream.start()
                        isRecording = true

                        val latency = newStream.latencyInfo
                        val latencyMs = latency.inputLatencyMs
                        inputLatency = "${latencyMs.toInt()} ms"
                    } catch (e: Exception) {
                        isRecording = false
                        inputLatency = "Error: ${e.message}"
                    }
                }
            },
        ) {
            Text(if (isRecording) "Stop" else "Record")
        }

        val currentStream = resources.stream
        if (isRecording && currentStream != null) {
            LaunchedEffect(currentStream) {
                currentStream.levelFlow(intervalMs = 50L).collect { peakLevel ->
                    level = peakLevel
                }
            }
        }
    }
}
