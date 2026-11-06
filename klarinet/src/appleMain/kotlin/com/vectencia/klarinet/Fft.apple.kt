@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.vectencia.klarinet

import klarinet_dsp.klarinet_fft_create
import klarinet_dsp.klarinet_fft_destroy
import klarinet_dsp.klarinet_fft_forward
import klarinet_dsp.klarinet_fft_magnitude
import kotlin.math.sqrt
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

actual class Fft actual constructor(size: Int) {
    actual val size: Int = size
    private val handle: COpaquePointer = klarinet_fft_create(size)
        ?: throw IllegalArgumentException("FFT size must be a positive power of 2, got $size")
    private val cleaner = createCleaner(handle, ::klarinet_fft_destroy)

    init {
        require(size > 0 && size and (size - 1) == 0) {
            "FFT size must be a positive power of 2, got $size"
        }
        cleaner
    }

    actual fun forward(real: FloatArray, imag: FloatArray) {
        require(real.size >= size && imag.size >= size) {
            "Arrays must be at least $size elements"
        }
        real.usePinned { realPin ->
            imag.usePinned { imagPin ->
                klarinet_fft_forward(
                    handle,
                    realPin.addressOf(0),
                    imagPin.addressOf(0),
                    size,
                )
            }
        }
    }

    actual fun magnitudeSpectrum(real: FloatArray, imag: FloatArray): FloatArray {
        val bins = size / 2 + 1
        if (real.size < bins || imag.size < bins) {
            return FloatArray(bins) { index ->
                val sampleReal = real[index]
                val sampleImag = imag[index]
                sqrt(sampleReal * sampleReal + sampleImag * sampleImag)
            }
        }
        val spectrum = FloatArray(bins)
        real.usePinned { realPin ->
            imag.usePinned { imagPin ->
                spectrum.usePinned { outPin ->
                    klarinet_fft_magnitude(
                        handle,
                        realPin.addressOf(0),
                        imagPin.addressOf(0),
                        outPin.addressOf(0),
                        bins,
                    )
                }
            }
        }
        return spectrum
    }
}
