package com.vectencia.klarinet.demo

import androidx.lifecycle.ViewModel
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioScene
import com.vectencia.klarinet.AudioSceneJson
import com.vectencia.klarinet.AudioScenePlayer
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.StreamDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sin

data class ScenesUiState(
    val fadeMs: Float = 2000f,
    val currentId: String? = null,
    val layerIds: List<String> = emptyList(),
    val json: String = "",
    val isPlaying: Boolean = false,
    val status: String = "",
)

sealed interface ScenesEvent {
    data class FadeMs(val value: Float) : ScenesEvent
    data class Select(val scene: AudioScene) : ScenesEvent
    data object Stop : ScenesEvent
}

class ScenesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ScenesUiState())
    val uiState: StateFlow<ScenesUiState> = _uiState.asStateFlow()

    private var engine: AudioEngine? = null
    private var player: AudioScenePlayer? = null
    private val attached = mutableListOf<AudioStream>()
    private val phases = mutableMapOf<String, Float>()

    fun onEvent(event: ScenesEvent) {
        when (event) {
            is ScenesEvent.FadeMs -> _uiState.update { it.copy(fadeMs = event.value) }
            is ScenesEvent.Select -> transition(event.scene)
            ScenesEvent.Stop -> stop()
        }
    }

    private fun ensurePlayer(): AudioScenePlayer {
        val existing = player
        if (existing != null) return existing
        val newEngine = AudioEngine.create()
        engine = newEngine
        val created = AudioScenePlayer(newEngine)
        player = created
        return created
    }

    private fun transition(scene: AudioScene) {
        try {
            val active = ensurePlayer()
            active.transitionTo(scene, _uiState.value.fadeMs) { layer ->
                openLayer(layer.id)
            }
            val current = active.current
            _uiState.update {
                it.copy(
                    currentId = current?.id,
                    layerIds = current?.layers?.map { layer -> layer.id } ?: emptyList(),
                    json = current?.let { AudioSceneJson.encode(it) } ?: "",
                    isPlaying = true,
                    status = "",
                )
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(status = error.message ?: "transition failed") }
        }
    }

    private fun openLayer(layerId: String): AudioStream {
        val hz = ScenePresets.hzFor(layerId)
        val sampleRate = 48_000
        val eng = engine ?: throw IllegalStateException("engine missing")
        val phaseKey = layerId
        phases.getOrPut(phaseKey) { 0f }
        val stream = eng.openStream(
            AudioStreamConfig(
                sampleRate = sampleRate,
                channelCount = 1,
                direction = StreamDirection.OUTPUT,
            ),
            object : AudioStreamCallback {
                override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                    val twoPi = (2.0 * kotlin.math.PI).toFloat()
                    val increment = (twoPi * hz / sampleRate).toFloat()
                    var phase = phases[phaseKey] ?: 0f
                    for (i in 0 until numFrames) {
                        buffer[i] = sin(phase.toDouble()).toFloat() * 0.2f
                        phase += increment
                        if (phase > twoPi) phase -= twoPi
                    }
                    phases[phaseKey] = phase
                    return numFrames
                }
            },
        )
        DemoSession.attach(stream)
        attached += stream
        return stream
    }

    private fun stop() {
        attached.toList().forEach { stream ->
            try { DemoSession.detach(stream) } catch (_: Exception) {}
        }
        attached.clear()
        try { player?.close() } catch (_: Exception) {}
        try { engine?.release() } catch (_: Exception) {}
        player = null
        engine = null
        phases.clear()
        _uiState.update {
            it.copy(isPlaying = false, currentId = null, layerIds = emptyList(), json = "", status = "")
        }
    }

    override fun onCleared() {
        stop()
    }
}
