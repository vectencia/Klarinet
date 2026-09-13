package com.vectencia.klarinet.demo.web

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
import com.vectencia.klarinet.coroutines.awaitState
import com.vectencia.klarinet.coroutines.remainingMsFlow
import com.vectencia.klarinet.coroutines.stateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
        var chain: AudioEffectChain? = null
        var gain: AudioEffect? = null
        var sleepTimer: SleepTimer? = null
        var sleepCollectJob: Job? = null
        var sleepDurationMs = 5_000L
        var sleepFadeMs = 1_000f
        var sleepState = SleepTimerState.IDLE
        var sleepRemainingMs = 0L
        val phase = floatArrayOf(0f)

        val play = screen.button("Play", primary = true) {}

        val durationRow = screen.el("div", "actions")
        durationRow.button("5 s") { sleepDurationMs = 5_000L }
        durationRow.button("10 s") { sleepDurationMs = 10_000L }
        durationRow.button("30 s") { sleepDurationMs = 30_000L }

        val fadeRow = screen.el("div", "actions")
        fadeRow.button("0.5 s") { sleepFadeMs = 500f }
        fadeRow.button("1 s") { sleepFadeMs = 1_000f }
        fadeRow.button("2 s") { sleepFadeMs = 2_000f }

        val sleepRow = screen.el("div", "actions")
        val schedule = sleepRow.button("Schedule") {
            if (stream == null) return@button
            sleepTimer?.schedule(sleepDurationMs, sleepFadeMs)
        }
        val pause = sleepRow.button("Pause") {
            if (stream == null) return@button
            sleepTimer?.pause(pauseStreams = false)
        }
        val resume = sleepRow.button("Resume") {
            if (stream == null) return@button
            sleepTimer?.resume()
        }
        val cancel = sleepRow.button("Cancel") {
            if (stream == null) return@button
            sleepTimer?.cancel()
        }
        val timerStatus = screen.el("p", "status", "Timer: IDLE remaining 0 ms")

        fun renderTimer() {
            timerStatus.textContent = "Timer: $sleepState remaining $sleepRemainingMs ms"
        }

        fun setSleepActionsEnabled(enabled: Boolean) {
            schedule.disabled = !enabled
            pause.disabled = !enabled
            resume.disabled = !enabled
            cancel.disabled = !enabled
        }
        setSleepActionsEnabled(false)

        fun stop(keepCompleted: Boolean = false) {
            sleepCollectJob?.cancel()
            sleepCollectJob = null
            try {
                sleepTimer?.close()
            } catch (_: Throwable) {
            }
            sleepTimer = null
            stream?.let { DemoSession.detach(it) }
            try {
                stream?.stop()
                stream?.close()
            } catch (_: Throwable) {
            }
            try {
                chain?.close()
            } catch (_: Throwable) {
            }
            try {
                gain?.close()
            } catch (_: Throwable) {
            }
            try {
                engine?.release()
            } catch (_: Throwable) {
            }
            stream = null
            chain = null
            gain = null
            engine = null
            setSleepActionsEnabled(false)
            if (keepCompleted) {
                sleepState = SleepTimerState.COMPLETED
                sleepRemainingMs = 0L
            } else {
                sleepState = SleepTimerState.IDLE
                sleepRemainingMs = 0L
            }
            renderTimer()
        }

        fun collectSleep(timer: SleepTimer) {
            sleepCollectJob?.cancel()
            sleepCollectJob = scope.launch {
                launch {
                    timer.stateFlow().collect { state ->
                        sleepState = state
                        renderTimer()
                    }
                }
                launch {
                    timer.remainingMsFlow(200).collect { remaining ->
                        sleepRemainingMs = remaining
                        renderTimer()
                    }
                }
                timer.awaitState(SleepTimerState.COMPLETED)
                stop(keepCompleted = true)
                play.textContent = "Play"
            }
        }

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
                    val gainFx = created.createEffect(AudioEffectType.GAIN)
                    gainFx.setParameter(GainParams.GAIN_DB, 0f)
                    gain = gainFx
                    val createdChain = created.createEffectChain()
                    createdChain.add(gainFx)
                    chain = createdChain
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
                    opened.effectChain = createdChain
                    stream = opened
                    opened.start()
                    DemoSession.attach(opened)
                    val timer = SleepTimer(opened, gainFx)
                    sleepTimer = timer
                    sleepState = SleepTimerState.IDLE
                    sleepRemainingMs = 0L
                    renderTimer()
                    setSleepActionsEnabled(true)
                    play.textContent = "Stop"
                    collectSleep(timer)
                    scope.launch {
                        opened.stateFlow().collect { state ->
                            status.textContent = "State: $state"
                        }
                    }
                } catch (error: Throwable) {
                    status.textContent = "Error: ${error.message}"
                    status.className = "status error"
                    stop()
                    play.textContent = "Play"
                }
            }
        }

        scope.coroutineContext.job.invokeOnCompletion { stop() }
    }
}
