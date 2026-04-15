package com.vectencia.klarinet

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FftTest {

    @Test
    fun sizeNotPowerOfTwoThrows() {
        assertFailsWith<IllegalArgumentException> { Fft(100) }
    }

    @Test
    fun sizeZeroThrows() {
        assertFailsWith<IllegalArgumentException> { Fft(0) }
    }

    @Test
    fun silenceProducesZeroSpectrum() {
        val fft = Fft(256)
        val real = FloatArray(256)
        val imag = FloatArray(256)
        fft.forward(real, imag)
        val spectrum = fft.magnitudeSpectrum(real, imag)
        for (bin in spectrum) {
            assertEquals(0f, bin, 0.001f)
        }
    }

    @Test
    fun dcSignalOnlyInBinZero() {
        val fft = Fft(256)
        val real = FloatArray(256) { 1f }
        val imag = FloatArray(256)
        fft.forward(real, imag)
        val spectrum = fft.magnitudeSpectrum(real, imag)
        assertTrue(spectrum[0] > 200f, "DC bin should be large, got ${spectrum[0]}")
        for (i in 1 until spectrum.size) {
            assertTrue(spectrum[i] < 1f, "Non-DC bin $i should be near zero, got ${spectrum[i]}")
        }
    }

    @Test
    fun pureOneBinSineDetectedCorrectly() {
        val size = 1024
        val fft = Fft(size)
        val targetBin = 10
        val real = FloatArray(size) { i ->
            sin(2.0 * PI * targetBin * i / size).toFloat()
        }
        val imag = FloatArray(size)
        fft.forward(real, imag)
        val spectrum = fft.magnitudeSpectrum(real, imag)

        var maxBin = 0
        var maxVal = 0f
        for (i in 1 until spectrum.size) {
            if (spectrum[i] > maxVal) {
                maxVal = spectrum[i]
                maxBin = i
            }
        }
        assertEquals(targetBin, maxBin, "Peak should be at bin $targetBin, found at $maxBin")
        assertTrue(maxVal > size / 4, "Peak magnitude should be substantial, got $maxVal")
    }

    @Test
    fun magnitudeSpectrumSizeIsHalfPlusOne() {
        val fft = Fft(512)
        val real = FloatArray(512)
        val imag = FloatArray(512)
        fft.forward(real, imag)
        val spectrum = fft.magnitudeSpectrum(real, imag)
        assertEquals(257, spectrum.size)
    }

    @Test
    fun twoSinesResolvedSeparately() {
        val size = 1024
        val fft = Fft(size)
        val bin1 = 10
        val bin2 = 50
        val real = FloatArray(size) { i ->
            (sin(2.0 * PI * bin1 * i / size) + sin(2.0 * PI * bin2 * i / size)).toFloat()
        }
        val imag = FloatArray(size)
        fft.forward(real, imag)
        val spectrum = fft.magnitudeSpectrum(real, imag)

        val threshold = spectrum.max() * 0.3f
        val peaks = (1 until spectrum.size).filter { spectrum[it] > threshold }
        assertTrue(peaks.contains(bin1), "Should detect peak at bin $bin1")
        assertTrue(peaks.contains(bin2), "Should detect peak at bin $bin2")
    }
}
