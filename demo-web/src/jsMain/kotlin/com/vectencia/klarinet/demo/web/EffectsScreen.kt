package com.vectencia.klarinet.demo.web

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
import com.vectencia.klarinet.coroutines.levelFlow
import com.vectencia.klarinet.coroutines.stateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal object EffectsScreen {
    fun mount(parent: HTMLElement, scope: CoroutineScope) {
        val screen = parent.el("section", "screen")
        screen.el("h2", text = "Effects")
        screen.el("p", "status", "440 Hz sine through gain → delay → reverb. Tweak while it plays.")

        var gainDb = 0f
        var gainOn = true
        var delayTime = 250f
        var delayFeedback = 0.4f
        var delayMix = 0.3f
        var delayOn = true
        var room = 0.7f
        var damping = 0.5f
        var reverbMix = 0.3f
        var reverbOn = true

        var engine: AudioEngine? = null
        var stream: AudioStream? = null
        var chain: AudioEffectChain? = null
        var gain: AudioEffect? = null
        var delay: AudioEffect? = null
        var reverb: AudioEffect? = null
        val phase = floatArrayOf(0f)

        val status = screen.el("p", "status", "State: stopped")
        val meter = screen.el("div", "meter")
        val bar = meter.el("span")

        fun stop() {
            try {
                stream?.stop()
                stream?.close()
            } catch (_: Throwable) {
            }
            try {
                gain?.release()
                delay?.release()
                reverb?.release()
                chain?.release()
                engine?.release()
            } catch (_: Throwable) {
            }
            stream = null
            chain = null
            gain = null
            delay = null
            reverb = null
            engine = null
            bar.style.width = "0%"
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
                    val gainFx = created.createEffect(AudioEffectType.GAIN).also {
                        it.setParameter(GainParams.GAIN_DB, gainDb)
                        it.isEnabled = gainOn
                    }
                    val delayFx = created.createEffect(AudioEffectType.DELAY).also {
                        it.setParameter(DelayParams.TIME_MS, delayTime)
                        it.setParameter(DelayParams.FEEDBACK, delayFeedback)
                        it.setParameter(DelayParams.WET_DRY_MIX, delayMix)
                        it.isEnabled = delayOn
                    }
                    val reverbFx = created.createEffect(AudioEffectType.REVERB).also {
                        it.setParameter(ReverbParams.ROOM_SIZE, room)
                        it.setParameter(ReverbParams.DAMPING, damping)
                        it.setParameter(ReverbParams.WET_DRY_MIX, reverbMix)
                        it.isEnabled = reverbOn
                    }
                    gain = gainFx
                    delay = delayFx
                    reverb = reverbFx
                    val createdChain = created.createEffectChain()
                    createdChain.add(gainFx)
                    createdChain.add(delayFx)
                    createdChain.add(reverbFx)
                    chain = createdChain
                    val sampleRate = 48_000
                    val opened = created.openStream(
                        AudioStreamConfig(sampleRate = sampleRate, channelCount = 1),
                        object : AudioStreamCallback {
                            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                                val increment = (2.0 * PI * 440.0 / sampleRate).toFloat()
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
                    opened.effectChain = createdChain
                    stream = opened
                    opened.start()
                    play.textContent = "Stop"
                    scope.launch {
                        opened.stateFlow().collect { state ->
                            status.textContent = "State: $state"
                        }
                    }
                    scope.launch {
                        opened.levelFlow(50L).collect { level ->
                            val clamped = level.coerceIn(0f, 1f)
                            bar.style.width = "${(clamped * 100).toInt()}%"
                            meter.className = when {
                                clamped > 0.8f -> "meter clip"
                                clamped > 0.5f -> "meter warn"
                                else -> "meter"
                            }
                        }
                    }
                } catch (error: Throwable) {
                    status.textContent = "Error: ${error.message}"
                    status.className = "status error"
                    stop()
                }
            }
        }

        effectCard(screen, "Gain", gainOn) { enabled ->
            gainOn = enabled
            gain?.isEnabled = enabled
        }.also { body ->
            param(body, "Volume", -24.0, 12.0, 0.5, gainDb.toDouble(), { "${it.roundToInt()} dB" }) { value ->
                gainDb = value.toFloat()
                gain?.setParameter(GainParams.GAIN_DB, gainDb)
            }
        }

        effectCard(screen, "Delay", delayOn) { enabled ->
            delayOn = enabled
            delay?.isEnabled = enabled
        }.also { body ->
            param(body, "Time", 10.0, 1000.0, 1.0, delayTime.toDouble(), { "${it.roundToInt()} ms" }) { value ->
                delayTime = value.toFloat()
                delay?.setParameter(DelayParams.TIME_MS, delayTime)
            }
            param(body, "Feedback", 0.0, 0.95, 0.01, delayFeedback.toDouble(), { formatMix(it) }) { value ->
                delayFeedback = value.toFloat()
                delay?.setParameter(DelayParams.FEEDBACK, delayFeedback)
            }
            param(body, "Mix", 0.0, 1.0, 0.01, delayMix.toDouble(), { formatMix(it) }) { value ->
                delayMix = value.toFloat()
                delay?.setParameter(DelayParams.WET_DRY_MIX, delayMix)
            }
        }

        effectCard(screen, "Reverb", reverbOn) { enabled ->
            reverbOn = enabled
            reverb?.isEnabled = enabled
        }.also { body ->
            param(body, "Room size", 0.0, 1.0, 0.01, room.toDouble(), { formatMix(it) }) { value ->
                room = value.toFloat()
                reverb?.setParameter(ReverbParams.ROOM_SIZE, room)
            }
            param(body, "Damping", 0.0, 1.0, 0.01, damping.toDouble(), { formatMix(it) }) { value ->
                damping = value.toFloat()
                reverb?.setParameter(ReverbParams.DAMPING, damping)
            }
            param(body, "Mix", 0.0, 1.0, 0.01, reverbMix.toDouble(), { formatMix(it) }) { value ->
                reverbMix = value.toFloat()
                reverb?.setParameter(ReverbParams.WET_DRY_MIX, reverbMix)
            }
        }

        scope.coroutineContext.job.invokeOnCompletion { stop() }
    }

    private fun effectCard(
        parent: HTMLElement,
        name: String,
        enabled: Boolean,
        onEnabled: (Boolean) -> Unit,
    ): HTMLElement {
        val card = parent.el("div", "card")
        val header = card.el("div", "row")
        header.el("strong", text = name)
        val toggle = header.el("label", "switch")
        toggle.el("span", "label", "On")
        toggle.checkbox(enabled, onEnabled)
        return card
    }

    private fun param(
        parent: HTMLElement,
        label: String,
        min: Double,
        max: Double,
        step: Double,
        value: Double,
        format: (Double) -> String,
        onChange: (Double) -> Unit,
    ) {
        val row = parent.el("div", "slider-row")
        row.el("span", "label", label)
        val valueLabel = kotlinx.browser.document.createElement("span") as HTMLElement
        valueLabel.textContent = format(value)
        row.slider(min, max, step, value) { next ->
            valueLabel.textContent = format(next)
            onChange(next)
        }
        row.appendChild(valueLabel)
    }

    private fun formatMix(value: Double): String = ((value * 100).toInt() / 100.0).toString()
}
