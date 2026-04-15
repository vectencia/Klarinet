package com.vectencia.klarinet

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioMathTest {

    private val delta = 0.001f

    @Test
    fun rmsSilenceIsZero() {
        val buffer = FloatArray(1024) { 0f }
        assertEquals(0f, AudioMath.rms(buffer), delta)
    }

    @Test
    fun rmsFullScaleSineIsCorrect() {
        val buffer = FloatArray(1024) { i ->
            kotlin.math.sin(2.0 * kotlin.math.PI * i / 1024).toFloat()
        }
        val expected = 1f / sqrt(2f)
        assertEquals(expected, AudioMath.rms(buffer), 0.01f)
    }

    @Test
    fun rmsDcSignal() {
        val buffer = FloatArray(100) { 0.5f }
        assertEquals(0.5f, AudioMath.rms(buffer), delta)
    }

    @Test
    fun rmsWithOffsetAndLength() {
        val buffer = FloatArray(100) { 0f }
        for (i in 10 until 20) buffer[i] = 1f
        val rms = AudioMath.rms(buffer, offset = 10, length = 10)
        assertEquals(1f, rms, delta)
    }

    @Test
    fun peakSilenceIsZero() {
        val buffer = FloatArray(1024) { 0f }
        assertEquals(0f, AudioMath.peak(buffer))
    }

    @Test
    fun peakFindsMaxAbsoluteValue() {
        val buffer = FloatArray(100) { 0.1f }
        buffer[50] = -0.9f
        assertEquals(0.9f, AudioMath.peak(buffer), delta)
    }

    @Test
    fun peakWithOffsetAndLength() {
        val buffer = FloatArray(100) { 0.1f }
        buffer[50] = 0.8f
        val peak = AudioMath.peak(buffer, offset = 0, length = 50)
        assertEquals(0.1f, peak, delta)
    }

    @Test
    fun linearToDbFullScale() {
        assertEquals(0f, AudioMath.linearToDb(1f), delta)
    }

    @Test
    fun linearToDbHalf() {
        assertEquals(-6.021f, AudioMath.linearToDb(0.5f), 0.01f)
    }

    @Test
    fun linearToDbSilence() {
        assertTrue(AudioMath.linearToDb(0f).isInfinite())
    }

    @Test
    fun dbToLinearZeroDb() {
        assertEquals(1f, AudioMath.dbToLinear(0f), delta)
    }

    @Test
    fun dbToLinearMinusSix() {
        assertEquals(0.5f, AudioMath.dbToLinear(-6.021f), 0.01f)
    }

    @Test
    fun dbRoundTrip() {
        val original = 0.75f
        val db = AudioMath.linearToDb(original)
        val back = AudioMath.dbToLinear(db)
        assertEquals(original, back, 0.001f)
    }

    @Test
    fun hannWindowEndsAreZero() {
        val window = AudioMath.hannWindow(256)
        assertEquals(256, window.size)
        assertEquals(0f, window[0], delta)
        assertEquals(0f, window[255], delta)
    }

    @Test
    fun hannWindowCenterIsOne() {
        val window = AudioMath.hannWindow(256)
        assertEquals(1f, window[127], 0.01f)
    }

    @Test
    fun hannWindowIsSymmetric() {
        val window = AudioMath.hannWindow(256)
        for (i in 0 until 128) {
            assertEquals(window[i], window[255 - i], delta)
        }
    }
}
