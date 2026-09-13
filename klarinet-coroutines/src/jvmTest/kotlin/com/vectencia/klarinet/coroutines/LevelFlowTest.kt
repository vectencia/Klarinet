package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.PerformanceMode
import com.vectencia.klarinet.StreamDirection
import com.vectencia.klarinet.StreamState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelFlowTest {

    @Test
    fun levelFlowEmitsWhileStartedThenCompletesWithZero() = runBlocking {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(
                config = AudioStreamConfig(
                    sampleRate = 48_000,
                    channelCount = 1,
                    direction = StreamDirection.OUTPUT,
                    performanceMode = PerformanceMode.LOW_LATENCY,
                ),
                callback = object : AudioStreamCallback {
                    private var phase = 0.0
                    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                        var p = phase
                        val inc = 2.0 * PI * 440.0 / 48_000.0
                        for (i in 0 until numFrames) {
                            buffer[i] = (sin(p) * 0.5).toFloat()
                            p += inc
                        }
                        phase = p
                        return numFrames
                    }
                },
            )
            try {
                stream.start()
                withTimeout(2_000) { stream.awaitState(StreamState.STARTED) }

                val levels = java.util.concurrent.CopyOnWriteArrayList<Float>()
                val job = launch {
                    stream.levelFlow(intervalMs = 20).collect { levels += it }
                }
                withTimeout(2_000) {
                    while (levels.isEmpty()) kotlinx.coroutines.yield()
                }
                stream.stop()
                withTimeout(2_000) { job.join() }

                assertTrue(levels.size >= 2, "levels=$levels")
                assertEquals(0f, levels.last())
            } finally {
                if (stream.state == StreamState.STARTED || stream.state == StreamState.PAUSED) {
                    stream.stop()
                }
                stream.close()
            }
        }
    }

    @Test
    fun levelFlowCompletesWithZeroIfNeverStarted() = runBlocking {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            try {
                val levels = withTimeout(1_000) {
                    val out = mutableListOf<Float>()
                    stream.levelFlow(intervalMs = 10).collect { out += it }
                    out
                }
                assertEquals(listOf(0f), levels)
            } finally {
                stream.close()
            }
        }
    }
}
