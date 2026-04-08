package com.vectencia.koboe.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Slider
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
import com.vectencia.koboe.AudioEngine
import com.vectencia.koboe.AudioStream
import com.vectencia.koboe.AudioStreamCallback
import com.vectencia.koboe.AudioStreamConfig
import com.vectencia.koboe.StreamState
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ToneGeneratorScreen() {
    var frequency by remember { mutableStateOf(440f) }
    var isPlaying by remember { mutableStateOf(false) }
    var streamState by remember { mutableStateOf(StreamState.UNINITIALIZED) }
    var engine by remember { mutableStateOf<AudioEngine?>(null) }
    var stream by remember { mutableStateOf<AudioStream?>(null) }

    // Phase accumulator shared with the callback — must be mutable outside the callback
    val phase = remember { floatArrayOf(0f) }

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
        Text("Tone Generator", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Frequency: ${frequency.roundToInt()} Hz", fontSize = 18.sp)

                Slider(
                    value = frequency,
                    onValueChange = { frequency = it },
                    valueRange = 220f..880f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isPlaying) {
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
                    isPlaying = false
                    streamState = StreamState.STOPPED
                } else {
                    // Play
                    try {
                        val sampleRate = 48000
                        val newEngine = AudioEngine.create()
                        engine = newEngine

                        val callback = object : AudioStreamCallback {
                            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                                val twoPi = 2.0 * kotlin.math.PI
                                val currentFreq = frequency
                                val increment = (twoPi * currentFreq / sampleRate).toFloat()
                                for (i in 0 until numFrames) {
                                    buffer[i] = sin(phase[0].toDouble()).toFloat() * 0.5f
                                    phase[0] += increment
                                    if (phase[0] > twoPi.toFloat()) {
                                        phase[0] -= twoPi.toFloat()
                                    }
                                }
                                return numFrames
                            }
                        }

                        val config = AudioStreamConfig(
                            sampleRate = sampleRate,
                            channelCount = 1,
                        )

                        val newStream = newEngine.openStream(config, callback)
                        stream = newStream
                        newStream.start()
                        isPlaying = true
                        streamState = newStream.state
                    } catch (e: Exception) {
                        streamState = StreamState.UNINITIALIZED
                        isPlaying = false
                    }
                }
            },
        ) {
            Text(if (isPlaying) "Stop" else "Play")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("State: $streamState", fontSize = 14.sp)
    }
}
