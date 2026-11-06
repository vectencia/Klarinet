package com.vectencia.klarinet

expect class Fft(size: Int) {
    val size: Int

    fun forward(real: FloatArray, imag: FloatArray)
    fun magnitudeSpectrum(real: FloatArray, imag: FloatArray): FloatArray
}
