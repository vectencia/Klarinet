package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AudioEngineLifecycleTest {

    @Test
    fun closeIsIdempotent() {
        val engine = AudioEngine.create()
        engine.close()
        engine.close()
    }

    @Test
    fun releasedEngineRejectsOpenStream() {
        val engine = AudioEngine.create()
        engine.release()
        assertFailsWith<ResourceReleasedException> {
            engine.openStream(AudioStreamConfig())
        }
    }

    @Test
    fun releasedEngineRejectsDeviceQueries() {
        val engine = AudioEngine.create()
        engine.close()
        assertFailsWith<ResourceReleasedException> {
            engine.getAvailableDevices()
        }
        assertFailsWith<ResourceReleasedException> {
            engine.getDefaultDevice(StreamDirection.OUTPUT)
        }
    }

    @Test
    fun releasedEngineRejectsEffectCreation() {
        val engine = AudioEngine.create()
        engine.close()
        assertFailsWith<ResourceReleasedException> {
            engine.createEffect(AudioEffectType.GAIN)
        }
        assertFailsWith<ResourceReleasedException> {
            engine.createEffectChain()
        }
    }

    @Test
    fun engineUseClosesAutomatically() {
        lateinit var engine: AudioEngine
        AudioEngine.create().use { created ->
            engine = created
        }
        assertFailsWith<ResourceReleasedException> {
            engine.getAvailableDevices()
        }
    }

    @Test
    fun closedStreamRejectsStart() {
        val engine = AudioEngine.create()
        val stream = engine.openStream(AudioStreamConfig())
        stream.close()
        assertFailsWith<ResourceReleasedException> {
            stream.start()
        }
        engine.close()
    }

    @Test
    fun unknownDeviceIdIsRejected() {
        val engine = AudioEngine.create()
        val error = assertFailsWith<DeviceNotFoundException> {
            engine.openStream(AudioStreamConfig(deviceId = 1_000_000))
        }
        assertTrue(error.message!!.contains("1000000"))
        engine.close()
    }
}
