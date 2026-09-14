package com.vectencia.klarinet.demo.web

import com.vectencia.klarinet.AudioAnalyzer
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.PermissionException
import com.vectencia.klarinet.StreamDirection
import com.vectencia.klarinet.StreamState
import com.vectencia.klarinet.coroutines.AnalyzingCallback
import com.vectencia.klarinet.coroutines.awaitState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

internal object MicScreen {
    fun mount(parent: HTMLElement, scope: CoroutineScope) {
        val screen = parent.el("section", "screen")
        screen.el("h2", text = "Mic Meter")
        screen.el("p", "status", "Uses getUserMedia. Allow the microphone when the browser asks.")

        val card = screen.el("div", "card")
        card.el("div", "label", "Level")
        val meter = card.el("div", "meter")
        val bar = meter.el("span")
        val peak = card.el("p", "status", "Peak: 0%")
        val latency = card.el("p", "status", "Input latency: --")
        val status = screen.el("p", "status", "State: stopped")

        var engine: AudioEngine? = null
        var stream: AudioStream? = null
        var analyzing: AnalyzingCallback? = null

        fun stop() {
            analyzing?.close()
            analyzing = null
            stream?.let { DemoSession.detach(it) }
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
            bar.style.width = "0%"
            meter.className = "meter"
        }

        val record = screen.button("Record", primary = true) {}
        record.onclick = {
            if (stream != null) {
                stop()
                record.textContent = "Record"
                status.textContent = "State: stopped"
                latency.textContent = "Input latency: --"
                peak.textContent = "Peak: 0%"
            } else {
                DemoSession.requestRecordPermission { granted ->
                    if (!granted) {
                        status.textContent = demoErrorMessage(PermissionException("Microphone permission denied"))
                        status.className = "status error"
                        return@requestRecordPermission
                    }
                    try {
                        val sampleRate = 48_000
                        val created = AudioEngine.create()
                        engine = created
                        val analyzer = AudioAnalyzer(fftSize = 1024, sampleRate = sampleRate)
                        val callback = AnalyzingCallback(
                            analyzer = analyzer,
                            delegate = object : AudioStreamCallback {
                                override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int = numFrames
                            },
                            scope = scope,
                        )
                        analyzing = callback
                        val opened = created.openStream(
                            AudioStreamConfig(
                                sampleRate = sampleRate,
                                direction = StreamDirection.INPUT,
                                channelCount = 1,
                            ),
                            callback,
                        )
                        stream = opened
                        opened.start()
                        DemoSession.attach(opened)
                        record.textContent = "Stop"
                        status.textContent = "State: STARTING (waiting for microphone)"
                        scope.launch {
                            opened.awaitState(StreamState.STARTED)
                            val ms = opened.latencyInfo.inputLatencyMs.toInt()
                            latency.textContent = "Input latency: $ms ms"
                            status.textContent = "State: STARTED"
                        }
                        scope.launch {
                            callback.results.collect { result ->
                                val clamped = result.peakLevel.coerceIn(0f, 1f)
                                bar.style.width = "${(clamped * 100).toInt()}%"
                                meter.className = when {
                                    clamped > 0.8f -> "meter clip"
                                    clamped > 0.5f -> "meter warn"
                                    else -> "meter"
                                }
                                peak.textContent =
                                    "Peak: ${(clamped * 100).toInt()}%  RMS ${formatDb(result.rmsDb)}"
                            }
                        }
                    } catch (error: Throwable) {
                        status.textContent = demoErrorMessage(error)
                        status.className = "status error"
                        stop()
                    }
                }
            }
        }

        scope.coroutineContext.job.invokeOnCompletion { stop() }
    }
}
