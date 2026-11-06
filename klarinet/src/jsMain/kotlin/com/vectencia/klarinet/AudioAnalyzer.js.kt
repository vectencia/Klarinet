package com.vectencia.klarinet

actual class AudioAnalyzer actual constructor(
    fftSize: Int,
    sampleRate: Int,
) {
    actual val fftSize: Int = fftSize
    actual val sampleRate: Int = sampleRate
    actual val frequencyResolution: Float = sampleRate.toFloat() / fftSize

    private val handle: Int

    init {
        require(fftSize > 0 && fftSize and (fftSize - 1) == 0) {
            "fftSize must be a positive power of 2, got $fftSize"
        }
        handle = wasmAnalyzerCreate(fftSize, sampleRate)
        require(handle != 0) {
            "fftSize must be a positive power of 2, got $fftSize"
        }
        registerWasmHandle(this, WASM_KIND_ANALYZER, handle)
    }

    actual fun analyze(
        buffer: FloatArray,
        numFrames: Int,
        channelCount: Int,
    ): AudioAnalysisResult {
        if (numFrames < 0) {
            if (channelCount == 1) buffer.copyOf(numFrames) else FloatArray(numFrames)
        }
        if (channelCount > 1 && numFrames > 0 &&
            (numFrames > Int.MAX_VALUE / channelCount || numFrames * channelCount > buffer.size)
        ) {
            return analyzeShort(buffer, numFrames, channelCount)
        }
        val spectrum = FloatArray(fftSize / 2 + 1)
        val levels = FloatArray(4)
        wasmAnalyzerAnalyze(handle, buffer, numFrames, channelCount, spectrum, levels)
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
        return wasmBandEnergy(result.magnitudeSpectrum, result.frequencyResolution, lowHz, highHz)
    }

    private fun analyzeShort(
        buffer: FloatArray,
        numFrames: Int,
        channelCount: Int,
    ): AudioAnalysisResult {
        val mono = FloatArray(numFrames) { frame ->
            var sum = 0f
            for (channel in 0 until channelCount) {
                sum += buffer[frame * channelCount + channel]
            }
            sum / channelCount
        }
        val rms = AudioMath.rms(mono)
        val peak = AudioMath.peak(mono)
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val window = AudioMath.hannWindow(fftSize)
        val framesToWindow = minOf(mono.size, fftSize)
        for (index in 0 until framesToWindow) {
            real[index] = mono[index] * window[index]
        }
        val fft = Fft(fftSize)
        fft.forward(real, imag)
        return AudioAnalysisResult(
            rmsLevel = rms,
            rmsDb = AudioMath.linearToDb(rms),
            peakLevel = peak,
            peakDb = AudioMath.linearToDb(peak),
            magnitudeSpectrum = fft.magnitudeSpectrum(real, imag),
            frequencyResolution = frequencyResolution,
            sampleRate = sampleRate,
            fftSize = fftSize,
        )
    }
}
