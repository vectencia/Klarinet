package com.vectencia.koboe

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class AndroidAudioEngineTest {
    @Test
    fun createEngineSucceeds() {
        val engine = AudioEngine.create()
        assertNotNull(engine)
        engine.release()
    }

    @Test
    fun openOutputStreamSucceeds() {
        val engine = AudioEngine.create()
        val stream = engine.openStream(
            AudioStreamConfig(sampleRate = 48000, channelCount = 1, audioFormat = AudioFormat.PCM_FLOAT)
        )
        assertNotNull(stream)
        assertEquals(48000, stream.config.sampleRate)
        stream.close()
        engine.release()
    }

    @Test
    fun closedStreamReportsClosedState() {
        val engine = AudioEngine.create()
        val stream = engine.openStream(AudioStreamConfig())
        stream.close()
        assertEquals(StreamState.CLOSED, stream.state)
        engine.release()
    }
}
