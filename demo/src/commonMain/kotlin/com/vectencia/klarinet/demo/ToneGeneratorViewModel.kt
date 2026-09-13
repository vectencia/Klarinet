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
import com.vectencia.klarinet.GainParams
import com.vectencia.klarinet.SleepTimer
import com.vectencia.klarinet.SleepTimerState
import com.vectencia.klarinet.StreamState
import com.vectencia.klarinet.coroutines.awaitState
import com.vectencia.klarinet.coroutines.remainingMsFlow
import com.vectencia.klarinet.coroutines.stateFlow
import kotlinx.coroutines.Job
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
    val sleepState: SleepTimerState = SleepTimerState.IDLE,
    val sleepRemainingMs: Long = 0L,
    val sleepDurationMs: Long = 5_000L,
    val sleepFadeMs: Float = 1_000f,
)

sealed interface ToneGeneratorEvent {
    data class FrequencyChanged(val value: Float) : ToneGeneratorEvent
    data object TogglePlayback : ToneGeneratorEvent
    data class SleepDuration(val durationMs: Long) : ToneGeneratorEvent
    data class SleepFade(val fadeMs: Float) : ToneGeneratorEvent
    data object ScheduleSleep : ToneGeneratorEvent
    data object PauseSleep : ToneGeneratorEvent
    data object ResumeSleep : ToneGeneratorEvent
    data object CancelSleep : ToneGeneratorEvent
}

class ToneGeneratorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ToneGeneratorUiState())
    val uiState: StateFlow<ToneGeneratorUiState> = _uiState.asStateFlow()

    private var engine: AudioEngine? = null
    private var stream: AudioStream? = null
    private var chain: AudioEffectChain? = null
    private var gain: AudioEffect? = null
    private var sleepTimer: SleepTimer? = null
    private var sleepCollectJob: Job? = null
    private val phase = floatArrayOf(0f)

    fun onEvent(event: ToneGeneratorEvent) {
        when (event) {
            is ToneGeneratorEvent.FrequencyChanged -> {
                _uiState.update { it.copy(frequency = event.value) }
            }
            is ToneGeneratorEvent.TogglePlayback -> {
                if (_uiState.value.isPlaying) stop() else play()
            }
            is ToneGeneratorEvent.SleepDuration -> {
                _uiState.update { it.copy(sleepDurationMs = event.durationMs) }
            }
            is ToneGeneratorEvent.SleepFade -> {
                _uiState.update { it.copy(sleepFadeMs = event.fadeMs) }
            }
            is ToneGeneratorEvent.ScheduleSleep -> {
                if (!_uiState.value.isPlaying) return
                val state = _uiState.value
                sleepTimer?.schedule(state.sleepDurationMs, state.sleepFadeMs)
            }
            is ToneGeneratorEvent.PauseSleep -> {
                if (!_uiState.value.isPlaying) return
                sleepTimer?.pause()
            }
            is ToneGeneratorEvent.ResumeSleep -> {
                if (!_uiState.value.isPlaying) return
                sleepTimer?.resume()
            }
            is ToneGeneratorEvent.CancelSleep -> {
                if (!_uiState.value.isPlaying) return
                sleepTimer?.cancel()
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

            val newGain = newEngine.createEffect(AudioEffectType.GAIN)
            newGain.setParameter(GainParams.GAIN_DB, 0f)
            gain = newGain
            val newChain = newEngine.createEffectChain()
            newChain.add(newGain)
            chain = newChain
            val newStream = newEngine.openStream(config, callback)
            newStream.effectChain = newChain
            stream = newStream
            newStream.start()
            DemoSession.attach(newStream)
            val timer = SleepTimer(newStream, newGain)
            sleepTimer = timer
            _uiState.update {
                it.copy(isPlaying = true, sleepState = SleepTimerState.IDLE, sleepRemainingMs = 0L)
            }
            collectSleep(timer)

            viewModelScope.launch {
                newStream.stateFlow().collect { newState ->
                    _uiState.update { it.copy(streamState = newState) }
                }
            }
        } catch (e: Exception) {
            stop()
            _uiState.update { it.copy(isPlaying = false, streamState = StreamState.UNINITIALIZED) }
        }
    }

    private fun stop(keepCompleted: Boolean = false) {
        sleepCollectJob?.cancel()
        sleepCollectJob = null
        try { sleepTimer?.close() } catch (_: Exception) {}
        sleepTimer = null
        stream?.let { DemoSession.detach(it) }
        try {
            stream?.stop()
            stream?.close()
        } catch (_: Exception) {}
        try { chain?.close() } catch (_: Exception) {}
        try { gain?.close() } catch (_: Exception) {}
        try { engine?.release() } catch (_: Exception) {}
        stream = null
        chain = null
        gain = null
        engine = null
        _uiState.update {
            it.copy(
                isPlaying = false,
                sleepState = if (keepCompleted) SleepTimerState.COMPLETED else SleepTimerState.IDLE,
                sleepRemainingMs = 0L,
            )
        }
    }

    private fun collectSleep(timer: SleepTimer) {
        sleepCollectJob?.cancel()
        sleepCollectJob = viewModelScope.launch {
            launch {
                timer.stateFlow().collect { state ->
                    _uiState.update { it.copy(sleepState = state) }
                }
            }
            launch {
                timer.remainingMsFlow(intervalMs = 200).collect { remaining ->
                    _uiState.update { it.copy(sleepRemainingMs = remaining) }
                }
            }
            timer.awaitState(SleepTimerState.COMPLETED)
            stop(keepCompleted = true)
        }
    }

    override fun onCleared() {
        stop()
    }
}
