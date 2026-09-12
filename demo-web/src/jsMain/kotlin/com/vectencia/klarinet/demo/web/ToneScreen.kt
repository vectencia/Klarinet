package com.vectencia.klarinet.demo.web

import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.coroutines.stateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal object ToneScreen {
    fun mount(parent: HTMLElement, scope: CoroutineScope) {
        val screen = parent.el("section", "screen")
        screen.el("h2", text = "Tone Generator")

        val card = screen.el("div", "card")
        val freqLabel = card.el("div", "row")
        val freqValue = freqLabel.el("strong")
        var frequency = 440.0
        fun renderFreq() {
            freqValue.textContent = "Frequency: ${frequency.roundToInt()} Hz"
        }
        renderFreq()
        card.slider(220.0, 880.0, 1.0, frequency) { value ->
            frequency = value
            renderFreq()
        }

        val status = screen.el("p", "status", "State: stopped")
        var engine: AudioEngine? = null
        var stream: AudioStream? = null
        val phase = floatArrayOf(0f)

        fun stop() {
            try {
                stream?.stop()
                stream?.close()
            } catch (_: Throwable) {
            }
            try {
                engine?.release()
            } catch (_: Throwable) {
            }
            stream = null
            engine = null
        }

        val play = screen.button("Play", primary = true) {}
        play.onclick = {
            if (stream != null) {
                stop()
                play.textContent = "Play"
                status.textContent = "State: stopped"
            } else {
                try {
                    val created = AudioEngine.create()
                    engine = created
                    val sampleRate = 48_000
                    val opened = created.openStream(
                        AudioStreamConfig(sampleRate = sampleRate, channelCount = 1),
                        object : AudioStreamCallback {
                            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                                val increment = (2.0 * PI * frequency / sampleRate).toFloat()
                                val twoPi = (2.0 * PI).toFloat()
                                for (i in 0 until numFrames) {
                                    buffer[i] = sin(phase[0].toDouble()).toFloat() * 0.5f
                                    phase[0] += increment
                                    if (phase[0] > twoPi) phase[0] -= twoPi
                                }
                                return numFrames
                            }
                        },
                    )
                    stream = opened
                    opened.start()
                    play.textContent = "Stop"
                    scope.launch {
                        opened.stateFlow().collect { state ->
                            status.textContent = "State: $state"
                        }
                    }
                } catch (error: Throwable) {
                    status.textContent = "Error: ${error.message}"
                    status.className = "status error"
                    stop()
                }
            }
        }

        scope.coroutineContext.job.invokeOnCompletion { stop() }
    }
}
