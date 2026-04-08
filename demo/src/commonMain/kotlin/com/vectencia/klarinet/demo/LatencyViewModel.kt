package com.vectencia.klarinet.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.StreamState
import com.vectencia.klarinet.coroutines.awaitState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LatencyUiState(
    val isMeasuring: Boolean = false,
    val outputLatency: String = "--",
    val inputLatency: String = "--",
    val sampleRate: String = "--",
    val bufferSize: String = "--",
    val performanceMode: String = "--",
)

sealed interface LatencyEvent {
    data object ToggleMeasure : LatencyEvent
}

class LatencyViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LatencyUiState())
    val uiState: StateFlow<LatencyUiState> = _uiState.asStateFlow()

    private var engine: AudioEngine? = null
    private var stream: AudioStream? = null

    fun onEvent(event: LatencyEvent) {
        when (event) {
            is LatencyEvent.ToggleMeasure -> {
                if (_uiState.value.isMeasuring) stop() else measure()
            }
        }
    }

    private fun measure() {
        viewModelScope.launch {
            try {
                val newEngine = AudioEngine.create()
                engine = newEngine
                val config = AudioStreamConfig()
                val newStream = newEngine.openStream(config)
                stream = newStream
                newStream.start()
                _uiState.update { it.copy(isMeasuring = true) }

                // Wait for stream to be fully started before reading latency
                newStream.awaitState(StreamState.STARTED)

                val latency = newStream.latencyInfo
                val streamConfig = newStream.config

                _uiState.update {
                    it.copy(
                        outputLatency = "${latency.outputLatencyMs.toInt()} ms",
                        inputLatency = "${latency.inputLatencyMs.toInt()} ms",
                        sampleRate = "${streamConfig.sampleRate} Hz",
                        bufferSize = if (streamConfig.bufferCapacityInFrames > 0) {
                            "${streamConfig.bufferCapacityInFrames} frames"
                        } else {
                            "Platform default"
                        },
                        performanceMode = streamConfig.performanceMode.name,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isMeasuring = false,
                        outputLatency = "Error",
                        inputLatency = e.message ?: "Unknown error",
                    )
                }
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
        _uiState.update { it.copy(isMeasuring = false) }
    }

    override fun onCleared() {
        stop()
    }
}
