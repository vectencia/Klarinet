package com.vectencia.klarinet

actual class Fft actual constructor(size: Int) {
    actual val size: Int = size
    private val handle: Long

    init {
        require(size > 0 && size and (size - 1) == 0) {
            "FFT size must be a positive power of 2, got $size"
        }
        handle = JniBridge.nativeFftCreate(size)
        require(handle != 0L) {
            "FFT size must be a positive power of 2, got $size"
        }
        NativeHandleCleaner.register(this, handle, JniBridge::nativeFftDestroy)
    }

    actual fun forward(real: FloatArray, imag: FloatArray) {
        require(real.size >= size && imag.size >= size) {
            "Arrays must be at least $size elements"
        }
        JniBridge.nativeFftForward(handle, real, imag, size)
    }

    actual fun magnitudeSpectrum(real: FloatArray, imag: FloatArray): FloatArray {
        val bins = size / 2 + 1
        val spectrum = FloatArray(bins)
        real[bins - 1]
        imag[bins - 1]
        JniBridge.nativeFftMagnitude(handle, real, imag, spectrum, bins)
        return spectrum
    }
}
