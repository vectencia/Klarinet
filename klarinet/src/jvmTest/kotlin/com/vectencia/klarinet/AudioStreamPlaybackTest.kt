package com.vectencia.klarinet

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioStreamPlaybackTest {

    @Test
    fun outputCallbackUpdatesPeakLevel() {
        AudioEngine.create().use { engine ->
            val amplitude = 0.5f
            val sampleRate = 48_000
            val stream = engine.openStream(
                config = AudioStreamConfig(
                    sampleRate = sampleRate,
                    channelCount = 1,
                    direction = StreamDirection.OUTPUT,
                    performanceMode = PerformanceMode.LOW_LATENCY,
                ),
                callback = object : AudioStreamCallback {
                    private var phase = 0.0
                    private val increment = 2.0 * PI * 440.0 / sampleRate

                    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                        var p = phase
                        val inc = increment
                        val amp = amplitude
                        for (i in 0 until numFrames) {
                            buffer[i] = (sin(p) * amp).toFloat()
                            p += inc
                        }
                        for (i in numFrames until buffer.size) {
                            buffer[i] = 0f
                        }
                        phase = p
                        return numFrames
                    }
                },
            )
            stream.use {
                assertEquals(StreamState.OPEN, stream.state)
                stream.start()
                assertEquals(StreamState.STARTED, stream.state)

                val deadline = System.nanoTime() + 2_000_000_000L
                while (System.nanoTime() < deadline && stream.peakLevel < 0.4f) {
                    Thread.sleep(20)
                }
                assertTrue(
                    stream.peakLevel >= 0.4f,
                    "Callback should push peakLevel near $amplitude, was ${stream.peakLevel}",
                )

                stream.pause()
                assertEquals(StreamState.PAUSED, stream.state)
                stream.start()
                assertEquals(StreamState.STARTED, stream.state)
                stream.stop()
                assertEquals(StreamState.STOPPED, stream.state)
            }
            assertEquals(StreamState.CLOSED, stream.state)
        }
    }
}
