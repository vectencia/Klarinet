package com.vectencia.klarinet

internal class ResamplerRead(
    private val readFrames: (Int) -> FloatArray,
) {
    fun pull(maxFrames: Int): FloatArray = readFrames(maxFrames)
}
