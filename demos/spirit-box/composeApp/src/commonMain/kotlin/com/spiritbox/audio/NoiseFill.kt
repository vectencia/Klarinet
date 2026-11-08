package com.spiritbox.audio

internal class NoiseFill {
    private var seed = 1

    fun fill(buffer: FloatArray, numFrames: Int): Int {
        var state = seed
        for (i in 0 until numFrames) {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            buffer[i] = (state and 0xFFFF) * (1f / 32_768f) - 1f
        }
        seed = state
        return numFrames
    }
}
