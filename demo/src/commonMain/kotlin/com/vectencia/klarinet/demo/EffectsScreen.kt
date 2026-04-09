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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vectencia.klarinet.AudioEffect
import com.vectencia.klarinet.AudioEffectChain
import com.vectencia.klarinet.AudioEffectType
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.DelayParams
import com.vectencia.klarinet.GainParams
import com.vectencia.klarinet.ReverbParams
import com.vectencia.klarinet.StreamState
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun EffectsScreen() {
    var isPlaying by remember { mutableStateOf(false) }
    var streamState by remember { mutableStateOf(StreamState.UNINITIALIZED) }
    var engine by remember { mutableStateOf<AudioEngine?>(null) }
    var stream by remember { mutableStateOf<AudioStream?>(null) }
    var effectChain by remember { mutableStateOf<AudioEffectChain?>(null) }

    var gainEffect by remember { mutableStateOf<AudioEffect?>(null) }
    var delayEffect by remember { mutableStateOf<AudioEffect?>(null) }
    var reverbEffect by remember { mutableStateOf<AudioEffect?>(null) }

    // Effect parameters as Compose state
    var gainDb by remember { mutableStateOf(0f) }
    var gainEnabled by remember { mutableStateOf(true) }

    var delayTimeMs by remember { mutableStateOf(250f) }
    var delayFeedback by remember { mutableStateOf(0.4f) }
    var delayMix by remember { mutableStateOf(0.3f) }
    var delayEnabled by remember { mutableStateOf(true) }

    var reverbRoomSize by remember { mutableStateOf(0.7f) }
    var reverbDamping by remember { mutableStateOf(0.5f) }
    var reverbMix by remember { mutableStateOf(0.3f) }
    var reverbEnabled by remember { mutableStateOf(true) }

    val phase = remember { floatArrayOf(0f) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                stream?.stop()
                stream?.close()
            } catch (_: Exception) {}
            try {
                gainEffect?.release()
                delayEffect?.release()
                reverbEffect?.release()
            } catch (_: Exception) {}
            try {
                effectChain?.release()
            } catch (_: Exception) {}
            try {
                engine?.release()
            } catch (_: Exception) {}
        }
    }

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
            onClick = {
                if (isPlaying) {
                    try {
                        stream?.stop()
                        stream?.close()
                    } catch (_: Exception) {}
                    try {
                        gainEffect?.release()
                        delayEffect?.release()
                        reverbEffect?.release()
                    } catch (_: Exception) {}
                    try {
                        effectChain?.release()
                    } catch (_: Exception) {}
                    try {
                        engine?.release()
                    } catch (_: Exception) {}
                    stream = null
                    effectChain = null
                    gainEffect = null
                    delayEffect = null
                    reverbEffect = null
                    engine = null
                    isPlaying = false
                    streamState = StreamState.STOPPED
                } else {
                    try {
                        val sampleRate = 48000
                        val newEngine = AudioEngine.create()
                        engine = newEngine

                        // Create effects
                        val gain = newEngine.createEffect(AudioEffectType.GAIN)
                        gain.setParameter(GainParams.GAIN_DB, gainDb)
                        gain.isEnabled = gainEnabled
                        gainEffect = gain

                        val delay = newEngine.createEffect(AudioEffectType.DELAY)
                        delay.setParameter(DelayParams.TIME_MS, delayTimeMs)
                        delay.setParameter(DelayParams.FEEDBACK, delayFeedback)
                        delay.setParameter(DelayParams.WET_DRY_MIX, delayMix)
                        delay.isEnabled = delayEnabled
                        delayEffect = delay

                        val reverb = newEngine.createEffect(AudioEffectType.REVERB)
                        reverb.setParameter(ReverbParams.ROOM_SIZE, reverbRoomSize)
                        reverb.setParameter(ReverbParams.DAMPING, reverbDamping)
                        reverb.setParameter(ReverbParams.WET_DRY_MIX, reverbMix)
                        reverb.isEnabled = reverbEnabled
                        reverbEffect = reverb

                        // Build effect chain
                        val chain = newEngine.createEffectChain()
                        chain.add(gain)
                        chain.add(delay)
                        chain.add(reverb)
                        effectChain = chain

                        // Tone generator callback (440 Hz sine wave)
                        val callback = object : AudioStreamCallback {
                            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                                val twoPi = 2.0 * kotlin.math.PI
                                val frequency = 440.0
                                val increment = (twoPi * frequency / sampleRate).toFloat()
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
                        newStream.effectChain = chain
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

        Spacer(modifier = Modifier.height(8.dp))

        Text("State: $streamState", fontSize = 14.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // -- Gain Effect --
        EffectCard(
            name = "Gain",
            enabled = gainEnabled,
            onEnabledChange = { enabled ->
                gainEnabled = enabled
                gainEffect?.isEnabled = enabled
            },
        ) {
            ParameterSlider(
                label = "Volume",
                value = gainDb,
                valueRange = -24f..12f,
                valueFormat = { "${it.roundToInt()} dB" },
                onValueChange = { value ->
                    gainDb = value
                    gainEffect?.setParameter(GainParams.GAIN_DB, value)
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -- Delay Effect --
        EffectCard(
            name = "Delay",
            enabled = delayEnabled,
            onEnabledChange = { enabled ->
                delayEnabled = enabled
                delayEffect?.isEnabled = enabled
            },
        ) {
            ParameterSlider(
                label = "Time",
                value = delayTimeMs,
                valueRange = 10f..1000f,
                valueFormat = { "${it.roundToInt()} ms" },
                onValueChange = { value ->
                    delayTimeMs = value
                    delayEffect?.setParameter(DelayParams.TIME_MS, value)
                },
            )
            ParameterSlider(
                label = "Feedback",
                value = delayFeedback,
                valueRange = 0f..0.95f,
                valueFormat = { "%.2f".format(it) },
                onValueChange = { value ->
                    delayFeedback = value
                    delayEffect?.setParameter(DelayParams.FEEDBACK, value)
                },
            )
            ParameterSlider(
                label = "Mix",
                value = delayMix,
                valueRange = 0f..1f,
                valueFormat = { "%.2f".format(it) },
                onValueChange = { value ->
                    delayMix = value
                    delayEffect?.setParameter(DelayParams.WET_DRY_MIX, value)
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -- Reverb Effect --
        EffectCard(
            name = "Reverb",
            enabled = reverbEnabled,
            onEnabledChange = { enabled ->
                reverbEnabled = enabled
                reverbEffect?.isEnabled = enabled
            },
        ) {
            ParameterSlider(
                label = "Room Size",
                value = reverbRoomSize,
                valueRange = 0f..1f,
                valueFormat = { "%.2f".format(it) },
                onValueChange = { value ->
                    reverbRoomSize = value
                    reverbEffect?.setParameter(ReverbParams.ROOM_SIZE, value)
                },
            )
            ParameterSlider(
                label = "Damping",
                value = reverbDamping,
                valueRange = 0f..1f,
                valueFormat = { "%.2f".format(it) },
                onValueChange = { value ->
                    reverbDamping = value
                    reverbEffect?.setParameter(ReverbParams.DAMPING, value)
                },
            )
            ParameterSlider(
                label = "Mix",
                value = reverbMix,
                valueRange = 0f..1f,
                valueFormat = { "%.2f".format(it) },
                onValueChange = { value ->
                    reverbMix = value
                    reverbEffect?.setParameter(ReverbParams.WET_DRY_MIX, value)
                },
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
