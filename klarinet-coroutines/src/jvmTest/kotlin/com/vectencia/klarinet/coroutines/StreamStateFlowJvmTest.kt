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
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamStateFlowJvmTest {

    @Test
    fun stateFlowTracksStartStopClose() = runBlocking {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(toneConfig(), silentCallback())
            val states = java.util.concurrent.CopyOnWriteArrayList<StreamState>()
            try {
                val job = launch {
                    stream.stateFlow(intervalMs = 10).collect { states += it }
                }
                withTimeout(1_000) {
                    while (states.isEmpty()) yield()
                }
                assertEquals(StreamState.OPEN, states.first())

                stream.start()
                withTimeout(2_000) { stream.awaitState(StreamState.STARTED) }
                withTimeout(2_000) {
                    while (StreamState.STARTED !in states) yield()
                }

                stream.stop()
                withTimeout(2_000) { stream.awaitState(StreamState.STOPPED) }
                withTimeout(2_000) {
                    while (StreamState.STOPPED !in states) yield()
                }

                stream.close()
                withTimeout(2_000) { job.join() }

                assertTrue(StreamState.STARTED in states, "states=$states")
                assertTrue(StreamState.STOPPED in states, "states=$states")
                assertEquals(StreamState.CLOSED, states.last())
            } finally {
                if (stream.state != StreamState.CLOSED) stream.close()
            }
        }
    }

    @Test
    fun awaitStateReturnsWhenAlreadyOpen() = runBlocking {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(toneConfig(), silentCallback())
            try {
                assertEquals(StreamState.OPEN, stream.state)
                withTimeout(1_000) { stream.awaitState(StreamState.OPEN) }
            } finally {
                stream.close()
            }
        }
    }

    private fun toneConfig() = AudioStreamConfig(
        sampleRate = 48_000,
        channelCount = 1,
        direction = StreamDirection.OUTPUT,
        performanceMode = PerformanceMode.LOW_LATENCY,
    )

    private fun silentCallback() = object : AudioStreamCallback {
        override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
            buffer.fill(0f, 0, numFrames)
            return numFrames
        }
    }
}
