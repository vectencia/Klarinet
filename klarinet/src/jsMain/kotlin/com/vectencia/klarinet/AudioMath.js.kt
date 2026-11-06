package com.vectencia.klarinet

actual object AudioMath {
    actual fun rms(buffer: FloatArray, offset: Int, length: Int): Float {
        if (length <= 0) return 0f
        if (outOfRange(buffer, offset, length)) return kotlinRms(buffer, offset, length)
        return wasmMathRms(buffer, offset, length)
    }

    actual fun peak(buffer: FloatArray, offset: Int, length: Int): Float {
        if (length == 0) return 0f
        if (outOfRange(buffer, offset, length)) return kotlinPeak(buffer, offset, length)
        return wasmMathPeak(buffer, offset, length)
    }

    actual fun linearToDb(linear: Float): Float = wasmMathLinearToDb(linear)

    actual fun dbToLinear(db: Float): Float = wasmMathDbToLinear(db)

    actual fun hannWindow(size: Int): FloatArray {
        val out = FloatArray(size)
        if (size <= 1) {
            out.fill(1f)
            return out
        }
        wasmMathHann(out)
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
    for (i in offset until offset + length) {
        val sample = buffer[i].toDouble()
        sum += sample * sample
    }
    return kotlin.math.sqrt(sum / length).toFloat()
}

private fun kotlinPeak(buffer: FloatArray, offset: Int, length: Int): Float {
    var max = 0f
    for (i in offset until offset + length) {
        val absVal = kotlin.math.abs(buffer[i])
        if (absVal > max) max = absVal
    }
    return max
}
