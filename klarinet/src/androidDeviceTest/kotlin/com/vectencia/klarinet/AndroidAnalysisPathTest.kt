package com.vectencia.klarinet

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Runs on a device or emulator against `libklarinet-jni.so`.
 * `Fft`, `AudioMath`, `AudioAnalyzer`, and `LinearResampler` call that library.
 */
class AndroidAnalysisPathTest {
    @Test
    fun fftRejectsNonPowerOfTwoAndFindsBin() {
        assertFailsWith<IllegalArgumentException> { Fft(100) }
        val size = 1024
        val fft = Fft(size)
        val targetBin = 10
        val real = FloatArray(size) { i ->
            sin(2.0 * PI * targetBin * i / size).toFloat()
        }
        val imag = FloatArray(size)
        fft.forward(real, imag)
        val spectrum = fft.magnitudeSpectrum(real, imag)
        assertEquals(size / 2 + 1, spectrum.size)
        var maxBin = 0
        var maxVal = 0f
        for (i in 1 until spectrum.size) {
            if (spectrum[i] > maxVal) {
                maxVal = spectrum[i]
                maxBin = i
            }
        }
        assertEquals(targetBin, maxBin)
        assertTrue(maxVal > size / 4, "peak $maxVal")
    }

    @Test
    fun peakPastTheEndThrows() {
        assertFailsWith<IndexOutOfBoundsException> {
            AudioMath.peak(floatArrayOf(0.1f, 0.9f), offset = 0, length = 4)
        }
    }

    @Test
    fun audioMathMatchesSineRmsPeakAndHann() {
        val sine = FloatArray(1024) { i -> sin(2.0 * PI * i / 1024).toFloat() }
        assertEquals(1f / sqrt(2f), AudioMath.rms(sine), 0.01f)
        assertEquals(0f, AudioMath.linearToDb(1f), 0.001f)
        assertEquals(1f, AudioMath.dbToLinear(0f), 0.001f)
        assertTrue(AudioMath.linearToDb(0f).isInfinite())
        val window = AudioMath.hannWindow(256)
        assertEquals(0f, window[0], 0.001f)
        assertEquals(0f, window[255], 0.001f)
        assertEquals(1f, window[127], 0.01f)
        val peaked = FloatArray(8) { 0.1f }
        peaked[3] = -0.9f
        assertEquals(0.9f, AudioMath.peak(peaked), 0.001f)
    }

    @Test
    fun analyzerPlacesSineEnergyInBand() {
        val sampleRate = 44100
        val fftSize = 1024
        val analyzer = AudioAnalyzer(fftSize = fftSize, sampleRate = sampleRate)
        val buffer = FloatArray(fftSize) { i ->
            sin(2.0 * PI * 300.0 * i / sampleRate).toFloat()
        }
        val result = analyzer.analyze(buffer, fftSize)
        assertEquals(fftSize / 2 + 1, result.magnitudeSpectrum.size)
        assertEquals(0.707f, result.rmsLevel, 0.05f)
        val low = analyzer.bandEnergy(result, 100f, 500f)
        val high = analyzer.bandEnergy(result, 2000f, 4000f)
        assertTrue(low > high * 10, "low=$low high=$high")
    }

    @Test
    fun resamplerCopiesSameRateAndUpsamples() {
        val source = floatArrayOf(0f, 0.25f, -0.5f, 1f)
        val dest = FloatArray(8)
        val copied = LinearResampler(44100, 1).render(44100, dest, 4, reader(source, 1))
        assertEquals(4, copied)
        for (i in 0 until 4) {
            assertEquals(source[i], dest[i], 0.0001f)
        }
        val up = FloatArray(8)
        val got = LinearResampler(2, 1).render(4, up, 4, reader(floatArrayOf(0f, 1f), 1))
        assertTrue(got >= 3, "got $got")
        assertEquals(0f, up[0], 0.001f)
        assertEquals(0.5f, up[1], 0.001f)
        assertEquals(1f, up[2], 0.001f)
    }

    private fun reader(source: FloatArray, channels: Int): (Int) -> FloatArray {
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
