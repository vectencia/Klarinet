package com.vectencia.klarinet

actual object AudioMath {
    actual fun rms(buffer: FloatArray, offset: Int, length: Int): Float {
        if (length <= 0) return 0f
        buffer[offset]
        buffer[offset + length - 1]
        return JniBridge.nativeMathRms(buffer, offset, length)
    }

    actual fun peak(buffer: FloatArray, offset: Int, length: Int): Float {
        if (length <= 0) return 0f
        buffer[offset]
        buffer[offset + length - 1]
        return JniBridge.nativeMathPeak(buffer, offset, length)
    }

    actual fun linearToDb(linear: Float): Float = JniBridge.nativeMathLinearToDb(linear)

    actual fun dbToLinear(db: Float): Float = JniBridge.nativeMathDbToLinear(db)

    actual fun hannWindow(size: Int): FloatArray {
        val out = FloatArray(size)
        if (size <= 1) {
            out.fill(1f)
            return out
        }
        JniBridge.nativeMathHann(out, size)
        return out
    }
}
