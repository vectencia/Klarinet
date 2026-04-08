package com.vectencia.klarinet.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.StreamState
import com.vectencia.klarinet.coroutines.stateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sin

data class ToneGeneratorUiState(
    val frequency: Float = 440f,
    val isPlaying: Boolean = false,
    val streamState: StreamState = StreamState.UNINITIALIZED,
)

sealed interface ToneGeneratorEvent {
    data class FrequencyChanged(val value: Float) : ToneGeneratorEvent
    data object TogglePlayback : ToneGeneratorEvent
}

class ToneGeneratorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ToneGeneratorUiState())
    val uiState: StateFlow<ToneGeneratorUiState> = _uiState.asStateFlow()

    private var engine: AudioEngine? = null
    private var stream: AudioStream? = null
    private val phase = floatArrayOf(0f)

    fun onEvent(event: ToneGeneratorEvent) {
        when (event) {
            is ToneGeneratorEvent.FrequencyChanged -> {
                _uiState.update { it.copy(frequency = event.value) }
            }
            is ToneGeneratorEvent.TogglePlayback -> {
                if (_uiState.value.isPlaying) stop() else play()
            }
        }
    }

    private fun play() {
        try {
            val sampleRate = 48000
            val newEngine = AudioEngine.create()
            engine = newEngine

            val callback = object : AudioStreamCallback {
                override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                    val twoPi = 2.0 * kotlin.math.PI
                    val currentFreq = _uiState.value.frequency
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
            _uiState.update { it.copy(isPlaying = true) }

            viewModelScope.launch {
                newStream.stateFlow.collect { newState ->
                    _uiState.update { it.copy(streamState = newState) }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isPlaying = false, streamState = StreamState.UNINITIALIZED) }
        }
    }

    private fun stop() {
        try {
            stream?.stop()
            stream?.close()
        } catch (_: Exception) {}
        try {
            engine?.release()
        } catch (_: Exception) {}
        stream = null
        engine = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    override fun onCleared() {
        stop()
    }
}
