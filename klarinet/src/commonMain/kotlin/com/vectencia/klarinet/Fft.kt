package com.vectencia.klarinet

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Fft(val size: Int) {

    init {
        require(size > 0 && size and (size - 1) == 0) {
            "FFT size must be a positive power of 2, got $size"
        }
    }

    private val halfSize = size / 2
    private val cosTable: FloatArray
    private val sinTable: FloatArray

    init {
        cosTable = FloatArray(halfSize) { i ->
            cos(2.0 * PI * i / size).toFloat()
        }
        sinTable = FloatArray(halfSize) { i ->
            sin(2.0 * PI * i / size).toFloat()
        }
    }

    fun forward(real: FloatArray, imag: FloatArray) {
        require(real.size >= size && imag.size >= size) {
            "Arrays must be at least $size elements"
        }

        var j = 0
        for (i in 0 until size - 1) {
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
            var k = halfSize
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var step = 1
        while (step < size) {
            val doubleStep = step shl 1
            val tableStep = halfSize / step
            for (group in 0 until size step doubleStep) {
                var tableIdx = 0
                for (pair in group until group + step) {
                    val match = pair + step
                    val tReal = cosTable[tableIdx] * real[match] + sinTable[tableIdx] * imag[match]
                    val tImag = cosTable[tableIdx] * imag[match] - sinTable[tableIdx] * real[match]
                    real[match] = real[pair] - tReal
                    imag[match] = imag[pair] - tImag
                    real[pair] = real[pair] + tReal
                    imag[pair] = imag[pair] + tImag
                    tableIdx += tableStep
                }
            }
            step = doubleStep
        }
    }

    fun magnitudeSpectrum(real: FloatArray, imag: FloatArray): FloatArray {
        val spectrum = FloatArray(halfSize + 1)
        for (i in 0..halfSize) {
            spectrum[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
        return spectrum
    }
}
