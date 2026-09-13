package com.vectencia.klarinet

import kotlin.math.abs
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinearResamplerTest {

    @Test
    fun sameRateIsCopy() {
        val source = floatArrayOf(0f, 0.25f, -0.5f, 1f)
        val dest = FloatArray(8)
        val got = LinearResampler(44100, 1).render(44100, dest, 4, sequentialReader(source, 1))
        assertEquals(4, got)
        for (i in 0 until 4) {
            assertEquals(source[i], dest[i], 0.0001f)
        }
    }

    @Test
    fun stereoSameRatePreservesInterleave() {
        val source = floatArrayOf(0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f)
        val dest = FloatArray(8)
        val got = LinearResampler(48000, 2).render(48000, dest, 3, sequentialReader(source, 2))
        assertEquals(3, got)
        for (i in source.indices) {
            assertEquals(source[i], dest[i], 0.0001f)
        }
    }

    @Test
    fun constantSignalStaysConstantWhenUpsampling44100To48000() {
        val sourceFrames = 44100
        val source = FloatArray(sourceFrames) { 0.5f }
        val dest = FloatArray(48000)
        val got = LinearResampler(44100, 1).render(
            destRate = 48000,
            dest = dest,
            destFrames = 48000,
            readFrames = sequentialReader(source, 1),
        )
        assertTrue(got in 47900..48000, "got $got output frames")
        for (i in 0 until got) {
            assertTrue(abs(dest[i] - 0.5f) < 1e-5f, "dest[$i]=${dest[i]}")
        }
    }

    @Test
    fun upsampleDoublesDurationInFrames() {
        val source = floatArrayOf(0f, 1f)
        val dest = FloatArray(8)
        val got = LinearResampler(2, 1).render(4, dest, 4, sequentialReader(source, 1))
        assertTrue(got >= 3, "got $got")
        assertEquals(0f, dest[0], 0.001f)
        assertEquals(0.5f, dest[1], 0.001f)
        assertEquals(1f, dest[2], 0.001f)
    }

    @Test
    fun chunkedUpsampleKeepsPhaseAcrossCalls() {
        val source = FloatArray(4410) { 0.25f }
        val reader = sequentialReader(source, 1)
        val resampler = LinearResampler(44100, 1)
        val first = FloatArray(512)
        val second = FloatArray(512)
        val a = resampler.render(48000, first, 512, reader)
        val b = resampler.render(48000, second, 512, reader)
        assertEquals(512, a)
        assertEquals(512, b)
        for (i in 0 until a) {
            assertTrue(abs(first[i] - 0.25f) < 1e-5f)
        }
        for (i in 0 until b) {
            assertTrue(abs(second[i] - 0.25f) < 1e-5f)
        }
    }

    @Test
    fun emptySourceReturnsZero() {
        val dest = FloatArray(16)
        val got = LinearResampler(44100, 1).render(48000, dest, 16) { FloatArray(0) }
        assertEquals(0, got)
    }

    private fun sequentialReader(source: FloatArray, channels: Int): (Int) -> FloatArray {
        var offset = 0
        return { maxFrames ->
            val remainingFrames = (source.size - offset) / channels
            val n = min(maxFrames, remainingFrames)
            if (n <= 0) {
                FloatArray(0)
            } else {
                val end = offset + n * channels
                val chunk = source.copyOfRange(offset, end)
                offset = end
                chunk
            }
        }
    }
}
