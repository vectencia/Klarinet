package com.vectencia.koboe

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class DarwinAudioEngineTest {
    @Test
    fun createEngineSucceeds() {
        val engine = AudioEngine.create()
        assertNotNull(engine)
        engine.release()
    }

    @Test
    fun getAvailableDevicesReturnsResults() {
        val engine = AudioEngine.create()
        val devices = engine.getAvailableDevices()
        assertNotNull(devices)
        engine.release()
    }

    @Test
    fun openOutputStreamSucceeds() {
        val engine = AudioEngine.create()
        val stream = engine.openStream(
            AudioStreamConfig(sampleRate = 48000, channelCount = 1)
        )
        assertNotNull(stream)
        assertEquals(StreamState.OPEN, stream.state)
        stream.close()
        assertEquals(StreamState.CLOSED, stream.state)
        engine.release()
    }

    @Test
    fun audioSessionManagerConfigures() {
        val manager = AudioSessionManager()
        manager.configure(AudioSessionCategory.PLAYBACK, AudioSessionMode.DEFAULT)
        manager.setActive(true)
        manager.setActive(false)
    }
}
