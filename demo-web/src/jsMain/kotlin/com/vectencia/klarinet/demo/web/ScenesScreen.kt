package com.vectencia.klarinet.demo.web

import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioScene
import com.vectencia.klarinet.AudioSceneJson
import com.vectencia.klarinet.AudioScenePlayer
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.StreamDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.w3c.dom.HTMLElement
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal object ScenesScreen {
    fun mount(parent: HTMLElement, scope: CoroutineScope) {
        val screen = parent.el("section", "screen")
        screen.el("h2", text = "Scenes")

        var fadeMs = 2000f
        var engine: AudioEngine? = null
        var player: AudioScenePlayer? = null
        val attached = mutableListOf<AudioStream>()
        val phases = mutableMapOf<String, Float>()

        val card = screen.el("div", "card")
        val fadeRow = card.el("div", "row")
        val fadeValue = fadeRow.el("strong")
        fun renderFade() {
            fadeValue.textContent = "Fade: ${fadeMs.roundToInt()} ms"
        }
        renderFade()
        card.slider(0.0, 5000.0, 1.0, fadeMs.toDouble()) { value ->
            fadeMs = value.toFloat()
            renderFade()
        }

        val row1 = screen.el("div", "actions")
        val row2 = screen.el("div", "actions")
        val currentEl = screen.el("p", "status", "Current: ")
        val layersEl = screen.el("p", "status", "Layers: ")
        val statusEl = screen.el("p", "status")
        val jsonEl = screen.el("pre")

        fun render(currentId: String?, layerIds: List<String>, json: String, status: String) {
            currentEl.textContent = "Current: ${currentId ?: ""}"
            layersEl.textContent = "Layers: ${layerIds.joinToString()}"
            statusEl.textContent = status
            jsonEl.textContent = json
        }

        fun stop() {
            attached.toList().forEach { stream ->
                try {
                    DemoSession.detach(stream)
                } catch (_: Exception) {
                }
            }
            attached.clear()
            try {
                player?.close()
            } catch (_: Exception) {
            }
            try {
                engine?.release()
            } catch (_: Exception) {
            }
            player = null
            engine = null
            phases.clear()
            render(null, emptyList(), "", "")
        }

        fun ensurePlayer(): AudioScenePlayer {
            val existing = player
            if (existing != null) return existing
            val newEngine = AudioEngine.create()
            engine = newEngine
            val created = AudioScenePlayer(newEngine)
            player = created
            return created
        }

        fun openLayer(layerId: String): AudioStream {
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
                        val twoPi = (2.0 * PI).toFloat()
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

        fun transition(scene: AudioScene) {
            try {
                val active = ensurePlayer()
                active.transitionTo(scene, fadeMs) { layer ->
                    openLayer(layer.id)
                }
                val current = active.current
                render(
                    currentId = current?.id,
                    layerIds = current?.layers?.map { layer -> layer.id } ?: emptyList(),
                    json = current?.let { AudioSceneJson.encode(it) } ?: "",
                    status = "",
                )
            } catch (error: Exception) {
                statusEl.textContent = error.message ?: "transition failed"
            }
        }

        row1.button("Low") { transition(ScenePresets.low) }
        row1.button("High") { transition(ScenePresets.high) }
        row2.button("Stack") { transition(ScenePresets.stack) }
        row2.button("Low hall") { transition(ScenePresets.lowHall) }
        screen.button("Stop") { stop() }

        scope.coroutineContext.job.invokeOnCompletion { stop() }
    }
}
