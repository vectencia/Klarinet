package com.vectencia.klarinet

actual class AudioAnalyzer actual constructor(
    fftSize: Int,
    sampleRate: Int,
) {
    actual val fftSize: Int = fftSize
    actual val sampleRate: Int = sampleRate
    actual val frequencyResolution: Float = sampleRate.toFloat() / fftSize

    private val handle: Long

    init {
        require(fftSize > 0 && fftSize and (fftSize - 1) == 0) {
            "fftSize must be a positive power of 2, got $fftSize"
        }
        handle = JniBridge.nativeAnalyzerCreate(fftSize, sampleRate)
        require(handle != 0L) {
            "fftSize must be a positive power of 2, got $fftSize"
        }
        NativeHandleCleaner.register(this, handle, JniBridge::nativeAnalyzerDestroy)
    }

    actual fun analyze(
        buffer: FloatArray,
        numFrames: Int,
        channelCount: Int,
    ): AudioAnalysisResult {
        if (numFrames < 0) {
            throw NegativeArraySizeException()
        }
        if (channelCount > 1 && numFrames > 0) {
            val needed = numFrames * channelCount
            if (needed > buffer.size) {
                buffer[buffer.size]
            }
        }
        val spectrum = FloatArray(fftSize / 2 + 1)
        val levels = FloatArray(4)
        JniBridge.nativeAnalyzerAnalyze(
            handle = handle,
            buffer = buffer,
            bufferSamples = buffer.size,
            numFrames = numFrames,
            channelCount = channelCount,
            spectrum = spectrum,
            spectrumLen = spectrum.size,
            levels = levels,
        )
        return AudioAnalysisResult(
            rmsLevel = levels[0],
            rmsDb = levels[1],
            peakLevel = levels[2],
            peakDb = levels[3],
            magnitudeSpectrum = spectrum,
            frequencyResolution = frequencyResolution,
            sampleRate = sampleRate,
            fftSize = fftSize,
        )
    }

    actual fun bandEnergy(result: AudioAnalysisResult, lowHz: Float, highHz: Float): Float {
        return JniBridge.nativeAnalyzerBandEnergy(
            spectrum = result.magnitudeSpectrum,
            spectrumLen = result.magnitudeSpectrum.size,
            resolution = result.frequencyResolution,
            lowHz = lowHz,
            highHz = highHz,
        )
    }
}
