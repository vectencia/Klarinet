package com.vectencia.klarinet.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vectencia.klarinet.AudioAnalyzer
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.PermissionException
import com.vectencia.klarinet.StreamDirection
import com.vectencia.klarinet.coroutines.AnalyzingCallback
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MicMeterUiState(
    val isRecording: Boolean = false,
    val level: Float = 0f,
    val rmsDb: String = "--",
    val peakDb: String = "--",
    val lowBand: Float = 0f,
    val midBand: Float = 0f,
    val highBand: Float = 0f,
    val xruns: Int = 0,
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
    private var analyzing: AnalyzingCallback? = null
    private var analysisJob: Job? = null

    fun onEvent(event: MicMeterEvent) {
        when (event) {
            is MicMeterEvent.ToggleRecording -> {
                if (_uiState.value.isRecording) stop() else start()
            }
        }
    }

    private fun start() {
        DemoSession.requestRecordPermission { granted ->
            viewModelScope.launch {
                if (!granted) {
                    _uiState.update {
                        it.copy(inputLatency = demoErrorMessage(PermissionException("Microphone permission denied")))
                    }
                    return@launch
                }
                startUnlocked()
            }
        }
    }

    private fun startUnlocked() {
        try {
            val sampleRate = 48_000
            val newEngine = AudioEngine.create()
            engine = newEngine
            val analyzer = AudioAnalyzer(fftSize = 1024, sampleRate = sampleRate)
            val callback = AnalyzingCallback(
                analyzer = analyzer,
                delegate = object : AudioStreamCallback {
                    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int = numFrames
                    override fun onStreamUnderrun(stream: AudioStream, count: Int) {
                        _uiState.update { it.copy(xruns = count) }
                    }
                },
                scope = viewModelScope,
            )
            analyzing = callback

            val config = AudioStreamConfig(
                sampleRate = sampleRate,
                direction = StreamDirection.INPUT,
                channelCount = 1,
            )

            val newStream = newEngine.openStream(config, callback)
            stream = newStream
            newStream.start()
            DemoSession.attach(newStream)

            val latency = newStream.latencyInfo
            _uiState.update {
                it.copy(
                    isRecording = true,
                    inputLatency = "${latency.inputLatencyMs.toInt()} ms",
                    xruns = 0,
                )
            }

            analysisJob = viewModelScope.launch {
                callback.results.collect { result ->
                    _uiState.update {
                        it.copy(
                            level = result.peakLevel,
                            rmsDb = formatDb(result.rmsDb),
                            peakDb = formatDb(result.peakDb),
                            lowBand = analyzer.bandEnergy(result, 20f, 250f),
                            midBand = analyzer.bandEnergy(result, 250f, 4_000f),
                            highBand = analyzer.bandEnergy(result, 4_000f, 16_000f),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            stop()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    inputLatency = demoErrorMessage(e),
                )
            }
        }
    }

    private fun stop() {
        analysisJob?.cancel()
        analysisJob = null
        stream?.let { DemoSession.detach(it) }
        try {
            analyzing?.close()
        } catch (_: Exception) {}
        analyzing = null
        try {
            stream?.stop()
            stream?.close()
        } catch (_: Exception) {}
        try {
            engine?.release()
        } catch (_: Exception) {}
        stream = null
        engine = null
        _uiState.update {
            it.copy(
                isRecording = false,
                level = 0f,
                rmsDb = "--",
                peakDb = "--",
                lowBand = 0f,
                midBand = 0f,
                highBand = 0f,
                xruns = 0,
            )
        }
    }

    override fun onCleared() {
        stop()
    }
}
