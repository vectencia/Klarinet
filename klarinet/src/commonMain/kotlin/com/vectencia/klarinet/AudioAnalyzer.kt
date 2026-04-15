package com.vectencia.klarinet

import kotlin.math.sqrt

class AudioAnalyzer(
    val fftSize: Int = 1024,
    val sampleRate: Int = 44100,
) {
    init {
        require(fftSize > 0 && fftSize and (fftSize - 1) == 0) {
            "fftSize must be a positive power of 2, got $fftSize"
        }
    }

    private val fft = Fft(fftSize)
    private val window = AudioMath.hannWindow(fftSize)

    val frequencyResolution: Float = sampleRate.toFloat() / fftSize

    fun analyze(
        buffer: FloatArray,
        numFrames: Int,
        channelCount: Int = 1,
    ): AudioAnalysisResult {
        val mono = if (channelCount == 1) {
            buffer.copyOf(numFrames)
        } else {
            averageChannels(buffer, numFrames, channelCount)
        }

        val monoLength = mono.size
        val rms = AudioMath.rms(mono, 0, monoLength)
        val peak = AudioMath.peak(mono, 0, monoLength)

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val framesToWindow = minOf(monoLength, fftSize)
        for (i in 0 until framesToWindow) {
            real[i] = mono[i] * window[i]
        }

        fft.forward(real, imag)
        val spectrum = fft.magnitudeSpectrum(real, imag)

        return AudioAnalysisResult(
            rmsLevel = rms,
            rmsDb = AudioMath.linearToDb(rms),
            peakLevel = peak,
            peakDb = AudioMath.linearToDb(peak),
            magnitudeSpectrum = spectrum,
            frequencyResolution = frequencyResolution,
            sampleRate = sampleRate,
            fftSize = fftSize,
        )
    }

    fun bandEnergy(result: AudioAnalysisResult, lowHz: Float, highHz: Float): Float {
        val lowBin = maxOf(0, (lowHz / result.frequencyResolution).toInt())
        val highBin = minOf(
            result.magnitudeSpectrum.size - 1,
            (highHz / result.frequencyResolution).toInt(),
        )
        if (highBin < lowBin) return 0f
        var energy = 0f
        for (i in lowBin..highBin) {
            energy += result.magnitudeSpectrum[i] * result.magnitudeSpectrum[i]
        }
        return sqrt(energy / (highBin - lowBin + 1))
    }

    private fun averageChannels(
        buffer: FloatArray,
        numFrames: Int,
        channelCount: Int,
    ): FloatArray {
        val mono = FloatArray(numFrames)
        for (frame in 0 until numFrames) {
            var sum = 0f
            for (ch in 0 until channelCount) {
                sum += buffer[frame * channelCount + ch]
            }
            mono[frame] = sum / channelCount
        }
        return mono
    }
}
