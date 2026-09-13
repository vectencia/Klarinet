package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StreamCallbackBridgeTest {

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
}
