@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import klarinet_dsp.klarinet_math_db_to_linear
import klarinet_dsp.klarinet_math_hann
import klarinet_dsp.klarinet_math_linear_to_db
import klarinet_dsp.klarinet_math_peak
import klarinet_dsp.klarinet_math_rms
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

actual object AudioMath {
    actual fun rms(buffer: FloatArray, offset: Int, length: Int): Float {
        if (length <= 0) return 0f
        if (outOfRange(buffer, offset, length)) return kotlinRms(buffer, offset, length)
        return buffer.usePinned { pinned ->
            klarinet_math_rms(pinned.addressOf(0), offset, length)
        }
    }

    actual fun peak(buffer: FloatArray, offset: Int, length: Int): Float {
        if (length == 0) return 0f
        if (outOfRange(buffer, offset, length)) return kotlinPeak(buffer, offset, length)
        return buffer.usePinned { pinned ->
            klarinet_math_peak(pinned.addressOf(0), offset, length)
        }
    }

    actual fun linearToDb(linear: Float): Float = klarinet_math_linear_to_db(linear)

    actual fun dbToLinear(db: Float): Float = klarinet_math_db_to_linear(db)

    actual fun hannWindow(size: Int): FloatArray {
        val out = FloatArray(size)
        if (size <= 1) {
            out.fill(1f)
            return out
        }
        out.usePinned { pinned ->
            klarinet_math_hann(pinned.addressOf(0), size)
        }
        return out
    }
}

private fun outOfRange(buffer: FloatArray, offset: Int, length: Int): Boolean {
    if (offset < 0 || length < 0) return true
    if (offset > Int.MAX_VALUE - length) return true
    return offset + length > buffer.size
}

private fun kotlinRms(buffer: FloatArray, offset: Int, length: Int): Float {
    var sum = 0.0
    for (index in offset until offset + length) {
        val sample = buffer[index].toDouble()
        sum += sample * sample
    }
    return sqrt(sum / length).toFloat()
}

private fun kotlinPeak(buffer: FloatArray, offset: Int, length: Int): Float {
    var max = 0f
    for (index in offset until offset + length) {
        val absVal = abs(buffer[index])
        if (absVal > max) max = absVal
    }
    return max
}
