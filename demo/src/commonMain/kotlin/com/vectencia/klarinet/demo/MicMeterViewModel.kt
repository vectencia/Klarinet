package com.vectencia.klarinet.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.StreamDirection
import com.vectencia.klarinet.coroutines.levelFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MicMeterUiState(
    val isRecording: Boolean = false,
    val level: Float = 0f,
    val inputLatency: String = "--",
)

sealed interface MicMeterEvent {
    data object ToggleRecording : MicMeterEvent
}

class MicMeterViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MicMeterUiState())
    val uiState: StateFlow<MicMeterUiState> = _uiState.asStateFlow()

    private var engine: AudioEngine? = null
    private var stream: AudioStream? = null

    fun onEvent(event: MicMeterEvent) {
        when (event) {
            is MicMeterEvent.ToggleRecording -> {
                if (_uiState.value.isRecording) stop() else start()
            }
        }
    }

    private fun start() {
        try {
            val newEngine = AudioEngine.create()
            engine = newEngine

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
            stream = newStream
            newStream.start()

            val latency = newStream.latencyInfo
            _uiState.update {
                it.copy(
                    isRecording = true,
                    inputLatency = "${latency.inputLatencyMs.toInt()} ms",
                )
            }

            viewModelScope.launch {
                newStream.levelFlow(intervalMs = 50L).collect { peakLevel ->
                    _uiState.update { it.copy(level = peakLevel) }
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isRecording = false,
                    inputLatency = "Error: ${e.message}",
                )
            }
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
        _uiState.update { it.copy(isRecording = false, level = 0f) }
    }

    override fun onCleared() {
        stop()
    }
}
