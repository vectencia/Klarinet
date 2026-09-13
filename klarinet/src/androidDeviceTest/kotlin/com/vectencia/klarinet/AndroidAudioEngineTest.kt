package com.vectencia.klarinet

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun outputCallbackUpdatesPeakLevel() {
        val engine = AudioEngine.create()
        val sampleRate = 48000
        val stream = engine.openStream(
            config = AudioStreamConfig(sampleRate = sampleRate, channelCount = 1),
            callback = object : AudioStreamCallback {
                private var phase = 0.0
                override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                    val inc = 2.0 * PI * 440.0 / sampleRate
                    for (i in 0 until numFrames) {
                        buffer[i] = (sin(phase) * 0.5).toFloat()
                        phase += inc
                    }
                    return numFrames
                }
            },
        )
        stream.start()
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline && stream.peakLevel < 0.4f) {
            Thread.sleep(20)
        }
        assertTrue(stream.peakLevel >= 0.4f, "peakLevel was ${stream.peakLevel}")
        stream.stop()
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

    @Test
    fun pauseBeforeStartThrows() {
        val engine = AudioEngine.create()
        val stream = engine.openStream(AudioStreamConfig())
        assertFailsWith<StreamOperationException> {
            stream.pause()
        }
        stream.close()
        engine.release()
    }

    @Test
    fun inputOpenThrowsWhenRecordAudioDenied() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AudioSessionManager().bind(context)
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Denied-path assertion only; skip when the instrumented app already has mic access.
            return
        }
        val engine = AudioEngine.create()
        try {
            assertFailsWith<PermissionException> {
                engine.openStream(AudioStreamConfig(direction = StreamDirection.INPUT))
            }
        } finally {
            engine.release()
        }
    }

    @Test
    fun integerAudioFormatThrowsUnsupportedFormat() {
        val engine = AudioEngine.create()
        try {
            assertFailsWith<UnsupportedFormatException> {
                engine.openStream(AudioStreamConfig(audioFormat = AudioFormat.PCM_I16))
            }
        } finally {
            engine.release()
        }
    }
}
