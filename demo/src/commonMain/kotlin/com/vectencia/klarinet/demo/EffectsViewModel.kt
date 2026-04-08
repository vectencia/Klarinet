package com.vectencia.klarinet.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.vectencia.klarinet.coroutines.levelFlow
import com.vectencia.klarinet.coroutines.stateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sin

data class EffectsUiState(
    val isPlaying: Boolean = false,
    val streamState: StreamState = StreamState.UNINITIALIZED,
    val outputLevel: Float = 0f,
    val gainEnabled: Boolean = true,
    val gainDb: Float = 0f,
    val delayEnabled: Boolean = true,
    val delayTimeMs: Float = 250f,
    val delayFeedback: Float = 0.4f,
    val delayMix: Float = 0.3f,
    val reverbEnabled: Boolean = true,
    val reverbRoomSize: Float = 0.7f,
    val reverbDamping: Float = 0.5f,
    val reverbMix: Float = 0.3f,
)

sealed interface EffectsEvent {
    data object TogglePlayback : EffectsEvent
    data class UpdateGainDb(val value: Float) : EffectsEvent
    data class UpdateGainEnabled(val enabled: Boolean) : EffectsEvent
    data class UpdateDelayTimeMs(val value: Float) : EffectsEvent
    data class UpdateDelayFeedback(val value: Float) : EffectsEvent
    data class UpdateDelayMix(val value: Float) : EffectsEvent
    data class UpdateDelayEnabled(val enabled: Boolean) : EffectsEvent
    data class UpdateReverbRoomSize(val value: Float) : EffectsEvent
    data class UpdateReverbDamping(val value: Float) : EffectsEvent
    data class UpdateReverbMix(val value: Float) : EffectsEvent
    data class UpdateReverbEnabled(val enabled: Boolean) : EffectsEvent
}

class EffectsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EffectsUiState())
    val uiState: StateFlow<EffectsUiState> = _uiState.asStateFlow()

    private var engine: AudioEngine? = null
    private var stream: AudioStream? = null
    private var chain: AudioEffectChain? = null
    private var gainEffect: AudioEffect? = null
    private var delayEffect: AudioEffect? = null
    private var reverbEffect: AudioEffect? = null
    private val phase = floatArrayOf(0f)

    fun onEvent(event: EffectsEvent) {
        when (event) {
            is EffectsEvent.TogglePlayback -> {
                if (_uiState.value.isPlaying) stop() else play()
            }
            is EffectsEvent.UpdateGainDb -> {
                _uiState.update { it.copy(gainDb = event.value) }
                gainEffect?.setParameter(GainParams.GAIN_DB, event.value)
            }
            is EffectsEvent.UpdateGainEnabled -> {
                _uiState.update { it.copy(gainEnabled = event.enabled) }
                gainEffect?.isEnabled = event.enabled
            }
            is EffectsEvent.UpdateDelayTimeMs -> {
                _uiState.update { it.copy(delayTimeMs = event.value) }
                delayEffect?.setParameter(DelayParams.TIME_MS, event.value)
            }
            is EffectsEvent.UpdateDelayFeedback -> {
                _uiState.update { it.copy(delayFeedback = event.value) }
                delayEffect?.setParameter(DelayParams.FEEDBACK, event.value)
            }
            is EffectsEvent.UpdateDelayMix -> {
                _uiState.update { it.copy(delayMix = event.value) }
                delayEffect?.setParameter(DelayParams.WET_DRY_MIX, event.value)
            }
            is EffectsEvent.UpdateDelayEnabled -> {
                _uiState.update { it.copy(delayEnabled = event.enabled) }
                delayEffect?.isEnabled = event.enabled
            }
            is EffectsEvent.UpdateReverbRoomSize -> {
                _uiState.update { it.copy(reverbRoomSize = event.value) }
                reverbEffect?.setParameter(ReverbParams.ROOM_SIZE, event.value)
            }
            is EffectsEvent.UpdateReverbDamping -> {
                _uiState.update { it.copy(reverbDamping = event.value) }
                reverbEffect?.setParameter(ReverbParams.DAMPING, event.value)
            }
            is EffectsEvent.UpdateReverbMix -> {
                _uiState.update { it.copy(reverbMix = event.value) }
                reverbEffect?.setParameter(ReverbParams.WET_DRY_MIX, event.value)
            }
            is EffectsEvent.UpdateReverbEnabled -> {
                _uiState.update { it.copy(reverbEnabled = event.enabled) }
                reverbEffect?.isEnabled = event.enabled
            }
        }
    }

    private fun play() {
        try {
            val sampleRate = 48000
            val newEngine = AudioEngine.create()
            engine = newEngine

            val currentState = _uiState.value

            // Create effects
            val gain = newEngine.createEffect(AudioEffectType.GAIN)
            gain.setParameter(GainParams.GAIN_DB, currentState.gainDb)
            gain.isEnabled = currentState.gainEnabled
            gainEffect = gain

            val delay = newEngine.createEffect(AudioEffectType.DELAY)
            delay.setParameter(DelayParams.TIME_MS, currentState.delayTimeMs)
            delay.setParameter(DelayParams.FEEDBACK, currentState.delayFeedback)
            delay.setParameter(DelayParams.WET_DRY_MIX, currentState.delayMix)
            delay.isEnabled = currentState.delayEnabled
            delayEffect = delay

            val reverb = newEngine.createEffect(AudioEffectType.REVERB)
            reverb.setParameter(ReverbParams.ROOM_SIZE, currentState.reverbRoomSize)
            reverb.setParameter(ReverbParams.DAMPING, currentState.reverbDamping)
            reverb.setParameter(ReverbParams.WET_DRY_MIX, currentState.reverbMix)
            reverb.isEnabled = currentState.reverbEnabled
            reverbEffect = reverb

            // Build effect chain
            val newChain = newEngine.createEffectChain()
            newChain.add(gain)
            newChain.add(delay)
            newChain.add(reverb)
            chain = newChain

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
            newStream.effectChain = newChain
            stream = newStream
            newStream.start()
            _uiState.update { it.copy(isPlaying = true) }

            viewModelScope.launch {
                launch {
                    newStream.stateFlow.collect { newState ->
                        _uiState.update { it.copy(streamState = newState) }
                    }
                }
                launch {
                    newStream.levelFlow(intervalMs = 50L).collect { level ->
                        _uiState.update { it.copy(outputLevel = level) }
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(streamState = StreamState.UNINITIALIZED, isPlaying = false) }
        }
    }

    private fun stop() {
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
            chain?.release()
        } catch (_: Exception) {}
        try {
            engine?.release()
        } catch (_: Exception) {}
        stream = null
        chain = null
        gainEffect = null
        delayEffect = null
        reverbEffect = null
        engine = null
        _uiState.update { it.copy(isPlaying = false, outputLevel = 0f) }
    }

    override fun onCleared() {
        stop()
    }
}
