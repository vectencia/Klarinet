package com.vectencia.klarinet

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SineFillTest {
    @Test
    fun fillCallbackSineMatchesFrequencyRateAndAmplitude() {
        val sampleRate = 48_000
        val frequency = 440f
        val amplitude = 0.5f
        val frames = 96
        val buffer = FloatArray(frames)
        val phase = fillCallbackSine(buffer, frames, 0f, frequency, sampleRate, amplitude)
        assertTrue(phase > 0f)
        var expected = 0.0
        val inc = 2.0 * PI * frequency / sampleRate
        val twoPi = 2.0 * PI
        for (index in buffer.indices) {
            assertEquals((sin(expected) * amplitude).toFloat(), buffer[index], 1.0e-4f)
            expected += inc
            if (expected > twoPi) expected -= twoPi
        }
    }
}
