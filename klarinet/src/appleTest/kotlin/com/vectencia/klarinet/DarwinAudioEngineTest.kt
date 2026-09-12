package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun unknownDeviceIdIsRejected() {
        val engine = AudioEngine.create()
        assertFailsWith<DeviceNotFoundException> {
            engine.openStream(AudioStreamConfig(deviceId = 1_000_000))
        }
        engine.release()
    }

    @Test
    fun listedOutputDeviceCanBeOpened() {
        val engine = AudioEngine.create()
        val output = engine.getAvailableDevices().firstOrNull { it.isOutput }
        if (output != null) {
            val stream = engine.openStream(AudioStreamConfig(deviceId = output.id))
            assertEquals(StreamState.OPEN, stream.state)
            stream.close()
        }
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
        manager.observeInterruptions { }
        manager.setActive(false)
    }
}
