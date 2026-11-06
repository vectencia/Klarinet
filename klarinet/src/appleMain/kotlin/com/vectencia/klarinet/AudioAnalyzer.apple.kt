@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.vectencia.klarinet

import klarinet_dsp.klarinet_analyzer_analyze
import klarinet_dsp.klarinet_analyzer_band_energy
import klarinet_dsp.klarinet_analyzer_create
import klarinet_dsp.klarinet_analyzer_destroy
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

actual class AudioAnalyzer actual constructor(
    fftSize: Int,
    sampleRate: Int,
) {
    actual val fftSize: Int = fftSize
    actual val sampleRate: Int = sampleRate
    actual val frequencyResolution: Float = sampleRate.toFloat() / fftSize

    private val handle: COpaquePointer = klarinet_analyzer_create(fftSize, sampleRate)
        ?: throw IllegalArgumentException("fftSize must be a positive power of 2, got $fftSize")
    private val cleaner = createCleaner(handle, ::klarinet_analyzer_destroy)

    init {
        require(fftSize > 0 && fftSize and (fftSize - 1) == 0) {
            "fftSize must be a positive power of 2, got $fftSize"
        }
        cleaner
    }

    actual fun analyze(
        buffer: FloatArray,
        numFrames: Int,
        channelCount: Int,
    ): AudioAnalysisResult {
        if (numFrames < 0) {
            if (channelCount == 1) buffer.copyOf(numFrames) else FloatArray(numFrames)
        }
        val input: FloatArray
        val frames: Int
        val channels: Int
        if (channelCount > 1 && numFrames > 0 &&
            (numFrames > Int.MAX_VALUE / channelCount || numFrames * channelCount > buffer.size)
        ) {
            input = averageChannels(buffer, numFrames, channelCount)
            frames = input.size
            channels = 1
        } else {
            input = buffer
            frames = numFrames
            channels = channelCount
        }
        val spectrum = FloatArray(fftSize / 2 + 1)
        val levels = FloatArray(4)
        spectrum.usePinned { spectrumPin ->
            levels.usePinned { levelPin ->
                if (input.isEmpty()) {
                    klarinet_analyzer_analyze(
                        handle,
                        null,
                        0,
                        frames,
                        channels,
                        spectrumPin.addressOf(0),
                        spectrum.size,
                        levelPin.addressOf(0),
                        levelPin.addressOf(1),
                        levelPin.addressOf(2),
                        levelPin.addressOf(3),
                    )
                } else {
                    input.usePinned { inputPin ->
                        klarinet_analyzer_analyze(
                            handle,
                            inputPin.addressOf(0),
                            input.size,
                            frames,
                            channels,
                            spectrumPin.addressOf(0),
                            spectrum.size,
                            levelPin.addressOf(0),
                            levelPin.addressOf(1),
                            levelPin.addressOf(2),
                            levelPin.addressOf(3),
                        )
                    }
                }
            }
        }
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
        val spectrum = result.magnitudeSpectrum
        if (spectrum.isEmpty()) return 0f
        return spectrum.usePinned { pinned ->
            klarinet_analyzer_band_energy(
                pinned.addressOf(0),
                spectrum.size,
                result.frequencyResolution,
                lowHz,
                highHz,
            )
        }
    }

    private fun averageChannels(
        buffer: FloatArray,
        numFrames: Int,
        channelCount: Int,
    ): FloatArray {
        val mono = FloatArray(numFrames)
        for (frame in 0 until numFrames) {
            var sum = 0f
            for (channel in 0 until channelCount) {
                sum += buffer[frame * channelCount + channel]
            }
            mono[frame] = sum / channelCount
        }
        return mono
    }
}
