package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamUnderrunTest {

    @Test
    fun notifyXrunForwardsCumulativeCountToDelegate() {
        val stream = AudioStream(AudioStreamConfig())
        var seenStream: AudioStream? = null
        var seenCount = 0
        val delegate = object : AudioStreamCallback {
            override fun onStreamUnderrun(stream: AudioStream, count: Int) {
                seenStream = stream
                seenCount = count
            }
        }

        StreamCallbackBridge(stream, delegate).notifyXrun(4)

        assertSame(stream, seenStream)
        assertEquals(4, seenCount)
    }


    @Test
    fun emptyOutputCallbackFiresOnStreamUnderrun() {
        val counts = java.util.concurrent.CopyOnWriteArrayList<Int>()
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(
                config = AudioStreamConfig(
                    sampleRate = 48_000,
                    channelCount = 1,
                    direction = StreamDirection.OUTPUT,
                    bufferCapacityInFrames = 64,
                ),
                callback = object : AudioStreamCallback {
                    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                        Thread.sleep(1)
                        return 0
                    }

                    override fun onStreamUnderrun(stream: AudioStream, count: Int) {
                        counts.add(count)
                    }
                },
            )
            stream.use {
                stream.start()
                val deadline = System.nanoTime() + 3_000_000_000L
                while (System.nanoTime() < deadline && counts.isEmpty()) {
                    Thread.sleep(20)
                }
                stream.stop()
            }
        }
        assertTrue(counts.isNotEmpty(), "expected FIFO underrun callback, got $counts")
        assertTrue(counts.last() >= 1, "cumulative xrun count should be >= 1, got $counts")
    }
}
