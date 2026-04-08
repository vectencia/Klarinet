package com.vectencia.koboe

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioStreamCallbackTest {

    @Test
    fun defaultOnAudioReadyReturnsNumFrames() {
        val callback = object : AudioStreamCallback {}
        val buffer = FloatArray(256)
        assertEquals(128, callback.onAudioReady(buffer, 128))
        assertEquals(0, callback.onAudioReady(buffer, 0))
        assertEquals(256, callback.onAudioReady(buffer, 256))
    }

    @Test
    fun customOnAudioReadyOverridesDefault() {
        val callback = object : AudioStreamCallback {
            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                // Simulate producing half the requested frames
                return numFrames / 2
            }
        }
        val buffer = FloatArray(256)
        assertEquals(64, callback.onAudioReady(buffer, 128))
    }
}
