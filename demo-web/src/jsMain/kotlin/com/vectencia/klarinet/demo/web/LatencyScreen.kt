package com.vectencia.klarinet.demo.web

import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.StreamState
import com.vectencia.klarinet.coroutines.awaitState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

internal object LatencyScreen {
    fun mount(parent: HTMLElement, scope: CoroutineScope) {
        val screen = parent.el("section", "screen")
        screen.el("h2", text = "Latency Info")

        val card = screen.el("div", "card")
        val output = row(card, "Output latency")
        val input = row(card, "Input latency")
        val rate = row(card, "Sample rate")
        val buffer = row(card, "Buffer size")
        val mode = row(card, "Performance mode")
        val xruns = row(card, "Xruns")

        val devices = screen.el("div", "card")
        devices.el("strong", text = "Devices")
        val deviceList = devices.el("div", "status", "Not queried yet")

        val status = screen.el("p", "status", "Idle")
        var engine: AudioEngine? = null
        var stream: AudioStream? = null

        fun stop() {
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
        }

        val measure = screen.button("Measure", primary = true) {}
        measure.onclick = {
            if (stream != null) {
                stop()
                measure.textContent = "Measure"
                status.textContent = "Idle"
            } else {
                scope.launch {
                    try {
                        val created = AudioEngine.create()
                        engine = created
                        deviceList.textContent = created.getAvailableDevices().joinToString("\n") { device ->
                            val kind = when {
                                device.isInput && device.isOutput -> "in/out"
                                device.isInput -> "input"
                                else -> "output"
                            }
                            "${device.name} ($kind)"
                        }.ifBlank { "No devices reported yet" }
                        val opened = created.openStream(
                            AudioStreamConfig(),
                            object : AudioStreamCallback {
                                override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int = numFrames
                                override fun onStreamUnderrun(stream: AudioStream, count: Int) {
                                    xruns.textContent = "$count"
                                }
                            },
                        )
                        stream = opened
                        opened.start()
                        DemoSession.attach(opened)
                        measure.textContent = "Stop"
                        status.textContent = "Waiting for STARTED…"
                        opened.awaitState(StreamState.STARTED)
                        val latency = opened.latencyInfo
                        val config = opened.config
                        output.textContent = "${latency.outputLatencyMs.toInt()} ms"
                        input.textContent = "${latency.inputLatencyMs.toInt()} ms"
                        rate.textContent = "${config.sampleRate} Hz"
                        buffer.textContent = if (config.bufferCapacityInFrames > 0) {
                            "${config.bufferCapacityInFrames} frames"
                        } else {
                            "Platform default"
                        }
                        mode.textContent = config.performanceMode.name
                        xruns.textContent = "0"
                        status.textContent = "Measuring"
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

    private fun row(parent: HTMLElement, label: String): HTMLElement {
        val row = parent.el("div", "row")
        row.el("span", "label", label)
        return row.el("span", text = "--")
    }
}
