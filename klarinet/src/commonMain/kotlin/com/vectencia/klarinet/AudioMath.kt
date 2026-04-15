package com.vectencia.klarinet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

object AudioMath {

    fun rms(buffer: FloatArray, offset: Int = 0, length: Int = buffer.size - offset): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (i in offset until offset + length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / length).toFloat()
    }

    fun peak(buffer: FloatArray, offset: Int = 0, length: Int = buffer.size - offset): Float {
        var max = 0f
        for (i in offset until offset + length) {
            val absVal = abs(buffer[i])
            if (absVal > max) max = absVal
        }
        return max
    }

    fun linearToDb(linear: Float): Float {
        return if (linear <= 0f) Float.NEGATIVE_INFINITY
        else 20f * log10(linear)
    }

    fun dbToLinear(db: Float): Float {
        return 10f.pow(db / 20f)
    }

    fun hannWindow(size: Int): FloatArray {
        if (size <= 1) return FloatArray(size) { 1f }
        return FloatArray(size) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (size - 1)))).toFloat()
        }
    }
}
