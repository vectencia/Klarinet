package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AudioStreamPushIoTest {

    @Test
    fun writeThrowsUnsupportedOnJvm() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            val error = assertFailsWith<StreamOperationException> {
                stream.write(FloatArray(64), 64)
            }
            assertTrue(error.message!!.contains("not supported"))
            stream.close()
        }
    }

    @Test
    fun readThrowsUnsupportedOnJvm() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            val error = assertFailsWith<StreamOperationException> {
                stream.read(FloatArray(64), 64)
            }
            assertTrue(error.message!!.contains("not supported"))
            stream.close()
        }
    }

    @Test
    fun writeOnClosedStreamThrowsReleased() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            stream.close()
            assertFailsWith<ResourceReleasedException> {
                stream.write(FloatArray(8), 8)
            }
        }
    }
}
