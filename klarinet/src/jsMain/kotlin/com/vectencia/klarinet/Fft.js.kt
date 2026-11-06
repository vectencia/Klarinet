package com.vectencia.klarinet

import kotlin.math.sqrt

actual class Fft actual constructor(size: Int) {
    actual val size: Int = size
    private val handle: Int

    init {
        require(size > 0 && size and (size - 1) == 0) {
            "FFT size must be a positive power of 2, got $size"
        }
        handle = wasmFftCreate(size)
        require(handle != 0) {
            "FFT size must be a positive power of 2, got $size"
        }
        registerWasmHandle(this, WASM_KIND_FFT, handle)
    }

    actual fun forward(real: FloatArray, imag: FloatArray) {
        require(real.size >= size && imag.size >= size) {
            "Arrays must be at least $size elements"
        }
        wasmFftForward(handle, real, imag, size)
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
        wasmFftMagnitude(handle, real, imag, spectrum)
        return spectrum
    }
}
