package com.vectencia.klarinet

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioAnalyzerTest {

    private val sampleRate = 44100
    private val fftSize = 1024
    private val analyzer = AudioAnalyzer(fftSize = fftSize, sampleRate = sampleRate)

    @Test
    fun silenceAnalysis() {
        val buffer = FloatArray(fftSize)
        val result = analyzer.analyze(buffer, fftSize)
        assertEquals(0f, result.rmsLevel, 0.001f)
        assertEquals(0f, result.peakLevel, 0.001f)
        assertTrue(result.rmsDb.isInfinite())
    }

    @Test
    fun sineWaveRmsAndPeak() {
        val buffer = FloatArray(fftSize) { i ->
            sin(2.0 * PI * 440.0 * i / sampleRate).toFloat()
        }
        val result = analyzer.analyze(buffer, fftSize)
        assertEquals(0.707f, result.rmsLevel, 0.05f)
        assertTrue(result.peakLevel > 0.99f)
    }

    @Test
    fun spectrumHasCorrectSize() {
        val buffer = FloatArray(fftSize)
        val result = analyzer.analyze(buffer, fftSize)
        assertEquals(fftSize / 2 + 1, result.magnitudeSpectrum.size)
    }

    @Test
    fun frequencyResolution() {
        val buffer = FloatArray(fftSize)
        val result = analyzer.analyze(buffer, fftSize)
        val expected = sampleRate.toFloat() / fftSize
        assertEquals(expected, result.frequencyResolution, 0.01f)
    }

    @Test
    fun sineDetectedInCorrectBand() {
        val freq = 300f
        val buffer = FloatArray(fftSize) { i ->
            sin(2.0 * PI * freq * i / sampleRate).toFloat()
        }
        val result = analyzer.analyze(buffer, fftSize)
        val snoringBand = analyzer.bandEnergy(result, 100f, 500f)
        val highBand = analyzer.bandEnergy(result, 2000f, 4000f)
        assertTrue(snoringBand > highBand * 10, "300Hz sine should have much more energy in 100-500Hz band than 2000-4000Hz")
    }

    @Test
    fun bandEnergyHighFreqSine() {
        val freq = 3000f
        val buffer = FloatArray(fftSize) { i ->
            sin(2.0 * PI * freq * i / sampleRate).toFloat()
        }
        val result = analyzer.analyze(buffer, fftSize)
        val snoringBand = analyzer.bandEnergy(result, 100f, 500f)
        val highBand = analyzer.bandEnergy(result, 2000f, 4000f)
        assertTrue(highBand > snoringBand * 10, "3000Hz sine should have much more energy in 2000-4000Hz band")
    }

    @Test
    fun multiChannelAveragedToMono() {
        val stereoBuffer = FloatArray(fftSize * 2) { i ->
            if (i % 2 == 0) 0.5f else -0.5f
        }
        val result = analyzer.analyze(stereoBuffer, fftSize, channelCount = 2)
        assertEquals(0f, result.rmsLevel, 0.01f)
    }

    @Test
    fun shortBufferZeroPadded() {
        val shortBuffer = FloatArray(100) { 0.5f }
        val result = analyzer.analyze(shortBuffer, 100)
        assertTrue(result.rmsLevel > 0f)
        assertEquals(fftSize / 2 + 1, result.magnitudeSpectrum.size)
    }
}
